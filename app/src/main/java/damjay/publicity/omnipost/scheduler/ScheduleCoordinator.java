package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.service.NagForegroundService;
import java.util.List;
import java.util.TimeZone;

public final class ScheduleCoordinator {
  private ScheduleCoordinator() {}

  public static void bootstrap(Context context) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    long now = System.currentTimeMillis();

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
      existing.draftAtMillis = candidate.draftAtMillis;
      existing.postAtMillis = candidate.postAtMillis;
      db.taskDao().update(existing);
    }

    boolean anyNag = false;
    List<Task> active = db.taskDao().getActiveSync();
    for (Task task : active) {
      if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
        AlarmScheduler.scheduleTask(app, task);
        continue;
      }
      String due = TaskStatus.dueStatus(task.draftAtMillis, task.postAtMillis, now);
      if (!due.equals(task.status) || task.snoozeUntilMillis != 0L) {
        db.taskDao().setSnooze(task.id, due, 0L);
        task.status = due;
        task.snoozeUntilMillis = 0L;
      }
      if (TaskStatus.NAGGING.equals(task.status)) {
        anyNag = true;
      }
      AlarmScheduler.scheduleTask(app, task);
    }

    if (anyNag) {
      NagForegroundService.start(app, 0L);
    } else {
      List<Task> nagging = db.taskDao().getNaggingSync();
      if (nagging == null || nagging.isEmpty()) {
        NagForegroundService.stop(app);
      } else {
        NagForegroundService.start(app, 0L);
      }
    }
    AlarmScheduler.scheduleWatchdog(app);
  }

  public static void resurrectNags(Context context) {
    Context app = context.getApplicationContext();
    long now = System.currentTimeMillis();
    AppDatabase db = AppDatabase.get(app);
    boolean any = false;
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
        any = true;
      }
    }
    List<Task> nagging = db.taskDao().getNaggingSync();
    if (any || (nagging != null && !nagging.isEmpty())) {
      NagForegroundService.start(app, 0L);
    } else {
      NagForegroundService.stop(app);
    }
  }

  public static void markPosted(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    db.taskDao().markPosted(taskId, System.currentTimeMillis());
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    stopOrRefreshNag(app);
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
    stopOrRefreshNag(app);
  }

  public static void onSnoozeWake(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return;
    }
    long now = System.currentTimeMillis();
    String due = TaskStatus.dueStatus(task.draftAtMillis, task.postAtMillis, now);
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
    }
  }

  public static void addCustom(Context context, String title, long postAt, boolean flexible) {
    Context app = context.getApplicationContext();
    long now = System.currentTimeMillis();
    Task task = new Task();
    task.type = flexible ? TaskTypes.FLEXIBLE : TaskTypes.ONE_OFF;
    task.title = title;
    task.description = flexible
      ? "Flexible — you pick the next time. Caption ready 30 minutes before."
      : "One-off. Caption ready 30 minutes before.";
    task.postAtMillis = postAt;
    long draft = postAt - 24L * 60L * 60L * 1000L;
    task.draftAtMillis = Math.max(now, draft);
    task.status = TaskStatus.dueStatus(task.draftAtMillis, task.postAtMillis, now);
    task.occurrenceKey = task.type + "|" + postAt + "|" + title.hashCode();
    long id = AppDatabase.get(app).taskDao().insert(task);
    if (id > 0L) {
      task.id = id;
      AlarmScheduler.scheduleTask(app, task);
    }
  }

  public static void deleteCustom(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    AppDatabase.get(app).taskDao().deleteById(taskId);
    stopOrRefreshNag(app);
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
  }

  private static void stopOrRefreshNag(Context app) {
    List<Task> nagging = AppDatabase.get(app).taskDao().getNaggingSync();
    if (nagging == null || nagging.isEmpty()) {
      NagForegroundService.stop(app);
    } else {
      NagForegroundService.refresh(app);
    }
  }
}
