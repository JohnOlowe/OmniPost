package damjay.publicity.omnipost.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import damjay.publicity.omnipost.MainActivity;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.receiver.TaskAlarmReceiver;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;

public final class AlarmScheduler {
  public static final int PHASE_DRAFT = 1;
  public static final int PHASE_WARNING = 2;
  public static final int PHASE_NAG = 3;
  public static final int PHASE_PULSE = 4;
  public static final int PHASE_WATCHDOG = 5;
  public static final int PHASE_SNOOZE = 6;

  private static final String TAG = "OmniPost";
  private static final int WATCHDOG_CODE = 0x0A11;
  private static final int GLOBAL_PULSE_CODE = 0x0A12;

  private AlarmScheduler() {}

  public static void scheduleTask(Context ctx, Task task) {
    if (task == null) {
      return;
    }
    if (TaskStatus.POSTED.equals(task.status)) {
      cancelTask(ctx, task.id);
      return;
    }
    long now = System.currentTimeMillis();
    if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
      cancelTask(ctx, task.id);
      setAlarmClock(ctx, task.id, PHASE_SNOOZE, task.snoozeUntilMillis);
      return;
    }
    if (task.draftAtMillis > now) {
      setAlarmClock(ctx, task.id, PHASE_DRAFT, task.draftAtMillis);
    }
    long warningAt = task.postAtMillis - Prefs.warningLeadMs(ctx);
    if (warningAt > now) {
      setExact(ctx, task.id, PHASE_WARNING, warningAt);
    }
    if (task.postAtMillis > now) {
      setAlarmClock(ctx, task.id, PHASE_NAG, task.postAtMillis);
    }
  }

  public static void schedulePulse(Context ctx, long taskId, long whenMillis) {
    setExact(ctx, taskId, PHASE_PULSE, whenMillis);
  }

  public static void scheduleWatchdog(Context ctx) {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.set(java.util.Calendar.HOUR_OF_DAY, ScheduleTimes.WATCHDOG_HOUR);
    c.set(java.util.Calendar.MINUTE, 0);
    c.set(java.util.Calendar.SECOND, 0);
    c.set(java.util.Calendar.MILLISECOND, 0);
    if (c.getTimeInMillis() <= System.currentTimeMillis()) {
      c.add(java.util.Calendar.DAY_OF_MONTH, 1);
    }
    setExact(ctx, 0, PHASE_WATCHDOG, c.getTimeInMillis());
  }

  public static void cancelTask(Context ctx, long taskId) {
    AlarmManager manager = am(ctx);
    manager.cancel(broadcast(ctx, taskId, PHASE_DRAFT));
    manager.cancel(broadcast(ctx, taskId, PHASE_WARNING));
    manager.cancel(broadcast(ctx, taskId, PHASE_NAG));
    manager.cancel(broadcast(ctx, taskId, PHASE_PULSE));
    manager.cancel(broadcast(ctx, taskId, PHASE_SNOOZE));
  }

  private static void setAlarmClock(Context ctx, long taskId, int phase, long when) {
    AlarmManager manager = am(ctx);
    PendingIntent pi = broadcast(ctx, taskId, phase);
    PendingIntent show = PendingIntent.getActivity(
      ctx,
      1,
      new Intent(ctx, MainActivity.class)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER),
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    try {
      manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
    } catch (SecurityException e) {
      Log.w(TAG, "setAlarmClock denied, falling back", e);
      setExact(ctx, taskId, phase, when);
    }
  }

  private static void setExact(Context ctx, long taskId, int phase, long when) {
    AlarmManager manager = am(ctx);
    PendingIntent pi = broadcast(ctx, taskId, phase);
    try {
      if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        return;
      }
      manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    } catch (SecurityException e) {
      Log.w(TAG, "exact alarm denied", e);
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }
  }

  private static PendingIntent broadcast(Context ctx, long taskId, int phase) {
    Intent intent = new Intent(ctx, TaskAlarmReceiver.class);
    intent.setAction("damjay.publicity.omnipost.ALARM." + phase + "." + taskId);
    intent.putExtra(ExtraKeys.TASK_ID, taskId);
    intent.putExtra(ExtraKeys.PHASE, phase);
    return PendingIntent.getBroadcast(
      ctx,
      requestCode(taskId, phase),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  static int requestCode(long taskId, int phase) {
    if (phase == PHASE_WATCHDOG) {
      return WATCHDOG_CODE;
    }
    if (taskId == 0L && phase == PHASE_PULSE) {
      return GLOBAL_PULSE_CODE;
    }
    return (int) ((taskId * 10 + phase) & 0x7fffffff);
  }

  private static AlarmManager am(Context ctx) {
    return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
  }
}
