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
import damjay.publicity.omnipost.notify.AlarmLaunch;
import damjay.publicity.omnipost.notify.AlarmPulse;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;

public class AlarmActivity extends AppCompatActivity {
  private ActivityAlarmBinding binding;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable paintPulse = this::paintPulse;
  private long taskId;
  private long postAt;
  private boolean selected;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    turnScreenOn();
    overridePendingTransition(0, 0);
    binding = ActivityAlarmBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
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
    binding.btnNag30.setOnClickListener(v -> snoozeTo(nagAt(SnoozeChooser.THIRTY_MIN_MS)));
    binding.btnNagHour.setOnClickListener(v -> snoozeTo(nagAt(SnoozeChooser.ONE_HOUR_MS)));
    binding.btnSnooze.setOnClickListener(v -> {
      acknowledge();
      SnoozeChooser.show(this, originMillis(), until -> snoozeTo(until));
    });
    binding.btnLater.setOnClickListener(v -> {
      acknowledge();
      finish();
    });
    paintFromIntent(getIntent());
    int phase = phaseOf(getIntent());
    if (AlarmLaunch.loud(phase)) {
      AlarmPulse.begin(this);
    } else {
      AlarmPulse.beginSoft(this);
    }
    paintPulse();
    handler.postDelayed(paintPulse, ScheduleTimes.ESCALATE_VIBRATE_MS);
    load();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    paintFromIntent(intent);
    load();
  }

  private void paintFromIntent(Intent intent) {
    if (intent == null || binding == null) {
      return;
    }
    taskId = intent.getLongExtra(ExtraKeys.TASK_ID, taskId);
    postAt = intent.getLongExtra(ExtraKeys.POST_AT, postAt);
    int phase = phaseOf(intent);
    if (phase == AlarmScheduler.PHASE_DRAFT) {
      binding.phase.setText(R.string.write_caption_now);
      binding.subtitle.setText(R.string.alarm_subtitle_soft);
    } else if (phase == AlarmScheduler.PHASE_WARNING) {
      binding.phase.setText(getString(R.string.caption_ready_phase, Prefs.warningMinutes(this)));
      binding.subtitle.setText(R.string.alarm_subtitle_soft);
    } else if (phase == AlarmScheduler.PHASE_MINUTE) {
      binding.phase.setText(R.string.one_minute);
      binding.subtitle.setText(
        Prefs.escalate(this) ? R.string.alarm_subtitle_escalate : R.string.alarm_subtitle);
    } else {
      binding.phase.setText(R.string.post_now);
      binding.subtitle.setText(
        Prefs.escalate(this) ? R.string.alarm_subtitle_escalate : R.string.alarm_subtitle);
    }
    String title = intent.getStringExtra(ExtraKeys.TASK_TITLE);
    if (title == null || title.isEmpty()) {
      title = AlarmScheduler.cachedTitle(taskId);
    }
    if (title != null && !title.isEmpty()) {
      binding.title.setText(title);
    } else if (binding.title.getText() == null || binding.title.getText().length() == 0) {
      binding.title.setText(R.string.app_name);
    }
    if (postAt <= 0L) {
      postAt = AlarmScheduler.cachedPostAt(taskId);
    }
    if (postAt > 0L) {
      binding.when.setText(
        DateUtils.formatStamp(postAt) + " · " + DateUtils.formatUntil(postAt));
    }
    paintNagButtons();
  }

  private void paintNagButtons() {
    long thirty = nagAt(SnoozeChooser.THIRTY_MIN_MS);
    long hour = nagAt(SnoozeChooser.ONE_HOUR_MS);
    binding.btnNag30.setText(getString(R.string.nag_by, DateUtils.prettyClock(thirty)));
    binding.btnNagHour.setText(getString(R.string.nag_by, DateUtils.prettyClock(hour)));
  }

  private long originMillis() {
    return postAt > 0L ? postAt : System.currentTimeMillis();
  }

  private long nagAt(long offsetMs) {
    return DateUtils.nagFromOrigin(originMillis(), offsetMs, System.currentTimeMillis());
  }

  private void snoozeTo(long until) {
    acknowledge();
    final long when = until;
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.snooze(this, taskId, when);
      AppExecutors.main(() -> {
        Toast.makeText(
          this,
          getString(R.string.snoozed_until, DateUtils.formatStamp(when)),
          Toast.LENGTH_LONG)
          .show();
        finish();
      });
    });
  }

  private void load() {
    final long id = taskId;
    AppExecutors.disk().execute(() -> {
      Task task = AppDatabase.get(this).taskDao().getById(id);
      AppExecutors.main(() -> {
        if (binding == null || task == null) {
          return;
        }
        binding.title.setText(task.title);
        postAt = task.postAtMillis;
        binding.when.setText(
          DateUtils.formatStamp(task.postAtMillis) + " · " + DateUtils.formatUntil(task.postAtMillis));
        paintNagButtons();
      });
    });
  }

  private void paintPulse() {
    if (binding == null) {
      return;
    }
    int phase = phaseOf(getIntent());
    if (!AlarmLaunch.loud(phase) || !Prefs.escalate(this)) {
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

  private static int phaseOf(Intent intent) {
    if (intent == null) {
      return AlarmScheduler.PHASE_NAG;
    }
    return intent.getIntExtra(ExtraKeys.PHASE, AlarmScheduler.PHASE_NAG);
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
