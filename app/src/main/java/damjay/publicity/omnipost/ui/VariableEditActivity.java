package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.databinding.ActivityVariableEditBinding;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;

public class VariableEditActivity extends AppCompatActivity {
  private ActivityVariableEditBinding binding;
  private long varId;
  private CaptionVar loaded;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityVariableEditBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    varId = getIntent().getLongExtra(ExtraKeys.VAR_ID, 0L);
    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnSave.setOnClickListener(v -> persist());
    binding.btnDelete.setOnClickListener(v -> confirmDelete());
    watch(binding.inputName);
    watch(binding.inputValue);
    if (varId > 0L) {
      binding.heading.setText(R.string.edit_variable);
      binding.btnDelete.setVisibility(View.VISIBLE);
      AppExecutors.disk().execute(() -> {
        CaptionVar found = AppDatabase.get(this).captionVarDao().getById(varId);
        loaded = found;
        AppExecutors.main(() -> {
          if (isFinishing() || binding == null || found == null) {
            if (found == null) {
              finish();
            }
            return;
          }
          binding.inputName.setText(found.name);
          binding.inputLabel.setText(found.label);
          binding.inputValue.setText(found.value);
          paintPreview();
        });
      });
    } else {
      paintPreview();
    }
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

  private void paintPreview() {
    if (binding == null) {
      return;
    }
    String name = CaptionVars.normalizeName(text(binding.inputName));
    String value = text(binding.inputValue);
    if (name.isEmpty()) {
      binding.preview.setText(R.string.variable_preview_empty);
      return;
    }
    String sample = CaptionVars.token(name) + "\n\n" + (value.isEmpty() ? "—" : value);
    binding.preview.setText(sample);
  }

  private void persist() {
    String name = CaptionVars.normalizeName(text(binding.inputName));
    if (!CaptionTemplates.isTokenName(name)) {
      Toast.makeText(this, R.string.need_var_name, Toast.LENGTH_SHORT).show();
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
      CaptionVar var = loaded == null ? new CaptionVar() : loaded;
      var.name = name;
      var.label = label;
      var.value = value;
      if (var.id > 0L) {
        db.captionVarDao().update(var);
      } else {
        var.id = db.captionVarDao().insert(var);
        varId = var.id;
        loaded = var;
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
