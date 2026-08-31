package damjay.publicity.omnipost.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.Alerts;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.ui.AlarmActivity;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.List;

public class NagForegroundService extends Service {
  private static final long DESK_REFRESH_MS = 15L * 60L * 1000L;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable pulse = this::onPulse;
  private MediaPlayer player;
  private volatile boolean keepAlive;
  private volatile boolean hasNagging;
  private volatile boolean destroyed;
  private PowerManager.WakeLock wakeLock;

  public static void start(Context context, long taskId) {
    Intent intent = new Intent(context, NagForegroundService.class);
    intent.putExtra(ExtraKeys.TASK_ID, taskId);
    ContextCompat.startForegroundService(context.getApplicationContext(), intent);
  }

  public static void refresh(Context context) {
    start(context, 0L);
  }

  public static void stop(Context context) {
    context.getApplicationContext().stopService(new Intent(context, NagForegroundService.class));
  }

  @Override
  public void onCreate() {
    super.onCreate();
    destroyed = false;
    NotificationHelper.ensureChannels(this);
    promoteForeground(0, null);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    NotificationHelper.ensureChannels(this);
    promoteForeground(0, null);
    final long focusedId = intent == null ? 0L : intent.getLongExtra(ExtraKeys.TASK_ID, 0L);
    AppExecutors.disk().execute(() -> {
      List<Task> nagging = AppDatabase.get(this).taskDao().getNaggingSync();
      Task next = AppDatabase.get(this).taskDao().nextActive();
      hasNagging = nagging != null && !nagging.isEmpty();
      boolean desk = Prefs.deskOngoing(this);
      keepAlive = hasNagging || desk;
      AppExecutors.main(() -> {
        if (destroyed) {
          return;
        }
        if (!keepAlive) {
          stopForeground(STOP_FOREGROUND_REMOVE);
          stopSelf();
          return;
        }
        promoteForeground(hasNagging ? nagging.size() : 0, next);
        handler.removeCallbacks(pulse);
        if (hasNagging) {
          fireBurst(nagging, focusedId);
          long interval = Prefs.nagIntervalMs(this);
          handler.postDelayed(pulse, interval);
          AlarmScheduler.schedulePulse(this, 0L, System.currentTimeMillis() + interval);
        } else {
          handler.postDelayed(pulse, DESK_REFRESH_MS);
          AlarmScheduler.schedulePulse(this, 0L, System.currentTimeMillis() + DESK_REFRESH_MS);
        }
      });
    });
    return START_STICKY;
  }

  private void onPulse() {
    AppExecutors.disk().execute(() -> {
      List<Task> nagging = AppDatabase.get(this).taskDao().getNaggingSync();
      Task next = AppDatabase.get(this).taskDao().nextActive();
      hasNagging = nagging != null && !nagging.isEmpty();
      boolean desk = Prefs.deskOngoing(this);
      keepAlive = hasNagging || desk;
      AppExecutors.main(() -> {
        if (destroyed) {
          return;
        }
        if (!keepAlive) {
          stopForeground(STOP_FOREGROUND_REMOVE);
          stopSelf();
          return;
        }
        promoteForeground(hasNagging ? nagging.size() : 0, next);
        handler.removeCallbacks(pulse);
        if (hasNagging) {
          fireBurst(nagging, 0L);
          long interval = Prefs.nagIntervalMs(this);
          handler.postDelayed(pulse, interval);
          AlarmScheduler.schedulePulse(this, 0L, System.currentTimeMillis() + interval);
        } else {
          handler.postDelayed(pulse, DESK_REFRESH_MS);
          AlarmScheduler.schedulePulse(this, 0L, System.currentTimeMillis() + DESK_REFRESH_MS);
        }
      });
    });
  }

  private void fireBurst(List<Task> nagging, long focusedId) {
    acquireBurstLock();
    playAlarmSound();
    Alerts.vibrate(this);
    Task focus = null;
    for (Task task : nagging) {
      NotificationHelper.showNagBurst(this, task);
      if (focus == null || task.id == focusedId) {
        focus = task;
      }
    }
    if (focus != null && Prefs.fullScreen(this)) {
      try {
        Intent alarm = new Intent(this, AlarmActivity.class);
        alarm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        alarm.putExtra(ExtraKeys.TASK_ID, focus.id);
        startActivity(alarm);
      } catch (Exception ignored) {
      }
    }
  }

  private void promoteForeground(int count, Task next) {
    Notification notification = NotificationHelper.buildDesk(this, count, next);
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
        NotificationHelper.FGS_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
          | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    } else {
      startForeground(NotificationHelper.FGS_ID, notification);
    }
  }

  private void playAlarmSound() {
    stopAlarmSound();
    if (!Prefs.sound(this)) {
      return;
    }
    try {
      Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
      player = new MediaPlayer();
      player.setAudioAttributes(
        new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build());
      player.setDataSource(this, uri);
      player.setLooping(true);
      player.prepare();
      player.start();
      handler.postDelayed(this::stopAlarmSound, Prefs.burstMs());
    } catch (Exception ignored) {
    }
  }

  private void stopAlarmSound() {
    if (player != null) {
      try {
        player.stop();
      } catch (Exception ignored) {
      }
      try {
        player.release();
      } catch (Exception ignored) {
      }
      player = null;
    }
  }

  private void acquireBurstLock() {
    try {
      if (wakeLock == null) {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
          wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omnipost:nag");
          wakeLock.setReferenceCounted(false);
        }
      }
      if (wakeLock != null) {
        wakeLock.acquire(Prefs.burstMs() + 5_000L);
      }
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    if (keepAlive || Prefs.deskOngoing(this)) {
      AlarmScheduler.schedulePulse(this, 0L, System.currentTimeMillis() + 10_000L);
      try {
        ContextCompat.startForegroundService(this, new Intent(this, NagForegroundService.class));
      } catch (Exception ignored) {
      }
    }
    super.onTaskRemoved(rootIntent);
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    handler.removeCallbacksAndMessages(null);
    stopAlarmSound();
    if (wakeLock != null && wakeLock.isHeld()) {
      try {
        wakeLock.release();
      } catch (Exception ignored) {
      }
    }
    if (keepAlive || Prefs.deskOngoing(this)) {
      AlarmScheduler.schedulePulse(this, 0L, System.currentTimeMillis() + 15_000L);
    }
    super.onDestroy();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
