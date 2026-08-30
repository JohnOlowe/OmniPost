package damjay.publicity.omnipost.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.ui.AlarmActivity;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;

public class TaskAlarmReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    final PendingResult pending = goAsync();
    final Context app = context.getApplicationContext();
    final Intent copy = intent == null ? null : new Intent(intent);
    AppExecutors.disk().execute(() -> {
      try {
        handle(app, copy);
      } catch (Exception e) {
        Log.e("OmniPost", "alarm handle failed", e);
      } finally {
        pending.finish();
      }
    });
  }

  private static void handle(Context app, Intent intent) {
    if (intent == null) {
      return;
    }
    int phase = intent.getIntExtra(ExtraKeys.PHASE, 0);
    long taskId = intent.getLongExtra(ExtraKeys.TASK_ID, 0L);
    if (phase == AlarmScheduler.PHASE_WATCHDOG) {
      ScheduleCoordinator.bootstrap(app);
      return;
    }
    if (phase == AlarmScheduler.PHASE_PULSE && taskId == 0L) {
      ScheduleCoordinator.resurrectNags(app);
      return;
    }
    Task task = AppDatabase.get(app).taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      AlarmScheduler.cancelTask(app, taskId);
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
        launchAlarmScreen(app, task, phase);
        break;
      case AlarmScheduler.PHASE_NAG:
      case AlarmScheduler.PHASE_PULSE:
        if (task.postAtMillis <= now || phase == AlarmScheduler.PHASE_NAG) {
          AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.NAGGING);
          task.status = TaskStatus.NAGGING;
        }
        NagForegroundService.start(app, taskId);
        break;
      default:
        break;
    }
  }

  private static void launchAlarmScreen(Context app, Task task, int phase) {
    Intent activity = new Intent(app, AlarmActivity.class);
    activity.addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK
        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    activity.putExtra(ExtraKeys.TASK_ID, task.id);
    activity.putExtra(ExtraKeys.PHASE, phase);
    try {
      app.startActivity(activity);
    } catch (Exception ignored) {
      // Full-screen notification is the reliable path on Android 10+.
    }
  }
}
