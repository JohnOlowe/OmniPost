package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Series;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class MonthSlotsTest {
  @Test
  public void dayBitsAndLegacyTenthTwentieth() {
    Series old = new Series();
    old.tenth = true;
    old.twentieth = true;
    int days = MonthSlots.daysOf(old);
    assertTrue(MonthSlots.hasDay(days, 10));
    assertTrue(MonthSlots.hasDay(days, 20));
    assertFalse(MonthSlots.hasDay(days, 1));
    Series custom = new Series();
    custom.tenth = true;
    custom.monthDays = MonthSlots.withDay(0, 1, true);
    assertTrue(MonthSlots.hasDay(MonthSlots.daysOf(custom), 1));
    assertFalse(MonthSlots.hasDay(MonthSlots.daysOf(custom), 10));
  }

  @Test
  public void october2026NthWeekdays() {
    Calendar oct = utc(2026, Calendar.OCTOBER, 1);
    Calendar firstThu = MonthSlots.nthWeekday(oct, 1, Calendar.THURSDAY);
    Calendar firstMon = MonthSlots.nthWeekday(oct, 1, Calendar.MONDAY);
    Calendar secondWed = MonthSlots.nthWeekday(oct, 2, Calendar.WEDNESDAY);
    Calendar lastFri = MonthSlots.nthWeekday(oct, MonthSlots.LAST_ORDINAL, Calendar.FRIDAY);
    assertEquals(1, firstThu.get(Calendar.DAY_OF_MONTH));
    assertEquals(5, firstMon.get(Calendar.DAY_OF_MONTH));
    assertEquals(14, secondWed.get(Calendar.DAY_OF_MONTH));
    assertEquals(30, lastFri.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  public void hitsIncludeFirstOfMonthAndNthWeekdayWithoutDoubling() {
    Series series = new Series();
    series.lastOfPrevMonth = false;
    series.tenth = false;
    series.twentieth = false;
    series.monthDays = MonthSlots.withDay(0, 1, true);
    series.monthOrdinals = MonthSlots.withOrdinal(0L, 1, Calendar.THURSDAY, true);
    Calendar oct = utc(2026, Calendar.OCTOBER, 1);
    List<MonthSlots.Hit> hits = MonthSlots.hits(series, oct, 7, 0);
    assertEquals(1, hits.size());
    assertEquals("D1", hits.get(0).slot);
    assertEquals(1, hits.get(0).post.get(Calendar.DAY_OF_MONTH));
  }

  @Test
  public void sentenceListsPickedDates() {
    Series series = new Series();
    series.lastOfPrevMonth = true;
    series.monthDays = MonthSlots.withDay(MonthSlots.withDay(0, 1, true), MonthSlots.LAST_OF_MONTH, true);
    series.monthOrdinals = MonthSlots.withOrdinal(0L, 2, Calendar.WEDNESDAY, true);
    String text = MonthSlots.sentence(series);
    assertTrue(text.contains("previous month"));
    assertTrue(text.contains("the 1st"));
    assertTrue(text.contains("last day of this month"));
    assertTrue(text.contains("2nd Wednesday"));
  }

  private static Calendar utc(int year, int month, int day) {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    Calendar c = Calendar.getInstance(tz);
    c.clear();
    c.setTimeZone(tz);
    c.set(year, month, day, 0, 0, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }
}
