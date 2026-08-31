package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    assertEquals("Daily", TaskTypes.cadence(TaskTypes.COUNTDOWN));
    assertEquals("Monthly", TaskTypes.cadence(TaskTypes.BIRTHDAY_NOTICE));
  }

  @Test
  public void sectionsMatchHowTheDeskWorks() {
    assertEquals(TaskTypes.SECTION_WEEKLY, TaskTypes.section(TaskTypes.SUNDAY_SERVICE));
    assertEquals(TaskTypes.SECTION_MONTHLY, TaskTypes.section(TaskTypes.FASTING_DAY));
    assertEquals(TaskTypes.SECTION_MONTHLY, TaskTypes.section(TaskTypes.BIRTHDAY_NOTICE));
    assertEquals(TaskTypes.SECTION_CAMPAIGN, TaskTypes.section(TaskTypes.COUNTDOWN));
    assertEquals(TaskTypes.SECTION_FLEXIBLE, TaskTypes.section(TaskTypes.BIRTHDAY));
    assertEquals(TaskTypes.SECTION_FLEXIBLE, TaskTypes.section(TaskTypes.FLEXIBLE));
    assertEquals(TaskTypes.SECTION_ONCE, TaskTypes.section(TaskTypes.ONE_OFF));
  }

  @Test
  public void beyondLimitCountdownIsNineDaysOn31AugAndStopsAfterDDay() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 31, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks = RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.emptyList());
    Task today = null;
    int countdownCards = 0;
    for (Task task : tasks) {
      if (!TaskTypes.COUNTDOWN.equals(task.type)) {
        continue;
      }
      countdownCards++;
      if ((TaskTypes.COUNTDOWN + "|2026-09-09|2026-08-31").equals(task.occurrenceKey)) {
        today = task;
      }
    }
    assertTrue(countdownCards > 0);
    assertTrue(countdownCards <= ScheduleTimes.GENERATE_COUNTDOWN_DAYS);
    assertTrue(today != null);
    assertEquals("Beyond Limit '26 · 9 days to go", today.title);
    assertEquals(9, CaptionTemplates.countdownDays(today));
    String caption = CaptionTemplates.forTask(null, today);
    assertTrue(caption.contains("*IT'S 9 DAYS TO GO!*"));
    assertTrue(caption.contains("is 9 days away"));

    Calendar after = Calendar.getInstance(utc);
    after.clear();
    after.setTimeZone(utc);
    after.set(2026, Calendar.SEPTEMBER, 10, 12, 0, 0);
    after.set(Calendar.MILLISECOND, 0);
    List<Task> later = RoutineGenerator.generate(after.getTimeInMillis(), utc, Collections.emptyList());
    for (Task task : later) {
      assertFalse(TaskTypes.COUNTDOWN.equals(task.type));
    }
  }

  @Test
  public void birthdayNoticeIsEveThenTwoInMonthWithMonthName() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 31, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks = RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.emptyList());
    boolean eve = false;
    boolean first = false;
    boolean second = false;
    for (Task task : tasks) {
      if ((TaskTypes.BIRTHDAY_NOTICE + "|2026-09|EVE|2026-08-31").equals(task.occurrenceKey)) {
        eve = true;
        assertEquals("Birthday notice · September", task.title);
        String caption = CaptionTemplates.forTask(null, task);
        assertTrue(caption.contains("*Month of September*"));
        assertTrue(caption.contains("wa.me/2349112413798"));
        Calendar post = Calendar.getInstance(utc);
        post.setTimeInMillis(task.postAtMillis);
        assertEquals(31, post.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.AUGUST, post.get(Calendar.MONTH));
        assertEquals(7, post.get(Calendar.HOUR_OF_DAY));
      }
      if ((TaskTypes.BIRTHDAY_NOTICE + "|2026-09|FIRST|2026-09-10").equals(task.occurrenceKey)) {
        first = true;
      }
      if ((TaskTypes.BIRTHDAY_NOTICE + "|2026-09|SECOND|2026-09-20").equals(task.occurrenceKey)) {
        second = true;
      }
    }
    assertTrue(eve);
    assertTrue(first);
    assertTrue(second);
  }
}
