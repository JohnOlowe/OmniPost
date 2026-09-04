package damjay.publicity.omnipost.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import damjay.publicity.omnipost.notify.AlarmLaunch;
import damjay.publicity.omnipost.service.NagForegroundService;

/**
 * AlarmManager entry point. Vibrate/ring and pop the alarm screen on this
 * thread — do not wait for Room or the foreground service. Then start FGS so
 * the process stays promoted after onReceive returns.
 */
public class TaskAlarmReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    Context app = context.getApplicationContext();
    hold(app);
    try {
      AlarmLaunch.fromIntent(app, intent);
    } catch (Exception e) {
      Log.e("OmniPost", "alarm UI failed", e);
    }
    try {
      NagForegroundService.deliverAlarm(app, intent);
    } catch (Exception e) {
      Log.e("OmniPost", "alarm deliver failed", e);
    }
  }

  private static void hold(Context app) {
    try {
      PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
      if (pm == null) {
        return;
      }
      PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "omnipost:alarm");
      lock.setReferenceCounted(false);
      lock.acquire(15_000L);
    } catch (Exception ignored) {
    }
  }
}
