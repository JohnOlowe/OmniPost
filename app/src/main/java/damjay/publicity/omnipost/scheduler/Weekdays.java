package damjay.publicity.omnipost.scheduler;

import java.util.Calendar;
import java.util.Locale;

public final class Weekdays {
  public static final int NONE = 0;
  /** Sunday through Saturday. */
  public static final int ALL = 0b1111111;

  private static final int[] DAYS = {
    Calendar.SUNDAY,
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY,
    Calendar.FRIDAY,
    Calendar.SATURDAY
  };

  private static final String[] SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  private static final String[] LONG = {
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
  };

  private Weekdays() {}

  public static int bit(int calendarDay) {
    if (calendarDay < Calendar.SUNDAY || calendarDay > Calendar.SATURDAY) {
      return 0;
    }
    return 1 << (calendarDay - 1);
  }

  public static boolean has(int mask, int calendarDay) {
    return (mask & bit(calendarDay)) != 0;
  }

  public static int with(int mask, int calendarDay, boolean on) {
    int flag = bit(calendarDay);
    if (flag == 0) {
      return mask;
    }
    return on ? (mask | flag) : (mask & ~flag);
  }

  public static int[] days() {
    return DAYS.clone();
  }

  public static String shortName(int calendarDay) {
    int i = calendarDay - 1;
    if (i < 0 || i >= SHORT.length) {
      return "";
    }
    return SHORT[i];
  }

  public static String longName(int calendarDay) {
    int i = calendarDay - 1;
    if (i < 0 || i >= LONG.length) {
      return "";
    }
    return LONG[i];
  }

  public static String label(int mask) {
    if (mask == ALL) {
      return "Every day";
    }
    if (mask == NONE) {
      return "No days";
    }
    StringBuilder out = new StringBuilder();
    for (int day : DAYS) {
      if (!has(mask, day)) {
        continue;
      }
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append(shortName(day));
    }
    return out.toString();
  }

  public static String sentence(int mask) {
    if (mask == ALL) {
      return "every day";
    }
    String listed = label(mask);
    if (listed.isEmpty() || "No days".equals(listed)) {
      return "on the days you pick";
    }
    return "every " + listed;
  }

  public static String clock(int hour, int minute) {
    int h = Math.max(0, Math.min(23, hour));
    int m = Math.max(0, Math.min(59, minute));
    int twelve = h % 12;
    if (twelve == 0) {
      twelve = 12;
    }
    String ampm = h < 12 ? "AM" : "PM";
    return String.format(Locale.US, "%d:%02d %s", twelve, m, ampm);
  }
}
