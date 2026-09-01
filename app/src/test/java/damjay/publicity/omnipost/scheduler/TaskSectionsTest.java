package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TaskSectionsTest {
  @Test
  public void pendingGroupsByCadenceAndCollapsesSeries() {
    Task nag = task(1, TaskTypes.SUNDAY_SERVICE, TaskStatus.NAGGING, 100);
    Task nextSunday = task(2, TaskTypes.SUNDAY_SERVICE, TaskStatus.SCHEDULED, 800);
    Task laterSunday = task(3, TaskTypes.SUNDAY_SERVICE, TaskStatus.SCHEDULED, 1500);
    Task friday = task(4, TaskTypes.FRIDAY_PRAYER, TaskStatus.SCHEDULED, 400);
    Task fasting = task(5, TaskTypes.FASTING_DAY, TaskStatus.SCHEDULED, 900);
    Task laterFasting = task(6, TaskTypes.FASTING_DAY, TaskStatus.SCHEDULED, 2000);
    Task birthday = task(7, TaskTypes.BIRTHDAY, TaskStatus.SCHEDULED, 300);
    Task flexible = task(8, TaskTypes.FLEXIBLE, TaskStatus.DRAFTING, 250);
    Task once = task(9, TaskTypes.ONE_OFF, TaskStatus.SCHEDULED, 600);
    Task snoozed = task(10, TaskTypes.WEDNESDAY_BIBLE_STUDY, TaskStatus.SNOOZED, 50);

    List<TaskSections.Section> sections = TaskSections.group(
      Arrays.asList(
        nag, nextSunday, laterSunday, friday, fasting, laterFasting, birthday, flexible, once, snoozed),
      false);

    assertEquals(5, sections.size());
    assertEquals(TaskTypes.SECTION_NOW, sections.get(0).title);
    assertEquals(3, sections.get(0).tasks.size());
    assertEquals(snoozed.id, sections.get(0).tasks.get(0).id);
    assertEquals(nag.id, sections.get(0).tasks.get(1).id);
    assertEquals(flexible.id, sections.get(0).tasks.get(2).id);

    assertEquals(TaskTypes.SECTION_WEEKLY, sections.get(1).title);
    assertEquals(2, sections.get(1).tasks.size());
    assertEquals(friday.id, sections.get(1).tasks.get(0).id);
    assertEquals(nextSunday.id, sections.get(1).tasks.get(1).id);

    assertEquals(TaskTypes.SECTION_MONTHLY, sections.get(2).title);
    assertEquals(1, sections.get(2).tasks.size());
    assertEquals(fasting.id, sections.get(2).tasks.get(0).id);

    assertEquals(TaskTypes.SECTION_FLEXIBLE, sections.get(3).title);
    assertEquals(1, sections.get(3).tasks.size());
    assertEquals(birthday.id, sections.get(3).tasks.get(0).id);

    assertEquals(TaskTypes.SECTION_ONCE, sections.get(4).title);
    assertEquals(1, sections.get(4).tasks.size());
    assertEquals(once.id, sections.get(4).tasks.get(0).id);
  }

  @Test
  public void nextFortyEightHoursThenCadenceAndOneCountdownCard() {
    long now = 1_700_000_000_000L;
    Task drafting = task(1, TaskTypes.SUNDAY_SERVICE, TaskStatus.DRAFTING, now + 10_000L);
    Task soonFriday = task(2, TaskTypes.FRIDAY_PRAYER, TaskStatus.SCHEDULED, now + 3_600_000L);
    Task laterFasting = task(3, TaskTypes.FASTING_DAY, TaskStatus.SCHEDULED, now + 5L * 86_400_000L);
    Task countdownToday = task(4, TaskTypes.COUNTDOWN, TaskStatus.SCHEDULED, now + 2_000L);
    Task countdownTomorrow = task(5, TaskTypes.COUNTDOWN, TaskStatus.SCHEDULED, now + 86_400_000L);
    Task noticeEve = task(6, TaskTypes.BIRTHDAY_NOTICE, TaskStatus.SCHEDULED, now + 1_000L);
    Task noticeLater = task(7, TaskTypes.BIRTHDAY_NOTICE, TaskStatus.SCHEDULED, now + 10L * 86_400_000L);

    List<TaskSections.Section> sections = TaskSections.group(
      Arrays.asList(
        drafting, soonFriday, laterFasting, countdownToday, countdownTomorrow, noticeEve, noticeLater),
      false,
      now);

    assertEquals(TaskTypes.SECTION_NOW, sections.get(0).title);
    assertEquals(1, sections.get(0).tasks.size());
    assertEquals(drafting.id, sections.get(0).tasks.get(0).id);

    assertEquals(TaskTypes.SECTION_NEXT, sections.get(1).title);
    assertEquals(3, sections.get(1).tasks.size());
    assertEquals(noticeEve.id, sections.get(1).tasks.get(0).id);
    assertEquals(countdownToday.id, sections.get(1).tasks.get(1).id);
    assertEquals(soonFriday.id, sections.get(1).tasks.get(2).id);

    assertEquals(TaskTypes.SECTION_MONTHLY, sections.get(2).title);
    assertEquals(1, sections.get(2).tasks.size());
    assertEquals(laterFasting.id, sections.get(2).tasks.get(0).id);
    assertEquals(3, sections.size());
  }

  @Test
  public void snoozedOrderAndDeskFollowTheAlarmNotTheOriginalPost() {
    long now = 1_700_000_000_000L;
    Task birthday = task(1, TaskTypes.BIRTHDAY_NOTICE, TaskStatus.SNOOZED, now - 9L * 3_600_000L);
    birthday.snoozeUntilMillis = now + 3L * 3_600_000L;
    birthday.title = "Birthday notice · September";
    Task fasting = task(2, TaskTypes.NEW_MONTH_FASTING, TaskStatus.SNOOZED, now - 9L * 3_600_000L);
    fasting.snoozeUntilMillis = now + 3_600_000L;
    fasting.title = "Fasting tomorrow";

    List<TaskSections.Section> sections =
      TaskSections.group(Arrays.asList(birthday, fasting), false, now);
    assertEquals(TaskTypes.SECTION_NOW, sections.get(0).title);
    assertEquals(2, sections.get(0).tasks.size());
    assertEquals(fasting.id, sections.get(0).tasks.get(0).id);
    assertEquals(birthday.id, sections.get(0).tasks.get(1).id);

    Task next = TaskStatus.nextToRing(
      Arrays.asList(birthday, fasting), now, ScheduleTimes.WARNING_LEAD_MS);
    assertEquals(fasting.id, next.id);
    assertEquals(fasting.snoozeUntilMillis, TaskStatus.nextRingMillis(next, now, ScheduleTimes.WARNING_LEAD_MS));
  }

  @Test
  public void overdueNagRingsBeforeALaterSnooze() {
    long now = 1_700_000_000_000L;
    Task nag = task(1, TaskTypes.COUNTDOWN, TaskStatus.NAGGING, now - 3_600_000L);
    Task snoozed = task(2, TaskTypes.FRIDAY_PRAYER, TaskStatus.SNOOZED, now - 3_600_000L);
    snoozed.snoozeUntilMillis = now + 3_600_000L;
    Task next = TaskStatus.nextToRing(Arrays.asList(snoozed, nag), now, ScheduleTimes.WARNING_LEAD_MS);
    assertEquals(nag.id, next.id);
  }

  @Test
  public void twoCountdownsStayAsSeparateCards() {
    long now = 1_700_000_000_000L;
    Task beyond = task(1, TaskTypes.COUNTDOWN, TaskStatus.SCHEDULED, now + 2_000L);
    beyond.seriesId = 5L;
    Task camp = task(2, TaskTypes.COUNTDOWN, TaskStatus.SCHEDULED, now + 3_000L);
    camp.seriesId = 42L;
    Task beyondLater = task(3, TaskTypes.COUNTDOWN, TaskStatus.SCHEDULED, now + 86_400_000L);
    beyondLater.seriesId = 5L;
    List<TaskSections.Section> sections =
      TaskSections.group(java.util.Arrays.asList(beyond, camp, beyondLater), false, now);
    assertEquals(TaskTypes.SECTION_NEXT, sections.get(0).title);
    assertEquals(2, sections.get(0).tasks.size());
    assertEquals(beyond.id, sections.get(0).tasks.get(0).id);
    assertEquals(camp.id, sections.get(0).tasks.get(1).id);
  }

  @Test
  public void postedDoesNotCollapseAndSkipsNeedsYou() {
    Task postedSunday = task(1, TaskTypes.SUNDAY_SERVICE, TaskStatus.POSTED, 100);
    Task postedSunday2 = task(2, TaskTypes.SUNDAY_SERVICE, TaskStatus.POSTED, 200);
    List<TaskSections.Section> sections =
      TaskSections.group(Arrays.asList(postedSunday, postedSunday2), true);
    assertEquals(1, sections.size());
    assertEquals(TaskTypes.SECTION_WEEKLY, sections.get(0).title);
    assertEquals(2, sections.get(0).tasks.size());
    assertFalse(TaskTypes.SECTION_NOW.equals(sections.get(0).title));
  }

  @Test
  public void nextRingIncludesOneMinuteWarning() {
    long now = 1_000_000L;
    Task task = task(1, TaskTypes.SUNDAY_SERVICE, TaskStatus.WARNING, now + 90_000L);
    task.draftAtMillis = now - 10_000L;
    assertEquals(
      now + 30_000L,
      TaskStatus.nextRingMillis(task, now, ScheduleTimes.WARNING_LEAD_MS));
    assertEquals(AlarmScheduler.PHASE_MINUTE, 7);
    assertTrue(AlarmScheduler.loudPhase(AlarmScheduler.PHASE_MINUTE));
    assertTrue(AlarmScheduler.loudPhase(AlarmScheduler.PHASE_NAG));
    assertFalse(AlarmScheduler.loudPhase(AlarmScheduler.PHASE_DRAFT));
    assertFalse(AlarmScheduler.loudPhase(AlarmScheduler.PHASE_WARNING));
  }

  @Test
  public void dueStatusWalksTheChain() {
    long now = 1_000_000L;
    assertEquals(TaskStatus.NAGGING, TaskStatus.dueStatus(now - 10, now - 1, now));
    assertEquals(
      TaskStatus.WARNING,
      TaskStatus.dueStatus(now - 10, now + ScheduleTimes.WARNING_LEAD_MS / 2, now));
    assertEquals(TaskStatus.DRAFTING, TaskStatus.dueStatus(now - 1, now + 60_000L * 60L, now));
    long later = now + 3L * 60L * 60L * 1000L;
    assertEquals(TaskStatus.SCHEDULED, TaskStatus.dueStatus(later - 60_000L, later, now));
    long lead = 15L * 60_000L;
    assertEquals(
      TaskStatus.WARNING,
      TaskStatus.dueStatus(now - 10, now + lead / 2, now, lead));
    assertEquals(
      TaskStatus.DRAFTING,
      TaskStatus.dueStatus(now - 1, now + lead + 60_000L, now, lead));
  }

  private static Task task(long id, String type, String status, long postAt) {
    Task task = new Task();
    task.id = id;
    task.type = type;
    task.status = status;
    task.postAtMillis = postAt;
    task.title = type + id;
    return task;
  }
}
