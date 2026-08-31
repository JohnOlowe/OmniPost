package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Task;
import org.junit.Test;

public class CaptionTemplatesTest {
  @Test
  public void liveCaptionsFillEvenWhenSeedIsOff() {
    Task countdown = new Task();
    countdown.type = TaskTypes.COUNTDOWN;
    countdown.occurrenceKey = "COUNTDOWN|2026-09-09|2026-08-31";
    String caption = CaptionTemplates.forTask(null, countdown);
    assertTrue(caption.startsWith("*IT'S 9 DAYS TO GO!*"));
    assertTrue(caption.contains("*_No Cross, No Crown._*"));
    assertEquals("September", CaptionTemplates.monthNameFromOccurrence("BIRTHDAY_NOTICE|2026-09|EVE|2026-08-31"));

    Task notice = new Task();
    notice.type = TaskTypes.BIRTHDAY_NOTICE;
    notice.occurrenceKey = "BIRTHDAY_NOTICE|2026-09|EVE|2026-08-31";
    String body = CaptionTemplates.forTask(null, notice);
    assertTrue(body.contains("*Month of September*"));
    assertTrue(body.contains(Campaigns.BIRTHDAY_WA));
  }

  @Test
  public void dDayAndOneDayGrammar() {
    assertTrue(CaptionTemplates.countdownCaption(0).startsWith("*IT'S D-DAY!*"));
    assertTrue(CaptionTemplates.countdownCaption(1).contains("is 1 day away"));
    assertEquals("Beyond Limit '26 · D-Day", CaptionTemplates.countdownTitle(0));
    assertEquals("Beyond Limit '26 · 1 day to go", CaptionTemplates.countdownTitle(1));
  }
}
