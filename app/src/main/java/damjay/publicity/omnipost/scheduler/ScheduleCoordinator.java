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
      if (task.postAtMillis <= now && !TaskStatus.POSTED.equals(task.status)) {
        db.taskDao().updateStatus(task.id, TaskStatus.NAGGING);
        task.status = TaskStatus.NAGGING;
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
      if (task.postAtMillis <= now && !TaskStatus.POSTED.equals(task.status)) {
        db.taskDao().updateStatus(task.id, TaskStatus.NAGGING);
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
    List<Task> nagging = db.taskDao().getNaggingSync();
    if (nagging == null || nagging.isEmpty()) {
      NagForegroundService.stop(app);
    } else {
      NagForegroundService.refresh(app);
    }
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
}
