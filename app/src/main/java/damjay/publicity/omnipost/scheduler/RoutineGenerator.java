package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public final class RoutineGenerator {
  private RoutineGenerator() {}

  public static List<Task> generate(long nowMillis, TimeZone tz, List<Member> members) {
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
    addCountdown(out, now);
    addBirthdayNotices(out, now);
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
        type + "|" + DateUtils.dayKey(post), 0L));
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
        0L));
      post.add(Calendar.MONTH, 1);
      post.set(Calendar.DAY_OF_MONTH, 1);
      post.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      post.set(Calendar.MINUTE, 0);
    }
  }

  private static void addCountdown(List<Task> out, Calendar now) {
    Calendar event = DateUtils.startOfDay(now);
    event.set(Campaigns.BEYOND_LIMIT_YEAR, Campaigns.BEYOND_LIMIT_MONTH, Campaigns.BEYOND_LIMIT_DAY);
    Calendar today = DateUtils.startOfDay(now);
    if (today.after(event)) {
      return;
    }
    for (int i = 0; i < ScheduleTimes.GENERATE_COUNTDOWN_DAYS; i++) {
      Calendar postDay = (Calendar) today.clone();
      postDay.add(Calendar.DAY_OF_MONTH, i);
      if (postDay.after(event)) {
        break;
      }
      int days = DateUtils.calendarDaysBetween(postDay, event);
      Calendar post = DateUtils.sameDayAt(postDay, ScheduleTimes.MONTH_POST_HOUR, 0);
      Calendar draft = DateUtils.dayBeforeAt(post, ScheduleTimes.EVENING_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.COUNTDOWN,
        CaptionTemplates.countdownTitle(days),
        "Daily until 9 Sept 2026. One card — the day count updates itself.",
        draft.getTimeInMillis(),
        post.getTimeInMillis(),
        TaskTypes.COUNTDOWN + "|" + DateUtils.dayKey(event) + "|" + DateUtils.dayKey(post),
        0L));
    }
  }

  private static void addBirthdayNotices(List<Task> out, Calendar now) {
    Calendar monthStart = DateUtils.startOfDay(now);
    monthStart.set(Calendar.DAY_OF_MONTH, 1);
    String todayKey = DateUtils.dayKey(now);
    for (int m = 0; m < ScheduleTimes.GENERATE_MONTH_COUNT; m++) {
      Calendar target = (Calendar) monthStart.clone();
      target.add(Calendar.MONTH, m);
      String monthKey = DateUtils.monthKey(target);
      String monthName = DateUtils.monthName(target);
      int max = target.getActualMaximum(Calendar.DAY_OF_MONTH);
      int[] thirds = DateUtils.inMonthThirds(max);

      Calendar eve = (Calendar) target.clone();
      eve.set(Calendar.DAY_OF_MONTH, 1);
      eve.add(Calendar.MONTH, -1);
      eve.set(Calendar.DAY_OF_MONTH, eve.getActualMaximum(Calendar.DAY_OF_MONTH));
      eve.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      eve.set(Calendar.MINUTE, 0);
      eve.set(Calendar.SECOND, 0);
      eve.set(Calendar.MILLISECOND, 0);

      Calendar first = DateUtils.sameDayAt(target, ScheduleTimes.MONTH_POST_HOUR, 0);
      first.set(Calendar.DAY_OF_MONTH, thirds[0]);
      first.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      first.set(Calendar.MINUTE, 0);

      Calendar second = DateUtils.sameDayAt(target, ScheduleTimes.MONTH_POST_HOUR, 0);
      second.set(Calendar.DAY_OF_MONTH, thirds[1]);
      second.set(Calendar.HOUR_OF_DAY, ScheduleTimes.MONTH_POST_HOUR);
      second.set(Calendar.MINUTE, 0);

      addNoticeIfDue(out, todayKey, monthKey, monthName, "EVE", eve);
      addNoticeIfDue(out, todayKey, monthKey, monthName, "FIRST", first);
      addNoticeIfDue(out, todayKey, monthKey, monthName, "SECOND", second);
    }
  }

  private static void addNoticeIfDue(
    List<Task> out,
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
      "Birthday notice · " + monthName,
      "Last day of the previous month, then twice in the month. Month name fills itself.",
      draft.getTimeInMillis(),
      post.getTimeInMillis(),
      TaskTypes.BIRTHDAY_NOTICE + "|" + monthKey + "|" + slot + "|" + DateUtils.dayKey(post),
      0L));
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
      member.id));
  }

  private static Task build(
    String type,
    String title,
    String description,
    long draftAt,
    long postAt,
    String key,
    long memberId) {
    Task task = new Task();
    task.type = type;
    task.title = title;
    task.description = description;
    task.draftAtMillis = draftAt;
    task.postAtMillis = postAt;
    task.status = TaskStatus.SCHEDULED;
    task.occurrenceKey = key;
    task.memberId = memberId;
    return task;
  }

  public static List<Task> none() {
    return Collections.emptyList();
  }
}
