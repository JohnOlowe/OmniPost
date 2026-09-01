package damjay.publicity.omnipost.scheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateUtils {
  private DateUtils() {}

  public static Calendar nextWeekdayAt(Calendar now, int dayOfWeek, int hour, int minute) {
    Calendar c = strip(now);
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    int add = dayOfWeek - c.get(Calendar.DAY_OF_WEEK);
    if (add < 0) {
      add += 7;
    }
    c.add(Calendar.DAY_OF_MONTH, add);
    if (!c.after(now)) {
      c.add(Calendar.DAY_OF_MONTH, 7);
    }
    return c;
  }

  public static Calendar nextMonthEndAt(Calendar now, int hour, int minute) {
    Calendar c = strip(now);
    c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    if (!c.after(now)) {
      c.add(Calendar.MONTH, 1);
      c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
      c.set(Calendar.HOUR_OF_DAY, hour);
      c.set(Calendar.MINUTE, minute);
    }
    return c;
  }

  public static Calendar nextMonthStartAt(Calendar now, int hour, int minute) {
    Calendar c = strip(now);
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    if (!c.after(now)) {
      c.add(Calendar.MONTH, 1);
      c.set(Calendar.DAY_OF_MONTH, 1);
    }
    return c;
  }

  public static Calendar nextBirthdayAt(
    Calendar now, int month1to12, int day, int hour, int minute) {
    Calendar c = strip(now);
    c.set(Calendar.MONTH, month1to12 - 1);
    int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
    c.set(Calendar.DAY_OF_MONTH, Math.min(day, max));
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    if (!c.after(now)) {
      c.add(Calendar.YEAR, 1);
      c.set(Calendar.MONTH, month1to12 - 1);
      max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
      c.set(Calendar.DAY_OF_MONTH, Math.min(day, max));
      c.set(Calendar.HOUR_OF_DAY, hour);
      c.set(Calendar.MINUTE, minute);
    }
    return c;
  }

  public static Calendar dayBeforeAt(Calendar event, int hour, int minute) {
    Calendar c = strip(event);
    c.add(Calendar.DAY_OF_MONTH, -1);
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    return c;
  }

  public static Calendar sameDayAt(Calendar event, int hour, int minute) {
    Calendar c = strip(event);
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    return c;
  }

  public static String dayKey(Calendar c) {
    return String.format(
      Locale.US,
      "%04d-%02d-%02d",
      c.get(Calendar.YEAR),
      c.get(Calendar.MONTH) + 1,
      c.get(Calendar.DAY_OF_MONTH));
  }

  public static String monthKey(Calendar calendar) {
    return String.format(Locale.US, "%04d-%02d",
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
  }

  public static Calendar startOfDay(Calendar calendar) {
    Calendar c = (Calendar) calendar.clone();
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  /** Whole calendar days from {@code from} to {@code to} (negative if {@code to} is earlier). */
  public static int calendarDaysBetween(Calendar from, Calendar to) {
    Calendar a = startOfDay(from);
    Calendar b = startOfDay(to);
    int days = 0;
    int step = a.after(b) ? -1 : 1;
    while (!dayKey(a).equals(dayKey(b)) && Math.abs(days) < 4000) {
      a.add(Calendar.DAY_OF_MONTH, step);
      days += step;
    }
    return days;
  }

  public static Calendar parseDayKey(String key, TimeZone zone) {
    String[] parts = key.split("-");
    Calendar c = Calendar.getInstance(zone);
    c.clear();
    c.setTimeZone(zone);
    c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]), 0, 0, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  public static int daysBetweenKeys(String fromKey, String toKey) {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    return calendarDaysBetween(parseDayKey(fromKey, utc), parseDayKey(toKey, utc));
  }

  public static String monthName(Calendar calendar) {
    String name = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US);
    if (name != null && !name.isEmpty()) {
      return name;
    }
    return new SimpleDateFormat("MMMM", Locale.US).format(calendar.getTime());
  }

  public static String ordinal(int day) {
    int n = Math.abs(day);
    int mod100 = n % 100;
    if (mod100 >= 11 && mod100 <= 13) {
      return n + "th";
    }
    switch (n % 10) {
      case 1:
        return n + "st";
      case 2:
        return n + "nd";
      case 3:
        return n + "rd";
      default:
        return n + "th";
    }
  }

  /** 5:00 PM */
  public static String prettyClock(Calendar calendar) {
    if (calendar == null) {
      return "";
    }
    return new SimpleDateFormat("h:mm a", Locale.US).format(calendar.getTime());
  }

  /** 9th September, 2026 */
  public static String prettyDate(Calendar calendar) {
    if (calendar == null) {
      return "";
    }
    return ordinal(calendar.get(Calendar.DAY_OF_MONTH))
      + " "
      + monthName(calendar)
      + ", "
      + calendar.get(Calendar.YEAR);
  }

  /** 9th – 13th September, 2026 (collapses when the month or year is shared). */
  public static String prettyRange(Calendar start, Calendar end) {
    if (start == null) {
      return "";
    }
    if (end == null || dayKey(start).equals(dayKey(end))) {
      return prettyDate(start);
    }
    boolean sameYear = start.get(Calendar.YEAR) == end.get(Calendar.YEAR);
    boolean sameMonth = sameYear && start.get(Calendar.MONTH) == end.get(Calendar.MONTH);
    if (sameMonth) {
      return ordinal(start.get(Calendar.DAY_OF_MONTH))
        + " – "
        + ordinal(end.get(Calendar.DAY_OF_MONTH))
        + " "
        + monthName(start)
        + ", "
        + start.get(Calendar.YEAR);
    }
    if (sameYear) {
      return ordinal(start.get(Calendar.DAY_OF_MONTH))
        + " "
        + monthName(start)
        + " – "
        + ordinal(end.get(Calendar.DAY_OF_MONTH))
        + " "
        + monthName(end)
        + ", "
        + start.get(Calendar.YEAR);
    }
    return prettyDate(start) + " – " + prettyDate(end);
  }

  /** Two in-month dates, equally spaced (thirds). */
  public static int[] inMonthThirds(int maxDay) {
    int first = Math.max(1, maxDay / 3);
    int second = Math.max(first + 1, (2 * maxDay) / 3);
    if (second > maxDay) {
      second = maxDay;
    }
    return new int[] {first, second};
  }

  public static String dayKey(long millis) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(millis);
    return dayKey(c);
  }

  public static String formatDayHeader(long millis) {
    Calendar today = Calendar.getInstance();
    String todayKey = dayKey(today);
    String targetKey = dayKey(millis);
    if (todayKey.equals(targetKey)) {
      return "Today";
    }
    Calendar neighbor = (Calendar) today.clone();
    neighbor.add(Calendar.DAY_OF_MONTH, 1);
    if (dayKey(neighbor).equals(targetKey)) {
      return "Tomorrow";
    }
    neighbor = (Calendar) today.clone();
    neighbor.add(Calendar.DAY_OF_MONTH, -1);
    if (dayKey(neighbor).equals(targetKey)) {
      return "Yesterday";
    }
    return new SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(new Date(millis));
  }

  public static String formatStamp(long millis) {
    SimpleDateFormat fmt = new SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault());
    return fmt.format(new Date(millis));
  }

  public static String relativeOrStamp(long millis) {
    if (millis <= System.currentTimeMillis()) {
      return "OVERDUE · " + formatStamp(millis);
    }
    return formatStamp(millis);
  }

  public static String formatUntil(long targetMillis) {
    return formatUntil(targetMillis, System.currentTimeMillis());
  }

  public static String formatUntil(long targetMillis, long nowMillis) {
    long delta = targetMillis - nowMillis;
    if (delta <= 0L) {
      return "that time has passed";
    }
    long minutes = (delta + 30_000L) / 60_000L;
    if (minutes < 1L) {
      return "in less than a minute";
    }
    long days = minutes / (60L * 24L);
    minutes -= days * 60L * 24L;
    long hours = minutes / 60L;
    minutes -= hours * 60L;
    StringBuilder out = new StringBuilder("in ");
    boolean any = false;
    if (days > 0L) {
      out.append(days).append(days == 1L ? " day" : " days");
      any = true;
    }
    if (hours > 0L && days < 7L) {
      if (any) {
        out.append(" ");
      }
      out.append(hours).append(hours == 1L ? " hour" : " hours");
      any = true;
    }
    if (days == 0L && minutes > 0L) {
      if (any) {
        out.append(" ");
      }
      out.append(minutes).append(minutes == 1L ? " minute" : " minutes");
      any = true;
    }
    if (!any) {
      return "in less than a minute";
    }
    return out.toString();
  }

  public static long hoursFromNow(int hours) {
    return System.currentTimeMillis() + hours * 60L * 60L * 1000L;
  }

  public static long nextClock(int hour, int minute) {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (!c.after(Calendar.getInstance())) {
      c.add(Calendar.DAY_OF_MONTH, 1);
    }
    return c.getTimeInMillis();
  }

  public static String monthDayLabel(int month1to12, int day) {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.MONTH, month1to12 - 1);
    c.set(Calendar.DAY_OF_MONTH, 1);
    String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(c.getTime());
    return day + " " + month;
  }

  private static Calendar strip(Calendar now) {
    Calendar c = (Calendar) now.clone();
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }
}
