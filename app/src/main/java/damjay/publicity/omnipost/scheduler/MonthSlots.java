package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Series;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monthly post dates: any day number (1–31 or last of this month), last day of
 * the previous month, and ordinal weekdays (1st Monday, 2nd Wednesday, last Friday).
 */
public final class MonthSlots {
  /** Bit 0 of {@link Series#monthDays}: last day of the target month. */
  public static final int LAST_OF_MONTH = 0;
  /** 5th ordinal = last weekday in the month. */
  public static final int LAST_ORDINAL = 5;

  public static final class Hit {
    public final String slot;
    public final Calendar post;

    Hit(String slot, Calendar post) {
      this.slot = slot;
      this.post = post;
    }
  }

  private MonthSlots() {}

  public static int dayBit(int day) {
    if (day == LAST_OF_MONTH) {
      return 1;
    }
    if (day < 1 || day > 31) {
      return 0;
    }
    return 1 << day;
  }

  public static boolean hasDay(int mask, int day) {
    int bit = dayBit(day);
    return bit != 0 && (mask & bit) != 0;
  }

  public static boolean hasLast(int mask) {
    return hasDay(mask, LAST_OF_MONTH);
  }

  public static int withDay(int mask, int day, boolean on) {
    int bit = dayBit(day);
    if (bit == 0) {
      return mask;
    }
    return on ? (mask | bit) : (mask & ~bit);
  }

  /** ordinal 1–4, or {@link #LAST_ORDINAL}; weekday {@link Calendar#SUNDAY}–{@link Calendar#SATURDAY}. */
  public static int ordinalIndex(int ordinal, int weekday) {
    if (ordinal < 1 || ordinal > LAST_ORDINAL) {
      return -1;
    }
    if (weekday < Calendar.SUNDAY || weekday > Calendar.SATURDAY) {
      return -1;
    }
    return (ordinal - 1) * 7 + (weekday - 1);
  }

  public static boolean hasOrdinal(long mask, int ordinal, int weekday) {
    int i = ordinalIndex(ordinal, weekday);
    if (i < 0) {
      return false;
    }
    return (mask & (1L << i)) != 0L;
  }

  public static long withOrdinal(long mask, int ordinal, int weekday, boolean on) {
    int i = ordinalIndex(ordinal, weekday);
    if (i < 0) {
      return mask;
    }
    long bit = 1L << i;
    return on ? (mask | bit) : (mask & ~bit);
  }

  /**
   * Day-number mask. Old series with empty {@code monthDays}/{@code monthOrdinals}
   * still honour the 10th / 20th checkboxes.
   */
  public static int daysOf(Series series) {
    if (series == null) {
      return 0;
    }
    if (series.monthDays != 0 || series.monthOrdinals != 0L) {
      return series.monthDays;
    }
    int mask = 0;
    if (series.tenth) {
      mask = withDay(mask, 10, true);
    }
    if (series.twentieth) {
      mask = withDay(mask, 20, true);
    }
    return mask;
  }

  public static boolean any(Series series) {
    if (series == null) {
      return false;
    }
    return series.lastOfPrevMonth || daysOf(series) != 0 || series.monthOrdinals != 0L;
  }

  public static Calendar nthWeekday(Calendar monthStart, int ordinal, int weekday) {
    if (monthStart == null) {
      return null;
    }
    Calendar c = (Calendar) monthStart.clone();
    c.set(Calendar.DAY_OF_MONTH, 1);
    int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
    int found = 0;
    Calendar last = null;
    for (int d = 1; d <= max; d++) {
      c.set(Calendar.DAY_OF_MONTH, d);
      if (c.get(Calendar.DAY_OF_WEEK) != weekday) {
        continue;
      }
      found++;
      last = (Calendar) c.clone();
      if (ordinal != LAST_ORDINAL && found == ordinal) {
        return last;
      }
    }
    return ordinal == LAST_ORDINAL ? last : null;
  }

  public static List<Hit> hits(Series series, Calendar monthStart, int hour, int minute) {
    List<Hit> out = new ArrayList<>();
    if (series == null || monthStart == null) {
      return out;
    }
    Map<String, Hit> unique = new LinkedHashMap<>();
    if (series.lastOfPrevMonth) {
      Calendar eve = (Calendar) monthStart.clone();
      eve.set(Calendar.DAY_OF_MONTH, 1);
      eve.add(Calendar.MONTH, -1);
      eve.set(Calendar.DAY_OF_MONTH, eve.getActualMaximum(Calendar.DAY_OF_MONTH));
      stamp(eve, hour, minute);
      unique.put(DateUtils.dayKey(eve), new Hit("EVE", eve));
    }
    int days = daysOf(series);
    int max = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH);
    for (int d = 1; d <= max; d++) {
      if (hasDay(days, d)) {
        putDay(unique, monthStart, d, hour, minute, "D" + d);
      }
    }
    if (hasLast(days)) {
      putDay(unique, monthStart, max, hour, minute, "LAST");
    }
    long ords = series.monthOrdinals;
    if (ords != 0L) {
      for (int ordinal = 1; ordinal <= LAST_ORDINAL; ordinal++) {
        for (int weekday = Calendar.SUNDAY; weekday <= Calendar.SATURDAY; weekday++) {
          if (!hasOrdinal(ords, ordinal, weekday)) {
            continue;
          }
          Calendar day = nthWeekday(monthStart, ordinal, weekday);
          if (day == null) {
            continue;
          }
          stamp(day, hour, minute);
          String key = DateUtils.dayKey(day);
          if (!unique.containsKey(key)) {
            unique.put(key, new Hit(ordinalSlot(ordinal, weekday), day));
          }
        }
      }
    }
    out.addAll(unique.values());
    return out;
  }

  public static String sentence(Series series) {
    if (series == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    if (series.lastOfPrevMonth) {
      append(out, "Last day of the previous month");
    }
    int days = daysOf(series);
    for (int d = 1; d <= 31; d++) {
      if (hasDay(days, d)) {
        append(out, "the " + dayLabel(d));
      }
    }
    if (hasLast(days)) {
      append(out, "last day of this month");
    }
    long ords = series.monthOrdinals;
    if (ords != 0L) {
      for (int ordinal = 1; ordinal <= LAST_ORDINAL; ordinal++) {
        for (int weekday = Calendar.SUNDAY; weekday <= Calendar.SATURDAY; weekday++) {
          if (hasOrdinal(ords, ordinal, weekday)) {
            append(out, ordinalLabel(ordinal) + " " + Weekdays.longName(weekday));
          }
        }
      }
    }
    if (out.length() == 0) {
      return "{month} fills itself.";
    }
    out.append(". {month} fills itself.");
    return out.toString();
  }

  public static String dayLabel(int day) {
    if (day <= 0) {
      return "";
    }
    int mod100 = day % 100;
    int mod10 = day % 10;
    String suffix = "th";
    if (mod100 < 11 || mod100 > 13) {
      if (mod10 == 1) {
        suffix = "st";
      } else if (mod10 == 2) {
        suffix = "nd";
      } else if (mod10 == 3) {
        suffix = "rd";
      }
    }
    return day + suffix;
  }

  public static String ordinalLabel(int ordinal) {
    if (ordinal == LAST_ORDINAL) {
      return "Last";
    }
    return dayLabel(ordinal);
  }

  public static String ordinalSlot(int ordinal, int weekday) {
    return "W" + ordinal + Weekdays.shortName(weekday).toUpperCase(Locale.US);
  }

  private static void putDay(
      Map<String, Hit> unique, Calendar monthStart, int day, int hour, int minute, String slot) {
    Calendar post = (Calendar) monthStart.clone();
    post.set(Calendar.DAY_OF_MONTH, day);
    stamp(post, hour, minute);
    String key = DateUtils.dayKey(post);
    if (!unique.containsKey(key)) {
      unique.put(key, new Hit(slot, post));
    }
  }

  private static void stamp(Calendar c, int hour, int minute) {
    c.set(Calendar.HOUR_OF_DAY, hour);
    c.set(Calendar.MINUTE, minute);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
  }

  private static void append(StringBuilder out, String bit) {
    if (out.length() > 0) {
      out.append(", ");
    }
    out.append(bit);
  }
}
