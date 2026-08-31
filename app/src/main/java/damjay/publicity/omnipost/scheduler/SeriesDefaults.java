package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Series;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public final class SeriesDefaults {
  public static final String SEED_COUNTDOWN = "beyond-limit";
  public static final String SEED_NOTICE = "birthday-notice";

  private SeriesDefaults() {}

  public static List<Series> builtins() {
    List<Series> out = new ArrayList<>();
    out.add(beyondLimit());
    out.add(birthdayNotice());
    return out;
  }

  public static Series beyondLimit() {
    Series series = new Series();
    series.title = Campaigns.BEYOND_LIMIT_NAME;
    series.kind = Series.KIND_COUNTDOWN;
    series.caption = countdownCaption();
    Calendar event = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    event.clear();
    event.setTimeZone(TimeZone.getTimeZone("UTC"));
    event.set(
      Campaigns.BEYOND_LIMIT_YEAR,
      Campaigns.BEYOND_LIMIT_MONTH,
      Campaigns.BEYOND_LIMIT_DAY,
      0,
      0,
      0);
    event.set(Calendar.MILLISECOND, 0);
    series.eventAtMillis = event.getTimeInMillis();
    series.postHour = ScheduleTimes.MONTH_POST_HOUR;
    series.seedKey = SEED_COUNTDOWN;
    series.enabled = true;
    return series;
  }

  public static Series birthdayNotice() {
    Series series = new Series();
    series.title = "Birthday notice";
    series.kind = Series.KIND_MONTHLY;
    series.caption = noticeCaption();
    series.postHour = ScheduleTimes.MONTH_POST_HOUR;
    series.lastOfPrevMonth = true;
    series.tenth = true;
    series.twentieth = true;
    series.seedKey = SEED_NOTICE;
    series.enabled = true;
    return series;
  }

  public static String countdownCaption() {
    return "*IT'S {Days}!*\n\n"
      + "*_No Cross, No Crown._*\n\n"
      + "The 9th edition of *Beyond Limit* {away}.\n\n"
      + "*Date:* 9th – 13th September, 2026\n"
      + "*Venue:* RCCG The Lord's Court, 13, Osholake Street, Ebute-Metta, Lagos.\n"
      + "*Time:* 5pm Daily\n\n"
      + "Theme: _\"No Cross, No Crown.\"_\n\n"
      + "Come one, come all. Invite your family, friends, neighbours, colleagues, and so on.\n\n"
      + "_Pray, plan and prepare!_\n\n"
      + "———\n\n"
      + "RCCG The Lord's Court\n"
      + "Youth Church\n"
      + "Department/Level:";
  }

  public static String noticeCaption() {
    return "*NOTICE!*\n\n"
      + "This is to notify you that we will be celebrating the birthdays of all members born in the *Month of {month}*\n\n"
      + "Kindly click on this link to send in your pictures\n"
      + Campaigns.BIRTHDAY_WA
      + "\n\n"
      + "Ensure you send in your pictures on time so we can celebrate you.\n\n"
      + "Thank you";
  }

  public static String blankCountdownCaption() {
    return "*IT'S {Days}!*\n\n{away}.";
  }

  public static String blankNoticeCaption() {
    return "*NOTICE!*\n\n*Month of {month}*\n";
  }
}
