package damjay.publicity.omnipost.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.databinding.ActivityAlumniHomeBinding;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.SurvivalHelper;

public class AlumniHomeActivity extends AppCompatActivity {
  private ActivityAlumniHomeBinding binding;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Homes.rememberAlumni(this);
    binding = ActivityAlumniHomeBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    NotificationHelper.ensureChannels(this);
    SurvivalHelper.requestPostNotifications(this);
    AppExecutors.disk().execute(() -> ScheduleCoordinator.bootstrap(this));
    binding.btnSwitchHome.setOnClickListener(v -> {
      Homes.rememberFellowship(this);
      startActivity(Homes.fellowship(this));
      finish();
    });
    if (savedInstanceState == null) {
      show(new AlumniMonthFragment());
    }
    binding.bottomNav.setOnItemSelectedListener(item -> {
      int id = item.getItemId();
      if (id == R.id.nav_month) {
        show(new AlumniMonthFragment());
        return true;
      }
      if (id == R.id.nav_people) {
        show(BirthdaysFragment.alumniPeople());
        return true;
      }
      if (id == R.id.nav_captions) {
        show(new AlumniCaptionsFragment());
        return true;
      }
      return false;
    });
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    binding.bottomNav.setSelectedItemId(R.id.nav_month);
    show(new AlumniMonthFragment());
  }

  private void show(@NonNull Fragment fragment) {
    getSupportFragmentManager()
      .beginTransaction()
      .replace(R.id.fragment_container, fragment)
      .commit();
  }
}
