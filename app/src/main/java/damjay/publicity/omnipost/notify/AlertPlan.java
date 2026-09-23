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
    return ringDelayMs(mode, ScheduleTimes.ESCALATE_VIBRATE_MS);
  }

  public static long ringDelayMs(String mode, long vibrateMs) {
    if (!escalate(mode)) {
      return 0L;
    }
    return Math.max(0L, vibrateMs);
  }

  public static long ringMs(String mode) {
    return ringMs(mode, ScheduleTimes.ESCALATE_RING_MS);
  }

  public static long ringMs(String mode, long customRingMs) {
    if (!ring(mode)) {
      return 0L;
    }
    if (escalate(mode)) {
      return Math.max(0L, customRingMs);
    }
    return ScheduleTimes.BURST_MS;
  }

  public static long totalMs(String mode) {
    return totalMs(mode, ScheduleTimes.ESCALATE_VIBRATE_MS, ScheduleTimes.ESCALATE_RING_MS);
  }

  public static long totalMs(String mode, long vibrateMs, long customRingMs) {
    long ring = ringDelayMs(mode, vibrateMs) + ringMs(mode, customRingMs);
    if (ring > 0L) {
      return ring;
    }
    return vibrate(mode) ? ScheduleTimes.BURST_MS : 0L;
  }
}
