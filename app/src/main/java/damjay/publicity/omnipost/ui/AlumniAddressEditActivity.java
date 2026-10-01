package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.scheduler.AlumniAddress;
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlumniAddressEditActivity extends AppCompatActivity {
  private TextInputEditText inputName;
  private TextInputEditText inputLabel;
  private TextInputEditText inputMale;
  private TextInputEditText inputFemale;
  private LinearLayout tokenRow;
  private android.widget.TextView preview;
  private String original;
  private List<AlumniAddress> all = new ArrayList<>();
  private TextInputEditText focused;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_address_edit);
    original = CaptionVars.normalizeName(getIntent().getStringExtra(ExtraKeys.ADDRESS_NAME));
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    findViewById(R.id.btn_save).setOnClickListener(v -> persist());
    findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
    inputName = findViewById(R.id.input_name);
    inputLabel = findViewById(R.id.input_label);
    inputMale = findViewById(R.id.input_male);
    inputFemale = findViewById(R.id.input_female);
    tokenRow = findViewById(R.id.token_row);
    preview = findViewById(R.id.preview);
    focused = inputMale;
    watch(inputName);
    watch(inputMale);
    watch(inputFemale);
    inputMale.setOnFocusChangeListener((v, has) -> {
      if (has) {
        focused = inputMale;
      }
    });
    inputFemale.setOnFocusChangeListener((v, has) -> {
      if (has) {
        focused = inputFemale;
      }
    });
    all = Prefs.alumniAddresses(this);
    AlumniAddress found = find(original);
    if (!original.isEmpty()) {
      if (found == null) {
        finish();
        return;
      }
      android.widget.TextView heading = findViewById(R.id.heading);
      heading.setText(R.string.alumni_address_edit);
      findViewById(R.id.btn_delete).setVisibility(View.VISIBLE);
      inputName.setText(found.name);
      inputLabel.setText(found.label);
      inputMale.setText(found.male);
      inputFemale.setText(found.female);
    }
    paintChips();
    paintPreview();
  }

  private AlumniAddress find(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    for (AlumniAddress item : all) {
      if (item != null && name.equals(item.name)) {
        return item;
      }
    }
    return null;
  }

  private void watch(TextInputEditText input) {
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
  }

  private void paintChips() {
    tokenRow.removeAllViews();
    addChip("first");
    addChip("last");
    addChip("full");
    addChip("title");
    addChip("title_lc");
    addChip("who");
    java.util.HashSet<String> seen = new java.util.HashSet<>();
    seen.add("first");
    seen.add("last");
    seen.add("full");
    seen.add("title");
    seen.add("title_lc");
    seen.add("who");
    for (AlumniAddress item : all) {
      if (item == null || item.name == null || item.name.isEmpty()) {
        continue;
      }
      if (item.name.equals(original) || !seen.add(item.name)) {
        continue;
      }
      addChip(item.name);
    }
  }

  private void addChip(String name) {
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
    tokenRow.addView(chip);
  }

  private void insert(String token) {
    TextInputEditText target = focused == null ? inputMale : focused;
    Editable editable = target.getText();
    if (editable == null) {
      target.setText(token);
      paintPreview();
      return;
    }
    int start = Math.max(target.getSelectionStart(), 0);
    int end = Math.max(target.getSelectionEnd(), start);
    editable.replace(start, end, token);
    paintPreview();
  }

  private void paintPreview() {
    String name = CaptionVars.normalizeName(text(inputName));
    AlumniAddress draft = new AlumniAddress(
      name,
      text(inputLabel),
      raw(inputMale),
      raw(inputFemale));
    List<AlumniAddress> previewList = new ArrayList<>();
    for (AlumniAddress item : all) {
      if (item == null || item.name == null) {
        continue;
      }
      if (item.name.equals(original) || item.name.equals(name)) {
        continue;
      }
      previewList.add(item);
    }
    if (!name.isEmpty()) {
      previewList.add(draft);
    }
    Member man = sample("Tobi Ade", Member.GENDER_MALE);
    Member woman = sample("Ada Okwuoma", Member.GENDER_FEMALE);
    Calendar oct = Calendar.getInstance();
    oct.set(Calendar.MONTH, Calendar.OCTOBER);
    oct.set(Calendar.DAY_OF_MONTH, 1);
    String token = name.isEmpty() ? "{dear}" : CaptionVars.token(name);
    String maleGot = AlumniTemplates.fill(token, man, 10, oct, Prefs.alumniCaptionBag(this), previewList);
    String femaleGot = AlumniTemplates.fill(token, woman, 10, oct, Prefs.alumniCaptionBag(this), previewList);
    preview.setText("Male\n" + maleGot + "\n\nFemale\n" + femaleGot);
  }

  private void persist() {
    String name = CaptionVars.normalizeName(text(inputName));
    if (!CaptionTemplates.isTokenName(name)) {
      Toast.makeText(this, R.string.need_var_name, Toast.LENGTH_SHORT).show();
      return;
    }
    if (AlumniAddress.reserved(name)) {
      Toast.makeText(this, R.string.var_reserved, Toast.LENGTH_SHORT).show();
      return;
    }
    for (AlumniAddress item : all) {
      if (item == null || item.name == null) {
        continue;
      }
      if (name.equals(item.name) && !name.equals(original)) {
        Toast.makeText(this, R.string.var_name_taken, Toast.LENGTH_SHORT).show();
        return;
      }
    }
    AlumniAddress item = find(original);
    if (item == null) {
      item = new AlumniAddress(name, text(inputLabel), raw(inputMale), raw(inputFemale));
      all.add(item);
    } else {
      item.name = name;
      item.label = text(inputLabel);
      item.male = raw(inputMale);
      item.female = raw(inputFemale);
    }
    Prefs.setAlumniAddresses(this, all);
    Toast.makeText(this, R.string.alumni_address_saved, Toast.LENGTH_SHORT).show();
    finish();
  }

  private void confirmDelete() {
    new MaterialAlertDialogBuilder(this)
      .setTitle(R.string.delete_variable_title)
      .setMessage(getString(R.string.delete_variable_body, CaptionVars.token(text(inputName))))
      .setPositiveButton(R.string.delete, (d, w) -> {
        List<AlumniAddress> keep = new ArrayList<>();
        for (AlumniAddress item : all) {
          if (item == null || item.name == null || item.name.equals(original)) {
            continue;
          }
          keep.add(item);
        }
        Prefs.setAlumniAddresses(this, keep);
        finish();
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private static Member sample(String name, String gender) {
    Member member = new Member();
    member.kind = Member.KIND_ALUMNI;
    member.name = name;
    int space = name.indexOf(' ');
    member.firstName = space > 0 ? name.substring(0, space) : name;
    member.lastName = space > 0 ? name.substring(space + 1) : "";
    member.gender = gender;
    member.birthMonth = 10;
    member.birthDay = 2;
    return member;
  }

  private static String text(TextInputEditText input) {
    return input.getText() == null ? "" : input.getText().toString().trim();
  }

  private static String raw(TextInputEditText input) {
    return input.getText() == null ? "" : input.getText().toString();
  }
}
