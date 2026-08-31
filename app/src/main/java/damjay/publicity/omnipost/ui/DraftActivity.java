package damjay.publicity.omnipost.ui;

import android.content.res.Configuration;
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
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityDraftBinding;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;

public class DraftActivity extends AppCompatActivity {
  private ActivityDraftBinding binding;
  private long taskId;
  private long draftId;
  private Draft draft;
  private Task task;
  private boolean loaded;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityDraftBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    taskId = getIntent().getLongExtra(ExtraKeys.TASK_ID, 0L);
    draftId = getIntent().getLongExtra(ExtraKeys.DRAFT_ID, 0L);

    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnSave.setOnClickListener(v -> persist(true));
    binding.toggleCompare.setOnCheckedChangeListener((b, checked) -> applyCompareLayout());
    binding.btnUseA.setOnClickListener(v -> binding.inputFinal.setText(text(binding.inputA)));
    binding.btnUseB.setOnClickListener(v -> binding.inputFinal.setText(text(binding.inputB)));
    binding.btnPeeps.setOnClickListener(v -> sendToPeeps());
    binding.btnFinal.setOnClickListener(v -> finalPost());
    watch(binding.inputA, binding.countA);
    watch(binding.inputB, binding.countB);
    applyCompareLayout();
    load();
  }

  private void load() {
    AppExecutors.disk().execute(() -> {
      AppDatabase db = AppDatabase.get(this);
      Draft found = null;
      Task linked = null;
      if (draftId > 0L) {
        found = db.draftDao().getById(draftId);
        if (found != null && found.taskId > 0L) {
          linked = db.taskDao().getById(found.taskId);
        }
      } else if (taskId > 0L) {
        linked = db.taskDao().getById(taskId);
        found = db.draftDao().findByTaskId(taskId);
        if (found == null) {
          found = new Draft();
          found.taskId = taskId;
          found.title = linked == null ? "Caption" : linked.title;
          found.variantA = CaptionTemplates.forTask(this, linked);
          found.finalizedText = found.variantA;
          found.updatedAt = System.currentTimeMillis();
          found.id = db.draftDao().insert(found);
          if (linked != null) {
            db.taskDao().setLinkedDraft(linked.id, found.id);
            if (TaskStatus.SCHEDULED.equals(linked.status)) {
              db.taskDao().updateStatus(linked.id, TaskStatus.DRAFTING);
            }
          }
        }
      } else {
        found = new Draft();
        found.title = "Untitled caption";
        found.updatedAt = System.currentTimeMillis();
        found.id = db.draftDao().insert(found);
      }
      draft = found;
      task = linked;
      if (draft != null) {
        draftId = draft.id;
        taskId = draft.taskId > 0L ? draft.taskId : taskId;
      }
      AppExecutors.main(() -> {
        if (isFinishing() || binding == null || draft == null) {
          return;
        }
        binding.taskTitle.setText(draft.title);
        binding.inputA.setText(draft.variantA);
        binding.inputB.setText(draft.variantB);
        binding.inputFinal.setText(
          draft.finalizedText == null || draft.finalizedText.isEmpty()
            ? draft.variantA
            : draft.finalizedText);
        loaded = true;
      });
    });
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (loaded) {
      persist(false);
    }
  }

  private void persist(boolean toast) {
    if (draft == null || binding == null) {
      return;
    }
    draft.variantA = text(binding.inputA);
    draft.variantB = text(binding.inputB);
    draft.finalizedText = text(binding.inputFinal);
    if (draft.title == null || draft.title.isEmpty()) {
      draft.title = task != null ? task.title : "Untitled caption";
    }
    draft.updatedAt = System.currentTimeMillis();
    Draft snapshot = copy(draft);
    AppExecutors.disk().execute(() -> {
      AppDatabase.get(this).draftDao().update(snapshot);
      if (toast) {
        AppExecutors.main(() ->
          Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show());
      }
    });
  }

  private void sendToPeeps() {
    persist(false);
    String caption = pickText();
    if (caption.isEmpty()) {
      Toast.makeText(this, R.string.need_caption, Toast.LENGTH_SHORT).show();
      return;
    }
    boolean opened = WhatsAppRouter.sendExplicit(this, caption);
    Toast.makeText(
      this,
      opened ? R.string.copied : R.string.whatsapp_missing,
      Toast.LENGTH_LONG).show();
  }

  private void finalPost() {
    persist(false);
    String caption = pickText();
    if (caption.isEmpty()) {
      Toast.makeText(this, R.string.need_caption, Toast.LENGTH_SHORT).show();
      return;
    }
    boolean opened = WhatsAppRouter.sendExplicit(this, caption);
    if (taskId <= 0L) {
      if (!opened) {
        Toast.makeText(this, R.string.whatsapp_missing, Toast.LENGTH_LONG).show();
      }
      return;
    }
    if (opened) {
      markPostedAndFinish();
      return;
    }
    new MaterialAlertDialogBuilder(this)
      .setMessage(R.string.whatsapp_missing)
      .setPositiveButton(R.string.mark_anyway, (d, w) -> markPostedAndFinish())
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private void markPostedAndFinish() {
    final long id = taskId;
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.markPosted(this, id);
      AppExecutors.main(() -> {
        Toast.makeText(this, R.string.posted_toast, Toast.LENGTH_LONG).show();
        finish();
      });
    });
  }

  private String pickText() {
    String finalized = text(binding.inputFinal);
    if (!finalized.isEmpty()) {
      return finalized;
    }
    String a = text(binding.inputA);
    if (!a.isEmpty()) {
      return a;
    }
    return text(binding.inputB);
  }

  private void applyCompareLayout() {
    boolean compare = binding.toggleCompare.isChecked();
    binding.colB.setVisibility(compare ? View.VISIBLE : View.GONE);
    binding.btnUseA.setVisibility(compare ? View.VISIBLE : View.GONE);
    binding.labelA.setText(compare ? R.string.variation_a : R.string.caption);
    boolean landscape =
      getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    if (compare && landscape) {
      binding.compareRow.setOrientation(LinearLayout.HORIZONTAL);
      LinearLayout.LayoutParams left =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
      left.setMarginEnd(8);
      binding.colA.setLayoutParams(left);
      LinearLayout.LayoutParams right =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
      right.setMarginStart(8);
      binding.colB.setLayoutParams(right);
    } else {
      binding.compareRow.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams full =
        new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      binding.colA.setLayoutParams(full);
      binding.colB.setLayoutParams(full);
    }
  }

  private void watch(
    com.google.android.material.textfield.TextInputEditText input,
    android.widget.TextView count) {
    TextWatcher watcher = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count1, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count1) {}

      @Override
      public void afterTextChanged(Editable s) {
        int n = s == null ? 0 : s.length();
        count.setText(getString(R.string.chars, n));
      }
    };
    input.addTextChangedListener(watcher);
    count.setText(getString(R.string.chars, 0));
  }

  private static String text(com.google.android.material.textfield.TextInputEditText input) {
    return input.getText() == null ? "" : input.getText().toString().trim();
  }

  private static Draft copy(Draft source) {
    Draft out = new Draft();
    out.id = source.id;
    out.taskId = source.taskId;
    out.title = source.title;
    out.variantA = source.variantA;
    out.variantB = source.variantB;
    out.finalizedText = source.finalizedText;
    out.updatedAt = source.updatedAt;
    return out;
  }
}
