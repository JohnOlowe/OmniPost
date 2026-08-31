package damjay.publicity.omnipost.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;

public class MarkPostedReceiver extends BroadcastReceiver {
  public static final String ACTION = "damjay.publicity.omnipost.action.MARK_POSTED";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }
    final long taskId = intent.getLongExtra(ExtraKeys.TASK_ID, 0L);
    if (taskId == 0L) {
      return;
    }
    final Context app = context.getApplicationContext();
    try {
      NagForegroundService.refresh(app);
    } catch (Exception ignored) {
    }
    final PendingResult pending = goAsync();
    AppExecutors.disk().execute(() -> {
      try {
        ScheduleCoordinator.markPosted(app, taskId);
      } catch (Exception e) {
        Log.e("OmniPost", "mark posted failed", e);
      } finally {
        pending.finish();
      }
    });
  }
}
