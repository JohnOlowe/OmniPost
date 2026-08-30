package damjay.publicity.omnipost.util;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AppExecutors {
  private static final Executor DISK = Executors.newSingleThreadExecutor();
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private AppExecutors() {}

  public static Executor disk() {
    return DISK;
  }

  public static void main(Runnable runnable) {
    MAIN.post(runnable);
  }
}
