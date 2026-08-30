package damjay.publicity.omnipost.scheduler;

public final class TaskTypes {
  public static final String SUNDAY_SERVICE = "SUNDAY_SERVICE";
  public static final String WEDNESDAY_BIBLE_STUDY = "WEDNESDAY_BIBLE_STUDY";
  public static final String FRIDAY_PRAYER = "FRIDAY_PRAYER";
  public static final String BIRTHDAY = "BIRTHDAY";
  public static final String NEW_MONTH_FASTING = "NEW_MONTH_FASTING";
  public static final String HAPPY_NEW_MONTH = "HAPPY_NEW_MONTH";
  public static final String TEST = "TEST";

  private TaskTypes() {}

  public static String label(String type) {
    if (type == null) {
      return "";
    }
    switch (type) {
      case SUNDAY_SERVICE:
        return "Sunday Service";
      case WEDNESDAY_BIBLE_STUDY:
        return "Wednesday Bible Study";
      case FRIDAY_PRAYER:
        return "Friday Prayer Meeting";
      case BIRTHDAY:
        return "Birthday";
      case NEW_MONTH_FASTING:
        return "New Month Fasting";
      case HAPPY_NEW_MONTH:
        return "Happy New Month";
      case TEST:
        return "Persistence test";
      default:
        return type;
    }
  }
}
