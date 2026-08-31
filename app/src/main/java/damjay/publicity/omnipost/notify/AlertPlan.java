package damjay.publicity.omnipost.notify;

import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.util.Prefs;

/** Durations for caption-ready and post-now alerts. No Android types so tests can read them. */
public final class AlertPlan {
  private AlertPlan() {}

  public static boolean escalate(String mode) {
    return Prefs.MODE_ESCALATE.equals(mode);
  }

  public static boolean ring(String mode) {
    return escalate(mode) || Prefs.MODE_BOTH.equals(mode) || Prefs.MODE_SOUND.equals(mode);
  }

  public static boolean vibrate(String mode) {
    return escalate(mode) || Prefs.MODE_BOTH.equals(mode) || Prefs.MODE_VIBRATE.equals(mode);
  }

  /** Wait this long, vibrating, before the ring. Zero means ring at once. */
  public static long ringDelayMs(String mode) {
    return escalate(mode) ? ScheduleTimes.ESCALATE_VIBRATE_MS : 0L;
  }

  public static long ringMs(String mode) {
    if (!ring(mode)) {
      return 0L;
    }
    if (escalate(mode)) {
      return ScheduleTimes.ESCALATE_RING_MS;
    }
    return ScheduleTimes.BURST_MS;
  }

  public static long totalMs(String mode) {
    long ring = ringDelayMs(mode) + ringMs(mode);
    if (ring > 0L) {
      return ring;
    }
    return vibrate(mode) ? ScheduleTimes.BURST_MS : 0L;
  }
}
