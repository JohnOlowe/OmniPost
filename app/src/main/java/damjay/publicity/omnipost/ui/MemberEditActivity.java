package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;

/** Full-screen scrolling editor so Previous pastor and captions are never off-screen. */
public class MemberEditActivity extends AppCompatActivity {
  private long memberId;
  private boolean pastorDefault;
  private TextInputEditText name;
  private TextInputEditText phone;
  private TextInputEditText honorific;
  private TextInputEditText captionHnm;
  private TextInputEditText captionDetails;
  private Spinner month;
  private Spinner day;
  private Spinner photo;
  private Spinner gender;
  private CheckBox skipCaption;
  private CheckBox previousPastor;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_member_edit);
    memberId = getIntent().getLongExtra(ExtraKeys.MEMBER_ID, 0L);
    pastorDefault = getIntent().getBooleanExtra(ExtraKeys.PASTOR_DEFAULT, false);
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    findViewById(R.id.btn_save).setOnClickListener(v -> save());
    bindForm();
    if (memberId > 0L) {
      AppExecutors.disk().execute(() -> {
        Member existing = AppDatabase.get(this).memberDao().getById(memberId);
        AppExecutors.main(() -> fill(existing));
      });
    } else {
      skipCaption.setChecked(Prefs.alumniSkipCaption(this));
      previousPastor.setChecked(pastorDefault);
      android.widget.TextView title = findViewById(R.id.title);
      title.setText(pastorDefault ? R.string.add_pastor : R.string.add_alumni);
    }
  }

  private void bindForm() {
    name = findViewById(R.id.input_name);
    phone = findViewById(R.id.input_phone);
    honorific = findViewById(R.id.input_honorific);
    captionHnm = findViewById(R.id.input_caption_hnm);
    captionDetails = findViewById(R.id.input_caption_details);
    month = findViewById(R.id.spinner_month);
    day = findViewById(R.id.spinner_day);
    photo = findViewById(R.id.spinner_photo);
    gender = findViewById(R.id.spinner_gender);
    skipCaption = findViewById(R.id.skip_caption);
    previousPastor = findViewById(R.id.previous_pastor);
    ArrayAdapter<CharSequence> months = ArrayAdapter.createFromResource(
      this, R.array.months, R.layout.spinner_item);
    months.setDropDownViewResource(R.layout.spinner_item);
    month.setAdapter(months);
    String[] days = new String[31];
    for (int i = 0; i < 31; i++) {
      days[i] = String.valueOf(i + 1);
    }
    ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, days);
    dayAdapter.setDropDownViewResource(R.layout.spinner_item);
    day.setAdapter(dayAdapter);
    ArrayAdapter<CharSequence> photos = ArrayAdapter.createFromResource(
      this, R.array.alumni_photo_status, R.layout.spinner_item);
    photos.setDropDownViewResource(R.layout.spinner_item);
    photo.setAdapter(photos);
    ArrayAdapter<CharSequence> genders = ArrayAdapter.createFromResource(
      this, R.array.alumni_gender, R.layout.spinner_item);
    genders.setDropDownViewResource(R.layout.spinner_item);
    gender.setAdapter(genders);
    findViewById(R.id.layout_honorific).setVisibility(View.VISIBLE);
    findViewById(R.id.layout_caption_hnm).setVisibility(View.VISIBLE);
    findViewById(R.id.layout_caption_details).setVisibility(View.VISIBLE);
    previousPastor.setVisibility(View.VISIBLE);
  }

  private void fill(Member existing) {
    if (existing == null) {
      Toast.makeText(this, R.string.alumni_desk_empty, Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    android.widget.TextView title = findViewById(R.id.title);
    title.setText(R.string.edit_alumni);
    name.setText(existing.name);
    month.setSelection(Math.max(0, existing.birthMonth - 1));
    day.setSelection(Math.max(0, existing.birthDay - 1));
    skipCaption.setChecked(existing.skipCaption);
    previousPastor.setChecked(Member.isPastor(existing));
    if (existing.phone != null) {
      phone.setText(existing.phone);
    }
    if (existing.honorific != null) {
      honorific.setText(existing.honorific);
    }
    if (existing.captionHnm != null) {
      captionHnm.setText(existing.captionHnm);
    }
    if (existing.captionDetails != null) {
      captionDetails.setText(existing.captionDetails);
    }
    photo.setSelection(AlumniDesk.photoSpinnerIndex(existing));
    if (Member.GENDER_MALE.equals(existing.gender)) {
      gender.setSelection(1);
    } else if (Member.GENDER_FEMALE.equals(existing.gender)) {
      gender.setSelection(2);
    } else {
      gender.setSelection(0);
    }
  }

  private void save() {
    String value = name.getText() == null ? "" : name.getText().toString().trim();
    if (value.isEmpty()) {
      return;
    }
    int month1 = month.getSelectedItemPosition() + 1;
    int dayOfMonth = day.getSelectedItemPosition() + 1;
    int max = maxDay(month1);
    if (dayOfMonth > max) {
      dayOfMonth = max;
    }
    final int birthMonth = month1;
    final int birthDay = dayOfMonth;
    final boolean pastor = previousPastor.isChecked();
    final boolean skip = skipCaption.isChecked();
    String rawPhone = phone.getText() == null ? "" : phone.getText().toString().trim();
    if (AlumniDesk.nigeriaDigits(rawPhone).length() == 13) {
      rawPhone = AlumniDesk.nigeriaDigits(rawPhone);
    }
    final String phoneValue = rawPhone;
    final String honor = honorific.getText() == null ? "" : honorific.getText().toString().trim();
    final String hnm = captionHnm.getText() == null ? "" : captionHnm.getText().toString();
    final String details = captionDetails.getText() == null ? "" : captionDetails.getText().toString();
    final String photoStatus = AlumniDesk.photoStatusFromIndex(photo.getSelectedItemPosition());
    int g = gender.getSelectedItemPosition();
    final String genderValue = g == 1 ? Member.GENDER_MALE : g == 2 ? Member.GENDER_FEMALE : "";
    final long id = memberId;
    AppExecutors.disk().execute(() -> {
      AppDatabase db = AppDatabase.get(this);
      Member member = id > 0L ? db.memberDao().getById(id) : null;
      boolean merged = false;
      if (member == null) {
        Member prior = db.memberDao().findByKindAndNameIgnoreCase(Member.KIND_ALUMNI, value);
        if (prior != null) {
          member = prior;
          merged = true;
        } else {
          member = new Member();
          member.kind = Member.KIND_ALUMNI;
        }
      }
      member.name = value;
      member.kind = Member.KIND_ALUMNI;
      member.birthMonth = birthMonth;
      member.birthDay = birthDay;
      member.skipCaption = skip;
      member.desk = pastor ? Member.DESK_PASTOR : "";
      member.phone = phoneValue;
      member.honorific = honor;
      member.captionHnm = hnm.trim();
      member.captionDetails = details.trim();
      member.photoStatus = photoStatus;
      member.gender = genderValue;
      if (member.id == 0L) {
        member.id = db.memberDao().insert(member);
      } else {
        db.memberDao().update(member);
        ScheduleCoordinator.cancelMemberTasks(this, member.id);
      }
      ScheduleCoordinator.bootstrap(this);
      boolean showMerged = merged;
      AppExecutors.main(() -> {
        if (showMerged) {
          Toast.makeText(this, R.string.alumni_filled_existing, Toast.LENGTH_LONG).show();
        }
        finish();
      });
    });
  }

  private static int maxDay(int month1to12) {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.MONTH, month1to12 - 1);
    c.set(Calendar.DAY_OF_MONTH, 1);
    return c.getActualMaximum(Calendar.DAY_OF_MONTH);
  }
}
