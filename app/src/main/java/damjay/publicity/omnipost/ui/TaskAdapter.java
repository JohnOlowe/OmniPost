package damjay.publicity.omnipost.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ItemTaskBinding;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Holder> {
  public interface Listener {
    void onOpen(Task task);

    void onMarkPosted(Task task);
  }

  private final Listener listener;
  private final List<Task> items = new ArrayList<>();

  public TaskAdapter(Listener listener) {
    this.listener = listener;
  }

  public void submit(List<Task> tasks) {
    items.clear();
    if (tasks != null) {
      items.addAll(tasks);
    }
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new Holder(ItemTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder holder, int position) {
    holder.bind(items.get(position), listener);
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  static class Holder extends RecyclerView.ViewHolder {
    private final ItemTaskBinding binding;

    Holder(ItemTaskBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }

    void bind(Task task, Listener listener) {
      binding.title.setText(task.title);
      binding.description.setText(task.description);
      binding.when.setText(DateUtils.relativeOrStamp(task.postAtMillis));
      binding.status.setText(task.status);
      style(task);
      binding.btnPosted.setVisibility(
        TaskStatus.POSTED.equals(task.status) ? View.GONE : View.VISIBLE);
      binding.getRoot().setOnClickListener(v -> listener.onOpen(task));
      binding.btnDraft.setOnClickListener(v -> listener.onOpen(task));
      binding.btnPosted.setOnClickListener(v -> listener.onMarkPosted(task));
    }

    private void style(Task task) {
      int bg = R.drawable.bg_chip_muted;
      int color = R.color.text_muted;
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
        stroke = R.color.navy_700;
      } else {
        bg = R.drawable.bg_chip_gold;
        color = R.color.gold;
      }
      binding.status.setBackgroundResource(bg);
      binding.status.setTextColor(ContextCompat.getColor(itemView.getContext(), color));
      binding.accent.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), accent));
      binding.card.setStrokeColor(ContextCompat.getColor(itemView.getContext(), stroke));
    }
  }
}
