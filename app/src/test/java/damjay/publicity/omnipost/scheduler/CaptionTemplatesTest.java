package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.Calendar;
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
      "9 DAYS TO GO / is 9 days away / 9 / " + DateUtils.monthName(Calendar.getInstance()),
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
  public void customVarsFillWithoutTouchingOtherWords() {
    damjay.publicity.omnipost.data.entity.Series series =
      new damjay.publicity.omnipost.data.entity.Series();
    series.vars = "theme=Grace\nvenue=The Court";
    Task countdown = new Task();
    countdown.type = TaskTypes.COUNTDOWN;
    countdown.occurrenceKey = "COUNTDOWN|2026-09-09|2026-08-31";
    assertEquals(
      "Grace at The Court",
      CaptionTemplates.apply("{theme} at {venue}", countdown, series));
  }

  @Test
  public void globalVarsFillAnyCaptionAndSeriesWins() {
    java.util.Map<String, String> extras = new java.util.LinkedHashMap<>();
    extras.put("theme", "Grace");
    extras.put("venue", "Line 1\nLine 2");
    Task sunday = new Task();
    sunday.type = TaskTypes.SUNDAY_SERVICE;
    assertEquals(
      "Grace at Line 1\nLine 2",
      CaptionTemplates.apply("{theme} at {venue}", sunday, null, extras));

    damjay.publicity.omnipost.data.entity.Series series =
      new damjay.publicity.omnipost.data.entity.Series();
    series.vars = "theme=Local";
    Task countdown = new Task();
    countdown.type = TaskTypes.COUNTDOWN;
    countdown.occurrenceKey = "COUNTDOWN|2026-09-09|2026-08-31";
    assertEquals(
      "Local",
      CaptionTemplates.apply("{theme}", countdown, series, extras));
  }

  @Test
  public void todayIsNotTheDueDate() {
    Calendar today = DateUtils.startOfDay(Calendar.getInstance());
    Calendar due = (Calendar) today.clone();
    due.add(Calendar.DAY_OF_MONTH, 3);
    Task task = new Task();
    task.type = TaskTypes.ONE_OFF;
    task.postAtMillis = due.getTimeInMillis();
    assertEquals(DateUtils.prettyDate(today), CaptionTemplates.apply("{today}", task));
    assertEquals(DateUtils.prettyDate(due), CaptionTemplates.apply("{date}", task));
    assertEquals(
      DateUtils.monthName(today),
      CaptionTemplates.apply("{today_month}", task));
  }

  @Test
  public void nestedVarsAndComposedRangeFill() {
    Calendar today = DateUtils.startOfDay(Calendar.getInstance());
    Calendar week = (Calendar) today.clone();
    week.add(Calendar.DAY_OF_MONTH, 6);
    java.util.Map<String, String> extras = new java.util.LinkedHashMap<>();
    extras.put("theme", "Grace");
    extras.put("line", "*{theme}* through {range:today:today+6}");
    Task task = new Task();
    task.type = TaskTypes.ONE_OFF;
    task.postAtMillis = today.getTimeInMillis();
    assertEquals(
      "*Grace* through " + DateUtils.prettyRange(today, week),
      CaptionTemplates.apply("{line}", task, null, extras));
    Calendar tomorrow = (Calendar) today.clone();
    tomorrow.add(Calendar.DAY_OF_MONTH, 1);
    assertEquals(
      DateUtils.prettyDate(tomorrow),
      CaptionTemplates.apply("{today+1}", task));
  }

  @Test
  public void dDayAndOneDayGrammar() {
    assertTrue(CaptionTemplates.countdownCaption(0).startsWith("*IT'S D-DAY!*"));
    assertTrue(CaptionTemplates.countdownCaption(1).contains("is 1 day away"));
    assertEquals("Beyond Limit '26 · D-Day", CaptionTemplates.countdownTitle(0));
    assertEquals("Beyond Limit '26 · 1 day to go", CaptionTemplates.countdownTitle(1));
  }

  @Test
  public void lingerDraftKeepsCustomAndUpgradesCanned() {
    String custom = "*IT'S {Days}!*\n\nSee you at church.";
    assertTrue(CaptionTemplates.isCanned(SeriesDefaults.countdownCaption()));
    assertTrue(CaptionTemplates.isCanned(SeriesDefaults.noticeCaption()));
    assertTrue(CaptionTemplates.isCanned(""));
    assertFalse(CaptionTemplates.isCanned(custom));
    assertEquals(custom, CaptionTemplates.lingerDraft(SeriesDefaults.countdownCaption(), custom));
    assertEquals(custom, CaptionTemplates.lingerDraft(custom, SeriesDefaults.countdownCaption()));
    assertEquals(custom, CaptionTemplates.lingerDraft("", custom));
  }

  @Test
  public void sharedTemplatePrefersSeriesThenSiblingAndFillsEachDay() {
    String custom = "Join us — {Days} / {away}";
    damjay.publicity.omnipost.data.entity.Series series =
      new damjay.publicity.omnipost.data.entity.Series();
    series.caption = custom;
    Task countdown = new Task();
    countdown.type = TaskTypes.COUNTDOWN;
    countdown.occurrenceKey = "COUNTDOWN|2026-09-09|2026-09-04";
    assertEquals(custom, CaptionTemplates.sharedTemplate(custom, "", countdown, series));

    series.caption = SeriesDefaults.countdownCaption();
    assertEquals(
      custom,
      CaptionTemplates.sharedTemplate(series.caption, custom, countdown, series));
    assertEquals(
      SeriesDefaults.countdownCaption(),
      CaptionTemplates.sharedTemplate(series.caption, "", countdown, series));

    Task today = new Task();
    today.type = TaskTypes.COUNTDOWN;
    today.occurrenceKey = "COUNTDOWN|2026-09-09|2026-09-04";
    Task tomorrow = new Task();
    tomorrow.type = TaskTypes.COUNTDOWN;
    tomorrow.occurrenceKey = "COUNTDOWN|2026-09-09|2026-09-05";
    series.caption = custom;
    String filledToday = CaptionTemplates.apply(custom, today, series);
    String filledTomorrow = CaptionTemplates.apply(custom, tomorrow, series);
    assertEquals("Join us — 5 DAYS TO GO / is 5 days away", filledToday);
    assertEquals("Join us — 4 DAYS TO GO / is 4 days away", filledTomorrow);
  }

  @Test
  public void seriesMatchingTitleFindsCountdownCard() {
    damjay.publicity.omnipost.data.entity.Series beyond = SeriesDefaults.beyondLimit();
    damjay.publicity.omnipost.data.entity.Series other =
      new damjay.publicity.omnipost.data.entity.Series();
    other.title = "Youth camp";
    assertEquals(
      beyond,
      CaptionTemplates.seriesMatchingTitle(
        java.util.Arrays.asList(other, beyond),
        "Beyond Limit '26 · 5 days to go"));
  }

  @Test
  public void inheritCaptionSavedCopiesOntoLaterDays() {
    Task tomorrow = new Task();
    tomorrow.type = TaskTypes.COUNTDOWN;
    tomorrow.seriesId = 5L;
    java.util.Map<Long, Long> captured = new java.util.HashMap<>();
    captured.put(5L, 99L);
    ScheduleCoordinator.inheritCaptionSaved(null, tomorrow, captured);
    assertEquals(99L, tomorrow.captionSavedAt);
  }
}
