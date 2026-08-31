package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.util.Prefs;
import java.util.List;
import java.util.TimeZone;

public final class ScheduleCoordinator {
  private ScheduleCoordinator() {}

  public static void bootstrap(Context context) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    long now = System.currentTimeMillis();
    long warningLead = Prefs.warningLeadMs(app);

    List<Task> stale = db.taskDao().staleTests(now - 24L * 60L * 60L * 1000L);
    if (stale != null) {
      for (Task test : stale) {
        AlarmScheduler.cancelTask(app, test.id);
        NotificationHelper.cancelForTask(app, test.id);
      }
    }
    db.taskDao().deleteStaleTests(now - 24L * 60L * 60L * 1000L);

    List<Member> members = db.memberDao().getAllSync();
    List<Task> generated = RoutineGenerator.generate(now, TimeZone.getDefault(), members);
    for (Task candidate : generated) {
      Task existing = db.taskDao().findByKey(candidate.occurrenceKey);
      if (existing == null) {
        db.taskDao().insert(candidate);
        continue;
      }
      if (TaskStatus.POSTED.equals(existing.status)) {
        continue;
      }
      existing.title = candidate.title;
      existing.description = candidate.description;
      if (!existing.timesLocked) {
        existing.draftAtMillis = candidate.draftAtMillis;
        existing.postAtMillis = candidate.postAtMillis;
      }
      db.taskDao().update(existing);
    }

    List<Task> active = db.taskDao().getActiveSync();
    for (Task task : active) {
      if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
        AlarmScheduler.scheduleTask(app, task);
        continue;
      }
      String due = TaskStatus.dueStatus(
        task.draftAtMillis, task.postAtMillis, now, warningLead);
      if (!due.equals(task.status) || task.snoozeUntilMillis != 0L) {
        db.taskDao().setSnooze(task.id, due, 0L);
        task.status = due;
        task.snoozeUntilMillis = 0L;
      }
      AlarmScheduler.scheduleTask(app, task);
    }

    NagForegroundService.refresh(app);
    AlarmScheduler.scheduleWatchdog(app);
  }

  public static void resurrectNags(Context context) {
    Context app = context.getApplicationContext();
    long now = System.currentTimeMillis();
    AppDatabase db = AppDatabase.get(app);
    List<Task> active = db.taskDao().getActiveSync();
    for (Task task : active) {
      if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
        continue;
      }
      if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis <= now) {
        onSnoozeWake(app, task.id);
        continue;
      }
      if (task.postAtMillis <= now && !TaskStatus.POSTED.equals(task.status)) {
        db.taskDao().setSnooze(task.id, TaskStatus.NAGGING, 0L);
      }
    }
    NagForegroundService.refresh(app);
  }

  public static void markPosted(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    db.taskDao().markPosted(taskId, System.currentTimeMillis());
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    NagForegroundService.refresh(app);
  }

  public static void reopen(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null) {
      return;
    }
    long now = System.currentTimeMillis();
    String due = TaskStatus.dueStatus(
      task.draftAtMillis, task.postAtMillis, now, Prefs.warningLeadMs(app));
    task.status = due;
    task.postedAtMillis = 0L;
    task.snoozeUntilMillis = 0L;
    db.taskDao().update(task);
    AlarmScheduler.scheduleTask(app, task);
    NagForegroundService.refresh(app);
  }

  public static void shift(Context context, long taskId, long newPostAt) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null) {
      return;
    }
    long now = System.currentTimeMillis();
    long postAt = Math.max(newPostAt, now + 60_000L);
    task.postAtMillis = postAt;
    long draft = postAt - 24L * 60L * 60L * 1000L;
    task.draftAtMillis = Math.max(now, draft);
    task.timesLocked = true;
    task.postedAtMillis = 0L;
    task.snoozeUntilMillis = 0L;
    task.status = TaskStatus.dueStatus(
      task.draftAtMillis, task.postAtMillis, now, Prefs.warningLeadMs(app));
    db.taskDao().update(task);
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    AlarmScheduler.scheduleTask(app, task);
    NagForegroundService.refresh(app);
  }

  public static void snooze(Context context, long taskId, long untilMillis) {
    Context app = context.getApplicationContext();
    long when = Math.max(untilMillis, System.currentTimeMillis() + 60_000L);
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return;
    }
    db.taskDao().setSnooze(taskId, TaskStatus.SNOOZED, when);
    task.status = TaskStatus.SNOOZED;
    task.snoozeUntilMillis = when;
    NotificationHelper.cancelForTask(app, taskId);
    AlarmScheduler.scheduleTask(app, task);
    NagForegroundService.refresh(app);
  }

  public static void onSnoozeWake(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return;
    }
    long now = System.currentTimeMillis();
    String due = TaskStatus.dueStatus(
      task.draftAtMillis, task.postAtMillis, now, Prefs.warningLeadMs(app));
    db.taskDao().setSnooze(taskId, due, 0L);
    task.status = due;
    task.snoozeUntilMillis = 0L;
    AlarmScheduler.scheduleTask(app, task);
    if (TaskStatus.NAGGING.equals(due)) {
      NagForegroundService.start(app, taskId);
    } else if (TaskStatus.WARNING.equals(due)) {
      NotificationHelper.showWarning(app, task);
    } else if (TaskStatus.DRAFTING.equals(due)) {
      NotificationHelper.showDraft(app, task);
    } else {
      NagForegroundService.refresh(app);
    }
  }

  public static void addCustom(Context context, String title, long postAt, boolean flexible) {
    Context app = context.getApplicationContext();
    long now = System.currentTimeMillis();
    int warn = Prefs.warningMinutes(app);
    Task task = new Task();
    task.type = flexible ? TaskTypes.FLEXIBLE : TaskTypes.ONE_OFF;
    task.title = title;
    task.description = flexible
      ? "Flexible — you pick the next time. Write the caption. Ready "
        + warn
        + " minutes before."
      : "One-off. Write the caption. Ready " + warn + " minutes before.";
    task.postAtMillis = postAt;
    long draft = postAt - 24L * 60L * 60L * 1000L;
    task.draftAtMillis = Math.max(now, draft);
    task.timesLocked = true;
    task.status = TaskStatus.dueStatus(
      task.draftAtMillis, task.postAtMillis, now, Prefs.warningLeadMs(app));
    task.occurrenceKey = task.type + "|" + postAt + "|" + title.hashCode();
    long id = AppDatabase.get(app).taskDao().insert(task);
    if (id > 0L) {
      task.id = id;
      AlarmScheduler.scheduleTask(app, task);
      NagForegroundService.refresh(app);
    }
  }

  public static void deleteCustom(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    AppDatabase.get(app).taskDao().deleteById(taskId);
    NagForegroundService.refresh(app);
  }

  public static void deleteDraft(Context context, long draftId) {
    Context app = context.getApplicationContext();
    AppDatabase.get(app).draftDao().deleteById(draftId);
  }

  public static void cancelMemberTasks(Context context, long memberId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    List<Task> tasks = db.taskDao().getActiveForMember(memberId);
    if (tasks == null) {
      return;
    }
    for (Task task : tasks) {
      AlarmScheduler.cancelTask(app, task.id);
      NotificationHelper.cancelForTask(app, task.id);
      db.taskDao().deleteById(task.id);
    }
    NagForegroundService.refresh(app);
  }
}
