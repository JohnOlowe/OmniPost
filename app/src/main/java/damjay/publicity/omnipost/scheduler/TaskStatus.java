package damjay.publicity.omnipost.scheduler;

public final class TaskStatus {
  public static final String SCHEDULED = "SCHEDULED";
  public static final String DRAFTING = "DRAFTING";
  public static final String WARNING = "WARNING";
  public static final String NAGGING = "NAGGING";
  public static final String POSTED = "POSTED";

  private TaskStatus() {}

  public static String label(String status) {
    if (status == null) {
      return "";
    }
    switch (status) {
      case SCHEDULED:
        return "Upcoming";
      case DRAFTING:
        return "Write caption";
      case WARNING:
        return "30 min";
      case NAGGING:
        return "Post now";
      case POSTED:
        return "Posted";
      default:
        return status;
    }
  }
}
