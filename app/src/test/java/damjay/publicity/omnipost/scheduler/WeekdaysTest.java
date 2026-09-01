package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import org.junit.Test;

public class WeekdaysTest {
  @Test
  public void bitmaskRoundTrip() {
    int mask = Weekdays.NONE;
    mask = Weekdays.with(mask, Calendar.SATURDAY, true);
    mask = Weekdays.with(mask, Calendar.WEDNESDAY, true);
    assertTrue(Weekdays.has(mask, Calendar.SATURDAY));
    assertTrue(Weekdays.has(mask, Calendar.WEDNESDAY));
    assertFalse(Weekdays.has(mask, Calendar.SUNDAY));
    assertEquals("Wed, Sat", Weekdays.label(mask));
    assertEquals("every Wed, Sat", Weekdays.sentence(mask));
    assertEquals("Every day", Weekdays.label(Weekdays.ALL));
    assertEquals("4:05 PM", Weekdays.clock(16, 5));
    assertEquals("12:00 AM", Weekdays.clock(0, 0));
  }

  @Test
  public void tokenNames() {
    assertEquals("theme", CaptionVars.normalizeName("{theme}"));
    assertEquals("{venue}", CaptionVars.token("venue"));
    assertTrue(CaptionTemplates.isTokenName("theme_2"));
    assertFalse(CaptionTemplates.isTokenName("2theme"));
    assertFalse(CaptionTemplates.isTokenName("no spaces"));
    assertTrue(CaptionVars.isReserved("today"));
    assertTrue(CaptionVars.isReserved("{date}"));
    assertFalse(CaptionVars.isReserved("theme"));
  }
}
