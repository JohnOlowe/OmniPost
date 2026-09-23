package damjay.publicity.omnipost.util;

import android.content.Context;
import android.content.SharedPreferences;
import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.share.InstagramStyle;

public final class Prefs {
  public static final String MODE_ESCALATE = "escalate";
  public static final String MODE_BOTH = "both";
  public static final String MODE_SOUND = "sound";
  public static final String MODE_VIBRATE = "vibrate";

  private static final String FILE = "omnipost.prefs";
  private static final String ALERT = "alert_mode";
  private static final String NAG_MIN = "nag_minutes";
  private static final String WARN_MIN = "warning_minutes";
  private static final String DRAFT_HOUR = "draft_hour";
  private static final String DRAFT_LEAD = "draft_lead_days";
  private static final String VIBRATE_SEC = "vibrate_seconds";
  private static final String RING_MIN = "ring_minutes";
  private static final String ALUMNI_SKIP = "alumni_skip_caption";
  private static final String SEED = "seed_captions";
  private static final String DESK = "desk_ongoing";
  private static final String FULLSCREEN = "full_screen";
  private static final String LAST_NAG = "last_nag_burst";
  private static final String SERIES_SEEDED = "series_defaults_v1";
  private static final String ALUMNI_SEEDED = "alumni_roster_v1";
  private static final String IG_BOLD = "ig_bold_face";
  private static final String IG_ITALIC = "ig_italic_face";
  private static final String IG_BOTH = "ig_both_face";

  private Prefs() {}

  private static SharedPreferences sp(Context ctx) {
    return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
  }

  public static String alertMode(Context ctx) {
    return sp(ctx).getString(ALERT, MODE_ESCALATE);
  }

  public static void setAlertMode(Context ctx, String mode) {
    sp(ctx).edit().putString(ALERT, mode).apply();
  }

  public static boolean escalate(Context ctx) {
    return MODE_ESCALATE.equals(alertMode(ctx));
  }

  public static boolean sound(Context ctx) {
    String mode = alertMode(ctx);
    return MODE_ESCALATE.equals(mode) || MODE_BOTH.equals(mode) || MODE_SOUND.equals(mode);
  }

  public static boolean vibrate(Context ctx) {
    String mode = alertMode(ctx);
    return MODE_ESCALATE.equals(mode) || MODE_BOTH.equals(mode) || MODE_VIBRATE.equals(mode);
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
    int minutes = warningMinutes(ctx);
    if (minutes <= 0) {
      return 0L;
    }
    return minutes * 60_000L;
  }

  /** Hour (0–23) the day before the post when OmniPost nudges you to write the caption. */
  public static int draftHour(Context ctx) {
    return sp(ctx).getInt(DRAFT_HOUR, ScheduleTimes.EVENING_DRAFT_HOUR);
  }

  public static void setDraftHour(Context ctx, int hour) {
    sp(ctx).edit().putInt(DRAFT_HOUR, hour).apply();
  }

  /** 0 = morning of the post, 1 = evening before, 2 = two evenings before. */
  public static int draftLeadDays(Context ctx) {
    return sp(ctx).getInt(DRAFT_LEAD, 1);
  }

  public static void setDraftLeadDays(Context ctx, int days) {
    int lead = days;
    if (lead < 0) {
      lead = 0;
    }
    if (lead > 7) {
      lead = 7;
    }
    sp(ctx).edit().putInt(DRAFT_LEAD, lead).apply();
  }

  public static int vibrateSeconds(Context ctx) {
    int seconds = sp(ctx).getInt(VIBRATE_SEC, 30);
    if (seconds < 10) {
      return 10;
    }
    if (seconds > 120) {
      return 120;
    }
    return seconds;
  }

  public static void setVibrateSeconds(Context ctx, int seconds) {
    int value = seconds;
    if (value < 10) {
      value = 10;
    }
    if (value > 120) {
      value = 120;
    }
    sp(ctx).edit().putInt(VIBRATE_SEC, value).apply();
  }

  public static long vibrateMs(Context ctx) {
    return vibrateSeconds(ctx) * 1000L;
  }

  public static int ringMinutes(Context ctx) {
    int minutes = sp(ctx).getInt(RING_MIN, 5);
    if (minutes < 1) {
      return 1;
    }
    if (minutes > 15) {
      return 15;
    }
    return minutes;
  }

  public static void setRingMinutes(Context ctx, int minutes) {
    int value = minutes;
    if (value < 1) {
      value = 1;
    }
    if (value > 15) {
      value = 15;
    }
    sp(ctx).edit().putInt(RING_MIN, value).apply();
  }

  public static long ringMs(Context ctx) {
    return ringMinutes(ctx) * 60_000L;
  }

  public static boolean alumniSkipCaption(Context ctx) {
    return sp(ctx).getBoolean(ALUMNI_SKIP, true);
  }

  public static void setAlumniSkipCaption(Context ctx, boolean skip) {
    sp(ctx).edit().putBoolean(ALUMNI_SKIP, skip).apply();
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

  public static boolean seriesDefaultsInstalled(Context ctx) {
    return sp(ctx).getBoolean(SERIES_SEEDED, false);
  }

  public static void setSeriesDefaultsInstalled(Context ctx, boolean on) {
    sp(ctx).edit().putBoolean(SERIES_SEEDED, on).apply();
  }

  public static boolean alumniRosterInstalled(Context ctx) {
    return sp(ctx).getBoolean(ALUMNI_SEEDED, false);
  }

  public static void setAlumniRosterInstalled(Context ctx, boolean on) {
    sp(ctx).edit().putBoolean(ALUMNI_SEEDED, on).apply();
  }

  public static InstagramStyle.Faces instagramFaces(Context ctx) {
    return new InstagramStyle.Faces(instagramBoldFace(ctx), instagramItalicFace(ctx), instagramBothFace(ctx));
  }

  public static String instagramBoldFace(Context ctx) {
    return InstagramStyle.Faces.normalize(
      sp(ctx).getString(IG_BOLD, InstagramStyle.FACE_SANS), InstagramStyle.FACE_SANS);
  }

  public static void setInstagramBoldFace(Context ctx, String face) {
    sp(ctx).edit().putString(IG_BOLD, InstagramStyle.Faces.normalize(face, InstagramStyle.FACE_SANS)).apply();
  }

  public static String instagramItalicFace(Context ctx) {
    return InstagramStyle.Faces.normalize(
      sp(ctx).getString(IG_ITALIC, InstagramStyle.FACE_SERIF), InstagramStyle.FACE_SERIF);
  }

  public static void setInstagramItalicFace(Context ctx, String face) {
    sp(ctx).edit().putString(IG_ITALIC, InstagramStyle.Faces.normalize(face, InstagramStyle.FACE_SERIF)).apply();
  }

  public static String instagramBothFace(Context ctx) {
    return InstagramStyle.Faces.normalize(
      sp(ctx).getString(IG_BOTH, InstagramStyle.FACE_SANS), InstagramStyle.FACE_SANS);
  }

  public static void setInstagramBothFace(Context ctx, String face) {
    sp(ctx).edit().putString(IG_BOTH, InstagramStyle.Faces.normalize(face, InstagramStyle.FACE_SANS)).apply();
  }
}
