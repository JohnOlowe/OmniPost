package damjay.publicity.omnipost.scheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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
