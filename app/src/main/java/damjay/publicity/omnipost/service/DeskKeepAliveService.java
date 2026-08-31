package damjay.publicity.omnipost.service;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;

/**
 * Optional OEM keep-alive. Does not read the screen or tap other apps.
 * The system binding is what stops Tecno/Infinix from burying OmniPost.
 */
public class DeskKeepAliveService extends AccessibilityService {
  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    NotificationHelper.ensureChannels(this);
    try {
      NagForegroundService.refresh(this);
    } catch (Exception e) {
      Log.e("OmniPost", "keep-alive desk start failed", e);
    }
    AlarmScheduler.scheduleHeartbeat(this);
    AppExecutors.disk().execute(() -> {
      try {
        ScheduleCoordinator.bootstrap(DeskKeepAliveService.this);
      } catch (Exception e) {
        Log.e("OmniPost", "keep-alive bootstrap failed", e);
      }
    });
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    // Intentionally empty: we never inspect other apps.
  }

  @Override
  public void onInterrupt() {}

  @Override
  public void onDestroy() {
    AlarmScheduler.scheduleKick(this);
    AlarmScheduler.scheduleHeartbeat(this);
    super.onDestroy();
  }
}
