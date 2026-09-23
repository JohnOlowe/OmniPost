package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
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

  private BirthdayHorizon() {}

  public static Calendar nextAt(Member member, Calendar now) {
    if (member == null || now == null) {
      return null;
    }
    Calendar today = DateUtils.startOfDay(now);
    if (today.get(Calendar.MONTH) + 1 == member.birthMonth
      && today.get(Calendar.DAY_OF_MONTH) == member.birthDay) {
      return DateUtils.sameDayAt(now, 7, 0);
    }
    return DateUtils.nextBirthdayAt(now, member.birthMonth, member.birthDay, 7, 0);
  }

  public static boolean inWindow(Member member, Calendar now, int horizon) {
    Calendar next = nextAt(member, now);
    if (next == null) {
      return false;
    }
    if (horizon == ALL) {
      return true;
    }
    Calendar today = DateUtils.startOfDay(now);
    Calendar day = DateUtils.startOfDay(next);
    if (horizon == MONTH) {
      return next.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        && next.get(Calendar.MONTH) == now.get(Calendar.MONTH);
    }
    int days = DateUtils.calendarDaysBetween(today, day);
    if (horizon == TODAY) {
      return days == 0;
    }
    int limit = horizon < 0 ? WEEK : horizon;
    return days >= 0 && days <= limit;
  }

  public static List<Section> group(List<Member> members, Calendar now, int horizon) {
    List<Member> kept = new ArrayList<>();
    if (members != null) {
      for (Member member : members) {
        if (inWindow(member, now, horizon)) {
          kept.add(member);
        }
      }
    }
    Collections.sort(kept, (a, b) -> {
      Calendar na = nextAt(a, now);
      Calendar nb = nextAt(b, now);
      long ta = na == null ? Long.MAX_VALUE : na.getTimeInMillis();
      long tb = nb == null ? Long.MAX_VALUE : nb.getTimeInMillis();
      if (ta != tb) {
        return Long.compare(ta, tb);
      }
      String an = a.name == null ? "" : a.name;
      String bn = b.name == null ? "" : b.name;
      return an.compareToIgnoreCase(bn);
    });
    Map<String, List<Member>> buckets = new LinkedHashMap<>();
    Map<String, String> titles = new LinkedHashMap<>();
    for (Member member : kept) {
      Calendar next = nextAt(member, now);
      String key;
      String title;
      if (horizon == ALL) {
        key = DateUtils.monthKey(next);
        title = DateUtils.monthName(next);
      } else {
        key = DateUtils.dayKey(next);
        title = dayTitle(now, next);
      }
      List<Member> bucket = buckets.get(key);
      if (bucket == null) {
        bucket = new ArrayList<>();
        buckets.put(key, bucket);
        titles.put(key, title);
      }
      bucket.add(member);
    }
    List<Section> out = new ArrayList<>();
    for (Map.Entry<String, List<Member>> entry : buckets.entrySet()) {
      List<Member> group = entry.getValue();
      String subtitle = group.size() == 1 ? "1 person" : group.size() + " people";
      out.add(new Section(titles.get(entry.getKey()), subtitle, group));
    }
    return out;
  }

  static String dayTitle(Calendar now, Calendar day) {
    String today = DateUtils.dayKey(now);
    String key = DateUtils.dayKey(day);
    if (today.equals(key)) {
      return "Today";
    }
    Calendar tomorrow = DateUtils.startOfDay(now);
    tomorrow.add(Calendar.DAY_OF_MONTH, 1);
    if (DateUtils.dayKey(tomorrow).equals(key)) {
      return "Tomorrow";
    }
    return new SimpleDateFormat("EEEE, d MMM", Locale.US).format(day.getTime());
  }
}
