package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskSections {
  public static final String SUB_NOW = "Write the caption, post, or say when to nag again.";
  public static final String SUB_NEXT = "Coming up in the next two days.";
  public static final String SUB_WEEKLY = "Does not change — next of each weekly post.";
  public static final String SUB_MONTHLY = "Does not change — next fasting / new month / birthday notice.";
  public static final String SUB_CAMPAIGN = "One card. The day count updates itself.";
  public static final String SUB_FLEXIBLE = "Birthdays and posts you time yourself.";
  public static final String SUB_ONCE = "Happens once.";

  public static final long NEXT_WINDOW_MS = 48L * 60L * 60L * 1000L;

  public static final class Section {
    public final String title;
    public final String subtitle;
    public final List<Task> tasks;

    public Section(String title, String subtitle, List<Task> tasks) {
      this.title = title;
      this.subtitle = subtitle;
      this.tasks = tasks;
    }
  }

  private TaskSections() {}

  public static List<Section> group(List<Task> source, boolean postedMode) {
    return group(source, postedMode, System.currentTimeMillis());
  }

  public static List<Section> group(List<Task> source, boolean postedMode, long now) {
    return group(source, postedMode, now, ScheduleTimes.WARNING_LEAD_MS);
  }

  public static List<Section> group(List<Task> source, boolean postedMode, long now, long warningLeadMs) {
    List<Section> out = new ArrayList<>();
    if (source == null || source.isEmpty()) {
      return out;
    }
    if (postedMode) {
      add(out, TaskTypes.SECTION_WEEKLY, SUB_WEEKLY, filterSection(source, TaskTypes.SECTION_WEEKLY));
      add(out, TaskTypes.SECTION_MONTHLY, SUB_MONTHLY, filterSection(source, TaskTypes.SECTION_MONTHLY));
      add(out, TaskTypes.SECTION_CAMPAIGN, SUB_CAMPAIGN, filterSection(source, TaskTypes.SECTION_CAMPAIGN));
      add(out, TaskTypes.SECTION_FLEXIBLE, SUB_FLEXIBLE, filterSection(source, TaskTypes.SECTION_FLEXIBLE));
      add(out, TaskTypes.SECTION_ONCE, SUB_ONCE, filterSection(source, TaskTypes.SECTION_ONCE));
      return out;
    }
    List<Task> nowList = new ArrayList<>();
    List<Task> soon = new ArrayList<>();
    List<Task> rest = new ArrayList<>();
    for (Task task : source) {
      if (TaskStatus.needsYou(task.status)) {
        nowList.add(task);
      } else if (isSoon(task, now)) {
        soon.add(task);
      } else {
        rest.add(task);
      }
    }
    keepOneCard(nowList, soon, rest, TaskTypes.COUNTDOWN);
    keepOneCard(nowList, soon, rest, TaskTypes.BIRTHDAY_NOTICE);
    sortByRing(nowList, now, warningLeadMs);
    add(out, TaskTypes.SECTION_NOW, SUB_NOW, nowList);
    add(out, TaskTypes.SECTION_NEXT, SUB_NEXT, collapseUpcoming(soon, now, warningLeadMs));
    add(out, TaskTypes.SECTION_WEEKLY, SUB_WEEKLY, nextOfEach(rest, TaskTypes.SECTION_WEEKLY, now, warningLeadMs));
    add(out, TaskTypes.SECTION_MONTHLY, SUB_MONTHLY, nextOfEach(rest, TaskTypes.SECTION_MONTHLY, now, warningLeadMs));
    add(out, TaskTypes.SECTION_CAMPAIGN, SUB_CAMPAIGN, nextOfEach(rest, TaskTypes.SECTION_CAMPAIGN, now, warningLeadMs));
    add(out, TaskTypes.SECTION_FLEXIBLE, SUB_FLEXIBLE, filterSection(rest, TaskTypes.SECTION_FLEXIBLE, now, warningLeadMs));
    add(out, TaskTypes.SECTION_ONCE, SUB_ONCE, filterSection(rest, TaskTypes.SECTION_ONCE, now, warningLeadMs));
    return out;
  }

  private static boolean isSoon(Task task, long now) {
    return TaskStatus.SCHEDULED.equals(task.status)
        && task.postAtMillis > now
        && task.postAtMillis <= now + NEXT_WINDOW_MS;
  }

  /**
   * Countdown and birthday NOTICE never cluster: keep the one that needs you
   * (latest post, i.e. today's), otherwise the soonest upcoming.
   */
  private static void keepOneCard(
    List<Task> nowList, List<Task> soon, List<Task> rest, String type) {
    Task pick = latest(nowList, type);
    if (pick == null) {
      pick = earliest(soon, type);
    }
    if (pick == null) {
      pick = earliest(rest, type);
    }
    if (pick == null) {
      return;
    }
    retainOnly(nowList, type, pick);
    retainOnly(soon, type, pick);
    retainOnly(rest, type, pick);
  }

  private static Task latest(List<Task> source, String type) {
    Task pick = null;
    for (Task task : source) {
      if (!type.equals(task.type)) {
        continue;
      }
      if (pick == null || task.postAtMillis >= pick.postAtMillis) {
        pick = task;
      }
    }
    return pick;
  }

  private static Task earliest(List<Task> source, String type) {
    Task pick = null;
    for (Task task : source) {
      if (!type.equals(task.type)) {
        continue;
      }
      if (pick == null || task.postAtMillis < pick.postAtMillis) {
        pick = task;
      }
    }
    return pick;
  }

  private static void retainOnly(List<Task> source, String type, Task keep) {
    for (int i = source.size() - 1; i >= 0; i--) {
      Task task = source.get(i);
      if (type.equals(task.type) && task != keep && task.id != keep.id) {
        source.remove(i);
      }
    }
  }

  private static List<Task> collapseUpcoming(List<Task> source, long now, long warningLeadMs) {
    Map<String, Task> series = new LinkedHashMap<>();
    List<Task> other = new ArrayList<>();
    for (Task task : source) {
      if (TaskTypes.isSeries(task.type)) {
        Task prev = series.get(task.type);
        if (prev == null || task.postAtMillis < prev.postAtMillis) {
          series.put(task.type, task);
        }
      } else {
        other.add(task);
      }
    }
    List<Task> out = new ArrayList<>(series.values());
    out.addAll(other);
    sortByRing(out, now, warningLeadMs);
    return out;
  }

  private static void add(List<Section> out, String title, String subtitle, List<Task> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    out.add(new Section(title, subtitle, tasks));
  }

  private static List<Task> filterSection(List<Task> source, String section) {
    return filterSection(source, section, 0L, 0L);
  }

  private static List<Task> filterSection(
    List<Task> source, String section, long now, long warningLeadMs) {
    List<Task> out = new ArrayList<>();
    for (Task task : source) {
      if (section.equals(TaskTypes.section(task.type))) {
        out.add(task);
      }
    }
    if (warningLeadMs > 0L) {
      sortByRing(out, now, warningLeadMs);
    } else {
      sortByPost(out);
    }
    return out;
  }

  private static List<Task> nextOfEach(
    List<Task> source, String section, long now, long warningLeadMs) {
    Map<String, Task> earliest = new LinkedHashMap<>();
    for (Task task : source) {
      if (!section.equals(TaskTypes.section(task.type))) {
        continue;
      }
      Task prev = earliest.get(task.type);
      if (prev == null || task.postAtMillis < prev.postAtMillis) {
        earliest.put(task.type, task);
      }
    }
    List<Task> out = new ArrayList<>(earliest.values());
    sortByRing(out, now, warningLeadMs);
    return out;
  }

  private static void sortByPost(List<Task> tasks) {
    Collections.sort(tasks, Comparator.comparingLong(task -> task.postAtMillis));
  }

  private static void sortByRing(List<Task> tasks, long now, long warningLeadMs) {
    Collections.sort(tasks, (a, b) -> {
      long ra = TaskStatus.nextRingMillis(a, now, warningLeadMs);
      long rb = TaskStatus.nextRingMillis(b, now, warningLeadMs);
      if (ra != rb) {
        return Long.compare(ra, rb);
      }
      if (a.postAtMillis != b.postAtMillis) {
        return Long.compare(a.postAtMillis, b.postAtMillis);
      }
      return Long.compare(a.id, b.id);
    });
  }
}
