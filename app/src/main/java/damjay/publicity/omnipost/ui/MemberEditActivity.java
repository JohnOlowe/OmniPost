package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.scheduler.AlumniDesk;
import damjay.publicity.omnipost.scheduler.AlumniSheet;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;

/**
 * Full-screen scrolling editor. Tap an existing card: fields paint from the
 * intent immediately so the form is never blank while Room loads. Pairing
 * keeps the roster {@code name} and writes the sheet into first/last — pick
 * which one captions use.
 */
public class MemberEditActivity extends AppCompatActivity {
  static final String EXTRA_NAME = "extra_member_name";
  static final String EXTRA_PHONE = "extra_member_phone";
  static final String EXTRA_GENDER = "extra_member_gender";
  static final String EXTRA_HONORIFIC = "extra_member_honorific";
  static final String EXTRA_EMAIL = "extra_member_email";
  static final String EXTRA_POSITION = "extra_member_position";
  static final String EXTRA_GRAD = "extra_member_grad";
  static final String EXTRA_FIRST = "extra_member_first";
  static final String EXTRA_LAST = "extra_member_last";
  static final String EXTRA_MONTH = "extra_member_month";
  static final String EXTRA_DAY = "extra_member_day";
  static final String EXTRA_HNM = "extra_member_hnm";
  static final String EXTRA_DETAILS = "extra_member_details";
  static final String EXTRA_PHOTO = "extra_member_photo";
  static final String EXTRA_PASTOR = "extra_member_pastor";
  static final String EXTRA_SKIP = "extra_member_skip";
  static final String EXTRA_SAVED = "extra_member_saved";

  private long memberId;
  private boolean pastorDefault;
  private TextInputEditText name;
  private TextInputEditText phone;
  private TextInputEditText honorific;
  private TextInputEditText email;
  private TextInputEditText position;
  private TextInputEditText gradSet;
  private TextInputEditText captionHnm;
  private TextInputEditText captionDetails;
  private Spinner month;
  private Spinner day;
  private Spinner photo;
  private Spinner gender;
  private CheckBox skipCaption;
  private CheckBox previousPastor;
  private CheckBox contactSaved;
  private View nameChoiceBox;
  private RadioGroup nameChoice;
  private RadioButton nameRoster;
  private RadioButton nameSheet;
  private String rosterName = "";
  private String sheetName = "";
  private boolean saving;

  public static void open(Context context, @Nullable Member member, boolean pastorDefault) {
    Intent intent = new Intent(context, MemberEditActivity.class);
    intent.putExtra(ExtraKeys.PASTOR_DEFAULT, pastorDefault);
    if (member != null) {
      intent.putExtra(ExtraKeys.MEMBER_ID, member.id);
      intent.putExtra(EXTRA_NAME, nz(member.name));
      intent.putExtra(EXTRA_PHONE, nz(member.phone));
      intent.putExtra(EXTRA_GENDER, nz(member.gender));
      intent.putExtra(EXTRA_HONORIFIC, nz(member.honorific));
      intent.putExtra(EXTRA_EMAIL, nz(member.email));
      intent.putExtra(EXTRA_POSITION, nz(member.positionHeld));
      intent.putExtra(EXTRA_GRAD, nz(member.gradSet));
      intent.putExtra(EXTRA_FIRST, nz(member.firstName));
      intent.putExtra(EXTRA_LAST, nz(member.lastName));
      intent.putExtra(EXTRA_MONTH, member.birthMonth);
      intent.putExtra(EXTRA_DAY, member.birthDay);
      intent.putExtra(EXTRA_HNM, nz(member.captionHnm));
      intent.putExtra(EXTRA_DETAILS, nz(member.captionDetails));
      intent.putExtra(EXTRA_PHOTO, nz(member.photoStatus));
      intent.putExtra(EXTRA_PASTOR, Member.isPastor(member));
      intent.putExtra(EXTRA_SKIP, member.skipCaption);
      intent.putExtra(EXTRA_SAVED, member.contactSaved);
    }
    context.startActivity(intent);
  }

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
      paintFromIntent();
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
    email = findViewById(R.id.input_email);
    position = findViewById(R.id.input_position);
    gradSet = findViewById(R.id.input_grad);
    captionHnm = findViewById(R.id.input_caption_hnm);
    captionDetails = findViewById(R.id.input_caption_details);
    month = findViewById(R.id.spinner_month);
    day = findViewById(R.id.spinner_day);
    photo = findViewById(R.id.spinner_photo);
    gender = findViewById(R.id.spinner_gender);
    skipCaption = findViewById(R.id.skip_caption);
    previousPastor = findViewById(R.id.previous_pastor);
    contactSaved = findViewById(R.id.contact_saved);
    nameChoiceBox = findViewById(R.id.name_choice_box);
    nameChoice = findViewById(R.id.name_choice);
    nameRoster = findViewById(R.id.name_roster);
    nameSheet = findViewById(R.id.name_sheet);
    contactSaved.setVisibility(View.VISIBLE);
    showAlumniExtras();
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
    if (nameChoice != null) {
      nameChoice.setOnCheckedChangeListener((group, checkedId) -> applyNameChoice());
    }
  }

  private void showAlumniExtras() {
    View emailLayout = findViewById(R.id.layout_email);
    View positionLayout = findViewById(R.id.layout_position);
    View gradLayout = findViewById(R.id.layout_grad);
    if (emailLayout != null) {
      emailLayout.setVisibility(View.VISIBLE);
    }
    if (positionLayout != null) {
      positionLayout.setVisibility(View.VISIBLE);
    }
    if (gradLayout != null) {
      gradLayout.setVisibility(View.VISIBLE);
    }
  }

  private void paintFromIntent() {
    Intent intent = getIntent();
    rosterName = nz(intent.getStringExtra(EXTRA_NAME));
    sheetName = AlumniSheet.displayName(
      intent.getStringExtra(EXTRA_FIRST), intent.getStringExtra(EXTRA_LAST));
    name.setText(rosterName);
    phone.setText(AlumniDesk.displayPhone(nz(intent.getStringExtra(EXTRA_PHONE))));
    honorific.setText(nz(intent.getStringExtra(EXTRA_HONORIFIC)));
    setText(email, intent.getStringExtra(EXTRA_EMAIL));
    setText(position, intent.getStringExtra(EXTRA_POSITION));
    setText(gradSet, intent.getStringExtra(EXTRA_GRAD));
    captionHnm.setText(nz(intent.getStringExtra(EXTRA_HNM)));
    captionDetails.setText(nz(intent.getStringExtra(EXTRA_DETAILS)));
    setMonthDay(intent.getIntExtra(EXTRA_MONTH, 0), intent.getIntExtra(EXTRA_DAY, 0));
    setGender(intent.getStringExtra(EXTRA_GENDER));
    setPhoto(intent.getStringExtra(EXTRA_PHOTO));
    previousPastor.setChecked(intent.getBooleanExtra(EXTRA_PASTOR, pastorDefault));
    skipCaption.setChecked(intent.getBooleanExtra(EXTRA_SKIP, false));
    contactSaved.setChecked(intent.getBooleanExtra(EXTRA_SAVED, false));
    android.widget.TextView title = findViewById(R.id.title);
    title.setText(R.string.edit_alumni);
    paintNameChoice(rosterName);
  }

  private void paintNameChoice(String current) {
    boolean differ = AlumniSheet.namesDiffer(rosterName, sheetName);
    if (nameChoiceBox == null) {
      return;
    }
    nameChoiceBox.setVisibility(differ ? View.VISIBLE : View.GONE);
    if (!differ) {
      return;
    }
    nameRoster.setText(getString(R.string.alumni_use_roster, rosterName));
    nameSheet.setText(getString(R.string.alumni_use_sheet, sheetName));
    boolean sheetPicked = current != null && current.trim().equalsIgnoreCase(sheetName);
    if (nameChoice != null) {
      nameChoice.setOnCheckedChangeListener(null);
    }
    nameSheet.setChecked(sheetPicked);
    nameRoster.setChecked(!sheetPicked);
    if (nameChoice != null) {
      nameChoice.setOnCheckedChangeListener((group, checkedId) -> applyNameChoice());
    }
  }

  private void applyNameChoice() {
    if (nameChoiceBox == null || nameChoiceBox.getVisibility() != View.VISIBLE) {
      return;
    }
    if (nameSheet.isChecked()) {
      name.setText(sheetName);
    } else {
      name.setText(rosterName);
    }
  }

  private void setMonthDay(int birthMonth, int birthDay) {
    if (birthMonth >= 1 && birthMonth <= 12) {
      month.setSelection(birthMonth - 1);
    }
    if (birthDay >= 1 && birthDay <= 31) {
      day.setSelection(birthDay - 1);
    }
  }

  private void setGender(String value) {
    if (Member.GENDER_MALE.equals(value)) {
      gender.setSelection(1);
    } else if (Member.GENDER_FEMALE.equals(value)) {
      gender.setSelection(2);
    } else {
      gender.setSelection(0);
    }
  }

  private void setPhoto(String status) {
    Member fake = new Member();
    fake.photoStatus = status == null ? "" : status;
    photo.setSelection(AlumniDesk.photoSpinnerIndex(fake));
  }

  private static void setText(TextInputEditText field, String value) {
    if (field != null) {
      field.setText(nz(value));
    }
  }

  private void save() {
    if (saving) {
      return;
    }
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
    final boolean saved = contactSaved.isChecked();
    String rawPhone = phone.getText() == null ? "" : phone.getText().toString().trim();
    if (AlumniDesk.nigeriaDigits(rawPhone).length() == 13) {
      rawPhone = AlumniDesk.nigeriaDigits(rawPhone);
    }
    final String phoneValue = rawPhone;
    final String honor = honorific.getText() == null ? "" : honorific.getText().toString().trim();
    final String emailValue = textOf(email);
    final String positionValue = textOf(position);
    final String gradValue = textOf(gradSet);
    final String hnm = captionHnm.getText() == null ? "" : captionHnm.getText().toString();
    final String details = captionDetails.getText() == null ? "" : captionDetails.getText().toString();
    final String photoStatus = AlumniDesk.photoStatusFromIndex(photo.getSelectedItemPosition());
    int g = gender.getSelectedItemPosition();
    final String genderValue = g == 1 ? Member.GENDER_MALE : g == 2 ? Member.GENDER_FEMALE : "";
    final String finalName = value;
    final long id = memberId;
    saving = true;
    findViewById(R.id.btn_save).setEnabled(false);
    final Context app = getApplicationContext();
    Toast.makeText(this, R.string.alumni_saving, Toast.LENGTH_SHORT).show();
    finish();
    AppExecutors.query().execute(() -> {
      AppDatabase db = AppDatabase.get(app);
      Member row = id > 0L ? db.memberDao().getById(id) : null;
      boolean merged = false;
      if (row == null) {
        Member prior = db.memberDao().findByKindAndNameIgnoreCase(Member.KIND_ALUMNI, finalName);
        if (prior != null) {
          row = prior;
          merged = true;
        } else {
          row = new Member();
          row.kind = Member.KIND_ALUMNI;
        }
      }
      final Member member = row;
      String oldName = member.name;
      Member scheduleSnap = new Member();
      scheduleSnap.birthMonth = member.birthMonth;
      scheduleSnap.birthDay = member.birthDay;
      scheduleSnap.skipCaption = member.skipCaption;
      scheduleSnap.desk = member.desk;
      scheduleSnap.kind = member.kind;
      scheduleSnap.photoStatus = member.photoStatus;
      member.name = finalName;
      member.kind = Member.KIND_ALUMNI;
      member.birthMonth = birthMonth;
      member.birthDay = birthDay;
      member.skipCaption = skip;
      member.contactSaved = saved;
      member.desk = pastor ? Member.DESK_PASTOR : "";
      member.phone = phoneValue;
      member.honorific = honor;
      member.email = emailValue;
      member.positionHeld = positionValue;
      member.gradSet = gradValue;
      member.captionHnm = hnm.trim();
      member.captionDetails = details.trim();
      member.photoStatus = photoStatus;
      member.gender = genderValue;
      boolean insert = member.id == 0L;
      if (insert) {
        member.id = db.memberDao().insert(member);
      } else {
        db.memberDao().update(member);
      }
      boolean reschedule = insert || Member.scheduleFieldsDiffer(scheduleSnap, member);
      boolean nameChanged = oldName == null || !finalName.equals(oldName);
      boolean showMerged = merged;
      AppExecutors.main(() -> Toast.makeText(
        app,
        showMerged ? R.string.alumni_filled_existing : R.string.alumni_saved,
        showMerged ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
      if (reschedule) {
        AppExecutors.disk().execute(() -> {
          if (!insert) {
            ScheduleCoordinator.cancelMemberTasks(app, member.id);
          }
          ScheduleCoordinator.bootstrap(app);
        });
      } else if (nameChanged) {
        ScheduleCoordinator.retitleMember(app, member);
      }
    });
  }

  private static String textOf(TextInputEditText field) {
    if (field == null || field.getText() == null) {
      return "";
    }
    return field.getText().toString().trim();
  }

  private static String nz(String value) {
    return value == null ? "" : value;
  }

  private static int maxDay(int month1to12) {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.MONTH, month1to12 - 1);
    c.set(Calendar.DAY_OF_MONTH, 1);
    return c.getActualMaximum(Calendar.DAY_OF_MONTH);
  }
}
