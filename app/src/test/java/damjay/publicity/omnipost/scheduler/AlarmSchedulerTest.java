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
    Task task = task(100_000L, 200_000L);
    String a = AlarmScheduler.armStamp(task, 50_000L, 30_000L);
    String b = AlarmScheduler.armStamp(task, 60_000L, 30_000L);
    assertEquals(a, b);
    assertEquals("arm|d100000", a);
  }

  @Test
  public void armStampChangesWhenAPhaseIsCrossed() {
    Task task = task(100_000L, 200_000L);
    String beforeDraft = AlarmScheduler.armStamp(task, 50_000L, 30_000L);
    String afterDraft = AlarmScheduler.armStamp(task, 120_000L, 30_000L);
    assertNotEquals(beforeDraft, afterDraft);
    assertEquals("arm|m140000", afterDraft);
  }

  @Test
  public void warningOffOmitsTheCaptionReadyArm() {
    Task task = task(100_000L, 200_000L);
    assertEquals("arm|d100000", AlarmScheduler.armStamp(task, 50_000L, 0L));
  }

  @Test
  public void alumniBirthdayOmitsThePostRingSoThePackNagsOnce() {
    Task task = task(100_000L, 200_000L);
    task.type = TaskTypes.ALUMNI_BIRTHDAY;
    assertEquals("arm|d100000", AlarmScheduler.armStamp(task, 50_000L, 30_000L));
  }

  @Test
  public void skipCaptionOmitsDraftAndWarningArms() {
    Task task = task(100_000L, 200_000L);
    task.skipCaption = true;
    assertEquals("arm|m140000", AlarmScheduler.armStamp(task, 50_000L, 30_000L));
  }

  @Test
  public void nextArmIsOnlyTheSoonestPhase() {
    Task task = task(100_000L, 200_000L);
    long[] next = AlarmScheduler.nextArm(task, 50_000L, 30_000L);
    assertEquals(AlarmScheduler.PHASE_DRAFT, (int) next[0]);
    assertEquals(100_000L, next[1]);
    long[] afterDraft = AlarmScheduler.nextArm(task, 120_000L, 30_000L);
    assertEquals(AlarmScheduler.PHASE_MINUTE, (int) afterDraft[0]);
    assertEquals(140_000L, afterDraft[1]);
  }

  @Test
  public void recycleStaleIgnoresANullContext() {
    AlarmScheduler.recycleStale(null, null, 0L);
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
