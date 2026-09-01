package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class CaptionTemplates {
  private CaptionTemplates() {}

  public static boolean isLive(String type) {
    return TaskTypes.COUNTDOWN.equals(type) || TaskTypes.BIRTHDAY_NOTICE.equals(type);
  }

  public static String forTask(Context context, Task task) {
    return forTask(context, task, null);
  }

  public static String forTask(Context context, Task task, Series series) {
    if (task == null || task.type == null) {
      return "";
    }
    if (isLive(task.type)) {
      return live(task, series);
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
    if (task == null || task.type == null) {
      return "";
    }
    return apply(rawTemplate(task, series), task);
  }

  /** Fill Days / away / days / month in the user's caption. Does not replace their words. */
  public static String apply(String source, Task task) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    int days = countdownDays(task);
    String month = monthNameFromOccurrence(task == null ? null : task.occurrenceKey);
    if (month == null || month.isEmpty()) {
      month = DateUtils.monthName(Calendar.getInstance());
    }
    return fill(source, days, month);
  }

  public static String rawTemplate(Task task, Series series) {
    return templateFor(task, series);
  }

  public static String fill(String template, int days, String monthName) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    String month = monthName == null || monthName.isEmpty() ? "this month" : monthName;
    String out = template;
    out = out.replace("{Days}", daysHeadline(days));
    out = out.replace("{away}", daysAway(days));
    out = out.replace("{days}", String.valueOf(Math.max(days, 0)));
    out = out.replace("{month}", month);
    out = out.replace("{Month}", month);
    return out;
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
}
