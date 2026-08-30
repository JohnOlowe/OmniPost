package damjay.publicity.omnipost.ui;

import android.app.KeyguardManager;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityAlarmBinding;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;

public class AlarmActivity extends AppCompatActivity {
  private ActivityAlarmBinding binding;
  private Ringtone ringtone;
  private long taskId;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    turnScreenOn();
    binding = ActivityAlarmBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    taskId = getIntent().getLongExtra(ExtraKeys.TASK_ID, 0L);
    int phase = getIntent().getIntExtra(ExtraKeys.PHASE, AlarmScheduler.PHASE_NAG);
    binding.phase.setText(
      phase == AlarmScheduler.PHASE_WARNING ? R.string.one_minute : R.string.post_now);
    binding.btnDraft.setOnClickListener(v -> {
      Intent intent = new Intent(this, DraftActivity.class);
      intent.putExtra(ExtraKeys.TASK_ID, taskId);
      startActivity(intent);
      finish();
    });
    binding.btnPosted.setOnClickListener(v -> AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.markPosted(this, taskId);
      AppExecutors.main(() -> {
        Toast.makeText(this, R.string.posted_toast, Toast.LENGTH_LONG).show();
        finish();
      });
    }));
    binding.btnLater.setOnClickListener(v -> finish());
    load();
    startSound();
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
        binding.when.setText(DateUtils.formatStamp(task.postAtMillis));
      });
    });
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

  private void startSound() {
    try {
      Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
      ringtone = RingtoneManager.getRingtone(this, uri);
      if (ringtone != null) {
        ringtone.setAudioAttributes(
          new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
        ringtone.play();
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  protected void onDestroy() {
    if (ringtone != null && ringtone.isPlaying()) {
      ringtone.stop();
    }
    super.onDestroy();
  }
}
