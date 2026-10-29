package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Pause selected programs or single posts until a morning. Occurrences whose
 * post time falls before that morning are skipped, not stacked as overdue nags.
 */
public final class HoldRules {
  public static final String[] BUILTIN_TYPES = {
    TaskTypes.SUNDAY_SERVICE,
    TaskTypes.WEDNESDAY_BIBLE_STUDY,
    TaskTypes.FRIDAY_PRAYER,
    TaskTypes.NEW_MONTH_FASTING,
    TaskTypes.FASTING_DAY,
    TaskTypes.HAPPY_NEW_MONTH
  };

  public static final long DEFAULT_HOLD_DAYS = 28L;

  public static final class Program {
    public final String key;
    public final String label;

    public Program(String key, String label) {
      this.key = key;
      this.label = label;
    }
  }

  private HoldRules() {}

  public static String typeKey(String type) {
    return type == null ? "" : type;
  }

  public static String seriesKey(long seriesId) {
    return "s:" + seriesId;
  }

  public static String taskKey(long taskId) {
    return "t:" + taskId;
  }

  public static boolean isSeriesKey(String key) {
    return key != null && key.startsWith("s:");
  }

  public static boolean isTaskKey(String key) {
    return key != null && key.startsWith("t:");
  }

  public static Map<String, Long> parse(String json) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
      return out;
    }
    try {
      JSONObject obj = new JSONObject(json);
      Iterator<String> keys = obj.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (key == null || key.isEmpty()) {
          continue;
        }
        long until = obj.optLong(key, 0L);
        if (until > 0L) {
          out.put(key, until);
        }
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  public static String encode(Map<String, Long> holds) {
    if (holds == null || holds.isEmpty()) {
      return "";
    }
    JSONObject obj = new JSONObject();
    try {
      for (Map.Entry<String, Long> entry : holds.entrySet()) {
        if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
          continue;
        }
        if (entry.getValue() > 0L) {
          obj.put(entry.getKey(), entry.getValue().longValue());
        }
      }
    } catch (Exception ignored) {
    }
    return obj.length() == 0 ? "" : obj.toString();
  }

  public static Map<String, Long> prune(Map<String, Long> holds, long now) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (holds == null) {
      return out;
    }
    for (Map.Entry<String, Long> entry : holds.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        continue;
      }
      if (entry.getValue() > now) {
        out.put(entry.getKey(), entry.getValue());
      }
    }
    return out;
  }

  public static long later(long a, Long b) {
    if (b == null || b <= 0L) {
      return a;
    }
    return Math.max(a, b);
  }

  public static long programUntil(Map<String, Long> holds, Task task) {
    if (holds == null || holds.isEmpty() || task == null) {
      return 0L;
    }
    long until = 0L;
    if (task.seriesId > 0L) {
      until = later(until, holds.get(seriesKey(task.seriesId)));
    }
    if (task.type != null && !task.type.isEmpty()) {
      until = later(until, holds.get(typeKey(task.type)));
    }
    return until;
  }

  public static long until(Map<String, Long> holds, Task task) {
    if (holds == null || holds.isEmpty() || task == null) {
      return 0L;
    }
    long best = later(0L, holds.get(taskKey(task.id)));
    return later(best, programUntil(holds, task));
  }

  public static boolean quiet(Map<String, Long> holds, Task task, long now) {
    return until(holds, task) > now;
  }

  /**
   * Generated occurrence falls inside the break — do not mint, and drop any
   * leftover card so resume does not dump a pile of overdue nags.
   */
  public static boolean skipMint(Map<String, Long> holds, Task task) {
    if (task == null) {
      return false;
    }
    long until = programUntil(holds, task);
    return until > 0L && task.postAtMillis < until;
  }

  public static List<Task> live(List<Task> tasks, Map<String, Long> holds, long now) {
    List<Task> out = new ArrayList<>();
    if (tasks == null) {
      return out;
    }
    for (Task task : tasks) {
      if (task != null && !quiet(holds, task, now)) {
        out.add(task);
      }
    }
    return out;
  }

  public static boolean any(Map<String, Long> holds, long now) {
    if (holds == null || holds.isEmpty()) {
      return false;
    }
    for (Long until : holds.values()) {
      if (until != null && until > now) {
        return true;
      }
    }
    return false;
  }

  public static long latestUntil(Map<String, Long> holds, long now) {
    long latest = 0L;
    if (holds == null) {
      return 0L;
    }
    for (Long until : holds.values()) {
      if (until != null && until > now) {
        latest = Math.max(latest, until);
      }
    }
    return latest;
  }

  public static int activeCount(Map<String, Long> holds, long now) {
    int count = 0;
    if (holds == null) {
      return 0;
    }
    for (Map.Entry<String, Long> entry : holds.entrySet()) {
      if (entry.getValue() != null && entry.getValue() > now && !isTaskKey(entry.getKey())) {
        count++;
      }
    }
    return count;
  }

  public static List<String> activeLabels(Map<String, Long> holds, long now, List<Program> programs) {
    List<String> labels = new ArrayList<>();
    if (holds == null || holds.isEmpty()) {
      return labels;
    }
    Map<String, String> byKey = new LinkedHashMap<>();
    if (programs != null) {
      for (Program program : programs) {
        if (program != null && program.key != null) {
          byKey.put(program.key, program.label);
        }
      }
    }
    for (Map.Entry<String, Long> entry : holds.entrySet()) {
      if (entry.getValue() == null || entry.getValue() <= now) {
        continue;
      }
      String key = entry.getKey();
      if (isTaskKey(key)) {
        continue;
      }
      String label = byKey.get(key);
      if (label == null || label.isEmpty()) {
        label = builtinLabel(key);
      }
      if (label != null && !label.isEmpty()) {
        labels.add(label);
      }
    }
    return labels;
  }

  public static String builtinLabel(String type) {
    if (type == null || type.isEmpty() || isSeriesKey(type) || isTaskKey(type)) {
      return "";
    }
    if (TaskTypes.SUNDAY_SERVICE.equals(type)) {
      return "Sunday Service (Saturday)";
    }
    return TaskTypes.label(type);
  }

  public static String programKey(Task task) {
    if (task == null) {
      return "";
    }
    if (task.seriesId > 0L) {
      return seriesKey(task.seriesId);
    }
    return typeKey(task.type);
  }

  public static boolean canHoldProgram(Task task) {
    if (task == null) {
      return false;
    }
    return task.seriesId > 0L || TaskTypes.isSeries(task.type);
  }

  public static long defaultUntil(long now) {
    return DateUtils.startOfDay(now) + DEFAULT_HOLD_DAYS * 24L * 60L * 60L * 1000L;
  }

  public static List<Program> programs(List<Series> series) {
    List<Program> out = new ArrayList<>();
    for (String type : BUILTIN_TYPES) {
      out.add(new Program(typeKey(type), builtinLabel(type)));
    }
    if (series == null) {
      return out;
    }
    for (Series item : series) {
      if (item == null || item.id <= 0L) {
        continue;
      }
      String title = item.title == null || item.title.trim().isEmpty()
        ? TaskTypes.label(kindType(item.kind))
        : item.title.trim();
      out.add(new Program(seriesKey(item.id), title));
    }
    return out;
  }

  private static String kindType(String kind) {
    if (Series.KIND_WEEKLY.equals(kind)) {
      return TaskTypes.WEEKLY;
    }
    if (Series.KIND_DAILY.equals(kind)) {
      return TaskTypes.DAILY;
    }
    if (Series.KIND_MONTHLY.equals(kind)) {
      return TaskTypes.BIRTHDAY_NOTICE;
    }
    return TaskTypes.COUNTDOWN;
  }

  /** Replace program keys with {@code until}; drop unchecked program keys; keep task holds. */
  public static Map<String, Long> applyPrograms(
    Map<String, Long> current, List<String> selectedKeys, long until) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (current != null) {
      for (Map.Entry<String, Long> entry : current.entrySet()) {
        if (isTaskKey(entry.getKey())) {
          out.put(entry.getKey(), entry.getValue());
        }
      }
    }
    if (selectedKeys == null || until <= 0L) {
      return out;
    }
    for (String key : selectedKeys) {
      if (key == null || key.isEmpty() || isTaskKey(key)) {
        continue;
      }
      out.put(key, until);
    }
    return out;
  }

  public static Map<String, Long> put(Map<String, Long> current, String key, long until) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (current != null) {
      out.putAll(current);
    }
    if (key == null || key.isEmpty()) {
      return out;
    }
    if (until <= 0L) {
      out.remove(key);
    } else {
      out.put(key, until);
    }
    return out;
  }

  public static Map<String, Long> clearPrograms(Map<String, Long> current) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (current == null) {
      return out;
    }
    for (Map.Entry<String, Long> entry : current.entrySet()) {
      if (isTaskKey(entry.getKey())) {
        out.put(entry.getKey(), entry.getValue());
      }
    }
    return out;
  }
}
