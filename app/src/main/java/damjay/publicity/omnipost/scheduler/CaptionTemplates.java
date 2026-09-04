package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class CaptionTemplates {
  private CaptionTemplates() {}

  public static boolean isLive(String type) {
    return TaskTypes.COUNTDOWN.equals(type)
        || TaskTypes.BIRTHDAY_NOTICE.equals(type)
        || TaskTypes.WEEKLY.equals(type)
        || TaskTypes.DAILY.equals(type);
  }

  public static String forTask(Context context, Task task) {
    return forTask(context, task, null);
  }

  public static String forTask(Context context, Task task, Series series) {
    if (task == null || task.type == null) {
      return "";
    }
    if (isLive(task.type)) {
      return live(task, series, extrasFrom(context));
    }
    if (context != null && !Prefs.seedCaptions(context)) {
      return "";
    }
    switch (task.type) {
      case TaskTypes.SUNDAY_SERVICE:
        return "Join us for Sunday Service tomorrow.\nCome expecting, come ready.\nSee you there!";
      case TaskTypes.WEDNESDAY_BIBLE_STUDY:
        return "Bible Study holds today.\nCome and grow in the Word.";
      case TaskTypes.FRIDAY_PRAYER:
        return "Prayer Meeting holds today.\nLet's seek the Lord together.";
      case TaskTypes.BIRTHDAY:
        String name = task.title.replace("'s Birthday", "").trim();
        return "Happy Birthday, "
          + name
          + "!\nWe celebrate you and pray God's blessings over your new year.";
      case TaskTypes.NEW_MONTH_FASTING:
        return "New Month Fasting starts tomorrow.\nJoin us as we seek the Lord for the month ahead.";
      case TaskTypes.FASTING_DAY:
        return "New Month Fasting holds today.\nStay in the place of prayer.";
      case TaskTypes.HAPPY_NEW_MONTH:
        return "Happy New Month!\nMay this month overflow with grace, favour, and testimonies.";
      default:
        return "";
    }
  }

  public static String live(Task task, Series series) {
    return live(task, series, null);
  }

  public static String live(Task task, Series series, Map<String, String> extras) {
    if (task == null || task.type == null) {
      return "";
    }
    Series resolved = implicit(task, series);
    return apply(rawTemplate(task, resolved), task, resolved, extras);
  }

  public static String apply(String source, Task task) {
    return apply(source, task, null, null);
  }

  /** Fill placeholders in the user's caption. Does not replace their other words. */
  public static String apply(String source, Task task, Series series) {
    return apply(source, task, series, null);
  }

  public static String apply(String source, Task task, Series series, Map<String, String> extras) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    return fillAll(source, task, implicit(task, series), extras);
  }

  public static String rawTemplate(Task task, Series series) {
    return templateFor(task, series);
  }

  /** Built-in seed / blank templates — not a caption the user wrote. */
  public static boolean isCanned(String text) {
    if (text == null) {
      return true;
    }
    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return true;
    }
    return trimmed.equals(SeriesDefaults.countdownCaption().trim())
        || trimmed.equals(SeriesDefaults.noticeCaption().trim())
        || trimmed.equals(SeriesDefaults.blankCountdownCaption().trim())
        || trimmed.equals(SeriesDefaults.blankNoticeCaption().trim())
        || trimmed.equals(SeriesDefaults.blankWeeklyCaption().trim())
        || trimmed.equals(SeriesDefaults.blankDailyCaption().trim());
  }

  /**
   * Keep a caption the user wrote. Only replace the box when it is still the
   * canned seed and the series (or a sibling day) already has their template.
   */
  public static String lingerDraft(String existing, String shared) {
    if (!isCanned(shared) && isCanned(existing)) {
      return shared;
    }
    if (existing != null && !existing.trim().isEmpty()) {
      return existing;
    }
    return shared == null ? "" : shared;
  }

  /**
   * One template for every day of a live series. Prefer the series caption,
   * then a sibling day's draft, then the built-in seed.
   */
  public static String sharedTemplate(
    String seriesCaption, String siblingCaption, Task task, Series series) {
    if (!isCanned(seriesCaption)) {
      return seriesCaption;
    }
    if (!isCanned(siblingCaption)) {
      return siblingCaption;
    }
    String raw = rawTemplate(task, series);
    return raw == null ? "" : raw;
  }

  public static Series seriesOf(AppDatabase db, Task task) {
    if (db == null || task == null) {
      return null;
    }
    if (task.seriesId > 0L) {
      Series series = db.seriesDao().getById(task.seriesId);
      if (series != null) {
        return series;
      }
    }
    if (TaskTypes.COUNTDOWN.equals(task.type)) {
      return db.seriesDao().findBySeed(SeriesDefaults.SEED_COUNTDOWN);
    }
    if (TaskTypes.BIRTHDAY_NOTICE.equals(task.type)) {
      return db.seriesDao().findBySeed(SeriesDefaults.SEED_NOTICE);
    }
    return null;
  }

  public static Series seriesMatchingTitle(List<Series> seriesList, String title) {
    if (seriesList == null || title == null || title.isEmpty()) {
      return null;
    }
    Series best = null;
    for (Series series : seriesList) {
      if (series == null || series.title == null || series.title.isEmpty()) {
        continue;
      }
      if (title.startsWith(series.title)
          && (best == null || series.title.length() > best.title.length())) {
        best = series;
      }
    }
    return best;
  }

  public static String fill(String template, int days, String monthName) {
    return fillAll(template, days, monthName, null, null);
  }

  static String fillAll(String source, Task task, Series series) {
    return fillAll(source, task, series, null);
  }

  static String fillAll(String source, Task task, Series series, Map<String, String> extras) {
    int days = countdownDays(task);
    String month = monthNameFromOccurrence(task == null ? null : task.occurrenceKey);
    if ((month == null || month.isEmpty()) && series != null && series.eventAtMillis > 0L) {
      Calendar event = Calendar.getInstance();
      event.setTimeInMillis(series.eventAtMillis);
      month = DateUtils.monthName(event);
    }
    if (month == null || month.isEmpty()) {
      month = DateUtils.monthName(Calendar.getInstance());
    }
    return fillAll(source, days, month, task, series, extras);
  }

  static String fillAll(String template, int days, String monthName, Task task, Series series) {
    return fillAll(template, days, monthName, task, series, null);
  }

  static String fillAll(
    String template,
    int days,
    String monthName,
    Task task,
    Series series,
    Map<String, String> extras) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    String month = monthName == null || monthName.isEmpty() ? "this month" : monthName;
    Calendar today = DateUtils.startOfDay(Calendar.getInstance());
    Calendar start = startOf(series, task);
    Calendar due = postDay(task, start);
    if (due == null) {
      due = today;
    }
    Calendar end = endOf(series, start);
    Map<String, String> tokens = new LinkedHashMap<>();
    if (extras != null) {
      tokens.putAll(extras);
    }
    tokens.putAll(parseVars(series == null ? null : series.vars));
    putBuiltins(tokens, days, month, today, due, start, end, series, task);
    return CaptionVars.expand(template, tokens, today, due, start, end);
  }

  static void putBuiltins(
    Map<String, String> tokens,
    int days,
    String month,
    Calendar today,
    Calendar due,
    Calendar start,
    Calendar end,
    Series series,
    Task task) {
    Calendar now = Calendar.getInstance();
    tokens.put("today", DateUtils.prettyDate(today));
    tokens.put("today_weekday", CaptionVars.weekdayName(today));
    tokens.put("today_month", DateUtils.monthName(today));
    tokens.put("today_year", CaptionVars.yearName(today));
    tokens.put("now", DateUtils.formatStamp(now.getTimeInMillis()));
    tokens.put("clock", DateUtils.prettyClock(now));
    tokens.put("date", DateUtils.prettyDate(due));
    tokens.put("due", DateUtils.prettyDate(due));
    tokens.put("weekday", CaptionVars.weekdayName(due));
    tokens.put("month", month);
    tokens.put("Month", month);
    Calendar yearSrc = start != null ? start : due;
    tokens.put("year", CaptionVars.yearName(yearSrc));
    tokens.put("start", DateUtils.prettyDate(start != null ? start : due));
    Calendar last = end != null ? end : start;
    tokens.put("end", DateUtils.prettyDate(last != null ? last : due));
    tokens.put("range", DateUtils.prettyRange(start != null ? start : due, last));
    tokens.put("Days", daysHeadline(days));
    tokens.put("away", daysAway(days));
    tokens.put("days", String.valueOf(Math.max(days, 0)));
    String name = "";
    if (series != null && series.title != null && !series.title.isEmpty()) {
      name = series.title;
    } else if (task != null && task.title != null && !task.title.isEmpty()
        && TaskTypes.BIRTHDAY.equals(task.type)) {
      name = task.title.replace("'s Birthday", "").trim();
    }
    tokens.put("name", name);
  }

  static String applyVars(String template, String vars) {
    return applyTokens(template, parseVars(vars));
  }

  static Map<String, String> parseVars(String vars) {
    Map<String, String> out = new LinkedHashMap<>();
    if (vars == null || vars.isEmpty()) {
      return out;
    }
    String[] lines = vars.split("\n");
    for (String line : lines) {
      if (line == null) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String key = line.substring(0, eq).trim();
      String value = line.substring(eq + 1).trim();
      if (key.isEmpty() || !isTokenName(key)) {
        continue;
      }
      out.put(key, value);
    }
    return out;
  }

  static String applyTokens(String template, Map<String, String> tokens) {
    return CaptionVars.applyLongest(template, tokens);
  }

  public static boolean isTokenName(String key) {
    if (key == null || key.isEmpty()) {
      return false;
    }
    char first = key.charAt(0);
    if (!((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))) {
      return false;
    }
    for (int i = 1; i < key.length(); i++) {
      char c = key.charAt(i);
      boolean ok = (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || c == '_';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  public static String daysHeadline(int days) {
    if (days <= 0) {
      return "D-DAY";
    }
    if (days == 1) {
      return "1 DAY TO GO";
    }
    return days + " DAYS TO GO";
  }

  public static String daysAway(int days) {
    if (days <= 0) {
      return "is here";
    }
    if (days == 1) {
      return "is 1 day away";
    }
    return "is " + days + " days away";
  }

  public static int countdownDays(Task task) {
    if (task == null || task.occurrenceKey == null) {
      return 0;
    }
    String[] parts = task.occurrenceKey.split("\\|");
    if (parts.length < 3 || !TaskTypes.COUNTDOWN.equals(parts[0])) {
      return 0;
    }
    try {
      if (parts.length >= 4 && parts[1].indexOf('-') < 0) {
        return DateUtils.daysBetweenKeys(parts[3], parts[2]);
      }
      return DateUtils.daysBetweenKeys(parts[2], parts[1]);
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  public static String monthNameFromOccurrence(String key) {
    if (key == null) {
      return "";
    }
    String[] parts = key.split("\\|");
    for (String part : parts) {
      if (part != null && part.length() == 7 && part.charAt(4) == '-') {
        String[] ym = part.split("-");
        if (ym.length < 2) {
          continue;
        }
        try {
          Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
          c.clear();
          c.set(Integer.parseInt(ym[0]), Integer.parseInt(ym[1]) - 1, 1);
          return DateUtils.monthName(c);
        } catch (RuntimeException ignored) {
          return "";
        }
      }
    }
    return "";
  }

  public static String countdownCaption(int days) {
    return fill(SeriesDefaults.countdownCaption(), days, "");
  }

  public static String birthdayNoticeCaption(String monthName) {
    return fill(SeriesDefaults.noticeCaption(), 0, monthName);
  }

  public static String countdownTitle(int days) {
    return countdownTitle(Campaigns.BEYOND_LIMIT_NAME, days);
  }

  public static String countdownTitle(String name, int days) {
    String prefix = name == null || name.isEmpty() ? "Countdown" : name;
    if (days <= 0) {
      return prefix + " · D-Day";
    }
    if (days == 1) {
      return prefix + " · 1 day to go";
    }
    return prefix + " · " + days + " days to go";
  }

  public static String monthlyTitle(String name, String monthName) {
    String prefix = name == null || name.isEmpty() ? "Notice" : name;
    if (monthName == null || monthName.isEmpty()) {
      return prefix;
    }
    return prefix + " · " + monthName;
  }

  private static String templateFor(Task task, Series series) {
    if (series != null && series.caption != null && !series.caption.isEmpty()) {
      return series.caption;
    }
    if (task == null || task.type == null) {
      return "";
    }
    if (TaskTypes.COUNTDOWN.equals(task.type)) {
      return SeriesDefaults.countdownCaption();
    }
    if (TaskTypes.BIRTHDAY_NOTICE.equals(task.type)) {
      return SeriesDefaults.noticeCaption();
    }
    return "";
  }

  private static Map<String, String> extrasFrom(Context context) {
    if (context == null) {
      return null;
    }
    try {
      return CaptionVars.map(AppDatabase.get(context));
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  static Series implicit(Task task, Series series) {
    if (series != null) {
      return series;
    }
    if (task == null || task.type == null) {
      return null;
    }
    if (TaskTypes.COUNTDOWN.equals(task.type)) {
      return SeriesDefaults.beyondLimit();
    }
    if (TaskTypes.BIRTHDAY_NOTICE.equals(task.type)) {
      return SeriesDefaults.birthdayNotice();
    }
    return null;
  }

  private static Calendar startOf(Series series, Task task) {
    if (series != null && series.eventAtMillis > 0L) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(series.eventAtMillis);
      return DateUtils.startOfDay(c);
    }
    return postDay(task, null);
  }

  private static Calendar endOf(Series series, Calendar start) {
    if (series != null && series.endAtMillis > 0L) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(series.endAtMillis);
      return DateUtils.startOfDay(c);
    }
    return start;
  }

  private static Calendar postDay(Task task, Calendar fallback) {
    if (task != null && task.postAtMillis > 0L) {
      Calendar c = Calendar.getInstance();
      c.setTimeInMillis(task.postAtMillis);
      return DateUtils.startOfDay(c);
    }
    return fallback;
  }
}
