package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class RoutineGeneratorTest {
  @Test
  public void generatesSaturdaySundayServiceFromWednesday() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks = RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.emptyList());
    boolean found = false;
    for (Task task : tasks) {
      if (TaskTypes.SUNDAY_SERVICE.equals(task.type)
        && task.occurrenceKey.contains("2026-08-29")) {
        found = true;
        Calendar post = Calendar.getInstance(utc);
        post.setTimeInMillis(task.postAtMillis);
        assertEquals(10, post.get(Calendar.HOUR_OF_DAY));
        assertEquals(Calendar.SATURDAY, post.get(Calendar.DAY_OF_WEEK));
        Calendar draft = Calendar.getInstance(utc);
        draft.setTimeInMillis(task.draftAtMillis);
        assertEquals(28, draft.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, draft.get(Calendar.HOUR_OF_DAY));
        assertEquals(ScheduleTimes.WARNING_LEAD_MS, 30 * 60_000L);
      }
    }
    assertTrue(found);
  }

  @Test
  public void generatesBirthdayForMemberTheEveningBefore() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Member member = new Member();
    member.id = 7;
    member.name = "Ada";
    member.birthMonth = 8;
    member.birthDay = 31;
    List<Task> tasks =
      RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.singletonList(member));
    boolean found = false;
    for (Task task : tasks) {
      if ("BIRTHDAY|7|2026-08-31".equals(task.occurrenceKey)) {
        found = true;
        assertEquals("Ada's Birthday", task.title);
        assertEquals("Yearly", TaskTypes.cadence(task.type));
        Calendar post = Calendar.getInstance(utc);
        post.setTimeInMillis(task.postAtMillis);
        assertEquals(7, post.get(Calendar.HOUR_OF_DAY));
        Calendar draft = Calendar.getInstance(utc);
        draft.setTimeInMillis(task.draftAtMillis);
        assertEquals(30, draft.get(Calendar.DAY_OF_MONTH));
        assertEquals(20, draft.get(Calendar.HOUR_OF_DAY));
      }
    }
    assertTrue(found);
  }

  @Test
  public void generatesFastingTomorrowAndAgainOnTheDay() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 30, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks = RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.emptyList());
    boolean fastingEve = false;
    boolean fastingDay = false;
    boolean happy = false;
    for (Task task : tasks) {
      if ((TaskTypes.NEW_MONTH_FASTING + "|2026-08-31").equals(task.occurrenceKey)) {
        fastingEve = true;
        assertEquals("Monthly", TaskTypes.cadence(task.type));
      }
      if ((TaskTypes.FASTING_DAY + "|2026-09-01").equals(task.occurrenceKey)) {
        fastingDay = true;
      }
      if ((TaskTypes.HAPPY_NEW_MONTH + "|2026-09-01").equals(task.occurrenceKey)) {
        happy = true;
      }
    }
    assertTrue(fastingEve);
    assertTrue(fastingDay);
    assertTrue(happy);
  }

  @Test
  public void weeklyCadenceIsWeekly() {
    assertEquals("Weekly", TaskTypes.cadence(TaskTypes.FRIDAY_PRAYER));
    assertEquals("Once", TaskTypes.cadence(TaskTypes.TEST));
    assertEquals("Flexible", TaskTypes.cadence(TaskTypes.FLEXIBLE));
    assertEquals("Once", TaskTypes.cadence(TaskTypes.ONE_OFF));
  }

  @Test
  public void sectionsMatchHowTheDeskWorks() {
    assertEquals(TaskTypes.SECTION_WEEKLY, TaskTypes.section(TaskTypes.SUNDAY_SERVICE));
    assertEquals(TaskTypes.SECTION_MONTHLY, TaskTypes.section(TaskTypes.FASTING_DAY));
    assertEquals(TaskTypes.SECTION_FLEXIBLE, TaskTypes.section(TaskTypes.BIRTHDAY));
    assertEquals(TaskTypes.SECTION_FLEXIBLE, TaskTypes.section(TaskTypes.FLEXIBLE));
    assertEquals(TaskTypes.SECTION_ONCE, TaskTypes.section(TaskTypes.ONE_OFF));
  }
}
