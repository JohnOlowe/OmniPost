package damjay.publicity.omnipost.scheduler;

import android.content.Context;
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
    if (task == null || task.type == null) {
      return "";
    }
    if (isLive(task.type)) {
      return live(task);
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

  public static String live(Task task) {
    if (task == null || task.type == null) {
      return "";
    }
    if (TaskTypes.COUNTDOWN.equals(task.type)) {
      return countdownCaption(countdownDays(task));
    }
    if (TaskTypes.BIRTHDAY_NOTICE.equals(task.type)) {
      return birthdayNoticeCaption(monthNameFromOccurrence(task.occurrenceKey));
    }
    return "";
  }

  public static int countdownDays(Task task) {
    if (task == null || task.occurrenceKey == null) {
      return 0;
    }
    String[] parts = task.occurrenceKey.split("\\|");
    if (parts.length >= 3) {
      try {
        return DateUtils.daysBetweenKeys(parts[2], parts[1]);
      } catch (RuntimeException ignored) {
        return 0;
      }
    }
    return 0;
  }

  public static String monthNameFromOccurrence(String key) {
    if (key == null) {
      return "";
    }
    String[] parts = key.split("\\|");
    if (parts.length < 2) {
      return "";
    }
    String[] ym = parts[1].split("-");
    if (ym.length < 2) {
      return "";
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

  public static String countdownCaption(int days) {
    String headline;
    String away;
    if (days <= 0) {
      headline = "*IT'S D-DAY!*";
      away = "is here";
    } else if (days == 1) {
      headline = "*IT'S 1 DAY TO GO!*";
      away = "is 1 day away";
    } else {
      headline = "*IT'S " + days + " DAYS TO GO!*";
      away = "is " + days + " days away";
    }
    return headline
      + "\n\n"
      + "*_No Cross, No Crown._*\n\n"
      + "The 9th edition of *Beyond Limit* "
      + away
      + ".\n\n"
      + "*Date:* 9th – 13th September, 2026\n"
      + "*Venue:* RCCG The Lord's Court, 13, Osholake Street, Ebute-Metta, Lagos.\n"
      + "*Time:* 5pm Daily\n\n"
      + "Theme: _\"No Cross, No Crown.\"_\n\n"
      + "Come one, come all. Invite your family, friends, neighbours, colleagues, and so on.\n\n"
      + "_Pray, plan and prepare!_\n\n"
      + "———\n\n"
      + "RCCG The Lord's Court\n"
      + "Youth Church\n"
      + "Department/Level:";
  }

  public static String birthdayNoticeCaption(String monthName) {
    String month = monthName == null || monthName.isEmpty() ? "this month" : monthName;
    return "*NOTICE!*\n\n"
      + "This is to notify you that we will be celebrating the birthdays of all members born in the *Month of "
      + month
      + "*\n\n"
      + "Kindly click on this link to send in your pictures\n"
      + Campaigns.BIRTHDAY_WA
      + "\n\n"
      + "Ensure you send in your pictures on time so we can celebrate you.\n\n"
      + "Thank you";
  }

  public static String countdownTitle(int days) {
    if (days <= 0) {
      return Campaigns.BEYOND_LIMIT_NAME + " · D-Day";
    }
    if (days == 1) {
      return Campaigns.BEYOND_LIMIT_NAME + " · 1 day to go";
    }
    return Campaigns.BEYOND_LIMIT_NAME + " · " + days + " days to go";
  }
}
