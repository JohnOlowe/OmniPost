package damjay.publicity.omnipost;

import android.app.Application;
import android.util.Log;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import java.util.List;

public class OmniPostApp extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    NotificationHelper.ensureChannels(this);
    AppExecutors.disk().execute(() -> {
      try {
        List<Task> tasks = AppDatabase.get(this).taskDao().getActiveSync();
        Long maxId = AppDatabase.get(this).taskDao().maxId();
        AlarmScheduler.recycleStale(this, tasks, maxId == null ? 0L : maxId);
        NotificationHelper.recycleStale(this, maxId == null ? 0L : maxId);
        ScheduleCoordinator.bootstrap(this);
      } catch (Exception e) {
        Log.e("OmniPost", "bootstrap failed", e);
      }
    });
  }
}
