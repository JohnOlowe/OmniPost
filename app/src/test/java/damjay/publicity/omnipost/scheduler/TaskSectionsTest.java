package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
    assertEquals(2, sections.get(0).tasks.size());
    assertEquals(snoozed.id, sections.get(0).tasks.get(0).id);
    assertEquals(nag.id, sections.get(0).tasks.get(1).id);

    assertEquals(TaskTypes.SECTION_WEEKLY, sections.get(1).title);
    assertEquals(2, sections.get(1).tasks.size());
    assertEquals(friday.id, sections.get(1).tasks.get(0).id);
    assertEquals(nextSunday.id, sections.get(1).tasks.get(1).id);

    assertEquals(TaskTypes.SECTION_MONTHLY, sections.get(2).title);
    assertEquals(1, sections.get(2).tasks.size());
    assertEquals(fasting.id, sections.get(2).tasks.get(0).id);

    assertEquals(TaskTypes.SECTION_FLEXIBLE, sections.get(3).title);
    assertEquals(2, sections.get(3).tasks.size());
    assertEquals(flexible.id, sections.get(3).tasks.get(0).id);
    assertEquals(birthday.id, sections.get(3).tasks.get(1).id);

    assertEquals(TaskTypes.SECTION_ONCE, sections.get(4).title);
    assertEquals(1, sections.get(4).tasks.size());
    assertEquals(once.id, sections.get(4).tasks.get(0).id);
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
  public void dueStatusWalksTheChain() {
    long now = 1_000_000L;
    assertEquals(TaskStatus.NAGGING, TaskStatus.dueStatus(now - 10, now - 1, now));
    assertEquals(
      TaskStatus.WARNING,
      TaskStatus.dueStatus(now - 10, now + ScheduleTimes.WARNING_LEAD_MS / 2, now));
    assertEquals(TaskStatus.DRAFTING, TaskStatus.dueStatus(now - 1, now + 60_000L * 60L, now));
    assertEquals(TaskStatus.SCHEDULED, TaskStatus.dueStatus(now + 10, now + 20, now));
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
