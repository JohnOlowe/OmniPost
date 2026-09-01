package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class AlarmSchedulerTest {
  @Test
  public void heartbeatAndWatchdogUseStableDistinctCodes() {
    int pulse = AlarmScheduler.requestCode(0L, AlarmScheduler.PHASE_PULSE);
    int watch = AlarmScheduler.requestCode(0L, AlarmScheduler.PHASE_WATCHDOG);
    assertEquals(pulse, AlarmScheduler.requestCode(0L, AlarmScheduler.PHASE_PULSE));
    assertNotEquals(pulse, watch);
  }

  @Test
  public void taskPhasesDoNotCollide() {
    long id = 42L;
    int draft = AlarmScheduler.requestCode(id, AlarmScheduler.PHASE_DRAFT);
    int warn = AlarmScheduler.requestCode(id, AlarmScheduler.PHASE_WARNING);
    int nag = AlarmScheduler.requestCode(id, AlarmScheduler.PHASE_NAG);
    assertNotEquals(draft, warn);
    assertNotEquals(warn, nag);
    assertNotEquals(draft, nag);
  }
}
