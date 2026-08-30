package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Task;

public final class CaptionTemplates {
  private CaptionTemplates() {}

  public static String forTask(Task task) {
    if (task == null || task.type == null) {
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
        return "New Month Fasting starts now.\nJoin us as we seek the Lord for the month ahead.";
      case TaskTypes.HAPPY_NEW_MONTH:
        return "Happy New Month!\nMay this month overflow with grace, favour, and testimonies.";
      default:
        return "";
    }
  }
}
