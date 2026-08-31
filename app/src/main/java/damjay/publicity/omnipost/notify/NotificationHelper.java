package damjay.publicity.omnipost.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import damjay.publicity.omnipost.MainActivity;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.receiver.MarkPostedReceiver;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.ui.AlarmActivity;
import damjay.publicity.omnipost.ui.DraftActivity;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;

public final class NotificationHelper {
  public static final String CHANNEL_DRAFT = "omnipost.draft.v1";
  public static final String CHANNEL_ALARM = "omnipost.alarm.v1";
  public static final String CHANNEL_ONGOING = "omnipost.ongoing.v2";
  public static final int FGS_ID = 42;

  private NotificationHelper() {}

  public static void ensureChannels(Context context) {
    NotificationManager manager =
      (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null) {
      return;
    }
    Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    AudioAttributes alarmAttrs = new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build();

    NotificationChannel draft = new NotificationChannel(
      CHANNEL_DRAFT, "Drafting reminders", NotificationManager.IMPORTANCE_HIGH);
    draft.setDescription("Time to write the caption");
    draft.enableVibration(true);
    draft.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

    NotificationChannel alarmChannel = new NotificationChannel(
      CHANNEL_ALARM, "Posting alarms", NotificationManager.IMPORTANCE_HIGH);
    alarmChannel.setDescription("Caption-ready warning and nag until Posted");
    alarmChannel.enableVibration(true);
    alarmChannel.setSound(alarm, alarmAttrs);
    alarmChannel.setBypassDnd(true);
    alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

    NotificationChannel ongoing = new NotificationChannel(
      CHANNEL_ONGOING, "Desk (cannot dismiss)", NotificationManager.IMPORTANCE_LOW);
    ongoing.setDescription("Always-on next item on the posting desk");
    ongoing.setSound(null, null);
    ongoing.enableVibration(false);
    ongoing.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

    manager.createNotificationChannel(draft);
    manager.createNotificationChannel(alarmChannel);
    manager.createNotificationChannel(ongoing);
  }

  public static Notification buildDesk(Context ctx, int nagCount, Task next) {
    ensureChannels(ctx);
    Intent open = new Intent(ctx, MainActivity.class);
    open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent content = PendingIntent.getActivity(
      ctx, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    String title;
    String text;
    if (next != null) {
      title = ctx.getString(R.string.next_up_title);
      long now = System.currentTimeMillis();
      long when = TaskStatus.nextRingMillis(next, now, Prefs.warningLeadMs(ctx));
      if (when <= now) {
        text = next.title + " · " + ctx.getString(R.string.post_now);
      } else {
        text = next.title + " · " + DateUtils.formatStamp(when) + " · " + DateUtils.formatUntil(when, now);
      }
    } else if (nagCount > 0) {
      title = ctx.getString(R.string.nag_ongoing_title);
      text = ctx.getString(R.string.nag_ongoing_text, nagCount);
    } else {
      title = ctx.getString(R.string.desk_armed_title);
      text = ctx.getString(R.string.desk_armed_text);
    }
    return new NotificationCompat.Builder(ctx, CHANNEL_ONGOING)
      .setSmallIcon(R.drawable.ic_stat_omnipost)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
      .setOngoing(true)
      .setAutoCancel(false)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(content)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setColor(0xFFE8C36A)
      .build();
  }

  public static void showDraft(Context ctx, Task task) {
    showAlarm(ctx, task, AlarmScheduler.PHASE_DRAFT,
      ctx.getString(R.string.write_caption_now),
      ctx.getString(R.string.draft_notif_body));
  }

  public static void showWarning(Context ctx, Task task) {
    showAlarm(ctx, task, AlarmScheduler.PHASE_WARNING,
      ctx.getString(R.string.caption_ready_phase, Prefs.warningMinutes(ctx)),
      ctx.getString(R.string.warning_notif_text));
  }

  public static void showNagBurst(Context ctx, Task task) {
    showAlarm(ctx, task, AlarmScheduler.PHASE_NAG,
      ctx.getString(R.string.post_now),
      ctx.getString(R.string.nag_notif_text));
  }

  private static void showAlarm(Context ctx, Task task, int phase, String title, String body) {
    ensureChannels(ctx);
    PendingIntent fullScreen = fullScreen(ctx, task.id, phase);
    NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ALARM)
      .setSmallIcon(R.drawable.ic_stat_omnipost)
      .setContentTitle(title)
      .setContentText(task.title + " · " + DateUtils.formatStamp(task.postAtMillis))
      .setStyle(new NotificationCompat.BigTextStyle()
        .bigText(task.title + "\n" + body + "\n" + DateUtils.formatStamp(task.postAtMillis)))
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setAutoCancel(false)
      .setContentIntent(fullScreen)
      .addAction(0, ctx.getString(R.string.open_draft), openDraft(ctx, task.id))
      .addAction(0, ctx.getString(R.string.mark_posted), markPosted(ctx, task.id))
      .setColor(0xFFFF4D4D)
      .setSilent(!Prefs.sound(ctx));
    if (Prefs.fullScreen(ctx)) {
      builder.setFullScreenIntent(fullScreen, true);
    }
    if (Prefs.vibrate(ctx)) {
      builder.setVibrate(new long[] {0, 400, 200, 400, 200, 800});
    } else {
      builder.setVibrate(new long[] {0});
    }
    notify(ctx, alarmId(task.id), builder.build());
  }

  public static void cancelForTask(Context ctx, long taskId) {
    NotificationManager manager =
      (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager == null) {
      return;
    }
    manager.cancel(draftId(taskId));
    manager.cancel(alarmId(taskId));
  }

  public static void cancelOngoing(Context ctx) {
    NotificationManager manager =
      (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager != null) {
      manager.cancel(FGS_ID);
    }
  }

  private static void notify(Context ctx, int id, Notification notification) {
    NotificationManager manager =
      (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager != null) {
      manager.notify(id, notification);
    }
  }

  private static PendingIntent openDraft(Context ctx, long taskId) {
    Intent intent = new Intent(ctx, DraftActivity.class);
    intent.putExtra(ExtraKeys.TASK_ID, taskId);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(
      ctx,
      (int) (3000 + taskId),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private static PendingIntent markPosted(Context ctx, long taskId) {
    Intent intent = new Intent(ctx, MarkPostedReceiver.class);
    intent.setAction(MarkPostedReceiver.ACTION);
    intent.putExtra(ExtraKeys.TASK_ID, taskId);
    return PendingIntent.getBroadcast(
      ctx,
      (int) (4000 + taskId),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private static PendingIntent fullScreen(Context ctx, long taskId, int phase) {
    Intent intent = new Intent(ctx, AlarmActivity.class);
    intent.putExtra(ExtraKeys.TASK_ID, taskId);
    intent.putExtra(ExtraKeys.PHASE, phase);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(
      ctx,
      (int) (5000 + taskId),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }

  private static int draftId(long taskId) {
    return (int) (10000 + (taskId & 0xffff));
  }

  private static int alarmId(long taskId) {
    return (int) (20000 + (taskId & 0xffff));
  }

  public static boolean canUseFullScreen(Context ctx) {
    if (Build.VERSION.SDK_INT < 34) {
      return true;
    }
    NotificationManager manager =
      (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    return manager != null && manager.canUseFullScreenIntent();
  }
}
