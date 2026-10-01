package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.scheduler.AlumniTemplates;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.Calendar;
import java.util.Map;

public class AlumniCaptionEditActivity extends AppCompatActivity {
  private String key;
  private TextInputEditText input;
  private android.widget.TextView preview;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_caption_edit);
    key = getIntent().getStringExtra(ExtraKeys.CAPTION_KEY);
    if (key == null || key.isEmpty()) {
      finish();
      return;
    }
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    android.widget.TextView heading = findViewById(R.id.heading);
    android.widget.TextView hint = findViewById(R.id.hint);
    heading.setText(AlumniTemplates.label(key));
    hint.setText(AlumniTemplates.hint(key) + "\n\n" + getString(R.string.alumni_caption_tokens));
    input = findViewById(R.id.input);
    preview = findViewById(R.id.preview);
    input.setText(Prefs.alumniCaption(this, key));
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
    findViewById(R.id.btn_save).setOnClickListener(v -> {
      String value = input.getText() == null ? "" : input.getText().toString();
      Prefs.setAlumniCaption(this, key, value);
      Toast.makeText(this, R.string.alumni_caption_saved, Toast.LENGTH_SHORT).show();
      finish();
    });
    findViewById(R.id.btn_reset).setOnClickListener(v -> {
      input.setText(AlumniTemplates.fallback(key));
      Prefs.setAlumniCaption(this, key, "");
      Toast.makeText(this, R.string.alumni_caption_reset, Toast.LENGTH_SHORT).show();
    });
    paintPreview();
  }

  private void paintPreview() {
    String template = input.getText() == null ? "" : input.getText().toString();
    Map<String, String> bag = Prefs.alumniCaptionBag(this);
    bag.put(key, template);
    Member sample = new Member();
    sample.kind = Member.KIND_ALUMNI;
    sample.name = "Ada Okafor";
    sample.firstName = "Ada";
    sample.lastName = "Okafor";
    sample.gender = Member.GENDER_FEMALE;
    sample.birthMonth = 10;
    sample.birthDay = 2;
    Calendar oct = Calendar.getInstance();
    oct.set(Calendar.MONTH, Calendar.OCTOBER);
    oct.set(Calendar.DAY_OF_MONTH, 1);
    String filled;
    if (AlumniTemplates.OFFICER.equals(key) || AlumniTemplates.ROLE.equals(key)) {
      filled = template;
    } else {
      filled = AlumniTemplates.fill(template, sample, 10, oct, bag);
    }
    preview.setText(filled);
  }
}
