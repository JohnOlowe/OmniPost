package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskSections {
  public static final String SUB_NOW = "Flyer missing, 30-minute window, or still not posted.";
  public static final String SUB_WEEKLY = "Does not change — next of each weekly post.";
  public static final String SUB_MONTHLY = "Does not change — next fasting / new month.";
  public static final String SUB_FLEXIBLE = "Birthdays and posts you time yourself.";
  public static final String SUB_ONCE = "Happens once.";

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
    List<Section> out = new ArrayList<>();
    if (source == null || source.isEmpty()) {
      return out;
    }
    if (postedMode) {
      add(out, TaskTypes.SECTION_WEEKLY, SUB_WEEKLY, filterSection(source, TaskTypes.SECTION_WEEKLY));
      add(out, TaskTypes.SECTION_MONTHLY, SUB_MONTHLY, filterSection(source, TaskTypes.SECTION_MONTHLY));
      add(out, TaskTypes.SECTION_FLEXIBLE, SUB_FLEXIBLE, filterSection(source, TaskTypes.SECTION_FLEXIBLE));
      add(out, TaskTypes.SECTION_ONCE, SUB_ONCE, filterSection(source, TaskTypes.SECTION_ONCE));
      return out;
    }
    List<Task> now = new ArrayList<>();
    List<Task> rest = new ArrayList<>();
    for (Task task : source) {
      if (TaskStatus.needsYou(task.status)) {
        now.add(task);
      } else {
        rest.add(task);
      }
    }
    sortByPost(now);
    add(out, TaskTypes.SECTION_NOW, SUB_NOW, now);
    add(out, TaskTypes.SECTION_WEEKLY, SUB_WEEKLY, nextOfEach(rest, TaskTypes.SECTION_WEEKLY));
    add(out, TaskTypes.SECTION_MONTHLY, SUB_MONTHLY, nextOfEach(rest, TaskTypes.SECTION_MONTHLY));
    add(out, TaskTypes.SECTION_FLEXIBLE, SUB_FLEXIBLE, filterSection(rest, TaskTypes.SECTION_FLEXIBLE));
    add(out, TaskTypes.SECTION_ONCE, SUB_ONCE, filterSection(rest, TaskTypes.SECTION_ONCE));
    return out;
  }

  private static void add(List<Section> out, String title, String subtitle, List<Task> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    out.add(new Section(title, subtitle, tasks));
  }

  private static List<Task> filterSection(List<Task> source, String section) {
    List<Task> out = new ArrayList<>();
    for (Task task : source) {
      if (section.equals(TaskTypes.section(task.type))) {
        out.add(task);
      }
    }
    sortByPost(out);
    return out;
  }

  private static List<Task> nextOfEach(List<Task> source, String section) {
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
    sortByPost(out);
    return out;
  }

  private static void sortByPost(List<Task> tasks) {
    Collections.sort(tasks, Comparator.comparingLong(task -> task.postAtMillis));
  }
}
