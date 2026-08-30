package damjay.publicity.omnipost;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.badge.BadgeDrawable;
import damjay.publicity.omnipost.databinding.ActivityMainBinding;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.ui.BirthdaysFragment;
import damjay.publicity.omnipost.ui.DraftActivity;
import damjay.publicity.omnipost.ui.DraftsFragment;
import damjay.publicity.omnipost.ui.SurviveFragment;
import damjay.publicity.omnipost.ui.TasksFragment;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.SurvivalHelper;

public class MainActivity extends AppCompatActivity {
  private ActivityMainBinding binding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    NotificationHelper.ensureChannels(this);
    SurvivalHelper.requestPostNotifications(this);
    AppExecutors.disk().execute(() -> ScheduleCoordinator.bootstrap(this));

    if (savedInstanceState == null) {
      show(new TasksFragment());
    }
    binding.bottomNav.setOnItemSelectedListener(item -> {
      int id = item.getItemId();
      if (id == R.id.nav_tasks) {
        show(new TasksFragment());
        return true;
      }
      if (id == R.id.nav_drafts) {
        show(new DraftsFragment());
        return true;
      }
      if (id == R.id.nav_birthdays) {
        show(new BirthdaysFragment());
        return true;
      }
      if (id == R.id.nav_survive) {
        show(new SurviveFragment());
        return true;
      }
      return false;
    });
    handleIncoming(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleIncoming(intent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (SurvivalHelper.allClear(this)) {
      binding.bottomNav.removeBadge(R.id.nav_survive);
    } else {
      BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_survive);
      badge.setVisible(true);
      badge.setBackgroundColor(getColor(R.color.danger));
    }
  }

  private void handleIncoming(Intent intent) {
    if (intent == null) {
      return;
    }
    long taskId = intent.getLongExtra(ExtraKeys.TASK_ID, 0L);
    if (taskId > 0L) {
      Intent draft = new Intent(this, DraftActivity.class);
      draft.putExtra(ExtraKeys.TASK_ID, taskId);
      startActivity(draft);
    }
  }

  private void show(@NonNull Fragment fragment) {
    getSupportFragmentManager()
      .beginTransaction()
      .replace(R.id.fragment_container, fragment)
      .commit();
  }
}
