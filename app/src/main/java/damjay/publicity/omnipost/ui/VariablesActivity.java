package damjay.publicity.omnipost.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.databinding.ActivityVariablesBinding;
import damjay.publicity.omnipost.util.ExtraKeys;

public class VariablesActivity extends AppCompatActivity {
  private ActivityVariablesBinding binding;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityVariablesBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.btnBack.setOnClickListener(v -> finish());
    VarAdapter adapter = new VarAdapter(this::open);
    binding.list.setLayoutManager(new LinearLayoutManager(this));
    binding.list.setAdapter(adapter);
    AppDatabase.get(this)
      .captionVarDao()
      .observeAll()
      .observe(this, vars -> {
        adapter.submit(vars);
        boolean empty = vars == null || vars.isEmpty();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
      });
    binding.fab.setOnClickListener(v -> open(null));
  }

  private void open(@Nullable CaptionVar var) {
    Intent intent = new Intent(this, VariableEditActivity.class);
    if (var != null) {
      intent.putExtra(ExtraKeys.VAR_ID, var.id);
    }
    startActivity(intent);
  }
}
