package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
import java.util.LinkedHashMap;
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
    Map<String, String> tokens = new LinkedHashMap<>();
    if (extras != null) {
      tokens.putAll(extras);
    }
    tokens.putAll(parseVars(series == null ? null : series.vars));
    String out = applyTokens(template, tokens);
    if (series != null && series.title != null && !series.title.isEmpty()) {
      out = out.replace("{name}", series.title);
    } else if (task != null && task.title != null && !task.title.isEmpty()
        && TaskTypes.BIRTHDAY.equals(task.type)) {
      out = out.replace("{name}", task.title.replace("'s Birthday", "").trim());
    }
    out = out.replace("{Days}", daysHeadline(days));
    out = out.replace("{away}", daysAway(days));
    out = out.replace("{days}", String.valueOf(Math.max(days, 0)));
    out = out.replace("{month}", month);
    out = out.replace("{Month}", month);
    Calendar start = startOf(series, task);
    Calendar end = endOf(series, start);
    if (out.contains("{range}")) {
      out = out.replace("{range}", DateUtils.prettyRange(start, end));
    }
    if (out.contains("{date}")) {
      out = out.replace("{date}", start == null ? "" : DateUtils.prettyDate(start));
    }
    if (out.contains("{start}")) {
      out = out.replace("{start}", start == null ? "" : DateUtils.prettyDate(start));
    }
    if (out.contains("{end}")) {
      Calendar last = end == null ? start : end;
      out = out.replace("{end}", last == null ? "" : DateUtils.prettyDate(last));
    }
    if (out.contains("{year}")) {
      Calendar yearSrc = start == null ? Calendar.getInstance() : start;
      out = out.replace("{year}", String.valueOf(yearSrc.get(Calendar.YEAR)));
    }
    if (out.contains("{weekday}")) {
      Calendar day = postDay(task, start);
      String weekday = day == null
        ? ""
        : day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US);
      out = out.replace("{weekday}", weekday == null ? "" : weekday);
    }
    return out;
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
    if (template == null || template.isEmpty()) {
      return template == null ? "" : template;
    }
    if (tokens == null || tokens.isEmpty()) {
      return template;
    }
    String out = template;
    for (Map.Entry<String, String> entry : tokens.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isEmpty()) {
        continue;
      }
      String value = entry.getValue() == null ? "" : entry.getValue();
      out = out.replace("{" + entry.getKey() + "}", value);
    }
    return out;
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
