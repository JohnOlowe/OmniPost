package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
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
    dropStaleCountdowns(app, db, now);
    ensureSeriesDefaults(app, db);

    List<Member> members = db.memberDao().getAllSync();
    List<Series> series = enabledSeries(db.seriesDao().getAllSync());
    List<Task> generated = RoutineGenerator.generate(
      now, TimeZone.getDefault(), members, series, Prefs.draftHour(app));
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
      existing.seriesId = candidate.seriesId;
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

  private static List<Series> enabledSeries(List<Series> all) {
    List<Series> out = new java.util.ArrayList<>();
    if (all == null) {
      return out;
    }
    for (Series series : all) {
      if (series != null && series.enabled) {
        out.add(series);
      }
    }
    return out;
  }

  private static void ensureSeriesDefaults(Context app, AppDatabase db) {
    boolean seeded = Prefs.seriesDefaultsInstalled(app);
    Series countdown = db.seriesDao().findBySeed(SeriesDefaults.SEED_COUNTDOWN);
    if (countdown == null) {
      if (!seeded) {
        db.seriesDao().insert(SeriesDefaults.beyondLimit());
      }
    } else {
      backfillVars(
        db, countdown, SeriesDefaults.countdownVars(), SeriesDefaults.beyondLimit().endAtMillis);
    }
    Series notice = db.seriesDao().findBySeed(SeriesDefaults.SEED_NOTICE);
    if (notice == null) {
      if (!seeded) {
        db.seriesDao().insert(SeriesDefaults.birthdayNotice());
      }
    } else {
      backfillVars(db, notice, SeriesDefaults.noticeVars(), 0L);
    }
    Prefs.setSeriesDefaultsInstalled(app, true);
  }

  private static void backfillVars(AppDatabase db, Series series, String vars, long endAt) {
    boolean dirty = false;
    if ((series.vars == null || series.vars.isEmpty()) && vars != null && !vars.isEmpty()) {
      series.vars = vars;
      dirty = true;
    }
    if (endAt > 0L && series.endAtMillis <= 0L) {
      series.endAtMillis = endAt;
      dirty = true;
    }
    if (dirty) {
      db.seriesDao().update(series);
    }
  }

  /** Missed countdown days drop off so the desk never stacks 9-days, 8-days, 7-days. */
  private static void dropStaleCountdowns(Context app, AppDatabase db, long now) {
    List<Task> active = db.taskDao().getActiveSync();
    if (active == null) {
      return;
    }
    Calendar today = Calendar.getInstance();
    today.setTimeInMillis(now);
    String todayKey = DateUtils.dayKey(today);
    for (Task task : active) {
      if (!TaskTypes.COUNTDOWN.equals(task.type)) {
        continue;
      }
      Calendar post = Calendar.getInstance();
      post.setTimeInMillis(task.postAtMillis);
      if (DateUtils.dayKey(post).compareTo(todayKey) < 0) {
        AlarmScheduler.cancelTask(app, task.id);
        NotificationHelper.cancelForTask(app, task.id);
        db.taskDao().deleteById(task.id);
      }
    }
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
      String due = TaskStatus.dueStatus(task, now, warningLead);
      if (!due.equals(task.status) || task.snoozeUntilMillis != 0L) {
        db.taskDao().setSnooze(task.id, due, 0L);
        task.status = due;
        task.snoozeUntilMillis = 0L;
      }
      if (!due.equals(previous)) {
        if (!TaskStatus.needsYou(due) && TaskStatus.needsYou(previous)) {
          NotificationHelper.hush(app, task.id);
        }
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
        if (TaskStatus.captionIsSaved(task)) {
          AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.READY);
          task.status = TaskStatus.READY;
          break;
        }
        AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.DRAFTING);
        task.status = TaskStatus.DRAFTING;
        NotificationHelper.showDraft(app, task);
        break;
      case AlarmScheduler.PHASE_WARNING:
        if (TaskStatus.captionIsSaved(task)) {
          AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.READY);
          task.status = TaskStatus.READY;
          break;
        }
        AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.WARNING);
        task.status = TaskStatus.WARNING;
        NotificationHelper.showWarning(app, task);
        break;
      case AlarmScheduler.PHASE_MINUTE:
        NotificationHelper.showMinute(app, task);
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

  public static void markCaptionSaved(Context context, long taskId) {
    if (taskId <= 0L) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return;
    }
    long now = System.currentTimeMillis();
    task.captionSavedAt = now;
    if (!(TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now)) {
      task.status = TaskStatus.dueStatus(task, now, Prefs.warningLeadMs(app));
      task.snoozeUntilMillis = 0L;
    }
    db.taskDao().update(task);
    NotificationHelper.hush(app, taskId);
    AlarmScheduler.cancelTask(app, taskId);
    AlarmScheduler.scheduleTask(app, task);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.refresh(app);
  }

  public static void markPosted(Context context, long taskId) {
    Context app = context.getApplicationContext();
    NotificationHelper.hush(app, taskId);
    AppDatabase db = AppDatabase.get(app);
    db.taskDao().markPosted(taskId, System.currentTimeMillis());
    AlarmScheduler.cancelTask(app, taskId);
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
    String due = TaskStatus.dueStatus(task, now, Prefs.warningLeadMs(app));
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
    NotificationHelper.hush(app, taskId);
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null) {
      return;
    }
    long now = System.currentTimeMillis();
    long postAt = Math.max(newPostAt, now + 60_000L);
    task.postAtMillis = postAt;
    Calendar postCal = Calendar.getInstance();
    postCal.setTimeInMillis(postAt);
    Calendar draftCal = DateUtils.dayBeforeAt(postCal, Prefs.draftHour(app), 0);
    task.draftAtMillis = Math.max(now, draftCal.getTimeInMillis());
    task.timesLocked = true;
    task.postedAtMillis = 0L;
    task.snoozeUntilMillis = 0L;
    task.status = TaskStatus.dueStatus(task, now, Prefs.warningLeadMs(app));
    db.taskDao().update(task);
    AlarmScheduler.cancelTask(app, taskId);
    NotificationHelper.cancelForTask(app, taskId);
    AlarmScheduler.scheduleTask(app, task);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.refresh(app);
  }

  public static void snooze(Context context, long taskId, long untilMillis) {
    Context app = context.getApplicationContext();
    NotificationHelper.hush(app, taskId);
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
    String due = TaskStatus.dueStatus(task, now, Prefs.warningLeadMs(app));
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
    Calendar postCal = Calendar.getInstance();
    postCal.setTimeInMillis(postAt);
    Calendar draftCal = DateUtils.dayBeforeAt(postCal, Prefs.draftHour(app), 0);
    task.draftAtMillis = Math.max(now, draftCal.getTimeInMillis());
    task.timesLocked = true;
    task.status = TaskStatus.dueStatus(task, now, Prefs.warningLeadMs(app));
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
    NotificationHelper.hush(app, taskId);
    AlarmScheduler.cancelTask(app, taskId);
    AppDatabase.get(app).taskDao().deleteById(taskId);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.refresh(app);
  }

  public static void deleteDraft(Context context, long draftId) {
    Context app = context.getApplicationContext();
    AppDatabase.get(app).draftDao().deleteById(draftId);
  }

  public static void saveSeries(Context context, Series series) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    if (series.postHour < 0 || series.postHour > 23) {
      series.postHour = ScheduleTimes.MONTH_POST_HOUR;
    }
    if (series.postMinute < 0 || series.postMinute > 59) {
      series.postMinute = 0;
    }
    boolean countdownSeed = SeriesDefaults.SEED_COUNTDOWN.equals(series.seedKey)
      && Series.KIND_COUNTDOWN.equals(series.kind);
    boolean noticeSeed = SeriesDefaults.SEED_NOTICE.equals(series.seedKey)
      && Series.KIND_MONTHLY.equals(series.kind);
    if (!countdownSeed && !noticeSeed) {
      series.seedKey = "";
    }
    if (series.id > 0L) {
      db.seriesDao().update(series);
      cancelSeriesTasks(app, series.id);
    } else {
      series.id = db.seriesDao().insert(series);
    }
    bootstrap(app);
  }

  public static void deleteSeries(Context context, long seriesId) {
    Context app = context.getApplicationContext();
    cancelSeriesTasks(app, seriesId);
    AppDatabase.get(app).seriesDao().deleteById(seriesId);
    bootstrap(app);
  }

  private static void cancelSeriesTasks(Context app, long seriesId) {
    if (seriesId <= 0L) {
      return;
    }
    AppDatabase db = AppDatabase.get(app);
    List<Task> tasks = db.taskDao().getActiveForSeries(seriesId);
    if (tasks == null) {
      return;
    }
    for (Task task : tasks) {
      AlarmScheduler.cancelTask(app, task.id);
      NotificationHelper.cancelForTask(app, task.id);
      db.taskDao().deleteById(task.id);
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
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.refresh(app);
  }
}
