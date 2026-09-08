package damjay.publicity.omnipost.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import damjay.publicity.omnipost.MainActivity;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.receiver.TaskAlarmReceiver;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.List;

public final class AlarmScheduler {
  public static final int PHASE_DRAFT = 1;
  public static final int PHASE_WARNING = 2;
  public static final int PHASE_NAG = 3;
  public static final int PHASE_PULSE = 4;
  public static final int PHASE_WATCHDOG = 5;
  public static final int PHASE_SNOOZE = 6;
  public static final int PHASE_MINUTE = 7;

  private static final String TAG = "OmniPost";
  private static final int WATCHDOG_CODE = 0x0A11;
  private static final int GLOBAL_PULSE_CODE = 0x0A12;
  private static final Object LOCK = new Object();
  private static final SparseArray<PendingIntent> TOKENS = new SparseArray<>();
  private static final LongSparseArray<String> ARMED = new LongSparseArray<>();
  private static final LongSparseArray<String> TITLES = new LongSparseArray<>();
  private static final LongSparseArray<Long> TIMES = new LongSparseArray<>();
  private static PendingIntent showPi;

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
    long warningLead = Prefs.warningLeadMs(ctx);
    String stamp = armStamp(task, now, warningLead);
    remember(task.id, task.title, task.postAtMillis);
    boolean same;
    synchronized (LOCK) {
      same = stamp.equals(ARMED.get(task.id));
    }
    if (!same) {
      cancelTask(ctx, task.id);
      remember(task.id, task.title, task.postAtMillis);
    }
    if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
      long minuteAt = task.snoozeUntilMillis - ScheduleTimes.MINUTE_LEAD_MS;
      if (minuteAt > now) {
        setAlarmClock(ctx, task, PHASE_MINUTE, minuteAt);
      }
      setAlarmClock(ctx, task, PHASE_SNOOZE, task.snoozeUntilMillis);
      markArmed(task.id, stamp);
      return;
    }
    boolean saved = TaskStatus.captionIsSaved(task);
    if (!saved && task.draftAtMillis > now) {
      setAlarmClock(ctx, task, PHASE_DRAFT, task.draftAtMillis);
    }
    long warningAt = task.postAtMillis - warningLead;
    if (!saved && warningAt > now) {
      setAlarmClock(ctx, task, PHASE_WARNING, warningAt);
    }
    long minuteAt = task.postAtMillis - ScheduleTimes.MINUTE_LEAD_MS;
    if (minuteAt > now && minuteAt < task.postAtMillis && minuteAt != warningAt) {
      setAlarmClock(ctx, task, PHASE_MINUTE, minuteAt);
    }
    if (task.postAtMillis > now) {
      setAlarmClock(ctx, task, PHASE_NAG, task.postAtMillis);
    }
    markArmed(task.id, stamp);
  }

  public static boolean loudPhase(int phase) {
    return phase == PHASE_NAG || phase == PHASE_MINUTE;
  }

  public static void scheduleHeartbeat(Context ctx) {
    scheduleHeartbeat(ctx, System.currentTimeMillis() + ScheduleTimes.HEARTBEAT_MS);
  }

  public static void scheduleHeartbeat(Context ctx, long whenMillis) {
    long when = Math.max(whenMillis, System.currentTimeMillis() + 3_000L);
    setAlarmClock(ctx, 0L, PHASE_PULSE, when);
  }

  public static void scheduleKick(Context ctx) {
    setAlarmClock(ctx, 0L, PHASE_PULSE, System.currentTimeMillis() + ScheduleTimes.KICK_MS);
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
    setAlarmClock(ctx, 0L, PHASE_WATCHDOG, c.getTimeInMillis());
  }

  public static void cancelTask(Context ctx, long taskId) {
    synchronized (LOCK) {
      ARMED.delete(taskId);
      TITLES.delete(taskId);
      TIMES.delete(taskId);
    }
    cancelPhase(ctx, taskId, PHASE_DRAFT);
    cancelPhase(ctx, taskId, PHASE_WARNING);
    cancelPhase(ctx, taskId, PHASE_MINUTE);
    cancelPhase(ctx, taskId, PHASE_NAG);
    cancelPhase(ctx, taskId, PHASE_PULSE);
    cancelPhase(ctx, taskId, PHASE_SNOOZE);
  }

  public static void rememberCue(long taskId, String title, long postAt) {
    remember(taskId, title, postAt);
  }

  public static String cachedTitle(long taskId) {
    synchronized (LOCK) {
      String title = TITLES.get(taskId);
      return title == null ? "" : title;
    }
  }

  public static long cachedPostAt(long taskId) {
    synchronized (LOCK) {
      Long when = TIMES.get(taskId);
      return when == null ? 0L : when;
    }
  }

  private static void setAlarmClock(Context ctx, Task task, int phase, long when) {
    String title = task.title == null ? "" : task.title;
    setAlarmClock(ctx, task.id, phase, when, title, task.postAtMillis);
  }

  private static void setAlarmClock(Context ctx, long taskId, int phase, long when) {
    setAlarmClock(ctx, taskId, phase, when, "", 0L);
  }

  private static void setAlarmClock(
    Context ctx, long taskId, int phase, long when, String title, long postAt) {
    remember(taskId, title, postAt);
    AlarmManager manager = am(ctx);
    PendingIntent pi = obtain(ctx, taskId, phase);
    if (pi == null) {
      return;
    }
    try {
      manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showIntent(ctx)), pi);
    } catch (IllegalStateException e) {
      Log.e(TAG, "PendingIntent cap on setAlarmClock", e);
      recycleAll(ctx);
      pi = obtain(ctx, taskId, phase);
      if (pi == null) {
        return;
      }
      try {
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showIntent(ctx)), pi);
      } catch (Exception e2) {
        Log.e(TAG, "setAlarmClock failed after recycle", e2);
        setExact(ctx, pi, when);
      }
    } catch (SecurityException e) {
      Log.w(TAG, "setAlarmClock denied, falling back", e);
      setExact(ctx, pi, when);
    }
  }

  private static void setExact(Context ctx, PendingIntent pi, long when) {
    AlarmManager manager = am(ctx);
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

  /**
   * Same action + requestCode every time. Title/time stay out of the Intent so
   * Tecno/Infinix cannot mint a new PendingIntentRecord on each heartbeat.
   */
  private static Intent alarmIntent(Context ctx, long taskId, int phase) {
    Intent intent = new Intent(ctx, TaskAlarmReceiver.class);
    intent.setClass(ctx, TaskAlarmReceiver.class);
    intent.setAction("damjay.publicity.omnipost.ALARM." + phase + "." + taskId);
    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
    intent.putExtra(ExtraKeys.TASK_ID, taskId);
    intent.putExtra(ExtraKeys.PHASE, phase);
    return intent;
  }

  private static PendingIntent obtain(Context ctx, long taskId, int phase) {
    int code = requestCode(taskId, phase);
    synchronized (LOCK) {
      PendingIntent cached = TOKENS.get(code);
      if (cached != null) {
        return cached;
      }
    }
    PendingIntent pi = mint(ctx, taskId, phase, false);
    if (pi == null) {
      recycleAll(ctx);
      pi = mint(ctx, taskId, phase, false);
    }
    if (pi != null) {
      synchronized (LOCK) {
        TOKENS.put(code, pi);
      }
    }
    return pi;
  }

  private static PendingIntent mint(Context ctx, long taskId, int phase, boolean noCreate) {
    int flags = PendingIntent.FLAG_IMMUTABLE
      | (noCreate ? PendingIntent.FLAG_NO_CREATE : PendingIntent.FLAG_UPDATE_CURRENT);
    try {
      return PendingIntent.getBroadcast(ctx, requestCode(taskId, phase), alarmIntent(ctx, taskId, phase), flags);
    } catch (IllegalStateException e) {
      Log.e(TAG, "Too many PendingIntent records", e);
      return null;
    }
  }

  private static void cancelPhase(Context ctx, long taskId, int phase) {
    int code = requestCode(taskId, phase);
    PendingIntent pi;
    synchronized (LOCK) {
      pi = TOKENS.get(code);
      TOKENS.remove(code);
    }
    if (pi == null) {
      pi = mint(ctx, taskId, phase, true);
    }
    if (pi == null) {
      return;
    }
    try {
      am(ctx).cancel(pi);
      pi.cancel();
    } catch (Exception ignored) {
    }
  }

  private static PendingIntent showIntent(Context ctx) {
    synchronized (LOCK) {
      if (showPi != null) {
        return showPi;
      }
    }
    PendingIntent pi;
    try {
      pi = PendingIntent.getActivity(
        ctx,
        1,
        new Intent(ctx, MainActivity.class)
          .setAction(Intent.ACTION_MAIN)
          .addCategory(Intent.CATEGORY_LAUNCHER),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    } catch (IllegalStateException e) {
      Log.e(TAG, "PendingIntent cap on show intent", e);
      recycleAll(ctx);
      try {
        pi = PendingIntent.getActivity(
          ctx,
          1,
          new Intent(ctx, MainActivity.class)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER),
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      } catch (IllegalStateException e2) {
        return null;
      }
    }
    synchronized (LOCK) {
      showPi = pi;
    }
    return pi;
  }

  private static void recycleAll(Context ctx) {
    List<PendingIntent> all = new ArrayList<>();
    synchronized (LOCK) {
      for (int i = 0; i < TOKENS.size(); i++) {
        PendingIntent pi = TOKENS.valueAt(i);
        if (pi != null) {
          all.add(pi);
        }
      }
      TOKENS.clear();
      ARMED.clear();
      if (showPi != null) {
        all.add(showPi);
        showPi = null;
      }
    }
    AlarmManager manager = am(ctx);
    for (PendingIntent pi : all) {
      try {
        manager.cancel(pi);
        pi.cancel();
      } catch (Exception ignored) {
      }
    }
  }

  private static void remember(long taskId, String title, long postAt) {
    if (taskId <= 0L) {
      return;
    }
    synchronized (LOCK) {
      TITLES.put(taskId, title == null ? "" : title);
      TIMES.put(taskId, postAt);
    }
  }

  private static void markArmed(long taskId, String stamp) {
    synchronized (LOCK) {
      ARMED.put(taskId, stamp);
    }
  }

  /** Future alarm times for this task. Heartbeat skips getBroadcast when this is unchanged. */
  static String armStamp(Task task, long now, long warningLeadMs) {
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return "posted";
    }
    if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
      long minuteAt = task.snoozeUntilMillis - ScheduleTimes.MINUTE_LEAD_MS;
      return "snooze|" + (minuteAt > now ? minuteAt : 0L) + "|" + task.snoozeUntilMillis;
    }
    boolean saved = TaskStatus.captionIsSaved(task);
    StringBuilder out = new StringBuilder("arm");
    if (!saved && task.draftAtMillis > now) {
      out.append("|d").append(task.draftAtMillis);
    }
    long warningAt = task.postAtMillis - warningLeadMs;
    if (!saved && warningAt > now) {
      out.append("|w").append(warningAt);
    }
    long minuteAt = task.postAtMillis - ScheduleTimes.MINUTE_LEAD_MS;
    if (minuteAt > now && minuteAt < task.postAtMillis && minuteAt != warningAt) {
      out.append("|m").append(minuteAt);
    }
    if (task.postAtMillis > now) {
      out.append("|n").append(task.postAtMillis);
    }
    return out.toString();
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
