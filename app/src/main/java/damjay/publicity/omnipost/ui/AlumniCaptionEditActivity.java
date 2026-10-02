package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.scheduler.AlumniAddress;
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.share.WhatsAppPreview;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class AlumniCaptionEditActivity extends AppCompatActivity {
  private String key;
  private long memberId;
  private Member person;
  private TextInputEditText input;
  private android.widget.TextView preview;
  private android.widget.TextView heading;
  private List<AlumniAddress> addresses;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_caption_edit);
    key = getIntent().getStringExtra(ExtraKeys.CAPTION_KEY);
    memberId = getIntent().getLongExtra(ExtraKeys.MEMBER_ID, 0L);
    if ((key == null || key.isEmpty()) && memberId <= 0L) {
      finish();
      return;
    }
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    heading = findViewById(R.id.heading);
    android.widget.TextView hint = findViewById(R.id.hint);
    hint.setText(getString(R.string.alumni_caption_tokens));
    input = findViewById(R.id.input);
    preview = findViewById(R.id.preview);
    WhatsAppPreview.attach(input);
    addresses = Prefs.alumniAddresses(this);
    input.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        paintPreview();
      }
    });
    findViewById(R.id.btn_save).setOnClickListener(v -> save());
    findViewById(R.id.btn_reset).setOnClickListener(v -> reset());
    paintChips();
    if (memberId > 0L) {
      loadPerson();
      return;
    }
    heading.setText(AlumniTemplates.label(key));
    hint.setText(AlumniTemplates.hint(key) + "\n\n" + getString(R.string.alumni_caption_tokens));
    input.setText(Prefs.alumniCaption(this, key));
    paintPreview();
  }

  private void loadPerson() {
    AppExecutors.query().execute(() -> {
      Member found = AppDatabase.get(this).memberDao().getById(memberId);
      AppExecutors.main(() -> {
        if (isFinishing() || isDestroyed()) {
          return;
        }
        if (found == null) {
          finish();
          return;
        }
        person = found;
        boolean details = AlumniTemplates.DETAILS.equals(key);
        heading.setText(getString(
          details ? R.string.alumni_person_details : R.string.alumni_person_hnm,
          found.name));
        findViewById(R.id.btn_reset).setContentDescription(getString(R.string.alumni_use_general));
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.btn_reset))
          .setText(R.string.alumni_use_general);
        String custom = details ? found.captionDetails : found.captionHnm;
        if (custom == null || custom.trim().isEmpty()) {
          input.setText(generalTemplate(found, details));
        } else {
          input.setText(custom);
        }
        paintPreview();
      });
    });
  }

  private String generalTemplate(Member member, boolean details) {
    Map<String, String> bag = Prefs.alumniCaptionBag(this);
    if (details) {
      return AlumniTemplates.pick(bag, AlumniTemplates.DETAILS);
    }
    return AlumniTemplates.pick(bag, AlumniTemplates.hnmKey(member));
  }

  private void paintChips() {
    LinearLayout row = findViewById(R.id.token_row);
    if (row == null) {
      return;
    }
    row.removeAllViews();
    addChip(row, "who");
    addChip(row, "dear");
    addChip(row, "formal");
    addChip(row, "titled");
    addChip(row, "title_only");
    addChip(row, "first");
    addChip(row, "full");
    addChip(row, "title");
    addChip(row, "title_lc");
    addChip(row, "month");
    if (addresses != null) {
      for (AlumniAddress item : addresses) {
        if (item != null && item.name != null && !item.name.isEmpty()) {
          addChip(row, item.name);
        }
      }
    }
  }

  private void addChip(LinearLayout row, String name) {
    android.widget.TextView chip = new android.widget.TextView(this);
    chip.setText(CaptionVars.token(name));
    chip.setTextColor(getColor(R.color.gold));
    chip.setTextSize(13f);
    chip.setPadding(20, 12, 20, 12);
    chip.setBackgroundResource(R.drawable.bg_chip_gold);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMarginEnd(8);
    chip.setLayoutParams(params);
    chip.setOnClickListener(v -> insert(CaptionVars.token(name)));
    row.addView(chip);
  }

  private void insert(String token) {
    Editable editable = input.getText();
    if (editable == null) {
      input.setText(token);
      return;
    }
    int start = Math.max(input.getSelectionStart(), 0);
    int end = Math.max(input.getSelectionEnd(), start);
    editable.replace(start, end, token);
  }

  private void save() {
    String value = input.getText() == null ? "" : input.getText().toString();
    if (memberId > 0L) {
      AppExecutors.disk().execute(() -> {
        Member member = AppDatabase.get(this).memberDao().getById(memberId);
        if (member == null) {
          return;
        }
        if (AlumniTemplates.DETAILS.equals(key)) {
          member.captionDetails = value.trim();
        } else {
          member.captionHnm = value.trim();
        }
        AppDatabase.get(this).memberDao().update(member);
        AppExecutors.main(() -> {
          Toast.makeText(this, R.string.alumni_caption_saved, Toast.LENGTH_SHORT).show();
          finish();
        });
      });
      return;
    }
    Prefs.setAlumniCaption(this, key, value);
    Toast.makeText(this, R.string.alumni_caption_saved, Toast.LENGTH_SHORT).show();
    finish();
  }

  private void reset() {
    if (memberId > 0L) {
      AppExecutors.disk().execute(() -> {
        Member member = AppDatabase.get(this).memberDao().getById(memberId);
        if (member == null) {
          return;
        }
        if (AlumniTemplates.DETAILS.equals(key)) {
          member.captionDetails = "";
        } else {
          member.captionHnm = "";
        }
        AppDatabase.get(this).memberDao().update(member);
        Member copy = member;
        AppExecutors.main(() -> {
          person = copy;
          boolean details = AlumniTemplates.DETAILS.equals(key);
          input.setText(generalTemplate(copy, details));
          Toast.makeText(this, R.string.alumni_caption_reset, Toast.LENGTH_SHORT).show();
        });
      });
      return;
    }
    input.setText(AlumniTemplates.fallback(key));
    Prefs.setAlumniCaption(this, key, "");
    Toast.makeText(this, R.string.alumni_caption_reset, Toast.LENGTH_SHORT).show();
  }

  private void paintPreview() {
    String template = input.getText() == null ? "" : input.getText().toString();
    Map<String, String> bag = Prefs.alumniCaptionBag(this);
    if (memberId <= 0L && key != null) {
      bag.put(key, template);
    }
    Member sample = person;
    if (sample == null) {
      sample = new Member();
      sample.kind = Member.KIND_ALUMNI;
      sample.name = "Ada Okwuoma";
      sample.firstName = "Ada";
      sample.lastName = "Okwuoma";
      sample.gender = Member.GENDER_FEMALE;
      sample.birthMonth = 10;
      sample.birthDay = 2;
    }
    Calendar oct = Calendar.getInstance();
    oct.set(Calendar.MONTH, Calendar.OCTOBER);
    oct.set(Calendar.DAY_OF_MONTH, 1);
    String filled;
    if (memberId <= 0L
        && (AlumniTemplates.OFFICER.equals(key)
          || AlumniTemplates.ROLE.equals(key)
          || AlumniTemplates.MALE_TITLE.equals(key)
          || AlumniTemplates.FEMALE_TITLE.equals(key))) {
      filled = template;
    } else {
      filled = AlumniTemplates.fill(template, sample, 10, oct, bag, addresses);
    }
    WhatsAppPreview.show(preview, filled);
  }
}
