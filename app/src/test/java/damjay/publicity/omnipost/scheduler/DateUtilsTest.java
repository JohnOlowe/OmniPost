package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class DateUtilsTest {
  private static Calendar utc(int year, int month, int day, int hour, int minute) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.clear();
    c.setTimeZone(TimeZone.getTimeZone("UTC"));
    c.set(year, month, day, hour, minute, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  @Test
  public void nextSaturdayFromFridayAfternoonIsTomorrow() {
    Calendar now = utc(2026, Calendar.AUGUST, 28, 15, 0);
    Calendar got = DateUtils.nextWeekdayAt(now, Calendar.SATURDAY, 10, 0);
    assertEquals(2026, got.get(Calendar.YEAR));
    assertEquals(Calendar.AUGUST, got.get(Calendar.MONTH));
    assertEquals(29, got.get(Calendar.DAY_OF_MONTH));
    assertEquals(10, got.get(Calendar.HOUR_OF_DAY));
    assertEquals(Calendar.SATURDAY, got.get(Calendar.DAY_OF_WEEK));
  }

  @Test
  public void nextSaturdayAfterDeadlineRollsAWeek() {
    Calendar now = utc(2026, Calendar.AUGUST, 29, 11, 0);
    Calendar got = DateUtils.nextWeekdayAt(now, Calendar.SATURDAY, 10, 0);
    assertEquals(Calendar.SEPTEMBER, got.get(Calendar.MONTH));
    assertEquals(5, got.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  public void monthEndFromAugust30IsAugust31() {
    Calendar now = utc(2026, Calendar.AUGUST, 30, 12, 0);
    Calendar got = DateUtils.nextMonthEndAt(now, 7, 0);
    assertEquals(Calendar.AUGUST, got.get(Calendar.MONTH));
    assertEquals(31, got.get(Calendar.DAY_OF_MONTH));
    assertEquals(7, got.get(Calendar.HOUR_OF_DAY));
  }

  @Test
  public void monthEndAfterMorningDeadlineRollsToNextMonth() {
    Calendar now = utc(2026, Calendar.AUGUST, 31, 8, 0);
    Calendar got = DateUtils.nextMonthEndAt(now, 7, 0);
    assertEquals(Calendar.SEPTEMBER, got.get(Calendar.MONTH));
    assertEquals(30, got.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  public void nextFirstFromLateAugustIsSeptember() {
    Calendar now = utc(2026, Calendar.AUGUST, 30, 12, 0);
    Calendar got = DateUtils.nextMonthStartAt(now, 7, 0);
    assertEquals(Calendar.SEPTEMBER, got.get(Calendar.MONTH));
    assertEquals(1, got.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  public void birthdayLaterThisYearStaysThisYear() {
    Calendar now = utc(2026, Calendar.AUGUST, 26, 12, 0);
    Calendar got = DateUtils.nextBirthdayAt(now, 8, 31, 7, 0);
    assertEquals(2026, got.get(Calendar.YEAR));
    assertEquals(Calendar.AUGUST, got.get(Calendar.MONTH));
    assertEquals(31, got.get(Calendar.DAY_OF_MONTH));
    assertEquals(7, got.get(Calendar.HOUR_OF_DAY));
  }

  @Test
  public void birthdayAlreadyPassedRollsToNextYear() {
    Calendar now = utc(2026, Calendar.AUGUST, 31, 8, 0);
    Calendar got = DateUtils.nextBirthdayAt(now, 8, 31, 7, 0);
    assertEquals(2027, got.get(Calendar.YEAR));
    assertEquals(Calendar.AUGUST, got.get(Calendar.MONTH));
    assertEquals(31, got.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  public void dayKeyIsIso() {
    Calendar c = utc(2026, Calendar.AUGUST, 29, 10, 0);
    assertEquals("2026-08-29", DateUtils.dayKey(c));
  }

  @Test
  public void formatUntilUsesDaysHoursMinutes() {
    long now = 1_000_000L;
    assertEquals("that time has passed", DateUtils.formatUntil(now - 1, now));
    assertEquals("in 1 minute", DateUtils.formatUntil(now + 60_000L, now));
    assertEquals("in 35 minutes", DateUtils.formatUntil(now + 35L * 60_000L, now));
    assertEquals("in 2 hours 10 minutes", DateUtils.formatUntil(now + 2L * 3_600_000L + 10L * 60_000L, now));
    assertEquals("in 2 days 4 hours", DateUtils.formatUntil(now + 2L * 86_400_000L + 4L * 3_600_000L, now));
  }

  @Test
  public void calendarDaysFrom31AugTo9SeptIsNine() {
    Calendar from = utc(2026, Calendar.AUGUST, 31, 12, 0);
    Calendar to = utc(2026, Calendar.SEPTEMBER, 9, 7, 0);
    assertEquals(9, DateUtils.calendarDaysBetween(from, to));
    assertEquals(9, DateUtils.daysBetweenKeys("2026-08-31", "2026-09-09"));
    assertEquals(0, DateUtils.calendarDaysBetween(to, to));
    assertEquals(-9, DateUtils.calendarDaysBetween(to, from));
  }

  @Test
  public void inMonthThirdsAreEquallySpaced() {
    assertEquals(10, DateUtils.inMonthThirds(31)[0]);
    assertEquals(20, DateUtils.inMonthThirds(31)[1]);
    assertEquals(10, DateUtils.inMonthThirds(30)[0]);
    assertEquals(20, DateUtils.inMonthThirds(30)[1]);
    assertEquals(9, DateUtils.inMonthThirds(28)[0]);
    assertEquals(18, DateUtils.inMonthThirds(28)[1]);
  }

  @Test
  public void dayBeforeIsPreviousEvening() {
    Calendar post = utc(2026, Calendar.AUGUST, 29, 10, 0);
    Calendar draft = DateUtils.dayBeforeAt(post, 20, 0);
    assertEquals(28, draft.get(Calendar.DAY_OF_MONTH));
    assertEquals(20, draft.get(Calendar.HOUR_OF_DAY));
    assertTrue(draft.before(post));
  }
}
