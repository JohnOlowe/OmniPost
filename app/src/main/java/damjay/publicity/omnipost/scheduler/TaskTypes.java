package damjay.publicity.omnipost.scheduler;

public final class TaskTypes {
  public static final String SUNDAY_SERVICE = "SUNDAY_SERVICE";
  public static final String WEDNESDAY_BIBLE_STUDY = "WEDNESDAY_BIBLE_STUDY";
  public static final String FRIDAY_PRAYER = "FRIDAY_PRAYER";
  public static final String BIRTHDAY = "BIRTHDAY";
  public static final String NEW_MONTH_FASTING = "NEW_MONTH_FASTING";
  public static final String FASTING_DAY = "FASTING_DAY";
  public static final String HAPPY_NEW_MONTH = "HAPPY_NEW_MONTH";
  public static final String COUNTDOWN = "COUNTDOWN";
  public static final String BIRTHDAY_NOTICE = "BIRTHDAY_NOTICE";
  public static final String FLEXIBLE = "FLEXIBLE";
  public static final String ONE_OFF = "ONE_OFF";
  public static final String TEST = "TEST";

  public static final String SECTION_NOW = "Needs you";
  public static final String SECTION_NEXT = "Next 48 hours";
  public static final String SECTION_WEEKLY = "Weekly";
  public static final String SECTION_MONTHLY = "Monthly";
  public static final String SECTION_CAMPAIGN = "Campaign";
  public static final String SECTION_FLEXIBLE = "Flexible";
  public static final String SECTION_ONCE = "One-off";

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
        return "Fasting tomorrow";
      case FASTING_DAY:
        return "Fasting today";
      case HAPPY_NEW_MONTH:
        return "Happy New Month";
      case COUNTDOWN:
        return "Beyond Limit countdown";
      case BIRTHDAY_NOTICE:
        return "Birthday notice";
      case FLEXIBLE:
        return "Flexible";
      case ONE_OFF:
        return "One-off";
      case TEST:
        return "Persistence test";
      default:
        return type;
    }
  }

  public static String cadence(String type) {
    if (type == null) {
      return "Once";
    }
    switch (type) {
      case SUNDAY_SERVICE:
      case WEDNESDAY_BIBLE_STUDY:
      case FRIDAY_PRAYER:
        return "Weekly";
      case NEW_MONTH_FASTING:
      case FASTING_DAY:
      case HAPPY_NEW_MONTH:
      case BIRTHDAY_NOTICE:
        return "Monthly";
      case COUNTDOWN:
        return "Daily";
      case BIRTHDAY:
        return "Yearly";
      case FLEXIBLE:
        return "Flexible";
      default:
        return "Once";
    }
  }

  public static String section(String type) {
    if (type == null) {
      return SECTION_ONCE;
    }
    switch (type) {
      case SUNDAY_SERVICE:
      case WEDNESDAY_BIBLE_STUDY:
      case FRIDAY_PRAYER:
        return SECTION_WEEKLY;
      case NEW_MONTH_FASTING:
      case FASTING_DAY:
      case HAPPY_NEW_MONTH:
      case BIRTHDAY_NOTICE:
        return SECTION_MONTHLY;
      case COUNTDOWN:
        return SECTION_CAMPAIGN;
      case BIRTHDAY:
      case FLEXIBLE:
        return SECTION_FLEXIBLE;
      default:
        return SECTION_ONCE;
    }
  }

  public static boolean isCustom(String type) {
    return FLEXIBLE.equals(type) || ONE_OFF.equals(type) || TEST.equals(type);
  }

  public static boolean isSeries(String type) {
    String section = section(type);
    return SECTION_WEEKLY.equals(section)
        || SECTION_MONTHLY.equals(section)
        || COUNTDOWN.equals(type);
  }

  /** Daily countdown and birthday NOTICE show as one card, not a cluster. */
  public static boolean oneCard(String type) {
    return COUNTDOWN.equals(type) || BIRTHDAY_NOTICE.equals(type);
  }
}
