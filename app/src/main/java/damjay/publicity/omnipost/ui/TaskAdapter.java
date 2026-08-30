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
  }

  static final class Row {
    final int kind;
    final String header;
    final Task task;

    Row(String header) {
      this.kind = TYPE_HEADER;
      this.header = header;
      this.task = null;
    }

    Row(Task task) {
      this.kind = TYPE_TASK;
      this.header = null;
      this.task = task;
    }
  }

  private final Listener listener;
  private final List<Row> rows = new ArrayList<>();

  public TaskAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<Task> tasks) {
    rows.clear();
    String lastKey = null;
    if (tasks != null) {
      for (Task task : tasks) {
        String key = DateUtils.dayKey(task.postAtMillis);
        if (!key.equals(lastKey)) {
          rows.add(new Row(DateUtils.formatDayHeader(task.postAtMillis)));
          lastKey = key;
        }
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
      ((HeaderHolder) holder).bind(row.header);
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

    void bind(String header) {
      binding.header.setText(header);
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
      binding.when.setText(DateUtils.relativeOrStamp(task.postAtMillis));
      binding.status.setText(TaskStatus.label(task.status));
      binding.cadence.setText(TaskTypes.cadence(task.type));
      style(task);
      binding.btnPosted.setVisibility(
        TaskStatus.POSTED.equals(task.status) ? View.GONE : View.VISIBLE);
      binding.getRoot().setOnClickListener(v -> listener.onOpen(task));
      binding.btnDraft.setOnClickListener(v -> listener.onOpen(task));
      binding.btnPosted.setOnClickListener(v -> listener.onMarkPosted(task));
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
