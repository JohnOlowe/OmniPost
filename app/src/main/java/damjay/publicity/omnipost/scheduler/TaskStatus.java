package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.List;

public final class TaskStatus {
  public static final String SCHEDULED = "SCHEDULED";
  public static final String DRAFTING = "DRAFTING";
  public static final String READY = "READY";
  public static final String WARNING = "WARNING";
  public static final String NAGGING = "NAGGING";
  public static final String SNOOZED = "SNOOZED";
  public static final String POSTED = "POSTED";

  private TaskStatus() {}

  public static String label(String status) {
    if (status == null) {
      return "";
    }
    switch (status) {
      case SCHEDULED:
        return "Upcoming";
      case DRAFTING:
        return "Write caption";
      case READY:
        return "Caption saved";
      case WARNING:
        return "Caption ready";
      case NAGGING:
        return "Post now";
      case SNOOZED:
        return "Snoozed";
      case POSTED:
        return "Posted";
      default:
        return status;
    }
  }

  public static boolean needsYou(String status) {
    return NAGGING.equals(status)
        || WARNING.equals(status)
        || SNOOZED.equals(status)
        || DRAFTING.equals(status);
  }

  public static String dueStatus(long draftAtMillis, long postAtMillis, long now) {
    return dueStatus(draftAtMillis, postAtMillis, now, ScheduleTimes.WARNING_LEAD_MS, false);
  }

  public static String dueStatus(
    long draftAtMillis, long postAtMillis, long now, long warningLeadMs) {
    return dueStatus(draftAtMillis, postAtMillis, now, warningLeadMs, false);
  }

  public static String dueStatus(Task task, long now, long warningLeadMs) {
    if (task == null) {
      return SCHEDULED;
    }
    return dueStatus(
      task.draftAtMillis,
      task.postAtMillis,
      now,
      warningLeadMs,
      captionIsSaved(task));
  }

  public static String dueStatus(
    long draftAtMillis,
    long postAtMillis,
    long now,
    long warningLeadMs,
    boolean captionSaved) {
    if (postAtMillis <= now) {
      return NAGGING;
    }
    if (captionSaved) {
      return READY;
    }
    long lead = warningLeadMs > 0L ? warningLeadMs : ScheduleTimes.WARNING_LEAD_MS;
    if (postAtMillis - lead <= now) {
      return WARNING;
    }
    if (draftAtMillis <= now) {
      return DRAFTING;
    }
    return SCHEDULED;
  }

  public static boolean captionIsSaved(Task task) {
    return task != null && task.captionSavedAt > 0L;
  }

  /**
   * When this task's next AlarmClock actually fires. Snooze wins while quiet;
   * otherwise the earliest of draft / caption-ready / post still in the future.
   * Overdue tasks return their post time so they sort first.
   */
  public static long nextRingMillis(Task task, long now, long warningLeadMs) {
    if (task == null || POSTED.equals(task.status)) {
      return Long.MAX_VALUE;
    }
    if (SNOOZED.equals(task.status) && task.snoozeUntilMillis > now) {
      long minuteAt = task.snoozeUntilMillis - ScheduleTimes.MINUTE_LEAD_MS;
      if (minuteAt > now) {
        return minuteAt;
      }
      return task.snoozeUntilMillis;
    }
    long lead = warningLeadMs > 0L ? warningLeadMs : ScheduleTimes.WARNING_LEAD_MS;
    long warningAt = task.postAtMillis - lead;
    long minuteAt = task.postAtMillis - ScheduleTimes.MINUTE_LEAD_MS;
    long next = Long.MAX_VALUE;
    boolean saved = captionIsSaved(task);
    if (!saved && task.draftAtMillis > now) {
      next = Math.min(next, task.draftAtMillis);
    }
    if (!saved && warningAt > now) {
      next = Math.min(next, warningAt);
    }
    if (minuteAt > now && minuteAt < task.postAtMillis) {
      next = Math.min(next, minuteAt);
    }
    if (task.postAtMillis > now) {
      next = Math.min(next, task.postAtMillis);
    }
    if (next != Long.MAX_VALUE) {
      return next;
    }
    return task.postAtMillis;
  }

  public static Task nextToRing(List<Task> tasks, long now, long warningLeadMs) {
    Task best = null;
    long bestAt = Long.MAX_VALUE;
    if (tasks == null) {
      return null;
    }
    for (Task task : tasks) {
      if (task == null || POSTED.equals(task.status)) {
        continue;
      }
      long at = nextRingMillis(task, now, warningLeadMs);
      if (best == null
          || at < bestAt
          || (at == bestAt && task.postAtMillis < best.postAtMillis)
          || (at == bestAt && task.postAtMillis == best.postAtMillis && task.id < best.id)) {
        best = task;
        bestAt = at;
      }
    }
    return best;
  }
}
