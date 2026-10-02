package damjay.publicity.omnipost.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import damjay.publicity.omnipost.databinding.ActivityGateBinding;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import damjay.publicity.omnipost.util.SurvivalHelper;

public class GateActivity extends AppCompatActivity {
  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    NotificationHelper.ensureChannels(this);
    SurvivalHelper.requestPostNotifications(this);

    boolean force = getIntent() != null && getIntent().getBooleanExtra(ExtraKeys.SHOW_GATE, false);
    if (!force) {
      String last = Prefs.lastHome(this);
      if (Prefs.HOME_ALUMNI.equals(last)) {
        goAlumni();
        return;
      }
      if (Prefs.HOME_FELLOWSHIP.equals(last)) {
        goFellowship();
        return;
      }
    }

    ActivityGateBinding binding = ActivityGateBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.cardFellowship.setOnClickListener(v -> goFellowship());
    binding.cardAlumni.setOnClickListener(v -> goAlumni());
  }

  private void goFellowship() {
    Homes.rememberFellowship(this);
    startActivity(Homes.fellowship(this));
    finish();
  }

  private void goAlumni() {
    Homes.rememberAlumni(this);
    Intent incoming = getIntent();
    Intent alumni = Homes.alumni(this);
    if (incoming != null) {
      alumni.putExtras(incoming);
    }
    startActivity(alumni);
    finish();
  }
}
