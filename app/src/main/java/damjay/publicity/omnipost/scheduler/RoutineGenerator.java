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
      "Draft and post Sunday Service publicity by Saturday 10:00 AM.");
    addWeeklies(
      out,
      now,
      Calendar.WEDNESDAY,
      TaskTypes.WEDNESDAY_BIBLE_STUDY,
      "Wednesday Bible Study",
      "Draft and post Bible Study publicity by Wednesday 10:00 AM.");
    addWeeklies(
      out,
      now,
      Calendar.FRIDAY,
      TaskTypes.FRIDAY_PRAYER,
      "Friday Prayer Meeting",
      "Draft and post Prayer Meeting publicity by Friday 10:00 AM.");
    addMonthEnd(out, now);
    addHappyNewMonth(out, now);
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

  private static void addMonthEnd(List<Task> out, Calendar now) {
    Calendar post = DateUtils.nextMonthEndAt(now, ScheduleTimes.MONTH_POST_HOUR, 0);
    for (int i = 0; i < ScheduleTimes.GENERATE_MONTH_COUNT; i++) {
      Calendar draft = DateUtils.sameDayAt(post, ScheduleTimes.MONTH_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.NEW_MONTH_FASTING,
        "New Month Fasting",
        "Post the New Month Fasting flyer early this morning.",
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

  private static void addHappyNewMonth(List<Task> out, Calendar now) {
    Calendar post = DateUtils.nextMonthStartAt(now, ScheduleTimes.MONTH_POST_HOUR, 0);
    for (int i = 0; i < ScheduleTimes.GENERATE_MONTH_COUNT; i++) {
      Calendar draft = DateUtils.dayBeforeAt(
        post, ScheduleTimes.HAPPY_MONTH_DRAFT_HOUR, 0);
      out.add(build(
        TaskTypes.HAPPY_NEW_MONTH,
        "Happy New Month",
        "Draft and post the Happy New Month caption for the 1st.",
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
    Calendar draft = DateUtils.sameDayAt(post, ScheduleTimes.BIRTHDAY_DRAFT_HOUR, 0);
    String title = member.name + "'s Birthday";
    out.add(build(
      TaskTypes.BIRTHDAY,
      title,
      "Draft and post the birthday greeting by 7:00 AM.",
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
