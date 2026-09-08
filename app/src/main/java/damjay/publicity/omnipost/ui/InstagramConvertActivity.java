package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.databinding.ActivityInstagramBinding;
import damjay.publicity.omnipost.share.InstagramStyle;
import damjay.publicity.omnipost.share.WhatsAppRouter;

/** Paste any WhatsApp caption and copy Instagram letters. Not tied to a task. */
public class InstagramConvertActivity extends AppCompatActivity {
  private ActivityInstagramBinding binding;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityInstagramBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnCopy.setOnClickListener(v -> copy());
    binding.input.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        paint();
      }
    });
    paint();
  }

  private void paint() {
    if (binding == null) {
      return;
    }
    String source = text();
    binding.preview.setText(source.isEmpty() ? "" : InstagramStyle.toUnicode(source));
  }

  private void copy() {
    String source = text();
    if (source.isEmpty()) {
      Toast.makeText(this, R.string.need_caption, Toast.LENGTH_SHORT).show();
      return;
    }
    WhatsAppRouter.copyToClipboard(this, InstagramStyle.toUnicode(source));
    Toast.makeText(this, R.string.instagram_copied, Toast.LENGTH_LONG).show();
  }

  private String text() {
    if (binding == null || binding.input.getText() == null) {
      return "";
    }
    return binding.input.getText().toString().trim();
  }
}
