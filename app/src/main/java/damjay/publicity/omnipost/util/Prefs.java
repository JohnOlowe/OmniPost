package damjay.publicity.omnipost.util;

import android.content.Context;
import android.content.SharedPreferences;
import damjay.publicity.omnipost.scheduler.ScheduleTimes;

public final class Prefs {
  public static final String MODE_BOTH = "both";
  public static final String MODE_SOUND = "sound";
  public static final String MODE_VIBRATE = "vibrate";

  private static final String FILE = "omnipost.prefs";
  private static final String ALERT = "alert_mode";
  private static final String NAG_MIN = "nag_minutes";
  private static final String WARN_MIN = "warning_minutes";
  private static final String SEED = "seed_captions";
  private static final String DESK = "desk_ongoing";
  private static final String FULLSCREEN = "full_screen";
  private static final String LAST_NAG = "last_nag_burst";

  private Prefs() {}

  private static SharedPreferences sp(Context ctx) {
    return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
  }

  public static String alertMode(Context ctx) {
    return sp(ctx).getString(ALERT, MODE_BOTH);
  }

  public static void setAlertMode(Context ctx, String mode) {
    sp(ctx).edit().putString(ALERT, mode).apply();
  }

  public static boolean sound(Context ctx) {
    String mode = alertMode(ctx);
    return MODE_BOTH.equals(mode) || MODE_SOUND.equals(mode);
  }

  public static boolean vibrate(Context ctx) {
    String mode = alertMode(ctx);
    return MODE_BOTH.equals(mode) || MODE_VIBRATE.equals(mode);
  }

  public static int nagMinutes(Context ctx) {
    return sp(ctx).getInt(NAG_MIN, 5);
  }

  public static void setNagMinutes(Context ctx, int minutes) {
    sp(ctx).edit().putInt(NAG_MIN, minutes).apply();
  }

  public static long nagIntervalMs(Context ctx) {
    return nagMinutes(ctx) * 60_000L;
  }

  public static int warningMinutes(Context ctx) {
    return sp(ctx).getInt(WARN_MIN, 30);
  }

  public static void setWarningMinutes(Context ctx, int minutes) {
    sp(ctx).edit().putInt(WARN_MIN, minutes).apply();
  }

  public static long warningLeadMs(Context ctx) {
    return warningMinutes(ctx) * 60_000L;
  }

  public static boolean seedCaptions(Context ctx) {
    return sp(ctx).getBoolean(SEED, false);
  }

  public static void setSeedCaptions(Context ctx, boolean on) {
    sp(ctx).edit().putBoolean(SEED, on).apply();
  }

  public static boolean deskOngoing(Context ctx) {
    return sp(ctx).getBoolean(DESK, true);
  }

  public static void setDeskOngoing(Context ctx, boolean on) {
    sp(ctx).edit().putBoolean(DESK, on).apply();
  }

  public static boolean fullScreen(Context ctx) {
    return sp(ctx).getBoolean(FULLSCREEN, true);
  }

  public static void setFullScreen(Context ctx, boolean on) {
    sp(ctx).edit().putBoolean(FULLSCREEN, on).apply();
  }

  public static long burstMs() {
    return ScheduleTimes.BURST_MS;
  }

  public static long lastNagBurstAt(Context ctx) {
    return sp(ctx).getLong(LAST_NAG, 0L);
  }

  public static void setLastNagBurstAt(Context ctx, long when) {
    sp(ctx).edit().putLong(LAST_NAG, when).apply();
  }
}
