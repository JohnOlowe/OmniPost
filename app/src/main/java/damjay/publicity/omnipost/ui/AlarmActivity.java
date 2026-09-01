package damjay.publicity.omnipost.ui;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityAlarmBinding;
import damjay.publicity.omnipost.notify.AlarmPulse;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;

public class AlarmActivity extends AppCompatActivity {
  private ActivityAlarmBinding binding;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable paintPulse = this::paintPulse;
  private long taskId;
  private boolean selected;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    turnScreenOn();
    binding = ActivityAlarmBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    taskId = getIntent().getLongExtra(ExtraKeys.TASK_ID, 0L);
    int phase = getIntent().getIntExtra(ExtraKeys.PHASE, AlarmScheduler.PHASE_NAG);
    if (phase == AlarmScheduler.PHASE_DRAFT) {
      binding.phase.setText(R.string.write_caption_now);
      binding.subtitle.setText(
        Prefs.escalate(this) ? R.string.alarm_subtitle_escalate : R.string.alarm_subtitle_draft);
    } else if (phase == AlarmScheduler.PHASE_WARNING) {
      binding.phase.setText(getString(R.string.caption_ready_phase, Prefs.warningMinutes(this)));
      binding.subtitle.setText(
        Prefs.escalate(this) ? R.string.alarm_subtitle_escalate : R.string.alarm_subtitle);
    } else {
      binding.phase.setText(R.string.post_now);
      binding.subtitle.setText(
        Prefs.escalate(this) ? R.string.alarm_subtitle_escalate : R.string.alarm_subtitle);
    }
    binding.btnDraft.setOnClickListener(v -> {
      acknowledge();
      Intent intent = new Intent(this, DraftActivity.class);
      intent.putExtra(ExtraKeys.TASK_ID, taskId);
      startActivity(intent);
      finish();
    });
    binding.btnPosted.setOnClickListener(v -> {
      acknowledge();
      AppExecutors.disk().execute(() -> {
        ScheduleCoordinator.markPosted(this, taskId);
        AppExecutors.main(() -> {
          Toast.makeText(this, R.string.posted_toast, Toast.LENGTH_LONG).show();
          finish();
        });
      });
    });
    binding.btnSnooze.setOnClickListener(v -> {
      acknowledge();
      SnoozeChooser.show(this, until -> {
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.snooze(this, taskId, until);
          AppExecutors.main(() -> {
            Toast.makeText(
              this,
              getString(R.string.snoozed_until, DateUtils.formatStamp(until)),
              Toast.LENGTH_LONG)
              .show();
            finish();
          });
        });
      });
    });
    binding.btnLater.setOnClickListener(v -> {
      acknowledge();
      finish();
    });
    load();
    NagForegroundService.startPulse(this);
    paintPulse();
    handler.postDelayed(paintPulse, ScheduleTimes.ESCALATE_VIBRATE_MS);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (intent != null) {
      taskId = intent.getLongExtra(ExtraKeys.TASK_ID, taskId);
      load();
    }
  }

  private void load() {
    AppExecutors.disk().execute(() -> {
      Task task = AppDatabase.get(this).taskDao().getById(taskId);
      AppExecutors.main(() -> {
        if (binding == null) {
          return;
        }
        if (task == null) {
          binding.title.setText(R.string.app_name);
          return;
        }
        binding.title.setText(task.title);
        binding.when.setText(
          DateUtils.formatStamp(task.postAtMillis) + " · " + DateUtils.formatUntil(task.postAtMillis));
      });
    });
  }

  private void paintPulse() {
    if (binding == null) {
      return;
    }
    if (!Prefs.escalate(this)) {
      binding.pulse.setVisibility(View.GONE);
      return;
    }
    binding.pulse.setVisibility(View.VISIBLE);
    if (AlarmPulse.state() == AlarmPulse.RINGING) {
      binding.pulse.setText(R.string.pulse_ringing);
    } else {
      binding.pulse.setText(R.string.pulse_vibrating);
    }
  }

  private void acknowledge() {
    selected = true;
    NotificationHelper.hush(this, taskId);
  }

  private void turnScreenOn() {
    if (Build.VERSION.SDK_INT >= 27) {
      setShowWhenLocked(true);
      setTurnScreenOn(true);
      KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
      if (km != null) {
        km.requestDismissKeyguard(this, null);
      }
    } else {
      getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
          | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
          | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
          | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacks(paintPulse);
    if (selected) {
      AlarmPulse.silence();
    }
    super.onDestroy();
  }
}
