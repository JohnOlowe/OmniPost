package damjay.publicity.omnipost.notify;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.ui.AlarmActivity;
import damjay.publicity.omnipost.util.ExtraKeys;

/**
 * Fire vibrate/ring and the alarm screen immediately. Do not wait for Room —
 * title and time come from the in-memory cue (AlarmClock extras stay stable).
 */
public final class AlarmLaunch {
  private static final String TAG = "OmniPost";

  private AlarmLaunch() {}

  public static boolean isCue(int phase) {
    return phase == AlarmScheduler.PHASE_DRAFT
        || phase == AlarmScheduler.PHASE_WARNING
        || phase == AlarmScheduler.PHASE_MINUTE
        || phase == AlarmScheduler.PHASE_NAG
        || phase == AlarmScheduler.PHASE_SNOOZE;
  }

  public static boolean loud(int phase) {
    return AlarmScheduler.loudPhase(phase) || phase == AlarmScheduler.PHASE_SNOOZE;
  }

  public static void fromIntent(Context ctx, Intent source) {
    if (ctx == null || source == null) {
      return;
    }
    long taskId = source.getLongExtra(ExtraKeys.TASK_ID, 0L);
    int phase = source.getIntExtra(ExtraKeys.PHASE, 0);
    if (taskId <= 0L || !isCue(phase)) {
      return;
    }
    String title = source.getStringExtra(ExtraKeys.TASK_TITLE);
    if (title == null || title.isEmpty()) {
      title = AlarmScheduler.cachedTitle(taskId);
    }
    long postAt = source.getLongExtra(ExtraKeys.POST_AT, 0L);
    if (postAt <= 0L) {
      postAt = AlarmScheduler.cachedPostAt(taskId);
    }
    fire(ctx, taskId, phase, title, postAt);
  }

  public static void fire(Context ctx, Task task, int phase) {
    if (task == null) {
      return;
    }
    fire(ctx, task.id, phase, task.title, task.postAtMillis);
  }

  public static void fire(Context ctx, long taskId, int phase, String title, long postAt) {
    if (ctx == null || taskId <= 0L || !isCue(phase)) {
      return;
    }
    Context app = ctx.getApplicationContext();
    AlarmScheduler.rememberCue(taskId, title, postAt);
    boolean loud = loud(phase);
    if (loud) {
      AlarmPulse.begin(app);
    } else {
      AlarmPulse.beginSoft(app);
    }
    launchActivity(app, taskId, phase, title, postAt);
    NotificationHelper.notifyCue(app, taskId, phase, title, postAt, loud);
  }

  static void launchActivity(Context app, long taskId, int phase, String title, long postAt) {
    Intent alarm = new Intent(app, AlarmActivity.class);
    alarm.addFlags(
      Intent.FLAG_ACTIVITY_NEW_TASK
        | Intent.FLAG_ACTIVITY_CLEAR_TOP
        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        | Intent.FLAG_ACTIVITY_NO_ANIMATION
        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    alarm.putExtra(ExtraKeys.TASK_ID, taskId);
    alarm.putExtra(ExtraKeys.PHASE, phase);
    alarm.putExtra(ExtraKeys.TASK_TITLE, title == null ? "" : title);
    alarm.putExtra(ExtraKeys.POST_AT, postAt);
    try {
      if (Build.VERSION.SDK_INT >= 34) {
        android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
        app.startActivity(alarm, options.toBundle());
      } else {
        app.startActivity(alarm);
      }
    } catch (Exception e) {
      Log.w(TAG, "alarm UI start failed", e);
    }
  }
}
