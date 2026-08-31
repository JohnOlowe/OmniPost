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

    applyDueAndSchedule(app);
    AlarmScheduler.scheduleWatchdog(app);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.refresh(app);
  }

  /** Heartbeat / pulse: catch missed phases and re-arm clocks. Does not start FGS. */
  public static void tick(Context context) {
    Context app = context.getApplicationContext();
    applyDueAndSchedule(app);
    AlarmScheduler.scheduleHeartbeat(app);
  }

  public static void onAlarm(Context context, int phase, long taskId) {
    Context app = context.getApplicationContext();
    if (phase == AlarmScheduler.PHASE_WATCHDOG) {
      bootstrap(app);
      return;
    }
    if (phase == AlarmScheduler.PHASE_PULSE && taskId == 0L) {
      tick(app);
      return;
    }
    if (phase == AlarmScheduler.PHASE_SNOOZE) {
      onSnoozeWake(app, taskId);
      tick(app);
      return;
    }
    handleTaskPhase(app, phase, taskId);
    tick(app);
  }

  private static void applyDueAndSchedule(Context app) {
    AppDatabase db = AppDatabase.get(app);
    long now = System.currentTimeMillis();
    long warningLead = Prefs.warningLeadMs(app);
    List<Task> active = db.taskDao().getActiveSync();
    if (active == null) {
      return;
    }
    for (Task task : active) {
      if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
        AlarmScheduler.scheduleTask(app, task);
        continue;
      }
      String previous = task.status;
      String due = TaskStatus.dueStatus(
        task.draftAtMillis, task.postAtMillis, now, warningLead);
      if (!due.equals(task.status) || task.snoozeUntilMillis != 0L) {
        db.taskDao().setSnooze(task.id, due, 0L);
        task.status = due;
        task.snoozeUntilMillis = 0L;
      }
      if (!due.equals(previous)) {
        fireTransition(app, task, due);
      }
      AlarmScheduler.scheduleTask(app, task);
    }
  }

  private static void fireTransition(Context app, Task task, String due) {
    if (TaskStatus.DRAFTING.equals(due)) {
      NotificationHelper.showDraft(app, task);
    } else if (TaskStatus.WARNING.equals(due)) {
      NotificationHelper.showWarning(app, task);
    } else if (TaskStatus.NAGGING.equals(due)) {
      NotificationHelper.showNagBurst(app, task);
    }
  }

  private static void handleTaskPhase(Context app, int phase, long taskId) {
    Task task = AppDatabase.get(app).taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      AlarmScheduler.cancelTask(app, taskId);
      return;
    }
    if (TaskStatus.SNOOZED.equals(task.status)
      && task.snoozeUntilMillis > System.currentTimeMillis()) {
      return;
    }
    long now = System.currentTimeMillis();
    switch (phase) {
      case AlarmScheduler.PHASE_DRAFT:
        AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.DRAFTING);
        task.status = TaskStatus.DRAFTING;
        NotificationHelper.showDraft(app, task);
        break;
      case AlarmScheduler.PHASE_WARNING:
        AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.WARNING);
        task.status = TaskStatus.WARNING;
        NotificationHelper.showWarning(app, task);
        break;
      case AlarmScheduler.PHASE_NAG:
      case AlarmScheduler.PHASE_PULSE:
        if (task.postAtMillis <= now || phase == AlarmScheduler.PHASE_NAG) {
          AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.NAGGING);
          task.status = TaskStatus.NAGGING;
          NotificationHelper.showNagBurst(app, task);
        }
        break;
      default:
        break;
    }
  }

  public static void resurrectNags(Context context) {
    tick(context);
  }

  public static void markPosted(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    db.taskDao().markPosted(taskId, System.currentTimeMillis());
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    AlarmScheduler.scheduleHeartbeat(app);
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
    fireTransition(app, task, due);
    AlarmScheduler.scheduleHeartbeat(app);
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
    AlarmScheduler.scheduleHeartbeat(app);
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
    AlarmScheduler.scheduleHeartbeat(app);
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
    fireTransition(app, task, due);
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
      fireTransition(app, task, task.status);
      AlarmScheduler.scheduleHeartbeat(app);
      NagForegroundService.refresh(app);
    }
  }

  public static void deleteCustom(Context context, long taskId) {
    Context app = context.getApplicationContext();
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    AppDatabase.get(app).taskDao().deleteById(taskId);
    AlarmScheduler.scheduleHeartbeat(app);
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
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.refresh(app);
  }
}
