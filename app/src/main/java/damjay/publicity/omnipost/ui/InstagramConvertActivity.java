package damjay.publicity.omnipost.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.databinding.ActivityInstagramBinding;
import damjay.publicity.omnipost.databinding.ItemSettingRowBinding;
import damjay.publicity.omnipost.share.InstagramStyle;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.Prefs;

/** Paste any caption either way: WhatsApp markup ↔ Instagram letters. */
public class InstagramConvertActivity extends AppCompatActivity {
  private ActivityInstagramBinding binding;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityInstagramBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnCopy.setOnClickListener(v -> copy());
    binding.direction.check(R.id.btn_to_ig);
    binding.direction.addOnButtonCheckedListener((group, id, checked) -> {
      if (checked) {
        paint();
      }
    });
    binding.rowBold.getRoot().setOnClickListener(v ->
      pickFace(R.string.instagram_opt_bold, Prefs.instagramBoldFace(this), Prefs::setInstagramBoldFace));
    binding.rowItalic.getRoot().setOnClickListener(v ->
      pickFace(R.string.instagram_opt_italic, Prefs.instagramItalicFace(this), Prefs::setInstagramItalicFace));
    binding.rowBoth.getRoot().setOnClickListener(v ->
      pickFace(R.string.instagram_opt_both, Prefs.instagramBothFace(this), Prefs::setInstagramBothFace));
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
    bindFaces();
    paint();
  }

  private void pickFace(int title, String current, FaceSetter setter) {
    String[] values = new String[] {InstagramStyle.FACE_SANS, InstagramStyle.FACE_SERIF};
    String[] labels = new String[] {
      getString(R.string.instagram_face_sans),
      getString(R.string.instagram_face_serif)
    };
    int selected = InstagramStyle.FACE_SERIF.equals(current) ? 1 : 0;
    new MaterialAlertDialogBuilder(this)
      .setTitle(title)
      .setSingleChoiceItems(labels, selected, (d, which) -> {
        setter.set(this, values[which]);
        d.dismiss();
        bindFaces();
        paint();
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void bindFaces() {
    paintFace(binding.rowBold, R.string.instagram_opt_bold, Prefs.instagramBoldFace(this), true, false);
    paintFace(binding.rowItalic, R.string.instagram_opt_italic, Prefs.instagramItalicFace(this), false, true);
    paintFace(binding.rowBoth, R.string.instagram_opt_both, Prefs.instagramBothFace(this), true, true);
  }

  private void paintFace(
    ItemSettingRowBinding row, int title, String face, boolean bold, boolean italic) {
    row.title.setText(title);
    InstagramStyle.Faces faces = faceFaces(face, bold, italic);
    String sample = InstagramStyle.style("Abc", bold, italic, faces);
    row.value.setText(faceLabel(face) + "  " + sample);
  }

  private InstagramStyle.Faces faceFaces(String face, boolean bold, boolean italic) {
    InstagramStyle.Faces current = Prefs.instagramFaces(this);
    if (bold && italic) {
      return new InstagramStyle.Faces(current.bold, current.italic, face);
    }
    if (bold) {
      return new InstagramStyle.Faces(face, current.italic, current.both);
    }
    return new InstagramStyle.Faces(current.bold, face, current.both);
  }

  private String faceLabel(String face) {
    if (InstagramStyle.FACE_SERIF.equals(face)) {
      return getString(R.string.instagram_face_serif);
    }
    return getString(R.string.instagram_face_sans);
  }

  private boolean toInstagram() {
    return binding.direction.getCheckedButtonId() != R.id.btn_to_wa;
  }

  private String converted() {
    String source = text();
    if (source.isEmpty()) {
      return "";
    }
    if (toInstagram()) {
      return InstagramStyle.toUnicode(source, Prefs.instagramFaces(this));
    }
    return InstagramStyle.toMarkup(source);
  }

  private void paint() {
    if (binding == null) {
      return;
    }
    boolean ig = toInstagram();
    binding.previewLabel.setText(ig ? R.string.instagram_preview : R.string.instagram_preview_markup);
    binding.btnCopy.setText(ig ? R.string.copy_instagram : R.string.instagram_copy_markup);
    binding.preview.setText(converted());
  }

  private void copy() {
    String out = converted();
    if (out.isEmpty()) {
      Toast.makeText(this, R.string.need_caption, Toast.LENGTH_SHORT).show();
      return;
    }
    WhatsAppRouter.copyToClipboard(this, out);
    Toast.makeText(
      this,
      toInstagram() ? R.string.instagram_copied : R.string.instagram_markup_copied,
      Toast.LENGTH_LONG).show();
  }

  private String text() {
    if (binding == null || binding.input.getText() == null) {
      return "";
    }
    return binding.input.getText().toString();
  }

  private interface FaceSetter {
    void set(android.content.Context ctx, String face);
  }
}
