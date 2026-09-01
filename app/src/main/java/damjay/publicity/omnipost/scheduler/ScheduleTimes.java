package damjay.publicity.omnipost.scheduler;

public final class ScheduleTimes {
  public static final int WEEKLY_POST_HOUR = 10;
  public static final int WEEKLY_POST_MINUTE = 0;
  /** Same clock time the day before — a full 24 hours to tinker. */
  public static final int WEEKLY_DRAFT_HOUR = 10;
  public static final int WEEKLY_DRAFT_MINUTE = 0;
  public static final int EVENING_DRAFT_HOUR = 20;
  public static final int BIRTHDAY_POST_HOUR = 7;
  public static final int MONTH_POST_HOUR = 7;
  public static final long WARNING_LEAD_MS = 30 * 60_000L;
  /** Loud ring so the flyer actually goes out on time. */
  public static final long MINUTE_LEAD_MS = 60_000L;
  public static final long NAG_INTERVAL_MS = 5 * 60_000L;
  public static final long BURST_MS = 25_000L;
  /** Write-caption nudge: a short vibrate, never the 5-minute ring. */
  public static final long GENTLE_MS = 8_000L;
  /** Escalate: shake first so a quiet pocket is enough; then ring until they tap. */
  public static final long ESCALATE_VIBRATE_MS = 30_000L;
  public static final long ESCALATE_RING_MS = 5 * 60_000L;
  /** AlarmClock heartbeat so the desk text and missed phases survive swipe-away. */
  public static final long HEARTBEAT_MS = 60_000L;
  public static final long KICK_MS = 5_000L;
  public static final int WATCHDOG_HOUR = 5;
  public static final int GENERATE_WEEKLY_COUNT = 4;
  public static final int GENERATE_MONTH_COUNT = 2;
  /** Rolling window so the next dawn is ready without dumping every day until D-Day. */
  public static final int GENERATE_COUNTDOWN_DAYS = 3;

  private ScheduleTimes() {}
}
