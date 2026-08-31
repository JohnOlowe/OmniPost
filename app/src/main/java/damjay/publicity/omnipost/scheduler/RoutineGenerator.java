package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public final class RoutineGenerator {
  private RoutineGenerator() {}

  public static List<Task> generate(long nowMillis, TimeZone tz, List<Member> members) {
    return generate(nowMillis, tz, members, SeriesDefaults.builtins());
  }

  public static List<Task> generate(
    long nowMillis, TimeZone tz, List<Member> members, List<Series> seriesList) {
    Calendar now = Calendar.getInstance(tz);
    now.setTimeInMillis(nowMillis);
    List<Task> out = new ArrayList<>();
    addWeeklies(
      out,
      now,
      Calendar.SATURDAY,
      TaskTypes.SUNDAY_SERVICE,
      "Sunday Service",
      "Write the caption tomorrow-minus-one. Have it ready by 9:30 AM Saturday. Post at 10:00 AM.");
    addWeeklies(
      out,
      now,
      Calendar.WEDNESDAY,
      TaskTypes.WEDNESDAY_BIBLE_STUDY,
      "Wednesday Bible Study",
      "Write the caption a day early. Have it ready by 9:30 AM Wednesday. Post at 10:00 AM.");
    addWeeklies(
      out,
      now,
      Calendar.FRIDAY,
      TaskTypes.FRIDAY_PRAYER,
      "Friday Prayer Meeting",
      "Write the caption a day early. Have it ready by 9:30 AM Friday. Post at 10:00 AM.");
    addFastingEve(out, now);
    addFastingDay(out, now);
    addHappyNewMonth(out, now);
    if (seriesList != null) {
      for (Series series : seriesList) {
        addSeries(out, now, series);
      }
    }
    if (members != null) {
      for (Member member : members) {
        addBirthday(out, now, member);
      }
    }
    return out;
  }

  private static void addWeeklies(
    List<Task> out,
    Calendar now,
    int dayOfWeek,
    String type,
    String title,
    String description) {
    Calendar post = DateUtils.nextWeekdayAt(
      now, dayOfWeek, ScheduleTimes.WEEKLY_POST_HOUR, ScheduleTimes.WEEKLY_POST_MINUTE);
    for (int i = 0; i < ScheduleTimes.GENERATE_WEEKLY_COUNT; i++) {
      Calendar draft = DateUtils.dayBeforeAt(
        post, ScheduleTimes.WEEKLY_DRAFT_HOUR, ScheduleTimes.WEEKLY_DRAFT_MINUTE);
      out.add(build(type, title, description, draft.getTimeInMillis(), post.getTimeInMillis(),
        type + "|" + DateUtils.dayKey(post), 0L, 0L));
      post.add(Calendar.DAY_OF_MONTH, 7);
    }
  }

  private static void addFastingEve(List<Task> out, Calendar now) {
    Calendar post = DateUtils.nextMonthEndAt(now, ScheduleTimes.MONTH_POST_HOUR, 0);
    for (int i = 0; i < ScheduleTimes.GENERATE_MONTH_COUNT; i++) {
      Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.NEW_MONTH_FASTING,
        "Fasting tomorrow",
        "Post that New Month Fasting starts tomorrow. Caption ready by 6:30 AM.",
        draft.getTimeInMillis(),
        post.getTimeInMillis(),
        TaskTypes.NEW_MONTH_FASTING + "|" + DateUtils.dayKey(post),
        0L,
        0L));
      post.add(Calendar.MONTH, 1);
      post.set(Calendar.DAY_OF_MONTH, post.getActualMaximum(Calendar.DAY_OF_MONTH));
      post.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      post.set(Calendar.MINUTE, 0);
    }
  }

  private static void addFastingDay(List<Task> out, Calendar now) {
    Calendar post = DateUtils.nextMonthStartAt(now, ScheduleTimes.MONTH_POST_HOUR, 0);
    for (int i = 0; i < ScheduleTimes.GENERATE_MONTH_COUNT; i++) {
      Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.FASTING_DAY,
        "Fasting today",
        "Post the fasting reminder again on the day itself. Caption ready by 6:30 AM.",
        draft.getTimeInMillis(),
        post.getTimeInMillis(),
        TaskTypes.FASTING_DAY + "|" + DateUtils.dayKey(post),
        0L,
        0L));
      post.add(Calendar.MONTH, 1);
      post.set(Calendar.DAY_OF_MONTH, 1);
      post.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      post.set(Calendar.MINUTE, 0);
    }
  }

  private static void addHappyNewMonth(List<Task> out, Calendar now) {
    Calendar post = DateUtils.nextMonthStartAt(now, ScheduleTimes.MONTH_POST_HOUR, 0);
    for (int i = 0; i < ScheduleTimes.GENERATE_MONTH_COUNT; i++) {
      Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.HAPPY_NEW_MONTH,
        "Happy New Month",
        "Draft last night. Caption ready by 6:30 AM on the 1st. Post at 7:00 AM.",
        draft.getTimeInMillis(),
        post.getTimeInMillis(),
        TaskTypes.HAPPY_NEW_MONTH + "|" + DateUtils.dayKey(post),
        0L,
        0L));
      post.add(Calendar.MONTH, 1);
      post.set(Calendar.DAY_OF_MONTH, 1);
      post.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      post.set(Calendar.MINUTE, 0);
    }
  }

  static void addSeries(List<Task> out, Calendar now, Series series) {
    if (series == null || !series.enabled) {
      return;
    }
    if (Series.KIND_COUNTDOWN.equals(series.kind)) {
      addCountdown(out, now, series);
      return;
    }
    if (Series.KIND_MONTHLY.equals(series.kind)) {
      addMonthlyNotice(out, now, series);
    }
  }

  private static void addCountdown(List<Task> out, Calendar now, Series series) {
    Calendar event = DateUtils.startOfDay(now);
    event.setTimeInMillis(series.eventAtMillis);
    event = DateUtils.startOfDay(event);
    Calendar today = DateUtils.startOfDay(now);
    if (today.after(event)) {
      return;
    }
    int hour = series.postHour;
    String eventKey = DateUtils.dayKey(event);
    for (int i = 0; i < ScheduleTimes.GENERATE_COUNTDOWN_DAYS; i++) {
      Calendar postDay = (Calendar) today.clone();
      postDay.add(Calendar.DAY_OF_MONTH, i);
      if (postDay.after(event)) {
        break;
      }
      int days = DateUtils.calendarDaysBetween(postDay, event);
      Calendar post = DateUtils.sameDayAt(postDay, hour, series.postMinute);
      Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.COUNTDOWN,
        CaptionTemplates.countdownTitle(series.title, days),
        "Daily until "
          + DateUtils.dayKey(event)
          + ". One card — {days} / {Days} / {away} fill themselves.",
        draft.getTimeInMillis(),
        post.getTimeInMillis(),
        countdownKey(series, eventKey, DateUtils.dayKey(post)),
        0L,
        series.id));
    }
  }

  private static void addMonthlyNotice(List<Task> out, Calendar now, Series series) {
    Calendar monthStart = DateUtils.startOfDay(now);
    monthStart.set(Calendar.DAY_OF_MONTH, 1);
    String todayKey = DateUtils.dayKey(now);
    int hour = series.postHour;
    for (int m = 0; m < ScheduleTimes.GENERATE_MONTH_COUNT; m++) {
      Calendar target = (Calendar) monthStart.clone();
      target.add(Calendar.MONTH, m);
      String monthKey = DateUtils.monthKey(target);
      String monthName = DateUtils.monthName(target);
      int max = target.getActualMaximum(Calendar.DAY_OF_MONTH);

      if (series.lastOfPrevMonth) {
        Calendar eve = (Calendar) target.clone();
        eve.set(Calendar.DAY_OF_MONTH, 1);
        eve.add(Calendar.MONTH, -1);
        eve.set(Calendar.DAY_OF_MONTH, eve.getActualMaximum(Calendar.DAY_OF_MONTH));
        eve.set(Calendar.HOUR_OF_DAY, hour);
        eve.set(Calendar.MINUTE, series.postMinute);
        eve.set(Calendar.SECOND, 0);
        eve.set(Calendar.MILLISECOND, 0);
        addNoticeIfDue(out, series, todayKey, monthKey, monthName, "EVE", eve);
      }
      if (series.tenth) {
        addFixedDay(out, series, todayKey, target, monthKey, monthName, "D10", 10, max, hour);
      }
      if (series.twentieth) {
        addFixedDay(out, series, todayKey, target, monthKey, monthName, "D20", 20, max, hour);
      }
    }
  }

  private static void addFixedDay(
    List<Task> out,
    Series series,
    String todayKey,
    Calendar target,
    String monthKey,
    String monthName,
    String slot,
    int day,
    int max,
    int hour) {
    if (day > max) {
      return;
    }
    Calendar post = DateUtils.sameDayAt(target, hour, series.postMinute);
    post.set(Calendar.DAY_OF_MONTH, day);
    post.set(Calendar.HOUR_OF_DAY, hour);
    post.set(Calendar.MINUTE, series.postMinute);
    addNoticeIfDue(out, series, todayKey, monthKey, monthName, slot, post);
  }

  private static void addNoticeIfDue(
    List<Task> out,
    Series series,
    String todayKey,
    String monthKey,
    String monthName,
    String slot,
    Calendar post) {
    if (DateUtils.dayKey(post).compareTo(todayKey) < 0) {
      return;
    }
    Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
    out.add(build(
      TaskTypes.BIRTHDAY_NOTICE,
      CaptionTemplates.monthlyTitle(series.title, monthName),
      monthlyDescription(series),
      draft.getTimeInMillis(),
      post.getTimeInMillis(),
      monthlyKey(series, monthKey, slot, DateUtils.dayKey(post)),
      0L,
      series.id));
  }

  private static String monthlyDescription(Series series) {
    StringBuilder out = new StringBuilder();
    if (series.lastOfPrevMonth) {
      out.append("Last day of the previous month");
    }
    if (series.tenth) {
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append("the 10th");
    }
    if (series.twentieth) {
      if (out.length() > 0) {
        out.append(", ");
      }
      out.append("the 20th");
    }
    if (out.length() == 0) {
      return "{month} fills itself.";
    }
    out.append(". {month} fills itself.");
    return out.toString();
  }

  private static String countdownKey(Series series, String eventKey, String postKey) {
    if (series.id > 0L && (series.seedKey == null || series.seedKey.isEmpty())) {
      return TaskTypes.COUNTDOWN + "|" + series.id + "|" + eventKey + "|" + postKey;
    }
    return TaskTypes.COUNTDOWN + "|" + eventKey + "|" + postKey;
  }

  private static String monthlyKey(Series series, String monthKey, String slot, String dayKey) {
    if (series.id > 0L && (series.seedKey == null || series.seedKey.isEmpty())) {
      return TaskTypes.BIRTHDAY_NOTICE + "|" + series.id + "|" + monthKey + "|" + slot + "|" + dayKey;
    }
    String legacySlot = slot;
    if ("D10".equals(slot)) {
      legacySlot = "FIRST";
    } else if ("D20".equals(slot)) {
      legacySlot = "SECOND";
    }
    return TaskTypes.BIRTHDAY_NOTICE + "|" + monthKey + "|" + legacySlot + "|" + dayKey;
  }

  private static void addBirthday(List<Task> out, Calendar now, Member member) {
    if (member == null || member.birthMonth < 1 || member.birthDay < 1) {
      return;
    }
    Calendar post = DateUtils.nextBirthdayAt(
      now,
      member.birthMonth,
      member.birthDay,
      ScheduleTimes.BIRTHDAY_POST_HOUR,
      0);
    Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
    String title = member.name + "'s Birthday";
    out.add(build(
      TaskTypes.BIRTHDAY,
      title,
      "Write the greeting the evening before. Caption ready by 6:30 AM. Post at 7:00 AM.",
      draft.getTimeInMillis(),
      post.getTimeInMillis(),
      TaskTypes.BIRTHDAY + "|" + member.id + "|" + DateUtils.dayKey(post),
      member.id,
      0L));
  }

  private static Task build(
    String type,
    String title,
    String description,
    long draftAt,
    long postAt,
    String key,
    long memberId,
    long seriesId) {
    Task task = new Task();
    task.type = type;
    task.title = title;
    task.description = description;
    task.draftAtMillis = draftAt;
    task.postAtMillis = postAt;
    task.status = TaskStatus.SCHEDULED;
    task.occurrenceKey = key;
    task.memberId = memberId;
    task.seriesId = seriesId;
    return task;
  }

  public static List<Task> none() {
    return Collections.emptyList();
  }
}
