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
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.scheduler.FellowshipTemplates;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.SeriesDefaults;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.share.WhatsAppPreview;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;
import java.util.List;

public class FellowshipCaptionEditActivity extends AppCompatActivity {
  private String key;
  private long seriesId;
  private Series series;
  private TextInputEditText input;
  private android.widget.TextView preview;
  private android.widget.TextView heading;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_alumni_caption_edit);
    key = getIntent().getStringExtra(ExtraKeys.CAPTION_KEY);
    seriesId = getIntent().getLongExtra(ExtraKeys.SERIES_ID, 0L);
    if ((key == null || key.isEmpty()) && seriesId <= 0L) {
      finish();
      return;
    }
    findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    heading = findViewById(R.id.heading);
    android.widget.TextView hint = findViewById(R.id.hint);
    hint.setText(R.string.series_placeholders);
    input = findViewById(R.id.input);
    preview = findViewById(R.id.preview);
    WhatsAppPreview.attach(input);
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
    ((com.google.android.material.button.MaterialButton) findViewById(R.id.btn_reset))
      .setText(R.string.fellowship_caption_clear);
    paintChips();
    if (seriesId > 0L) {
      loadSeries();
      return;
    }
    heading.setText(FellowshipTemplates.label(this, key));
    hint.setText(FellowshipTemplates.hint(this, key) + "\n\n" + getString(R.string.series_placeholders));
    String custom = Prefs.fellowshipCaptionRaw(this, key);
    input.setText(custom == null ? "" : custom);
    paintPreview();
  }

  private void loadSeries() {
    AppExecutors.query().execute(() -> {
      Series found = AppDatabase.get(this).seriesDao().getById(seriesId);
      AppExecutors.main(() -> {
        if (isFinishing() || isDestroyed()) {
          return;
        }
        if (found == null) {
          finish();
          return;
        }
        series = found;
        heading.setText(found.title);
        input.setText(found.caption == null ? "" : found.caption);
        paintPreview();
      });
    });
  }

  private void paintChips() {
    LinearLayout row = findViewById(R.id.token_row);
    if (row == null) {
      return;
    }
    row.removeAllViews();
    List<CaptionVars.Builtin> builtins = CaptionVars.builtins();
    for (CaptionVars.Builtin item : builtins) {
      if (item != null && item.name != null && !item.name.contains(":")) {
        addChip(row, item.name);
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
    if (seriesId > 0L) {
      AppExecutors.disk().execute(() -> {
        Series row = AppDatabase.get(this).seriesDao().getById(seriesId);
        if (row == null) {
          return;
        }
        row.caption = value;
        AppDatabase.get(this).seriesDao().update(row);
        ScheduleCoordinator.bootstrap(getApplicationContext());
        AppExecutors.main(() -> {
          Toast.makeText(this, R.string.fellowship_caption_saved, Toast.LENGTH_SHORT).show();
          finish();
        });
      });
      return;
    }
    Prefs.setFellowshipCaption(this, key, value);
    Toast.makeText(this, R.string.fellowship_caption_saved, Toast.LENGTH_SHORT).show();
    finish();
  }

  private void reset() {
    if (seriesId > 0L) {
      String blank = blankFor(series);
      input.setText(blank);
      return;
    }
    input.setText("");
    Prefs.setFellowshipCaption(this, key, "");
    Toast.makeText(this, R.string.fellowship_caption_reset, Toast.LENGTH_SHORT).show();
  }

  private static String blankFor(Series series) {
    if (series == null) {
      return "";
    }
    if (Series.KIND_MONTHLY.equals(series.kind)) {
      return SeriesDefaults.blankNoticeCaption();
    }
    if (Series.KIND_WEEKLY.equals(series.kind)) {
      return SeriesDefaults.blankWeeklyCaption();
    }
    if (Series.KIND_DAILY.equals(series.kind)) {
      return SeriesDefaults.blankDailyCaption();
    }
    return SeriesDefaults.blankCountdownCaption();
  }

  private void paintPreview() {
    String template = input.getText() == null ? "" : input.getText().toString();
    Task task = new Task();
    task.title = heading.getText() == null ? "" : heading.getText().toString();
    task.postAtMillis = System.currentTimeMillis();
    if (series != null) {
      if (Series.KIND_MONTHLY.equals(series.kind)) {
        task.type = TaskTypes.BIRTHDAY_NOTICE;
      } else if (Series.KIND_WEEKLY.equals(series.kind)) {
        task.type = TaskTypes.WEEKLY;
      } else if (Series.KIND_DAILY.equals(series.kind)) {
        task.type = TaskTypes.DAILY;
      } else {
        task.type = TaskTypes.COUNTDOWN;
      }
      task.seriesId = series.id;
    } else {
      task.type = key;
      if (TaskTypes.BIRTHDAY.equals(key)) {
        task.title = "Ada Okafor's Birthday";
      }
    }
    WhatsAppPreview.show(preview, CaptionTemplates.apply(template, task, series));
  }
}
