package damjay.publicity.omnipost.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;

public class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    final PendingResult pending = goAsync();
    final Context app = context.getApplicationContext();
    NotificationHelper.ensureChannels(app);
    AppExecutors.disk().execute(() -> {
      try {
        ScheduleCoordinator.bootstrap(app);
      } catch (Exception e) {
        Log.e("OmniPost", "boot reschedule failed", e);
      } finally {
        pending.finish();
      }
    });
  }
}
