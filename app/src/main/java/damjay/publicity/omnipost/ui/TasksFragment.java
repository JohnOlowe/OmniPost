package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.FragmentTasksBinding;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import java.util.ArrayList;
import java.util.List;

public class TasksFragment extends Fragment {
  private FragmentTasksBinding binding;
  private TaskAdapter adapter;
  private final List<Task> active = new ArrayList<>();
  private final List<Task> posted = new ArrayList<>();
  private boolean showPosted;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentTasksBinding.inflate(inflater, container, false);
    adapter = new TaskAdapter(new TaskAdapter.Listener() {
      @Override
      public void onOpen(Task task) {
        Intent intent = new Intent(requireContext(), DraftActivity.class);
        intent.putExtra(ExtraKeys.TASK_ID, task.id);
        startActivity(intent);
      }

      @Override
      public void onMarkPosted(Task task) {
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.markPosted(app, task.id);
          AppExecutors.main(() -> {
            if (!isAdded()) {
              return;
            }
            Toast.makeText(requireContext(), R.string.posted_toast, Toast.LENGTH_SHORT).show();
          });
        });
      }
    });
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.list.setAdapter(adapter);
    binding.filter.check(R.id.chip_pending);
    binding.filter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
      if (!isChecked) {
        return;
      }
      showPosted = checkedId == R.id.chip_posted;
      render();
    });
    AppDatabase db = AppDatabase.get(requireContext());
    db.taskDao().observeActive().observe(getViewLifecycleOwner(), list -> {
      active.clear();
      if (list != null) {
        active.addAll(list);
      }
      if (!showPosted) {
        render();
      }
    });
    db.taskDao().observePosted().observe(getViewLifecycleOwner(), list -> {
      posted.clear();
      if (list != null) {
        posted.addAll(list);
      }
      if (showPosted) {
        render();
      }
    });
    return binding.getRoot();
  }

  private void render() {
    if (binding == null) {
      return;
    }
    List<Task> source = showPosted ? posted : active;
    adapter.submit(source);
    binding.empty.setText(showPosted ? R.string.empty_posted : R.string.empty_tasks);
    binding.empty.setVisibility(source.isEmpty() ? View.VISIBLE : View.GONE);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
