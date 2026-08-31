package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ItemDayHeaderBinding;
import damjay.publicity.omnipost.databinding.ItemTaskBinding;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.TaskSections;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
  private static final int TYPE_HEADER = 0;
  private static final int TYPE_TASK = 1;

  public interface Listener {
    void onOpen(Task task);

    void onMarkPosted(Task task);

    void onSnooze(Task task);

    void onShift(Task task);

    void onReopen(Task task);

    void onDelete(Task task);

    void onEditSeries(Task task);
  }

  static final class Row {
    final int kind;
    final String header;
    final String subtitle;
    final Task task;

    Row(String header, String subtitle) {
      this.kind = TYPE_HEADER;
      this.header = header;
      this.subtitle = subtitle;
      this.task = null;
    }

    Row(Task task) {
      this.kind = TYPE_TASK;
      this.header = null;
      this.subtitle = null;
      this.task = task;
    }
  }

  private final Listener listener;
  private final List<Row> rows = new ArrayList<>();

  public TaskAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<Task> tasks, boolean postedMode) {
    rows.clear();
    List<TaskSections.Section> sections = TaskSections.group(tasks, postedMode);
    for (TaskSections.Section section : sections) {
      rows.add(new Row(section.title, section.subtitle));
      for (Task task : section.tasks) {
        rows.add(new Row(task));
      }
    }
    notifyDataSetChanged();
  }

  @Override
  public int getItemViewType(int position) {
    return rows.get(position).kind;
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    if (viewType == TYPE_HEADER) {
      return new HeaderHolder(ItemDayHeaderBinding.inflate(inflater, parent, false));
    }
    return new TaskHolder(ItemTaskBinding.inflate(inflater, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    Row row = rows.get(position);
    if (holder instanceof HeaderHolder) {
      ((HeaderHolder) holder).bind(row.header, row.subtitle);
    } else if (holder instanceof TaskHolder) {
      ((TaskHolder) holder).bind(row.task, listener);
    }
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  static class HeaderHolder extends RecyclerView.ViewHolder {
    private final ItemDayHeaderBinding binding;

    HeaderHolder(ItemDayHeaderBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(String header, String subtitle) {
      binding.header.setText(header);
      if (subtitle == null || subtitle.isEmpty()) {
        binding.subtitle.setVisibility(View.GONE);
      } else {
        binding.subtitle.setVisibility(View.VISIBLE);
        binding.subtitle.setText(subtitle);
      }
    }
  }

  static class TaskHolder extends RecyclerView.ViewHolder {
    private final ItemTaskBinding binding;

    TaskHolder(ItemTaskBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Task task, Listener listener) {
      binding.title.setText(task.title);
      binding.description.setText(task.description);
      if (TaskStatus.SNOOZED.equals(task.status) && task.snoozeUntilMillis > 0L) {
        binding.when.setText(
          itemView.getContext().getString(
            R.string.snoozed_until, DateUtils.formatStamp(task.snoozeUntilMillis)));
      } else {
        binding.when.setText(DateUtils.relativeOrStamp(task.postAtMillis));
      }
      binding.status.setText(TaskStatus.label(task.status));
      binding.cadence.setText(TaskTypes.cadence(task.type));
      style(task);
      boolean posted = TaskStatus.POSTED.equals(task.status);
      binding.btnPosted.setVisibility(posted ? View.GONE : View.VISIBLE);
      binding.btnReopen.setVisibility(posted ? View.VISIBLE : View.GONE);
      binding.btnSnooze.setVisibility(posted ? View.GONE : View.VISIBLE);
      binding.getRoot().setOnClickListener(v -> listener.onOpen(task));
      binding.btnDraft.setOnClickListener(v -> listener.onOpen(task));
      binding.btnPosted.setOnClickListener(v -> listener.onMarkPosted(task));
      binding.btnReopen.setOnClickListener(v -> listener.onReopen(task));
      binding.btnSnooze.setOnClickListener(v -> listener.onSnooze(task));
      binding.btnShift.setOnClickListener(v -> listener.onShift(task));
      binding.getRoot().setOnLongClickListener(v -> {
        if (posted) {
          return false;
        }
        if (task.seriesId > 0L || TaskTypes.oneCard(task.type)) {
          listener.onEditSeries(task);
          return true;
        }
        if (TaskTypes.isCustom(task.type)) {
          listener.onDelete(task);
          return true;
        }
        return false;
      });
    }

    private void style(Task task) {
      int bg = R.drawable.bg_chip_gold;
      int color = R.color.gold;
      int accent = R.color.gold;
      int stroke = R.color.navy_700;
      if (TaskStatus.NAGGING.equals(task.status)) {
        bg = R.drawable.bg_chip_red;
        color = R.color.danger;
        accent = R.color.danger;
        stroke = R.color.danger;
      } else if (TaskStatus.WARNING.equals(task.status)) {
        bg = R.drawable.bg_chip_warning;
        color = R.color.warning;
        accent = R.color.warning;
        stroke = R.color.warning;
      } else if (TaskStatus.SNOOZED.equals(task.status)) {
        bg = R.drawable.bg_chip_quiet;
        color = R.color.quiet;
        accent = R.color.quiet;
        stroke = R.color.quiet;
      } else if (TaskStatus.DRAFTING.equals(task.status)) {
        bg = R.drawable.bg_chip_gold;
        color = R.color.gold;
        accent = R.color.gold;
        stroke = R.color.gold;
      } else if (TaskStatus.POSTED.equals(task.status)) {
        bg = R.drawable.bg_chip_green;
        color = R.color.success;
        accent = R.color.success;
      }
      binding.status.setBackgroundResource(bg);
      binding.status.setTextColor(ContextCompat.getColor(itemView.getContext(), color));
      binding.accent.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), accent));
      binding.card.setStrokeColor(ContextCompat.getColor(itemView.getContext(), stroke));
    }
  }
}
