package damjay.publicity.omnipost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.databinding.ActivityVariablesBinding;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.util.ExtraKeys;

public class VariablesActivity extends AppCompatActivity {
  private ActivityVariablesBinding binding;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityVariablesBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.btnBack.setOnClickListener(v -> finish());
    VarAdapter adapter = new VarAdapter(new VarAdapter.Listener() {
      @Override
      public void onOpen(CaptionVar var) {
        open(var);
      }

      @Override
      public void onBuiltin(CaptionVars.Builtin builtin) {
        showBuiltin(builtin);
      }
    });
    binding.list.setLayoutManager(new LinearLayoutManager(this));
    binding.list.setAdapter(adapter);
    binding.empty.setVisibility(View.GONE);
    AppDatabase.get(this)
      .captionVarDao()
      .observeAll()
      .observe(this, adapter::submit);
    binding.fab.setOnClickListener(v -> open(null));
  }

  private void showBuiltin(CaptionVars.Builtin builtin) {
    new MaterialAlertDialogBuilder(this)
      .setTitle(CaptionVars.token(builtin.name))
      .setMessage(builtin.label + "\n\n" + builtin.hint)
      .setPositiveButton(android.R.string.ok, null)
      .show();
  }

  private void open(@Nullable CaptionVar var) {
    Intent intent = new Intent(this, VariableEditActivity.class);
    if (var != null) {
      intent.putExtra(ExtraKeys.VAR_ID, var.id);
    }
    startActivity(intent);
  }
}
