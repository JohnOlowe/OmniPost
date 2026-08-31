package damjay.publicity.omnipost;

import android.app.Application;
import android.util.Log;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.service.NagForegroundService;
import damjay.publicity.omnipost.util.AppExecutors;

public class OmniPostApp extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    NotificationHelper.ensureChannels(this);
    try {
      NagForegroundService.refresh(this);
    } catch (Exception e) {
      Log.e("OmniPost", "desk start failed", e);
      AlarmScheduler.scheduleKick(this);
    }
    AlarmScheduler.scheduleHeartbeat(this);
    AppExecutors.disk().execute(() -> {
      try {
        ScheduleCoordinator.bootstrap(OmniPostApp.this);
      } catch (Exception e) {
        Log.e("OmniPost", "bootstrap failed", e);
      }
    });
  }
}
