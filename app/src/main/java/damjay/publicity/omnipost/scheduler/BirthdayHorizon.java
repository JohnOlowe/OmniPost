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

  public static boolean nameMatches(Member member, String query) {
    if (member == null) {
      return false;
    }
    String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
    if (q.isEmpty()) {
      return true;
    }
    return contains(member.name, q)
      || contains(member.firstName, q)
      || contains(member.lastName, q)
      || contains(member.phone, q)
      || contains(member.honorific, q)
      || contains(member.email, q);
  }

  /**
   * People-tab search stays on the current roster: alumni hits never leak onto
   * Previous pastors, and pastors never leak onto Alumni.
   */
  public static List<Member> searchHits(
      List<Member> all, String query, boolean alumniHome, boolean pastorsTab) {
    return searchHits(all, query, alumniHome, pastorsTab, false);
  }

  public static List<Member> searchHits(
      List<Member> all,
      String query,
      boolean alumniHome,
      boolean pastorsTab,
      boolean noNumberTab) {
    List<Member> hits = new ArrayList<>();
    if (all == null) {
      return hits;
    }
    for (Member member : all) {
      if (member == null || !nameMatches(member, query)) {
        continue;
      }
      if (!onPeopleTab(member, alumniHome, pastorsTab, noNumberTab)) {
        continue;
      }
      hits.add(member);
    }
    return hits;
  }

  /** Alumni / pastors with a number stay on those tabs. No number is its own roster. */
  public static boolean onPeopleTab(
      Member member, boolean alumniHome, boolean pastorsTab, boolean noNumberTab) {
    if (member == null) {
      return false;
    }
    if (!alumniHome) {
      return !Member.isAlumni(member);
    }
    if (!Member.isAlumni(member)) {
      return false;
    }
    boolean numbered = AlumniDesk.hasPhone(member);
    if (noNumberTab) {
      return !numbered;
    }
    if (!numbered) {
      return false;
    }
    if (pastorsTab) {
      return Member.isPastor(member);
    }
    return !Member.isPastor(member);
  }

  public static boolean sheetMatches(AlumniSheet.Row row, String query) {
    if (row == null) {
      return false;
    }
    String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
    if (q.isEmpty()) {
      return true;
    }
    return contains(row.displayName(), q)
      || contains(row.firstName, q)
      || contains(row.lastName, q)
      || contains(row.phone, q);
  }

  private static boolean contains(String value, String query) {
    return value != null && value.toLowerCase(Locale.US).contains(query);
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
    List<Member> undated = new ArrayList<>();
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
        if (next == null) {
          if (horizon == ALL) {
            undated.add(member);
          }
          continue;
        }
        if (!accepts(horizon, next, todayY, todayM, todayD)) {
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
    List<Section> out = new ArrayList<>(buckets.size() + (undated.isEmpty() ? 0 : 1));
    if (!undated.isEmpty()) {
      Collections.sort(undated, (a, b) -> {
        String an = a.name == null ? "" : a.name;
        String bn = b.name == null ? "" : b.name;
        return an.compareToIgnoreCase(bn);
      });
      String subtitle = undated.size() == 1 ? "1 person" : undated.size() + " people";
      out.add(new Section("No date yet", subtitle, undated));
    }
    for (Map.Entry<String, List<Member>> entry : buckets.entrySet()) {
      List<Member> group = entry.getValue();
      String subtitle = group.size() == 1 ? "1 person" : group.size() + " people";
      out.add(new Section(titles.get(entry.getKey()), subtitle, group));
    }
    return out;
  }

  /** Names and dates from the current Today / week / month / all view. */
  public static String copyList(String heading, List<Section> sections) {
    StringBuilder out = new StringBuilder();
    if (heading != null && !heading.trim().isEmpty()) {
      out.append(heading.trim()).append('\n');
    }
    int n = 0;
    if (sections != null) {
      for (Section section : sections) {
        if (section == null || section.members == null) {
          continue;
        }
        for (Member member : section.members) {
          if (member == null) {
            continue;
          }
          String name = member.name == null ? "" : member.name.trim();
          if (name.isEmpty()) {
            continue;
          }
          n++;
          out.append(name);
          String when = DateUtils.monthDayLabel(member.birthMonth, member.birthDay);
          if (when != null && !when.isEmpty()) {
            out.append(" — ").append(when);
          }
          out.append('\n');
        }
      }
    }
    return n == 0 ? "" : out.toString().trim();
  }

  public static int copyCount(List<Section> sections) {
    int n = 0;
    if (sections == null) {
      return 0;
    }
    for (Section section : sections) {
      if (section == null || section.members == null) {
        continue;
      }
      for (Member member : section.members) {
        if (member != null && member.name != null && !member.name.trim().isEmpty()) {
          n++;
        }
      }
    }
    return n;
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
