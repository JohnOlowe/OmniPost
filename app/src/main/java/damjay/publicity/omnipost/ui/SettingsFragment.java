package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.FragmentSettingsBinding;
import damjay.publicity.omnipost.databinding.ItemSettingRowBinding;
import damjay.publicity.omnipost.databinding.ItemSurviveRowBinding;
import damjay.publicity.omnipost.databinding.ItemSwitchRowBinding;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.Prefs;
import damjay.publicity.omnipost.util.SurvivalHelper;

public class SettingsFragment extends Fragment {
  private FragmentSettingsBinding binding;

  @Nullable
  @Override
  public View onCreateView(
    @NonNull LayoutInflater inflater,
    @Nullable ViewGroup container,
    @Nullable Bundle savedInstanceState) {
    binding = FragmentSettingsBinding.inflate(inflater, container, false);
    binding.btnTest.setOnClickListener(v -> fireTest());
    binding.btnRearm.setOnClickListener(v -> rearm());
    bindDesk();
    return binding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    bindSurvive();
    bindDesk();
  }

  private void bindDesk() {
    if (binding == null) {
      return;
    }
    Context ctx = requireContext();
    paintChoice(binding.rowAlert, R.string.setting_alert, alertLabel(Prefs.alertMode(ctx)));
    binding.rowAlert.getRoot().setOnClickListener(v -> pickAlert());
    paintChoice(binding.rowNag, R.string.setting_nag, nagLabel(Prefs.nagMinutes(ctx)));
    binding.rowNag.getRoot().setOnClickListener(v -> pickNag());
    paintChoice(binding.rowWarning, R.string.setting_warning, warningLabel(Prefs.warningMinutes(ctx)));
    binding.rowWarning.getRoot().setOnClickListener(v -> pickWarning());
    paintSwitch(
      binding.rowSeed,
      R.string.setting_seed,
      R.string.setting_seed_hint,
      Prefs.seedCaptions(ctx),
      (b, on) -> {
        Prefs.setSeedCaptions(ctx, on);
        rearmQuiet();
      });
    paintSwitch(
      binding.rowDesk,
      R.string.setting_desk,
      R.string.setting_desk_hint,
      Prefs.deskOngoing(ctx),
      (b, on) -> {
        Prefs.setDeskOngoing(ctx, on);
        rearmQuiet();
      });
    paintSwitch(
      binding.rowFullscreenPref,
      R.string.setting_fullscreen,
      R.string.setting_fullscreen_hint,
      Prefs.fullScreen(ctx),
      (b, on) -> Prefs.setFullScreen(ctx, on));
  }

  private void paintChoice(ItemSettingRowBinding row, int title, String value) {
    row.title.setText(title);
    row.value.setText(value);
  }

  private void paintSwitch(
    ItemSwitchRowBinding row,
    int title,
    int hint,
    boolean on,
    CompoundButton.OnCheckedChangeListener listener) {
    row.title.setText(title);
    row.hint.setText(hint);
    row.toggle.setOnCheckedChangeListener(null);
    row.toggle.setChecked(on);
    row.toggle.setOnCheckedChangeListener(listener);
  }

  private void pickAlert() {
    String[] modes = new String[] {Prefs.MODE_BOTH, Prefs.MODE_SOUND, Prefs.MODE_VIBRATE};
    String[] labels = new String[] {
      getString(R.string.alert_both),
      getString(R.string.alert_sound),
      getString(R.string.alert_vibrate)
    };
    int selected = indexOf(modes, Prefs.alertMode(requireContext()));
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.setting_alert)
      .setSingleChoiceItems(labels, selected, (d, which) -> {
        Prefs.setAlertMode(requireContext(), modes[which]);
        d.dismiss();
        bindDesk();
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void pickNag() {
    int[] values = new int[] {2, 5, 10, 15};
    String[] labels = new String[] {
      nagLabel(2), nagLabel(5), nagLabel(10), nagLabel(15)
    };
    int selected = indexOf(values, Prefs.nagMinutes(requireContext()));
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.setting_nag)
      .setSingleChoiceItems(labels, selected, (d, which) -> {
        Prefs.setNagMinutes(requireContext(), values[which]);
        d.dismiss();
        bindDesk();
        rearmQuiet();
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void pickWarning() {
    int[] values = new int[] {15, 30, 60};
    String[] labels = new String[] {
      warningLabel(15), warningLabel(30), warningLabel(60)
    };
    int selected = indexOf(values, Prefs.warningMinutes(requireContext()));
    new MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.setting_warning)
      .setSingleChoiceItems(labels, selected, (d, which) -> {
        Prefs.setWarningMinutes(requireContext(), values[which]);
        d.dismiss();
        bindDesk();
        rearmQuiet();
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private String alertLabel(String mode) {
    if (Prefs.MODE_SOUND.equals(mode)) {
      return getString(R.string.alert_sound);
    }
    if (Prefs.MODE_VIBRATE.equals(mode)) {
      return getString(R.string.alert_vibrate);
    }
    return getString(R.string.alert_both);
  }

  private String nagLabel(int minutes) {
    return getString(R.string.nag_every, minutes);
  }

  private String warningLabel(int minutes) {
    return getString(R.string.warning_every, minutes);
  }

  private static int indexOf(String[] values, String target) {
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(target)) {
        return i;
      }
    }
    return 0;
  }

  private static int indexOf(int[] values, int target) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] == target) {
        return i;
      }
    }
    return 0;
  }

  private void bindSurvive() {
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

  private void rearm() {
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
  }

  private void rearmQuiet() {
    Context app = requireContext().getApplicationContext();
    AppExecutors.disk().execute(() -> ScheduleCoordinator.bootstrap(app));
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
