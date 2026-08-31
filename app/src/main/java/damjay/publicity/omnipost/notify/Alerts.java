package damjay.publicity.omnipost.notify;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import damjay.publicity.omnipost.util.Prefs;

public final class Alerts {
  private Alerts() {}

  public static void vibrate(Context ctx) {
    if (!Prefs.vibrate(ctx)) {
      return;
    }
    long[] pattern = new long[] {0, 400, 200, 400, 200, 800};
    try {
      if (Build.VERSION.SDK_INT >= 31) {
        VibratorManager vm =
          (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        if (vm != null) {
          vm.getDefaultVibrator().vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
      } else {
        Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
          vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
      }
    } catch (Exception ignored) {
    }
  }
}
