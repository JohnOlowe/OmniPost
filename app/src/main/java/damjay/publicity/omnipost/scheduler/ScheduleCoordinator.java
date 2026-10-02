package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.AlumniSend;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScheduleCoordinator {
  private static final AtomicBoolean BOOTSTRAP_BUSY = new AtomicBoolean();
  private static volatile long lastBootstrapAt;

  private ScheduleCoordinator() {}

  public static void bootstrap(Context context) {
    long started = System.currentTimeMillis();
    if (started - lastBootstrapAt < 2_000L && lastBootstrapAt > 0L) {
      return;
    }
    if (!BOOTSTRAP_BUSY.compareAndSet(false, true)) {
      return;
    }
    try {
      bootstrapNow(context);
      lastBootstrapAt = System.currentTimeMillis();
    } finally {
      BOOTSTRAP_BUSY.set(false);
    }
  }

  private static void bootstrapNow(Context context) {
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
    captureLiveCaptions(db);
    dropStaleCountdowns(app, db, now);
    ensureSeriesDefaults(app, db);
    ensureAlumni(app, db);

    List<Member> members = db.memberDao().getAllSync();
    AlumniMatch.rememberRoster(members);
    List<Series> series = enabledSeries(db.seriesDao().getAllSync());
    List<Task> generated = RoutineGenerator.generate(
      now, TimeZone.getDefault(), members, series, Prefs.draftHour(app), Prefs.draftLeadDays(app));
    Map<String, Task> byKey = indexByKey(db.taskDao().getAllSync());
    db.runInTransaction(() -> {
      for (Task candidate : generated) {
        if (candidate == null || candidate.occurrenceKey == null) {
          continue;
        }
        Task existing = byKey.get(candidate.occurrenceKey);
        if (existing == null) {
          long id = db.taskDao().insert(candidate);
          candidate.id = id;
          byKey.put(candidate.occurrenceKey, candidate);
          continue;
        }
        if (TaskStatus.POSTED.equals(existing.status)) {
          continue;
        }
        if (!existing.titleLocked) {
          existing.title = candidate.title;
        }
        existing.skipCaption = candidate.skipCaption;
        existing.description = candidate.description;
        existing.type = candidate.type;
        existing.seriesId = candidate.seriesId;
        if (!existing.timesLocked) {
          existing.draftAtMillis = candidate.draftAtMillis;
          existing.postAtMillis = candidate.postAtMillis;
        }
        db.taskDao().update(existing);
      }
    });

    applyDueAndSchedule(app);
    AlarmScheduler.scheduleWatchdog(app);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.paint(app);
  }

  private static Map<String, Task> indexByKey(List<Task> tasks) {
    Map<String, Task> out = new HashMap<>();
    if (tasks == null) {
      return out;
    }
    for (Task task : tasks) {
      if (task != null && task.occurrenceKey != null && !task.occurrenceKey.isEmpty()) {
        out.put(task.occurrenceKey, task);
      }
    }
    return out;
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

  private static void ensureAlumni(Context app, AppDatabase db) {
    if (Prefs.alumniRosterInstalled(app)) {
      return;
    }
    boolean skip = Prefs.alumniSkipCaption(app);
    List<Member> roster = AlumniRoster.members();
    for (Member member : roster) {
      if (member == null) {
        continue;
      }
      member.skipCaption = skip;
      db.memberDao().insert(member);
    }
    Prefs.setAlumniRosterInstalled(app, true);
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
        rememberTaskDraft(db, task);
        AlarmScheduler.cancelTask(app, task.id);
        NotificationHelper.cancelForTask(app, task.id);
        db.taskDao().deleteById(task.id);
      }
    }
  }

  private static void captureLiveCaptions(AppDatabase db) {
    List<Task> active = db.taskDao().getActiveSync();
    if (active == null) {
      return;
    }
    for (Task task : active) {
      rememberTaskDraft(db, task);
    }
  }

  private static void rememberTaskDraft(AppDatabase db, Task task) {
    if (db == null || task == null || !CaptionTemplates.isLive(task.type)) {
      return;
    }
    Draft draft = db.draftDao().findByTaskId(task.id);
    if (draft == null || CaptionTemplates.isCanned(draft.variantA)) {
      return;
    }
    Series series = CaptionTemplates.seriesOf(db, task);
    if (series == null || !CaptionTemplates.isCanned(series.caption)) {
      return;
    }
    series.caption = draft.variantA;
    db.seriesDao().update(series);
  }

  private static void inheritCaptionSaved(AppDatabase db, Task candidate) {
    if (db == null || candidate == null || candidate.seriesId <= 0L) {
      return;
    }
    if (!TaskTypes.oneCard(candidate.type) || candidate.captionSavedAt > 0L) {
      return;
    }
    List<Task> siblings = db.taskDao().getActiveForSeries(candidate.seriesId);
    if (siblings == null) {
      return;
    }
    for (Task sibling : siblings) {
      if (sibling != null && sibling.captionSavedAt > 0L) {
        candidate.captionSavedAt = sibling.captionSavedAt;
        return;
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
      if (phase == AlarmScheduler.PHASE_MINUTE) {
        NotificationHelper.showMinute(app, task);
      }
      return;
    }
    long now = System.currentTimeMillis();
    switch (phase) {
      case AlarmScheduler.PHASE_DRAFT:
        if (!TaskStatus.captionWorkPending(task)) {
          AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.READY);
          task.status = TaskStatus.READY;
          break;
        }
        AppDatabase.get(app).taskDao().updateStatus(taskId, TaskStatus.DRAFTING);
        task.status = TaskStatus.DRAFTING;
        NotificationHelper.showDraft(app, task);
        break;
      case AlarmScheduler.PHASE_WARNING:
        if (!TaskStatus.captionWorkPending(task)) {
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

  /** Keep the user's series template. Never write the canned seed back over it. */
  public static void rememberSeriesCaption(Context context, Task task, String template) {
    if (context == null || task == null || !CaptionTemplates.isLive(task.type)) {
      return;
    }
    if (template == null || CaptionTemplates.isCanned(template)) {
      return;
    }
    AppDatabase db = AppDatabase.get(context.getApplicationContext());
    Series series = CaptionTemplates.seriesOf(db, task);
    if (series == null) {
      return;
    }
    if (!template.equals(series.caption)) {
      series.caption = template;
      db.seriesDao().update(series);
    }
    if (task.seriesId != series.id && series.id > 0L) {
      task.seriesId = series.id;
      db.taskDao().update(task);
    }
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
    if (task.seriesId > 0L && TaskTypes.oneCard(task.type)) {
      List<Task> siblings = db.taskDao().getActiveForSeries(task.seriesId);
      if (siblings != null) {
        long warningLead = Prefs.warningLeadMs(app);
        for (Task sibling : siblings) {
          if (sibling == null || sibling.id == task.id) {
            continue;
          }
          sibling.captionSavedAt = now;
          if (!(TaskStatus.SNOOZED.equals(sibling.status) && sibling.snoozeUntilMillis > now)) {
            sibling.status = TaskStatus.dueStatus(sibling, now, warningLead);
            sibling.snoozeUntilMillis = 0L;
          }
          db.taskDao().update(sibling);
          AlarmScheduler.cancelTask(app, sibling.id);
          AlarmScheduler.scheduleTask(app, sibling);
        }
      }
    }
    NotificationHelper.hush(app, taskId);
    AlarmScheduler.cancelTask(app, taskId);
    AlarmScheduler.scheduleTask(app, task);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.paint(app);
  }

  public static void markPosted(Context context, long taskId) {
    Context app = context.getApplicationContext();
    NotificationHelper.hush(app, taskId);
    AppDatabase db = AppDatabase.get(app);
    db.taskDao().markPosted(taskId, System.currentTimeMillis());
    AlarmScheduler.cancelTask(app, taskId);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.paint(app);
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
    NagForegroundService.paint(app);
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
    Calendar draftCal = DateUtils.draftAt(
      postCal, Prefs.draftLeadDays(app), Prefs.draftHour(app), 0);
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
    NagForegroundService.paint(app);
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
    NagForegroundService.paint(app);
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
    addCustom(context, title, postAt, flexible, false);
  }

  public static void addCustom(
    Context context, String title, long postAt, boolean flexible, boolean skipCaption) {
    Context app = context.getApplicationContext();
    long now = System.currentTimeMillis();
    int warn = Prefs.warningMinutes(app);
    Task task = new Task();
    task.type = flexible ? TaskTypes.FLEXIBLE : TaskTypes.ONE_OFF;
    task.title = title;
    task.skipCaption = skipCaption;
    task.description = skipCaption
      ? "No caption — open WhatsApp and forward."
      : flexible
        ? "Flexible — you pick the next time. Write the caption. Ready "
          + warn
          + " minutes before."
        : "One-off. Write the caption. Ready " + warn + " minutes before.";
    task.postAtMillis = postAt;
    Calendar postCal = Calendar.getInstance();
    postCal.setTimeInMillis(postAt);
    Calendar draftCal = DateUtils.draftAt(
      postCal, Prefs.draftLeadDays(app), Prefs.draftHour(app), 0);
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
      NagForegroundService.paint(app);
    }
  }

  public static void rename(Context context, long taskId, String title) {
    if (context == null || taskId <= 0L) {
      return;
    }
    String name = title == null ? "" : title.trim();
    if (name.isEmpty()) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null) {
      return;
    }
    task.title = name;
    task.titleLocked = true;
    db.taskDao().update(task);
    AlarmScheduler.rememberCue(task.id, task.title, task.postAtMillis);
    NagForegroundService.paint(app);
  }

  public static void setSkipCaption(Context context, long taskId, boolean skip) {
    if (context == null || taskId <= 0L) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Task task = db.taskDao().getById(taskId);
    if (task == null || TaskStatus.POSTED.equals(task.status)) {
      return;
    }
    applySkipText(db, task, skip);
    long now = System.currentTimeMillis();
    if (!(TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now)) {
      task.status = TaskStatus.dueStatus(task, now, Prefs.warningLeadMs(app));
      task.snoozeUntilMillis = 0L;
    }
    db.taskDao().update(task);
    if (task.memberId > 0L
        && (TaskTypes.BIRTHDAY.equals(task.type) || TaskTypes.ALUMNI_BIRTHDAY.equals(task.type))) {
      Member member = db.memberDao().getById(task.memberId);
      if (member != null) {
        member.skipCaption = skip;
        db.memberDao().update(member);
      }
    }
    AlarmScheduler.cancelTask(app, taskId);
    AlarmScheduler.scheduleTask(app, task);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.paint(app);
  }

  public static void setAlumniSkipCaption(Context context, boolean skip) {
    if (context == null) {
      return;
    }
    Context app = context.getApplicationContext();
    Prefs.setAlumniSkipCaption(app, skip);
    AppDatabase db = AppDatabase.get(app);
    List<Member> members = db.memberDao().getAllSync();
    if (members == null) {
      return;
    }
    long now = System.currentTimeMillis();
    long warningLead = Prefs.warningLeadMs(app);
    for (Member member : members) {
      if (!Member.isAlumni(member)) {
        continue;
      }
      member.skipCaption = skip;
      db.memberDao().update(member);
      List<Task> tasks = db.taskDao().getActiveForMember(member.id);
      if (tasks == null) {
        continue;
      }
      for (Task task : tasks) {
        if (task == null || TaskStatus.POSTED.equals(task.status)) {
          continue;
        }
        if (!TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
          continue;
        }
        applySkipText(db, task, skip);
        if (!(TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > now)) {
          task.status = TaskStatus.dueStatus(task, now, warningLead);
          task.snoozeUntilMillis = 0L;
        }
        db.taskDao().update(task);
        AlarmScheduler.cancelTask(app, task.id);
        AlarmScheduler.scheduleTask(app, task);
      }
    }
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.paint(app);
  }

  public static void deleteCustom(Context context, long taskId) {
    Context app = context.getApplicationContext();
    NotificationHelper.hush(app, taskId);
    AlarmScheduler.cancelTask(app, taskId);
    AppDatabase.get(app).taskDao().deleteById(taskId);
    AlarmScheduler.scheduleHeartbeat(app);
    NagForegroundService.paint(app);
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

  public static final class AlumniImport {
    public int added;
    public int filled;
    public int skipped;
    public int pending;
  }

  public static AlumniImport importAlumniSheet(Context context, String csv) {
    AlumniImport stats = new AlumniImport();
    if (context == null) {
      return stats;
    }
    AlumniSheet.Result parsed = AlumniSheet.parse(csv);
    stats.skipped = parsed.skipped;
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    List<AlumniSheet.Row> leftover = AlumniPending.load(app);
    for (AlumniSheet.Row row : parsed.rows) {
      if (row == null) {
        continue;
      }
      String name = row.displayName();
      if (name.isEmpty()) {
        stats.skipped++;
        continue;
      }
      Member prior = findAlumni(db, row);
      if (prior != null) {
        fillAlumni(prior, row);
        db.memberDao().update(prior);
        stats.filled++;
        continue;
      }
      if (pendingHas(leftover, row)) {
        continue;
      }
      leftover.add(row);
      stats.pending++;
    }
    AlumniPending.save(app, leftover);
    bootstrap(app);
    return stats;
  }

  public static void pairAlumni(Context context, AlumniSheet.Row row, long rosterId) {
    if (context == null || row == null || rosterId <= 0L) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Member prior = db.memberDao().getById(rosterId);
    if (prior == null || !Member.isAlumni(prior)) {
      return;
    }
    fillAlumni(prior, row);
    db.memberDao().update(prior);
    dropPending(app, row);
    bootstrap(app);
  }

  public static void addAlumniFromSheet(Context context, AlumniSheet.Row row) {
    if (context == null || row == null || row.displayName().isEmpty()) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Member member = new Member();
    member.kind = Member.KIND_ALUMNI;
    member.skipCaption = Prefs.alumniSkipCaption(app);
    member.name = row.displayName();
    fillAlumni(member, row);
    if (row.birthMonth > 0 && member.birthMonth <= 0) {
      member.birthMonth = row.birthMonth;
      member.birthDay = row.birthDay;
    }
    db.memberDao().insert(member);
    dropPending(app, row);
    bootstrap(app);
  }

  public static void dropPendingRow(Context context, AlumniSheet.Row row) {
    if (context == null) {
      return;
    }
    dropPending(context.getApplicationContext(), row);
  }

  public static void setAlumniPastor(Context context, long memberId, boolean pastor) {
    if (context == null || memberId <= 0L) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Member member = db.memberDao().getById(memberId);
    if (member == null) {
      return;
    }
    member.desk = pastor ? Member.DESK_PASTOR : "";
    db.memberDao().update(member);
    bootstrap(app);
  }

  private static Member findAlumni(AppDatabase db, AlumniSheet.Row row) {
    String name = row.displayName();
    Member prior = db.memberDao().findByKindAndNameIgnoreCase(Member.KIND_ALUMNI, name);
    if (prior == null && !row.firstName.isEmpty() && !row.lastName.isEmpty()) {
      prior = db.memberDao().findByKindAndNameIgnoreCase(
        Member.KIND_ALUMNI, row.lastName + " " + row.firstName);
    }
    return prior;
  }

  private static void dropPending(Context app, AlumniSheet.Row row) {
    List<AlumniSheet.Row> leftover = AlumniPending.load(app);
    List<AlumniSheet.Row> keep = new java.util.ArrayList<>();
    for (AlumniSheet.Row item : leftover) {
      if (item == null || pendingSame(row, item)) {
        continue;
      }
      keep.add(item);
    }
    AlumniPending.save(app, keep);
  }

  private static boolean pendingHas(List<AlumniSheet.Row> leftover, AlumniSheet.Row row) {
    if (leftover == null) {
      return false;
    }
    for (AlumniSheet.Row item : leftover) {
      if (pendingSame(row, item)) {
        return true;
      }
    }
    return false;
  }

  private static boolean pendingSame(AlumniSheet.Row a, AlumniSheet.Row b) {
    if (a == null || b == null) {
      return false;
    }
    String name = a.displayName().toLowerCase(Locale.US);
    if (name.isEmpty() || !name.equals(b.displayName().toLowerCase(Locale.US))) {
      return false;
    }
    String phone = AlumniDesk.whatsAppDigits(a.phone);
    String other = AlumniDesk.whatsAppDigits(b.phone);
    return phone.isEmpty() || other.isEmpty() || phone.equals(other);
  }

  private static void fillAlumni(Member member, AlumniSheet.Row row) {
    if (member == null || row == null) {
      return;
    }
    if (!row.firstName.isEmpty()) {
      member.firstName = row.firstName;
    }
    if (!row.lastName.isEmpty()) {
      member.lastName = row.lastName;
    }
    if (AlumniDesk.hasPhone(row.phone)) {
      member.phone = AlumniDesk.storePhone(row.phone);
    }
    if (!row.gender.isEmpty()) {
      member.gender = row.gender;
    }
    if (!row.email.isEmpty()) {
      member.email = row.email;
    }
    if (!row.position.isEmpty()) {
      member.positionHeld = row.position;
    }
    if (!row.gradSet.isEmpty()) {
      member.gradSet = row.gradSet;
    }
    if (row.birthMonth > 0 && row.birthDay > 0 && member.birthMonth <= 0) {
      member.birthMonth = row.birthMonth;
      member.birthDay = row.birthDay;
    }
  }

  public static void markAlumniIntroduced(Context context, long memberId) {
    if (context == null || memberId <= 0L) {
      return;
    }
    AppDatabase db = AppDatabase.get(context.getApplicationContext());
    Member member = db.memberDao().getById(memberId);
    if (member == null || member.introduced) {
      return;
    }
    member.introduced = true;
    db.memberDao().update(member);
  }

  public static void markAlumniSent(Context context, long memberId, String kind) {
    if (context == null || memberId <= 0L) {
      return;
    }
    String value = AlumniSend.DETAILS.equals(kind) ? AlumniSend.DETAILS : AlumniSend.HNM;
    Context app = context.getApplicationContext();
    AlumniSend send = new AlumniSend();
    send.memberId = memberId;
    send.yearMonth = AlumniMonth.yearMonth(Calendar.getInstance());
    send.kind = value;
    send.sentAt = System.currentTimeMillis();
    AppDatabase.get(app).alumniSendDao().upsert(send);
    markAlumniIntroduced(app, memberId);
  }

  public static void unmarkAlumniSent(Context context, long memberId, String kind) {
    if (context == null || memberId <= 0L) {
      return;
    }
    String value = AlumniSend.DETAILS.equals(kind) ? AlumniSend.DETAILS : AlumniSend.HNM;
    AppDatabase.get(context.getApplicationContext())
      .alumniSendDao()
      .delete(memberId, AlumniMonth.yearMonth(Calendar.getInstance()), value);
  }

  public static void setAlumniPhoto(Context context, long memberId, String status) {
    if (context == null || memberId <= 0L) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Member member = db.memberDao().getById(memberId);
    if (member == null) {
      return;
    }
    String value = status == null ? "" : status;
    if (!AlumniDesk.PHOTO_GOT.equals(value) && !AlumniDesk.PHOTO_NONE.equals(value)) {
      value = "";
    }
    member.photoStatus = value;
    db.memberDao().update(member);
    List<Task> tasks = db.taskDao().getActiveForMember(memberId);
    if (tasks != null) {
      for (Task task : tasks) {
        if (task == null || TaskStatus.POSTED.equals(task.status)) {
          continue;
        }
        if (TaskTypes.ALUMNI_PHOTO.equals(task.type) && AlumniDesk.photoSettled(member)) {
          markPosted(app, task.id);
          continue;
        }
        if (TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
          task.description = AlumniDesk.birthdayBrief(member);
          db.taskDao().update(task);
        }
      }
    }
    bootstrap(app);
  }

  private static void applySkipText(AppDatabase db, Task task, boolean skip) {
    if (task == null) {
      return;
    }
    task.skipCaption = skip;
    if (!skip) {
      if (task.description != null && task.description.startsWith("No caption")) {
        task.description = "Write the caption. OmniPost will nag you when it is time.";
      }
      return;
    }
    if (task.memberId > 0L && db != null) {
      Member member = db.memberDao().getById(task.memberId);
      if (TaskTypes.ALUMNI_PHOTO.equals(task.type)) {
        task.description = AlumniDesk.photoBrief(member);
        return;
      }
      if (TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
        task.description = AlumniDesk.birthdayBrief(member);
        return;
      }
    }
    if (TaskTypes.ALUMNI_MONTH.equals(task.type) || TaskTypes.ALUMNI_WAVE.equals(task.type)) {
      return;
    }
    task.description = "No caption — open WhatsApp and forward.";
  }

  /** Update birthday / photo card titles after a name fix without rebuilding every alarm. */
  public static void retitleMember(Context context, Member member) {
    if (context == null || member == null || member.id <= 0L) {
      return;
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    List<Task> tasks = db.taskDao().getActiveForMember(member.id);
    if (tasks == null) {
      return;
    }
    String birthday = member.name + "'s Birthday";
    String photo = "Picture from " + member.name;
    for (Task task : tasks) {
      if (task == null || task.titleLocked) {
        continue;
      }
      boolean dirty = false;
      if (TaskTypes.BIRTHDAY.equals(task.type) || TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)) {
        if (!birthday.equals(task.title)) {
          task.title = birthday;
          dirty = true;
        }
        String brief = Member.isAlumni(member)
          ? AlumniDesk.birthdayBrief(member)
          : task.description;
        if (Member.isAlumni(member) && brief != null && !brief.equals(task.description)) {
          task.description = brief;
          dirty = true;
        }
      } else if (TaskTypes.ALUMNI_PHOTO.equals(task.type)) {
        if (!photo.equals(task.title)) {
          task.title = photo;
          dirty = true;
        }
        String brief = AlumniDesk.photoBrief(member);
        if (brief != null && !brief.equals(task.description)) {
          task.description = brief;
          dirty = true;
        }
      }
      if (dirty) {
        db.taskDao().update(task);
      }
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
    NagForegroundService.paint(app);
  }
}
