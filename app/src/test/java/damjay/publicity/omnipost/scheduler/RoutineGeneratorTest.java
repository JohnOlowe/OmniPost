package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertEquals(20, draft.get(Calendar.HOUR_OF_DAY));
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
        assertEquals(TaskTypes.BIRTHDAY, task.type);
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
  public void sameDayWriteNagLandsOnThePostMorning() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(),
      utc,
      Collections.<Member>emptyList(),
      Collections.<Series>emptyList(),
      8,
      0);
    boolean found = false;
    for (Task task : tasks) {
      if (TaskTypes.SUNDAY_SERVICE.equals(task.type)
        && task.occurrenceKey.contains("2026-08-29")) {
        found = true;
        Calendar draft = Calendar.getInstance(utc);
        draft.setTimeInMillis(task.draftAtMillis);
        assertEquals(29, draft.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, draft.get(Calendar.HOUR_OF_DAY));
      }
    }
    assertTrue(found);
  }

  @Test
  public void weeklyCadenceIsWeekly() {
    assertEquals("Weekly", TaskTypes.cadence(TaskTypes.FRIDAY_PRAYER));
    assertEquals("Once", TaskTypes.cadence(TaskTypes.TEST));
    assertEquals("Flexible", TaskTypes.cadence(TaskTypes.FLEXIBLE));
    assertEquals("Yearly", TaskTypes.cadence(TaskTypes.ALUMNI_BIRTHDAY));
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
    assertEquals(TaskTypes.SECTION_ALUMNI, TaskTypes.section(TaskTypes.ALUMNI_BIRTHDAY));
    assertEquals(TaskTypes.SECTION_ALUMNI, TaskTypes.section(TaskTypes.ALUMNI_MONTH));
    assertEquals(TaskTypes.SECTION_ALUMNI, TaskTypes.section(TaskTypes.ALUMNI_WAVE));
    assertEquals(TaskTypes.SECTION_ALUMNI, TaskTypes.section(TaskTypes.ALUMNI_PHOTO));
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

  @Test
  public void seededSeriesWithDatabaseIdKeepsLegacyKeys() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 31, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Series countdown = SeriesDefaults.beyondLimit();
    countdown.id = 5L;
    Series notice = SeriesDefaults.birthdayNotice();
    notice.id = 6L;
    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(), utc, Collections.<Member>emptyList(), Arrays.asList(countdown, notice));
    boolean countdownKey = false;
    boolean noticeKey = false;
    for (Task task : tasks) {
      if ((TaskTypes.COUNTDOWN + "|2026-09-09|2026-08-31").equals(task.occurrenceKey)) {
        countdownKey = true;
        assertEquals(5L, task.seriesId);
      }
      if ((TaskTypes.BIRTHDAY_NOTICE + "|2026-09|FIRST|2026-09-10").equals(task.occurrenceKey)) {
        noticeKey = true;
        assertEquals(6L, task.seriesId);
      }
    }
    assertTrue(countdownKey);
    assertTrue(noticeKey);
  }

  @Test
  public void customSeriesUseOwnKeysAndOptionalMonthSlots() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 31, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);

    Series camp = new Series();
    camp.id = 42L;
    camp.title = "Youth camp";
    camp.kind = Series.KIND_COUNTDOWN;
    camp.enabled = true;
    camp.postHour = 7;
    Calendar event = Calendar.getInstance(utc);
    event.clear();
    event.setTimeZone(utc);
    event.set(2026, Calendar.OCTOBER, 1, 0, 0, 0);
    event.set(Calendar.MILLISECOND, 0);
    camp.eventAtMillis = event.getTimeInMillis();

    Series notice = new Series();
    notice.id = 9L;
    notice.title = "Workers' meeting";
    notice.kind = Series.KIND_MONTHLY;
    notice.enabled = true;
    notice.postHour = 7;
    notice.lastOfPrevMonth = true;
    notice.tenth = true;
    notice.twentieth = false;

    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(), utc, Collections.<Member>emptyList(), Arrays.asList(camp, notice));
    boolean campToday = false;
    boolean eve = false;
    boolean tenth = false;
    boolean twentieth = false;
    for (Task task : tasks) {
      if ((TaskTypes.COUNTDOWN + "|42|2026-10-01|2026-08-31").equals(task.occurrenceKey)) {
        campToday = true;
        assertEquals("Youth camp · 31 days to go", task.title);
        assertEquals(42L, task.seriesId);
      }
      if ((TaskTypes.BIRTHDAY_NOTICE + "|9|2026-09|EVE|2026-08-31").equals(task.occurrenceKey)) {
        eve = true;
        assertEquals("Workers' meeting · September", task.title);
      }
      if ((TaskTypes.BIRTHDAY_NOTICE + "|9|2026-09|D10|2026-09-10").equals(task.occurrenceKey)) {
        tenth = true;
      }
      if (task.occurrenceKey != null && task.occurrenceKey.contains("|D20|")) {
        twentieth = true;
      }
    }
    assertTrue(campToday);
    assertTrue(eve);
    assertTrue(tenth);
    assertFalse(twentieth);
  }

  @Test
  public void monthlyCanRingOnTheFirstAndOnTheSecondWednesday() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.SEPTEMBER, 1, 8, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Series series = new Series();
    series.id = 21L;
    series.title = "Workers";
    series.kind = Series.KIND_MONTHLY;
    series.enabled = true;
    series.postHour = 7;
    series.lastOfPrevMonth = false;
    series.tenth = false;
    series.twentieth = false;
    series.monthDays = MonthSlots.withDay(0, 1, true);
    series.monthOrdinals = MonthSlots.withOrdinal(0L, 2, Calendar.WEDNESDAY, true);
    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(), utc, Collections.<Member>emptyList(), Collections.singletonList(series));
    boolean first = false;
    boolean secondWed = false;
    for (Task task : tasks) {
      if ((TaskTypes.BIRTHDAY_NOTICE + "|21|2026-09|D1|2026-09-01").equals(task.occurrenceKey)) {
        first = true;
      }
      if ((TaskTypes.BIRTHDAY_NOTICE + "|21|2026-09|W2WED|2026-09-09").equals(task.occurrenceKey)) {
        secondWed = true;
      }
    }
    assertTrue(first);
    assertTrue(secondWed);
  }

  @Test
  public void customWeeklyUsesChosenDayAndHour() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);

    Series weekly = new Series();
    weekly.id = 11L;
    weekly.title = "Youth service";
    weekly.kind = Series.KIND_WEEKLY;
    weekly.enabled = true;
    weekly.postHour = 16;
    weekly.postMinute = 30;
    weekly.weekdays = Weekdays.bit(Calendar.SATURDAY);

    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(), utc, Collections.<Member>emptyList(), Collections.singletonList(weekly));
    boolean found = false;
    for (Task task : tasks) {
      if ((TaskTypes.WEEKLY + "|11|2026-08-29").equals(task.occurrenceKey)) {
        found = true;
        assertEquals("Youth service", task.title);
        assertEquals("Weekly", TaskTypes.cadence(task.type));
        assertEquals(TaskTypes.SECTION_WEEKLY, TaskTypes.section(task.type));
        Calendar post = Calendar.getInstance(utc);
        post.setTimeInMillis(task.postAtMillis);
        assertEquals(Calendar.SATURDAY, post.get(Calendar.DAY_OF_WEEK));
        assertEquals(16, post.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, post.get(Calendar.MINUTE));
      }
    }
    assertTrue(found);
  }

  @Test
  public void customDailyUsesChosenClockAndSkipsPastToday() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);

    Series daily = new Series();
    daily.id = 12L;
    daily.title = "Vespers";
    daily.kind = Series.KIND_DAILY;
    daily.enabled = true;
    daily.postHour = 18;
    daily.postMinute = 0;

    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(), utc, Collections.<Member>emptyList(), Collections.singletonList(daily));
    boolean today = false;
    boolean tomorrow = false;
    for (Task task : tasks) {
      if ((TaskTypes.DAILY + "|12|2026-08-26").equals(task.occurrenceKey)) {
        today = true;
        Calendar post = Calendar.getInstance(utc);
        post.setTimeInMillis(task.postAtMillis);
        assertEquals(18, post.get(Calendar.HOUR_OF_DAY));
        assertEquals("Daily", TaskTypes.cadence(task.type));
        assertEquals(TaskTypes.SECTION_CAMPAIGN, TaskTypes.section(task.type));
        assertTrue(TaskTypes.oneCard(task.type));
      }
      if ((TaskTypes.DAILY + "|12|2026-08-27").equals(task.occurrenceKey)) {
        tomorrow = true;
      }
    }
    assertTrue(today);
    assertTrue(tomorrow);
  }

  @Test
  public void alumniBirthdaySkipsCaptionAndStaysOffTheMemberList() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Member member = new Member();
    member.id = 9;
    member.name = "Ada";
    member.birthMonth = 8;
    member.birthDay = 31;
    member.kind = Member.KIND_ALUMNI;
    member.skipCaption = true;
    List<Task> tasks =
      RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.singletonList(member));
    boolean found = false;
    for (Task task : tasks) {
      if ((TaskTypes.ALUMNI_BIRTHDAY + "|9|2026-08-31").equals(task.occurrenceKey)) {
        found = true;
        assertEquals(TaskTypes.ALUMNI_BIRTHDAY, task.type);
        assertTrue(task.skipCaption);
        assertEquals(TaskTypes.SECTION_ALUMNI, TaskTypes.section(task.type));
        task.status = TaskStatus.dueStatus(task, now.getTimeInMillis(), ScheduleTimes.WARNING_LEAD_MS);
        assertEquals(TaskStatus.READY, task.status);
        assertEquals("Forward", TaskStatus.label(task));
        assertEquals("", CaptionTemplates.forTask(null, task));
        assertTrue(task.description.contains("do not tag") || task.description.contains("NO PICTURE"));
      }
    }
    assertTrue(found);
  }

  @Test
  public void alumniDeskMintsOneMonthCardOneWaveAndAPhotoNotTwoHundredDms() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Member> alumni = new ArrayList<>();
    alumni.add(alumni(9, "Ada", 8, 31));
    alumni.add(alumni(10, "Bo", 9, 4));
    alumni.add(alumni(11, "Cy", 1, 2));
    alumni.add(alumni(12, "Di", 2, 3));
    alumni.add(alumni(13, "Ed", 3, 4));
    alumni.add(alumni(14, "Fa", 4, 5));
    List<Task> tasks = RoutineGenerator.generate(now.getTimeInMillis(), utc, alumni);
    int monthCards = 0;
    int waveCards = 0;
    int photos = 0;
    int birthdays = 0;
    boolean adaPhoto = false;
    for (Task task : tasks) {
      if (TaskTypes.ALUMNI_MONTH.equals(task.type)) {
        monthCards++;
        assertTrue(task.skipCaption);
        assertTrue(task.description.contains("Alumni Relations Officer"));
      }
      if (TaskTypes.ALUMNI_WAVE.equals(task.type)) {
        waveCards++;
        assertTrue(task.skipCaption);
        assertTrue(task.occurrenceKey.startsWith(TaskTypes.ALUMNI_WAVE + "|"));
      }
      if (TaskTypes.ALUMNI_PHOTO.equals(task.type)) {
        photos++;
        if ((TaskTypes.ALUMNI_PHOTO + "|9|2026-08-31").equals(task.occurrenceKey)) {
          adaPhoto = true;
          Calendar post = Calendar.getInstance(utc);
          post.setTimeInMillis(task.postAtMillis);
          assertEquals(29, post.get(Calendar.DAY_OF_MONTH));
          assertEquals(10, post.get(Calendar.HOUR_OF_DAY));
        }
      }
      if (TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
        birthdays++;
      }
    }
    assertTrue(monthCards >= 1 && monthCards <= 2);
    assertTrue(waveCards >= 1 && waveCards <= 2);
    assertTrue(adaPhoto);
    assertTrue(photos <= 2);
    assertTrue(birthdays <= 2);
    assertTrue(tasks.size() < 80);
  }

  @Test
  public void birthdayMonthStaysThroughTheFifthThenRolls() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar third = Calendar.getInstance(utc);
    third.clear();
    third.setTimeZone(utc);
    third.set(2026, Calendar.SEPTEMBER, 3, 12, 0, 0);
    third.set(Calendar.MILLISECOND, 0);
    Member ada = alumni(9, "Ada", 8, 31);
    List<Task> early =
      RoutineGenerator.generate(third.getTimeInMillis(), utc, Collections.singletonList(ada));
    boolean sepMonth = false;
    boolean octMonth = false;
    for (Task task : early) {
      if ((TaskTypes.ALUMNI_MONTH + "|2026-09").equals(task.occurrenceKey)) {
        sepMonth = true;
      }
      if ((TaskTypes.ALUMNI_MONTH + "|2026-10").equals(task.occurrenceKey)) {
        octMonth = true;
      }
    }
    assertTrue(sepMonth);
    assertTrue(octMonth);

    Calendar twentieth = (Calendar) third.clone();
    twentieth.set(Calendar.DAY_OF_MONTH, 20);
    List<Task> late =
      RoutineGenerator.generate(twentieth.getTimeInMillis(), utc, Collections.singletonList(ada));
    for (Task task : late) {
      assertFalse((TaskTypes.ALUMNI_MONTH + "|2026-09").equals(task.occurrenceKey));
      assertFalse((TaskTypes.ALUMNI_WAVE + "|2026-09").equals(task.occurrenceKey));
    }
  }

  @Test
  public void fullRosterDoesNotMintADmPerPerson() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks =
      RoutineGenerator.generate(now.getTimeInMillis(), utc, AlumniRoster.members());
    int photos = 0;
    int months = 0;
    int waves = 0;
    int birthdays = 0;
    for (Task task : tasks) {
      if (TaskTypes.ALUMNI_PHOTO.equals(task.type)) {
        photos++;
      } else if (TaskTypes.ALUMNI_MONTH.equals(task.type)) {
        months++;
      } else if (TaskTypes.ALUMNI_WAVE.equals(task.type)) {
        waves++;
      } else if (TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
        birthdays++;
      }
    }
    assertTrue(months >= 1 && months <= 2);
    assertTrue(waves >= 1 && waves <= 2);
    assertTrue(photos < 40);
    assertTrue(birthdays < 40);
    assertEquals(photos, birthdays);
  }

  @Test
  public void octoberFirstMintsMonthAndWaveTogether() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.OCTOBER, 1, 8, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Member ada = alumni(9, "Ada", 10, 2);
    List<Task> tasks =
      RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.singletonList(ada));
    boolean month = false;
    boolean wave = false;
    for (Task task : tasks) {
      if ((TaskTypes.ALUMNI_MONTH + "|2026-10").equals(task.occurrenceKey)) {
        month = true;
      }
      if ((TaskTypes.ALUMNI_WAVE + "|2026-10").equals(task.occurrenceKey)) {
        wave = true;
      }
    }
    assertTrue(month);
    assertTrue(wave);
  }

  @Test
  public void settledPhotoIsNotMinted() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Member ada = alumni(9, "Ada", 8, 31);
    ada.photoStatus = AlumniDesk.PHOTO_GOT;
    List<Task> tasks =
      RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.singletonList(ada));
    for (Task task : tasks) {
      assertFalse(TaskTypes.ALUMNI_PHOTO.equals(task.type));
    }
  }

  private static Member alumni(long id, String name, int month, int day) {
    Member member = new Member();
    member.id = id;
    member.name = name;
    member.birthMonth = month;
    member.birthDay = day;
    member.kind = Member.KIND_ALUMNI;
    member.skipCaption = true;
    member.phone = "";
    member.photoStatus = "";
    return member;
  }

  @Test
  public void birthdayMonthsAwayAreNotMinted() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 26, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    Member member = new Member();
    member.id = 3;
    member.name = "Far";
    member.birthMonth = 1;
    member.birthDay = 2;
    List<Task> tasks =
      RoutineGenerator.generate(now.getTimeInMillis(), utc, Collections.singletonList(member));
    for (Task task : tasks) {
      assertFalse(TaskTypes.BIRTHDAY.equals(task.type));
      assertFalse(TaskTypes.ALUMNI_BIRTHDAY.equals(task.type));
    }
  }

  @Test
  public void emptySeriesListDoesNotInventCountdown() {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar now = Calendar.getInstance(utc);
    now.clear();
    now.setTimeZone(utc);
    now.set(2026, Calendar.AUGUST, 31, 12, 0, 0);
    now.set(Calendar.MILLISECOND, 0);
    List<Task> tasks = RoutineGenerator.generate(
      now.getTimeInMillis(),
      utc,
      Collections.<Member>emptyList(),
      Collections.<Series>emptyList());
    for (Task task : tasks) {
      assertFalse(TaskTypes.COUNTDOWN.equals(task.type));
      assertFalse(TaskTypes.BIRTHDAY_NOTICE.equals(task.type));
    }
  }
}
