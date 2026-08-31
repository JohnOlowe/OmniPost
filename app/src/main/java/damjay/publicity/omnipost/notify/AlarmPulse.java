package damjay.publicity.omnipost.notify;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import damjay.publicity.omnipost.util.Prefs;

/**
 * Caption-ready and post-now attention: optional 30s vibrate, then a 5-minute ring
 * until the user taps an action. Lives in-process; the foreground service keeps it alive.
 */
public final class AlarmPulse {
  public static final int IDLE = 0;
  public static final int VIBRATING = 1;
  public static final int RINGING = 2;

  private static final Handler HANDLER = new Handler(Looper.getMainLooper());
  private static final long[] VIBE = new long[] {0, 600, 200, 600, 200, 900, 350};

  private static int state = IDLE;
  private static int generation;
  private static MediaPlayer player;
  private static Vibrator vibrator;

  private AlarmPulse() {}

  public static int state() {
    return state;
  }

  public static boolean isLive() {
    return state != IDLE;
  }

  public static void begin(Context ctx) {
    if (ctx == null) {
      return;
    }
    final Context app = ctx.getApplicationContext();
    if (Looper.myLooper() == Looper.getMainLooper()) {
      startOnMain(app);
    } else {
      HANDLER.post(() -> startOnMain(app));
    }
  }

  /** User tapped Open draft, Posted, snooze, or Keep nagging — do not ring. */
  public static void silence() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      stopOnMain();
    } else {
      HANDLER.post(AlarmPulse::stopOnMain);
    }
  }

  private static void startOnMain(Context app) {
    if (state != IDLE) {
      return;
    }
    final int gen = ++generation;
    String mode = Prefs.alertMode(app);
    long delay = AlertPlan.ringDelayMs(mode);
    if (AlertPlan.vibrate(mode)) {
      state = delay > 0L ? VIBRATING : RINGING;
      startVibrate(app);
    }
    if (AlertPlan.ring(mode)) {
      if (delay <= 0L) {
        startRingOnMain(app, gen);
      } else {
        HANDLER.postDelayed(() -> startRingOnMain(app, gen), delay);
      }
    }
    long total = AlertPlan.totalMs(mode);
    if (total > 0L) {
      HANDLER.postDelayed(() -> finish(gen), total);
    }
  }

  private static void startRingOnMain(Context app, int gen) {
    if (gen != generation) {
      return;
    }
    if (AlertPlan.escalate(Prefs.alertMode(app))) {
      stopVibrate();
    }
    if (!AlertPlan.ring(Prefs.alertMode(app))) {
      return;
    }
    state = RINGING;
    stopPlayer();
    try {
      Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
      if (uri == null) {
        uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
      }
      player = new MediaPlayer();
      player.setAudioAttributes(
        new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build());
      player.setDataSource(app, uri);
      player.setLooping(true);
      player.setVolume(1f, 1f);
      player.prepare();
      player.start();
    } catch (Exception e) {
      stopPlayer();
    }
  }

  private static void startVibrate(Context app) {
    stopVibrate();
    vibrator = vibratorOf(app);
    if (vibrator == null || !vibrator.hasVibrator()) {
      return;
    }
    try {
      vibrator.vibrate(VibrationEffect.createWaveform(VIBE, 0));
    } catch (Exception ignored) {
    }
  }

  private static void finish(int gen) {
    if (gen != generation) {
      return;
    }
    stopOnMain();
  }

  private static void stopOnMain() {
    generation++;
    state = IDLE;
    stopVibrate();
    stopPlayer();
  }

  private static void stopVibrate() {
    if (vibrator != null) {
      try {
        vibrator.cancel();
      } catch (Exception ignored) {
      }
      vibrator = null;
    }
  }

  private static void stopPlayer() {
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

  private static Vibrator vibratorOf(Context ctx) {
    try {
      if (Build.VERSION.SDK_INT >= 31) {
        VibratorManager manager =
          (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        return manager == null ? null : manager.getDefaultVibrator();
      }
      return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    } catch (Exception e) {
      return null;
    }
  }
}
