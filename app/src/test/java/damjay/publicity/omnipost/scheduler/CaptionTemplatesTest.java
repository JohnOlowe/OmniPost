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
  public void customTemplateFillsPlaceholdersAndFourPartCountdown() {
    damjay.publicity.omnipost.data.entity.Series series =
      new damjay.publicity.omnipost.data.entity.Series();
    series.caption = "{Days} / {away} / {days} / {month}";
    Task countdown = new Task();
    countdown.type = TaskTypes.COUNTDOWN;
    countdown.occurrenceKey = "COUNTDOWN|42|2026-09-09|2026-08-31";
    assertEquals(9, CaptionTemplates.countdownDays(countdown));
    assertEquals(
      "9 DAYS TO GO / is 9 days away / 9 / this month",
      CaptionTemplates.live(countdown, series));

    Task notice = new Task();
    notice.type = TaskTypes.BIRTHDAY_NOTICE;
    notice.occurrenceKey = "BIRTHDAY_NOTICE|9|2026-09|D10|2026-09-10";
    series.caption = "Month of {month}";
    assertEquals(0, CaptionTemplates.countdownDays(notice));
    assertEquals("Month of September", CaptionTemplates.live(notice, series));
  }

  @Test
  public void rawTemplateKeepsPlaceholders() {
    Task notice = new Task();
    notice.type = TaskTypes.BIRTHDAY_NOTICE;
    String raw = CaptionTemplates.rawTemplate(notice, null);
    assertTrue(raw.contains("{month}"));
    assertTrue(raw.contains("*NOTICE!*"));
  }

  @Test
  public void applyFillsPlaceholdersWithoutReplacingTheRest() {
    Task notice = new Task();
    notice.type = TaskTypes.BIRTHDAY_NOTICE;
    notice.occurrenceKey = "BIRTHDAY_NOTICE|2026-09|EVE|2026-08-31";
    assertEquals(
      "Send pictures for *Month of September*",
      CaptionTemplates.apply("Send pictures for *Month of {month}*", notice));
  }

  @Test
  public void dDayAndOneDayGrammar() {
    assertTrue(CaptionTemplates.countdownCaption(0).startsWith("*IT'S D-DAY!*"));
    assertTrue(CaptionTemplates.countdownCaption(1).contains("is 1 day away"));
    assertEquals("Beyond Limit '26 · D-Day", CaptionTemplates.countdownTitle(0));
    assertEquals("Beyond Limit '26 · 1 day to go", CaptionTemplates.countdownTitle(1));
  }
}
