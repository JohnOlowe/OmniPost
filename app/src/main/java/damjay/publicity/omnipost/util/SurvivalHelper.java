package damjay.publicity.omnipost.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import damjay.publicity.omnipost.notify.NotificationHelper;

public final class SurvivalHelper {
  public static final int REQ_POST_NOTIFICATIONS = 4401;

  private SurvivalHelper() {}

  public static boolean notificationsAllowed(Context context) {
    if (Build.VERSION.SDK_INT < 33) {
      return true;
    }
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
      == PackageManager.PERMISSION_GRANTED;
  }

  public static boolean exactAlarmsAllowed(Context context) {
    if (Build.VERSION.SDK_INT < 31) {
      return true;
    }
    AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    return manager != null && manager.canScheduleExactAlarms();
  }

  public static boolean batteryUnrestricted(Context context) {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
  }

  public static boolean fullScreenAllowed(Context context) {
    return NotificationHelper.canUseFullScreen(context);
  }

  public static boolean allClear(Context context) {
    return notificationsAllowed(context)
      && exactAlarmsAllowed(context)
      && batteryUnrestricted(context);
  }

  public static void requestPostNotifications(Activity activity) {
    if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(activity)) {
      activity.requestPermissions(
        new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }
  }

  public static void openNotificationSettings(Activity activity) {
    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
    activity.startActivity(intent);
  }

  public static void openExactAlarmSettings(Activity activity) {
    if (Build.VERSION.SDK_INT >= 31) {
      Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
      intent.setData(Uri.parse("package:" + activity.getPackageName()));
      activity.startActivity(intent);
    }
  }

  public static void openBatterySettings(Activity activity) {
    try {
      Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
      intent.setData(Uri.parse("package:" + activity.getPackageName()));
      activity.startActivity(intent);
    } catch (Exception e) {
      activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
    }
  }

  public static void openFullScreenSettings(Activity activity) {
    if (Build.VERSION.SDK_INT >= 34) {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
      intent.setData(Uri.parse("package:" + activity.getPackageName()));
      activity.startActivity(intent);
    }
  }
}
