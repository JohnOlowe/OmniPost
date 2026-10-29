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
  private static final int[] TASK_PHASES = {
    PHASE_DRAFT, PHASE_WARNING, PHASE_NAG, PHASE_PULSE, PHASE_SNOOZE, PHASE_MINUTE
  };
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
    try {
      long now = System.currentTimeMillis();
      long heldUntil = HoldRules.until(Prefs.holds(ctx), task);
      if (heldUntil > now) {
        String held = "held|" + heldUntil;
        boolean sameHeld;
        synchronized (LOCK) {
          sameHeld = held.equals(ARMED.get(task.id));
        }
        if (sameHeld) {
          return;
        }
        cancelTask(ctx, task.id);
        markArmed(task.id, held);
        return;
      }
      long warningLead = Prefs.warningLeadMs(ctx);
      String stamp = armStamp(task, now, warningLead);
      remember(task.id, task.title, task.postAtMillis);
      boolean same;
      synchronized (LOCK) {
        same = stamp.equals(ARMED.get(task.id));
      }
      if (same) {
        return;
      }
      cancelTask(ctx, task.id);
      remember(task.id, task.title, task.postAtMillis);
      long[] next = nextArm(task, now, warningLead);
      if (next != null) {
        setAlarmClock(ctx, task, (int) next[0], next[1]);
      }
      markArmed(task.id, stamp);
    } catch (RuntimeException e) {
      Log.e(TAG, "scheduleTask failed", e);
    }
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

  /** Once per process: drop leftover tokens from older builds. Never mint. */
  private static boolean recycled;

  public static void recycleStale(Context ctx, List<Task> tasks, long maxId) {
    if (ctx == null || recycled) {
      return;
    }
    recycled = true;
    if (tasks != null) {
      for (Task task : tasks) {
        if (task == null) {
          continue;
        }
        dropLegacy(ctx, task.id, task.title, task.postAtMillis);
      }
    }
    dropBroadcast(ctx, GLOBAL_PULSE_CODE, alarmIntent(ctx, 0L, PHASE_PULSE));
    dropBroadcast(ctx, WATCHDOG_CODE, alarmIntent(ctx, 0L, PHASE_WATCHDOG));
    dropShow(ctx);
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
    } catch (RuntimeException e) {
      Log.e(TAG, "setAlarmClock failed", e);
      setExact(ctx, pi, when);
    }
  }

  private static void setExact(Context ctx, PendingIntent pi, long when) {
    AlarmManager manager = am(ctx);
    try {
      if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
        idle(manager, when, pi);
        return;
      }
      manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    } catch (SecurityException e) {
      Log.w(TAG, "exact alarm denied", e);
      idle(manager, when, pi);
    } catch (IllegalStateException e) {
      Log.e(TAG, "AlarmManager cap on setExact", e);
      recycleAll(ctx);
      idle(manager, when, pi);
    } catch (RuntimeException e) {
      Log.e(TAG, "setExact failed", e);
    }
  }

  private static void idle(AlarmManager manager, long when, PendingIntent pi) {
    if (manager == null || pi == null) {
      return;
    }
    try {
      manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    } catch (RuntimeException e) {
      Log.e(TAG, "inexact alarm failed", e);
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
    PendingIntent pi = mint(ctx, taskId, phase, true);
    if (pi == null) {
      pi = mint(ctx, taskId, phase, false);
    }
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

  private static void dropLegacy(Context ctx, long taskId, String title, long postAt) {
    for (int phase : TASK_PHASES) {
      dropBroadcast(ctx, requestCode(taskId, phase), legacyAlarmIntent(ctx, taskId, phase, title, postAt));
    }
  }

  private static Intent legacyAlarmIntent(
    Context ctx, long taskId, int phase, String title, long postAt) {
    Intent intent = alarmIntent(ctx, taskId, phase);
    intent.putExtra(ExtraKeys.TASK_TITLE, title == null ? "" : title);
    intent.putExtra(ExtraKeys.POST_AT, postAt);
    return intent;
  }

  private static void dropBroadcast(Context ctx, int code, Intent intent) {
    try {
      PendingIntent pi = PendingIntent.getBroadcast(
        ctx, code, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
      if (pi == null) {
        return;
      }
      am(ctx).cancel(pi);
      pi.cancel();
    } catch (RuntimeException ignored) {
    }
  }

  private static void dropShow(Context ctx) {
    PendingIntent pi;
    synchronized (LOCK) {
      pi = showPi;
      showPi = null;
    }
    if (pi == null) {
      try {
        pi = PendingIntent.getActivity(
          ctx,
          1,
          new Intent(ctx, MainActivity.class)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER),
          PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
      } catch (RuntimeException e) {
        return;
      }
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

  /**
   * Soonest phase still in the future. One AlarmManager slot per task — four
   * phases × a 200-name desk blows the 500-alarm OEM cap.
   */
  static long[] nextArm(Task task, long now, long warningLeadMs) {
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return null;
    }
    if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
      long minuteAt = task.snoozeUntilMillis - ScheduleTimes.MINUTE_LEAD_MS;
      if (minuteAt > now) {
        return new long[] { PHASE_MINUTE, minuteAt };
      }
      return new long[] { PHASE_SNOOZE, task.snoozeUntilMillis };
    }
    int phase = 0;
    long when = Long.MAX_VALUE;
    boolean saved = !TaskStatus.captionWorkPending(task);
    if (!saved && task.draftAtMillis > now) {
      phase = PHASE_DRAFT;
      when = task.draftAtMillis;
    }
    long warningAt = task.postAtMillis - warningLeadMs;
    if (!saved && warningLeadMs > 0L && warningAt > now && warningAt < when) {
      phase = PHASE_WARNING;
      when = warningAt;
    }
    boolean postRing = !TaskTypes.alumniCaptionOnly(task.type);
    long minuteAt = task.postAtMillis - ScheduleTimes.MINUTE_LEAD_MS;
    if (postRing
        && minuteAt > now
        && minuteAt < task.postAtMillis
        && minuteAt != warningAt
        && minuteAt < when) {
      phase = PHASE_MINUTE;
      when = minuteAt;
    }
    if (postRing && task.postAtMillis > now && task.postAtMillis < when) {
      phase = PHASE_NAG;
      when = task.postAtMillis;
    }
    if (phase == 0) {
      return null;
    }
    return new long[] { phase, when };
  }

  /** Next arm only. Heartbeat skips getBroadcast when this is unchanged. */
  static String armStamp(Task task, long now, long warningLeadMs) {
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return "posted";
    }
    if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
      long minuteAt = task.snoozeUntilMillis - ScheduleTimes.MINUTE_LEAD_MS;
      return "snooze|" + (minuteAt > now ? minuteAt : 0L) + "|" + task.snoozeUntilMillis;
    }
    long[] next = nextArm(task, now, warningLeadMs);
    if (next == null) {
      return "arm";
    }
    int phase = (int) next[0];
    char letter = 'n';
    if (phase == PHASE_DRAFT) {
      letter = 'd';
    } else if (phase == PHASE_WARNING) {
      letter = 'w';
    } else if (phase == PHASE_MINUTE) {
      letter = 'm';
    } else if (phase == PHASE_SNOOZE) {
      letter = 's';
    }
    return "arm|" + letter + next[1];
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
