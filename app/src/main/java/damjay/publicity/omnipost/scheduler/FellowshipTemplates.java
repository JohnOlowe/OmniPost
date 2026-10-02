package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Task;

/** Fellowship caption templates — same idea as Alumni captions: write once, fill on the task. */
public final class FellowshipTemplates {
  public static final String[] KEYS = {
    TaskTypes.SUNDAY_SERVICE,
    TaskTypes.WEDNESDAY_BIBLE_STUDY,
    TaskTypes.FRIDAY_PRAYER,
    TaskTypes.NEW_MONTH_FASTING,
    TaskTypes.FASTING_DAY,
    TaskTypes.HAPPY_NEW_MONTH,
    TaskTypes.BIRTHDAY
  };

  private FellowshipTemplates() {}

  public static int labelRes(String key) {
    if (TaskTypes.SUNDAY_SERVICE.equals(key)) {
      return R.string.fellowship_caption_sunday;
    }
    if (TaskTypes.WEDNESDAY_BIBLE_STUDY.equals(key)) {
      return R.string.fellowship_caption_wednesday;
    }
    if (TaskTypes.FRIDAY_PRAYER.equals(key)) {
      return R.string.fellowship_caption_friday;
    }
    if (TaskTypes.NEW_MONTH_FASTING.equals(key)) {
      return R.string.fellowship_caption_fasting_eve;
    }
    if (TaskTypes.FASTING_DAY.equals(key)) {
      return R.string.fellowship_caption_fasting_day;
    }
    if (TaskTypes.HAPPY_NEW_MONTH.equals(key)) {
      return R.string.fellowship_caption_hnm;
    }
    if (TaskTypes.BIRTHDAY.equals(key)) {
      return R.string.fellowship_caption_birthday;
    }
    return R.string.caption;
  }

  public static int hintRes(String key) {
    if (TaskTypes.SUNDAY_SERVICE.equals(key)) {
      return R.string.fellowship_caption_sunday_hint;
    }
    if (TaskTypes.WEDNESDAY_BIBLE_STUDY.equals(key)) {
      return R.string.fellowship_caption_wednesday_hint;
    }
    if (TaskTypes.FRIDAY_PRAYER.equals(key)) {
      return R.string.fellowship_caption_friday_hint;
    }
    if (TaskTypes.NEW_MONTH_FASTING.equals(key)) {
      return R.string.fellowship_caption_fasting_eve_hint;
    }
    if (TaskTypes.FASTING_DAY.equals(key)) {
      return R.string.fellowship_caption_fasting_day_hint;
    }
    if (TaskTypes.HAPPY_NEW_MONTH.equals(key)) {
      return R.string.fellowship_caption_hnm_hint;
    }
    if (TaskTypes.BIRTHDAY.equals(key)) {
      return R.string.fellowship_caption_birthday_hint;
    }
    return R.string.series_placeholders;
  }

  public static String label(Context context, String key) {
    return context.getString(labelRes(key));
  }

  public static String hint(Context context, String key) {
    return context.getString(hintRes(key));
  }

  public static String fallback(String key) {
    if (TaskTypes.SUNDAY_SERVICE.equals(key)) {
      return "Join us for Sunday Service tomorrow.\nCome expecting, come ready.\nSee you there!";
    }
    if (TaskTypes.WEDNESDAY_BIBLE_STUDY.equals(key)) {
      return "Bible Study holds today.\nCome and grow in the Word.";
    }
    if (TaskTypes.FRIDAY_PRAYER.equals(key)) {
      return "Prayer Meeting holds today.\nLet's seek the Lord together.";
    }
    if (TaskTypes.NEW_MONTH_FASTING.equals(key)) {
      return "New Month Fasting starts tomorrow.\nJoin us as we seek the Lord for the month ahead.";
    }
    if (TaskTypes.FASTING_DAY.equals(key)) {
      return "New Month Fasting holds today.\nStay in the place of prayer.";
    }
    if (TaskTypes.HAPPY_NEW_MONTH.equals(key)) {
      return "Happy New Month!\nMay this month overflow with grace, favour, and testimonies.";
    }
    if (TaskTypes.BIRTHDAY.equals(key) || TaskTypes.ALUMNI_BIRTHDAY.equals(key)) {
      return "Happy Birthday, {name}!\nWe celebrate you and pray God's blessings over your new year.";
    }
    return "";
  }

  public static String fallback(Task task) {
    if (task == null || task.type == null) {
      return "";
    }
    if ((TaskTypes.BIRTHDAY.equals(task.type) || TaskTypes.ALUMNI_BIRTHDAY.equals(task.type))
        && task.title != null) {
      String name = task.title.replace("'s Birthday", "").trim();
      return "Happy Birthday, "
        + name
        + "!\nWe celebrate you and pray God's blessings over your new year.";
    }
    return fallback(task.type);
  }
}
