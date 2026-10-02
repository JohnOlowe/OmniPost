package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TaskSearch {
  private TaskSearch() {}

  public static List<Task> matches(List<Task> source, String query) {
    List<Task> out = new ArrayList<>();
    if (source == null) {
      return out;
    }
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
    if (needle.isEmpty()) {
      out.addAll(source);
      return out;
    }
    for (Task task : source) {
      if (task != null && hits(task, needle)) {
        out.add(task);
      }
    }
    return out;
  }

  private static boolean hits(Task task, String needle) {
    return contains(task.title, needle) || contains(task.description, needle);
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.US).contains(needle);
  }
}
