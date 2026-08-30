package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.FragmentSurviveBinding;
import damjay.publicity.omnipost.databinding.ItemSurviveRowBinding;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.SurvivalHelper;

public class SurviveFragment extends Fragment {
  private FragmentSurviveBinding binding;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentSurviveBinding.inflate(inflater, container, false);
    binding.btnTest.setOnClickListener(v -> fireTest());
    binding.btnRearm.setOnClickListener(v -> {
      Context app = requireContext().getApplicationContext();
      AppExecutors.disk().execute(() -> {
        ScheduleCoordinator.bootstrap(app);
        AppExecutors.main(() -> {
          if (!isAdded()) {
            return;
          }
          Toast.makeText(requireContext(), R.string.rearmed, Toast.LENGTH_SHORT).show();
        });
      });
    });
    return binding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    bindRows();
  }

  private void bindRows() {
    bindRow(
      binding.rowNotifications,
      R.string.perm_notifications,
      SurvivalHelper.notificationsAllowed(requireContext()),
      v -> SurvivalHelper.openNotificationSettings(requireActivity()));
    bindRow(
      binding.rowExact,
      R.string.perm_exact,
      SurvivalHelper.exactAlarmsAllowed(requireContext()),
      v -> SurvivalHelper.openExactAlarmSettings(requireActivity()));
    bindRow(
      binding.rowBattery,
      R.string.perm_battery,
      SurvivalHelper.batteryUnrestricted(requireContext()),
      v -> SurvivalHelper.openBatterySettings(requireActivity()));
    bindRow(
      binding.rowFullscreen,
      R.string.perm_fullscreen,
      SurvivalHelper.fullScreenAllowed(requireContext()),
      v -> SurvivalHelper.openFullScreenSettings(requireActivity()));
  }

  private void bindRow(
    ItemSurviveRowBinding row, int title, boolean ok, View.OnClickListener grant) {
    row.title.setText(title);
    row.status.setText(ok ? R.string.ok_status : R.string.missing_status);
    row.status.setBackgroundResource(ok ? R.drawable.bg_status_ok : R.drawable.bg_status_bad);
    row.grant.setVisibility(ok ? View.GONE : View.VISIBLE);
    row.grant.setOnClickListener(grant);
  }

  private void fireTest() {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> {
      long now = System.currentTimeMillis();
      Task task = new Task();
      task.type = TaskTypes.TEST;
      task.title = "Persistence test";
      task.description = "Swipe OmniPost away. If the nag still lands, the engine survived.";
      task.draftAtMillis = now + 5_000L;
      task.postAtMillis = now + 70_000L;
      task.status = TaskStatus.SCHEDULED;
      task.occurrenceKey = "TEST|" + now;
      task.id = AppDatabase.get(app).taskDao().insert(task);
      AlarmScheduler.scheduleTask(app, task);
      AppExecutors.main(() -> {
        if (!isAdded()) {
          return;
        }
        Toast.makeText(requireContext(), R.string.test_armed, Toast.LENGTH_LONG).show();
      });
    });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    binding = null;
  }
}
