package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.FragmentTasksBinding;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.SeriesDefaults;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

      @Override
      public void onSnooze(Task task) {
        SnoozeChooser.show(requireContext(), until -> applySnooze(task.id, until));
      }

      @Override
      public void onShift(Task task) {
        SnoozeChooser.pickDateTime(requireContext(), when -> {
          Context app = requireContext().getApplicationContext();
          AppExecutors.disk().execute(() -> {
            ScheduleCoordinator.shift(app, task.id, when);
            AppExecutors.main(() -> {
              if (!isAdded()) {
                return;
              }
              Toast.makeText(
                requireContext(),
                getString(R.string.shifted_to, DateUtils.formatStamp(when)),
                Toast.LENGTH_LONG)
                .show();
            });
          });
        });
      }

      @Override
      public void onReopen(Task task) {
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.reopen(app, task.id);
          AppExecutors.main(() -> {
            if (!isAdded()) {
              return;
            }
            Toast.makeText(requireContext(), R.string.brought_back, Toast.LENGTH_LONG).show();
          });
        });
      }

      @Override
      public void onDelete(Task task) {
        new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.delete_task_title)
          .setMessage(getString(R.string.delete_task_body, task.title))
          .setPositiveButton(R.string.delete, (d, w) -> {
            Context app = requireContext().getApplicationContext();
            AppExecutors.disk().execute(() -> ScheduleCoordinator.deleteCustom(app, task.id));
          })
          .setNegativeButton(android.R.string.cancel, null)
          .show();
      }

      @Override
      public void onEditSeries(Task task) {
        openSeriesEditor(task);
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
    binding.fab.setOnClickListener(v -> showAddChooser());
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

  private void applySnooze(long taskId, long until) {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.snooze(app, taskId, until);
      AppExecutors.main(() -> {
        if (!isAdded()) {
          return;
        }
        Toast.makeText(
          requireContext(),
          getString(R.string.snoozed_until, DateUtils.formatStamp(until)),
          Toast.LENGTH_LONG)
          .show();
      });
    });
  }

  private void showAddChooser() {
    CharSequence[] items = new CharSequence[] {
      getString(R.string.add_weekly),
      getString(R.string.add_daily),
      getString(R.string.add_custom),
      getString(R.string.add_countdown),
      getString(R.string.add_monthly)
    };
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.add)
      .setItems(items, (d, which) -> {
        if (which == 0) {
          SeriesEditor.createWeekly(requireContext());
        } else if (which == 1) {
          SeriesEditor.createDaily(requireContext());
        } else if (which == 2) {
          showCustomEditor();
        } else if (which == 3) {
          SeriesEditor.createCountdown(requireContext());
        } else {
          SeriesEditor.createMonthly(requireContext());
        }
      })
      .show();
  }

  private void openSeriesEditor(Task task) {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      AppDatabase db = AppDatabase.get(app);
      Series series = null;
      if (task.seriesId > 0L) {
        series = db.seriesDao().getById(task.seriesId);
      }
      if (series == null && TaskTypes.COUNTDOWN.equals(task.type)) {
        series = db.seriesDao().findBySeed(SeriesDefaults.SEED_COUNTDOWN);
      }
      if (series == null && TaskTypes.BIRTHDAY_NOTICE.equals(task.type)) {
        series = db.seriesDao().findBySeed(SeriesDefaults.SEED_NOTICE);
      }
      Series found = series;
      AppExecutors.main(() -> {
        if (!isAdded() || found == null) {
          return;
        }
        SeriesEditor.show(requireContext(), found);
      });
    });
  }

  private void showCustomEditor() {
    View view = getLayoutInflater().inflate(R.layout.dialog_custom_task, null, false);
    TextInputEditText title = view.findViewById(R.id.input_title);
    MaterialButton whenBtn = view.findViewById(R.id.btn_when);
    android.widget.TextView until = view.findViewById(R.id.until);
    RadioButton flexible = view.findViewById(R.id.kind_flexible);
    AtomicLong when = new AtomicLong(DateUtils.nextClock(10, 0));
    Runnable paint = () -> {
      whenBtn.setText(DateUtils.formatStamp(when.get()));
      until.setText(DateUtils.formatUntil(when.get()));
    };
    paint.run();
    whenBtn.setOnClickListener(v -> SnoozeChooser.pickDateTime(requireContext(), millis -> {
      when.set(millis);
      paint.run();
    }));
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.add_custom)
      .setView(view)
      .setPositiveButton(R.string.add, (d, w) -> {
        String value = title.getText() == null ? "" : title.getText().toString().trim();
        if (value.isEmpty()) {
          Toast.makeText(requireContext(), R.string.need_title, Toast.LENGTH_SHORT).show();
          return;
        }
        if (when.get() <= System.currentTimeMillis()) {
          Toast.makeText(requireContext(), R.string.need_future, Toast.LENGTH_SHORT).show();
          return;
        }
        boolean isFlexible = flexible.isChecked();
        Context app = requireContext().getApplicationContext();
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.addCustom(app, value, when.get(), isFlexible);
          AppExecutors.main(() -> {
            if (!isAdded()) {
              return;
            }
            Toast.makeText(requireContext(), R.string.custom_added, Toast.LENGTH_SHORT).show();
          });
        });
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void render() {
    if (binding == null) {
      return;
    }
    List<Task> source = showPosted ? posted : active;
    adapter.submit(source, showPosted);
    binding.empty.setText(showPosted ? R.string.empty_posted : R.string.empty_tasks);
    binding.empty.setVisibility(source.isEmpty() ? View.VISIBLE : View.GONE);
    binding.fab.setVisibility(showPosted ? View.GONE : View.VISIBLE);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
