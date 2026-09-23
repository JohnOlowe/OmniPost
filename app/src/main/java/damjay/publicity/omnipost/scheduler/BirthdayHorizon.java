package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Filter and group birthdays: today, this week, two weeks, this month, or everyone. */
public final class BirthdayHorizon {
  public static final int TODAY = 0;
  public static final int WEEK = 7;
  public static final int TWO_WEEKS = 14;
  public static final int MONTH = -2;
  public static final int ALL = -1;
  public static final int[] HORIZONS = { TODAY, WEEK, TWO_WEEKS, MONTH, ALL };

  private static final String[] MONTHS_US = new DateFormatSymbols(Locale.US).getMonths();

  public static final class Section {
    public final String title;
    public final String subtitle;
    public final List<Member> members;

    public Section(String title, String subtitle, List<Member> members) {
      this.title = title;
      this.subtitle = subtitle;
      this.members = members;
    }
  }

  static final class Hit {
    final Member member;
    final int year;
    final int month;
    final int day;
    final int julian;
    final int days;

    Hit(Member member, int year, int month, int day, int julian, int days) {
      this.member = member;
      this.year = year;
      this.month = month;
      this.day = day;
      this.julian = julian;
      this.days = days;
    }
  }

  private BirthdayHorizon() {}

  public static Calendar nextAt(Member member, Calendar now) {
    if (member == null || now == null) {
      return null;
    }
    int[] ymd = nextYmd(
      now.get(Calendar.YEAR),
      now.get(Calendar.MONTH) + 1,
      now.get(Calendar.DAY_OF_MONTH),
      member.birthMonth,
      member.birthDay);
    if (ymd == null) {
      return null;
    }
    Calendar c = (Calendar) now.clone();
    c.set(Calendar.YEAR, ymd[0]);
    c.set(Calendar.MONTH, ymd[1] - 1);
    c.set(Calendar.DAY_OF_MONTH, ymd[2]);
    c.set(Calendar.HOUR_OF_DAY, 7);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  public static boolean inWindow(Member member, Calendar now, int horizon) {
    if (member == null || now == null) {
      return false;
    }
    if (horizon == ALL) {
      return true;
    }
    int todayY = now.get(Calendar.YEAR);
    int todayM = now.get(Calendar.MONTH) + 1;
    int todayD = now.get(Calendar.DAY_OF_MONTH);
    int[] next = nextYmd(todayY, todayM, todayD, member.birthMonth, member.birthDay);
    if (next == null) {
      return false;
    }
    return accepts(horizon, next, todayY, todayM, todayD);
  }

  public static List<Section> group(List<Member> members, Calendar now, int horizon) {
    List<Hit> hits = new ArrayList<>();
    if (now != null && members != null) {
      int todayY = now.get(Calendar.YEAR);
      int todayM = now.get(Calendar.MONTH) + 1;
      int todayD = now.get(Calendar.DAY_OF_MONTH);
      int todayJd = DateUtils.julianDay(todayY, todayM, todayD);
      for (Member member : members) {
        if (member == null) {
          continue;
        }
        int[] next = nextYmd(todayY, todayM, todayD, member.birthMonth, member.birthDay);
        if (next == null || !accepts(horizon, next, todayY, todayM, todayD)) {
          continue;
        }
        int jd = DateUtils.julianDay(next[0], next[1], next[2]);
        hits.add(new Hit(member, next[0], next[1], next[2], jd, jd - todayJd));
      }
    }
    Collections.sort(hits, (a, b) -> {
      if (a.julian != b.julian) {
        return Integer.compare(a.julian, b.julian);
      }
      String an = a.member.name == null ? "" : a.member.name;
      String bn = b.member.name == null ? "" : b.member.name;
      return an.compareToIgnoreCase(bn);
    });
    Map<String, List<Member>> buckets = new LinkedHashMap<>();
    Map<String, String> titles = new LinkedHashMap<>();
    SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE, d MMM", Locale.US);
    Calendar stamp = now == null ? Calendar.getInstance() : (Calendar) now.clone();
    for (Hit hit : hits) {
      String key;
      String title;
      if (horizon == ALL) {
        key = hit.year + "-" + hit.month;
        title = monthName(hit.month);
      } else {
        key = hit.year + "-" + hit.month + "-" + hit.day;
        title = dayTitle(hit.days, hit.year, hit.month, hit.day, stamp, dayFmt);
      }
      List<Member> bucket = buckets.get(key);
      if (bucket == null) {
        bucket = new ArrayList<>();
        buckets.put(key, bucket);
        titles.put(key, title);
      }
      bucket.add(hit.member);
    }
    List<Section> out = new ArrayList<>(buckets.size());
    for (Map.Entry<String, List<Member>> entry : buckets.entrySet()) {
      List<Member> group = entry.getValue();
      String subtitle = group.size() == 1 ? "1 person" : group.size() + " people";
      out.add(new Section(titles.get(entry.getKey()), subtitle, group));
    }
    return out;
  }

  static boolean accepts(int horizon, int[] next, int todayY, int todayM, int todayD) {
    if (horizon == ALL) {
      return true;
    }
    if (horizon == MONTH) {
      return next[0] == todayY && next[1] == todayM;
    }
    int days = DateUtils.julianDay(next[0], next[1], next[2])
      - DateUtils.julianDay(todayY, todayM, todayD);
    if (horizon == TODAY) {
      return days == 0;
    }
    int limit = horizon < 0 ? WEEK : horizon;
    return days >= 0 && days <= limit;
  }

  /**
   * Next birthday on or after today, as year / month / day. Feb 29 on a
   * non-leap year lands on the 28th, matching {@link DateUtils#nextBirthdayAt}.
   */
  static int[] nextYmd(int todayY, int todayM, int todayD, int birthM, int birthD) {
    if (birthM < 1 || birthM > 12 || birthD < 1) {
      return null;
    }
    int year = todayY;
    int day = Math.min(birthD, daysInMonth(year, birthM));
    if (birthM < todayM || (birthM == todayM && day < todayD)) {
      year++;
      day = Math.min(birthD, daysInMonth(year, birthM));
    }
    return new int[] { year, birthM, day };
  }

  static int daysInMonth(int year, int month1to12) {
    switch (month1to12) {
      case 2:
        return isLeap(year) ? 29 : 28;
      case 4:
      case 6:
      case 9:
      case 11:
        return 30;
      default:
        return 31;
    }
  }

  static boolean isLeap(int year) {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
  }

  static String monthName(int month1to12) {
    if (month1to12 < 1 || month1to12 > 12) {
      return "";
    }
    String name = MONTHS_US[month1to12 - 1];
    return name == null ? "" : name;
  }

  static String dayTitle(
    int days, int year, int month, int day, Calendar stamp, SimpleDateFormat dayFmt) {
    if (days == 0) {
      return "Today";
    }
    if (days == 1) {
      return "Tomorrow";
    }
    stamp.set(Calendar.YEAR, year);
    stamp.set(Calendar.MONTH, month - 1);
    stamp.set(Calendar.DAY_OF_MONTH, day);
    return dayFmt.format(stamp.getTime());
  }
}
