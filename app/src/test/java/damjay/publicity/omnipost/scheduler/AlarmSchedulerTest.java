package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import damjay.publicity.omnipost.data.entity.Task;
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
    int minute = AlarmScheduler.requestCode(id, AlarmScheduler.PHASE_MINUTE);
    assertNotEquals(draft, warn);
    assertNotEquals(warn, nag);
    assertNotEquals(draft, nag);
    assertNotEquals(minute, nag);
    assertNotEquals(minute, warn);
  }

  @Test
  public void armStampStaysPutWhileTheSameAlarmsAreDue() {
    Task task = task(1_000L, 10_000L);
    String a = AlarmScheduler.armStamp(task, 2_000L, 3_000L);
    String b = AlarmScheduler.armStamp(task, 2_500L, 3_000L);
    assertEquals(a, b);
    assertEquals("arm|w7000|m9000|n10000", a);
  }

  @Test
  public void armStampChangesWhenAPhaseIsCrossed() {
    Task task = task(1_000L, 10_000L);
    String beforeDraft = AlarmScheduler.armStamp(task, 500L, 3_000L);
    String afterDraft = AlarmScheduler.armStamp(task, 1_500L, 3_000L);
    assertNotEquals(beforeDraft, afterDraft);
    assertEquals("arm|d1000|w7000|m9000|n10000", beforeDraft);
  }

  @Test
  public void snoozeStampTracksTheQuietUntilTime() {
    Task task = task(1_000L, 10_000L);
    task.status = TaskStatus.SNOOZED;
    task.snoozeUntilMillis = 80_000L;
    assertEquals("snooze|20000|80000", AlarmScheduler.armStamp(task, 1_000L, 3_000L));
    assertEquals("snooze|0|80000", AlarmScheduler.armStamp(task, 25_000L, 3_000L));
  }

  private static Task task(long draftAt, long postAt) {
    Task task = new Task();
    task.id = 9L;
    task.status = TaskStatus.SCHEDULED;
    task.draftAtMillis = draftAt;
    task.postAtMillis = postAt;
    return task;
  }
}
