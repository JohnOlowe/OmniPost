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
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityVariableEditBinding;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VariableEditActivity extends AppCompatActivity {
  private ActivityVariableEditBinding binding;
  private long varId;
  private CaptionVar loaded;
  private final List<CaptionVar> others = new ArrayList<>();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityVariableEditBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    varId = getIntent().getLongExtra(ExtraKeys.VAR_ID, 0L);
    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnSave.setOnClickListener(v -> persist());
    binding.btnDelete.setOnClickListener(v -> confirmDelete());
    binding.btnRange.setOnClickListener(v -> insert("{range:today:date}"));
    watch(binding.inputName);
    watch(binding.inputValue);
    AppExecutors.disk().execute(() -> {
      List<CaptionVar> all = AppDatabase.get(this).captionVarDao().getAllSync();
      CaptionVar found = varId > 0L ? AppDatabase.get(this).captionVarDao().getById(varId) : null;
      loaded = found;
      others.clear();
      if (all != null) {
        for (CaptionVar item : all) {
          if (item != null && item.id != varId) {
            others.add(item);
          }
        }
      }
      AppExecutors.main(() -> {
        if (isFinishing() || binding == null) {
          return;
        }
        if (varId > 0L) {
          if (found == null) {
            finish();
            return;
          }
          binding.heading.setText(R.string.edit_variable);
          binding.btnDelete.setVisibility(View.VISIBLE);
          binding.inputName.setText(found.name);
          binding.inputLabel.setText(found.label);
          binding.inputValue.setText(found.value);
        }
        paintChips();
        paintPreview();
      });
    });
  }

  private void watch(com.google.android.material.textfield.TextInputEditText input) {
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
    if (binding == null) {
      return;
    }
    binding.tokenRow.removeAllViews();
    addChip("today");
    addChip("date");
    addChip("start");
    addChip("end");
    addChip("range");
    addChip("today+1");
    addChip("range:today:date");
    for (CaptionVar item : others) {
      if (item.name != null && CaptionTemplates.isTokenName(item.name)) {
        addChip(item.name);
      }
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
    binding.tokenRow.addView(chip);
  }

  private void insert(String token) {
    if (token == null || token.isEmpty() || binding == null) {
      return;
    }
    Editable editable = binding.inputValue.getText();
    if (editable == null) {
      binding.inputValue.setText(token);
      paintPreview();
      return;
    }
    int start = Math.max(binding.inputValue.getSelectionStart(), 0);
    int end = Math.max(binding.inputValue.getSelectionEnd(), start);
    editable.replace(start, end, token);
    paintPreview();
  }

  private void paintPreview() {
    if (binding == null) {
      return;
    }
    String name = CaptionVars.normalizeName(text(binding.inputName));
    String value = binding.inputValue.getText() == null
      ? ""
      : binding.inputValue.getText().toString();
    if (name.isEmpty() && value.trim().isEmpty()) {
      binding.preview.setText(R.string.variable_preview_empty);
      return;
    }
    Map<String, String> extras = new LinkedHashMap<>(CaptionVars.map(others));
    if (!name.isEmpty()) {
      extras.put(name, value);
    }
    Task dummy = new Task();
    dummy.type = TaskTypes.ONE_OFF;
    dummy.postAtMillis = System.currentTimeMillis();
    String source = value.trim().isEmpty() ? CaptionVars.token(name) : value;
    String filled = CaptionTemplates.apply(source, dummy, null, extras);
    String heading = name.isEmpty() ? "" : CaptionVars.token(name) + "\n\n";
    binding.preview.setText(heading + (filled.isEmpty() ? "—" : filled));
  }

  private void persist() {
    String name = CaptionVars.normalizeName(text(binding.inputName));
    if (!CaptionTemplates.isTokenName(name)) {
      Toast.makeText(this, R.string.need_var_name, Toast.LENGTH_SHORT).show();
      return;
    }
    if (CaptionVars.isReserved(name)) {
      Toast.makeText(this, R.string.var_reserved, Toast.LENGTH_SHORT).show();
      return;
    }
    String label = text(binding.inputLabel);
    String value = binding.inputValue.getText() == null
      ? ""
      : binding.inputValue.getText().toString();
    AppExecutors.disk().execute(() -> {
      AppDatabase db = AppDatabase.get(this);
      CaptionVar clash = db.captionVarDao().findByName(name);
      if (clash != null && clash.id != varId) {
        AppExecutors.main(() ->
          Toast.makeText(this, R.string.var_name_taken, Toast.LENGTH_SHORT).show());
        return;
      }
      CaptionVar item = loaded == null ? new CaptionVar() : loaded;
      item.name = name;
      item.label = label;
      item.value = value;
      if (item.id > 0L) {
        db.captionVarDao().update(item);
      } else {
        item.id = db.captionVarDao().insert(item);
        varId = item.id;
        loaded = item;
      }
      AppExecutors.main(() -> {
        Toast.makeText(this, R.string.variable_saved, Toast.LENGTH_SHORT).show();
        finish();
      });
    });
  }

  private void confirmDelete() {
    new MaterialAlertDialogBuilder(this)
      .setTitle(R.string.delete_variable_title)
      .setMessage(getString(R.string.delete_variable_body, CaptionVars.token(text(binding.inputName))))
      .setPositiveButton(R.string.delete, (d, w) -> {
        long id = varId;
        AppExecutors.disk().execute(() -> {
          if (id > 0L) {
            AppDatabase.get(this).captionVarDao().deleteById(id);
          }
          AppExecutors.main(this::finish);
        });
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private static String text(com.google.android.material.textfield.TextInputEditText input) {
    return input.getText() == null ? "" : input.getText().toString().trim();
  }
}
