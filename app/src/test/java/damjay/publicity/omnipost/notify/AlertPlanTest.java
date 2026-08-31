package damjay.publicity.omnipost.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.util.Prefs;
import org.junit.Test;

public class AlertPlanTest {
  @Test
  public void escalateVibratesThirtySecondsThenRingsFiveMinutes() {
    assertTrue(AlertPlan.escalate(Prefs.MODE_ESCALATE));
    assertEquals(30_000L, AlertPlan.ringDelayMs(Prefs.MODE_ESCALATE));
    assertEquals(5 * 60_000L, AlertPlan.ringMs(Prefs.MODE_ESCALATE));
    assertEquals(
      ScheduleTimes.ESCALATE_VIBRATE_MS + ScheduleTimes.ESCALATE_RING_MS,
      AlertPlan.totalMs(Prefs.MODE_ESCALATE));
  }

  @Test
  public void otherModesDoNotDelayTheRing() {
    assertEquals(0L, AlertPlan.ringDelayMs(Prefs.MODE_BOTH));
    assertEquals(0L, AlertPlan.ringDelayMs(Prefs.MODE_SOUND));
    assertEquals(0L, AlertPlan.ringMs(Prefs.MODE_VIBRATE));
    assertEquals(ScheduleTimes.BURST_MS, AlertPlan.ringMs(Prefs.MODE_BOTH));
    assertFalse(AlertPlan.ring(Prefs.MODE_VIBRATE));
    assertEquals(ScheduleTimes.BURST_MS, AlertPlan.totalMs(Prefs.MODE_VIBRATE));
  }
}
