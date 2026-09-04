package damjay.publicity.omnipost.ui;

import android.content.Intent;
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
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.databinding.ActivityDraftBinding;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.CaptionVars;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.share.InstagramStyle;
import damjay.publicity.omnipost.share.WhatsAppPreview;
import damjay.publicity.omnipost.share.WhatsAppRouter;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.ExtraKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DraftActivity extends AppCompatActivity {
  private ActivityDraftBinding binding;
  private long taskId;
  private long draftId;
  private Draft draft;
  private Task task;
  private Series series;
  private Map<String, String> extras = new LinkedHashMap<>();
  private final List<CaptionVar> varList = new ArrayList<>();
  private boolean loaded;
  private boolean usingB;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityDraftBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    taskId = getIntent().getLongExtra(ExtraKeys.TASK_ID, 0L);
    draftId = getIntent().getLongExtra(ExtraKeys.DRAFT_ID, 0L);

    binding.btnBack.setOnClickListener(v -> finish());
    binding.btnVars.setOnClickListener(v ->
      startActivity(new Intent(this, VariablesActivity.class)));
    binding.btnSave.setOnClickListener(v -> persist(true));
    binding.btnUseA.setOnClickListener(v -> {
      usingB = false;
      paintPreview();
    });
    binding.btnUseB.setOnClickListener(v -> {
      usingB = true;
      paintPreview();
    });
    binding.btnPeeps.setOnClickListener(v -> sendToPeeps());
    binding.btnInstagram.setOnClickListener(v -> copyInstagram());
    binding.btnFinal.setOnClickListener(v -> finalPost());
    watch(binding.inputA, binding.countA);
    watch(binding.inputB, binding.countB);
    binding.compareScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
      if (r - l != or - ol) {
        sizeColumns();
      }
    });
    sizeColumns();
    if (taskId > 0L) {
      NotificationHelper.hush(this, taskId);
    }
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
        applyLingeredTemplate(db, found, linked);
      } else if (taskId > 0L) {
        linked = db.taskDao().getById(taskId);
        found = db.draftDao().findByTaskId(taskId);
        if (found == null) {
          found = new Draft();
          found.taskId = taskId;
          found.title = linked == null ? "Caption" : linked.title;
          found.variantA = seedCaption(this, db, linked);
          found.finalizedText = "";
          found.updatedAt = System.currentTimeMillis();
          found.id = db.draftDao().insert(found);
          if (linked != null) {
            db.taskDao().setLinkedDraft(linked.id, found.id);
            if (TaskStatus.SCHEDULED.equals(linked.status)
                && !TaskStatus.captionIsSaved(linked)) {
              db.taskDao().updateStatus(linked.id, TaskStatus.DRAFTING);
            }
          }
        } else {
          applyLingeredTemplate(db, found, linked);
        }
      } else {
        found = new Draft();
        found.title = "Untitled caption";
        found.updatedAt = System.currentTimeMillis();
        found.id = db.draftDao().insert(found);
      }
      draft = found;
      task = linked;
      series = CaptionTemplates.seriesOf(db, linked);
      List<CaptionVar> vars = db.captionVarDao().getAllSync();
      extras = CaptionVars.map(vars);
      varList.clear();
      if (vars != null) {
        varList.addAll(vars);
      }
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
        loaded = true;
        paintTokens();
        paintPreview();
      });
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (loaded) {
      AppExecutors.disk().execute(() -> {
        List<CaptionVar> vars = AppDatabase.get(this).captionVarDao().getAllSync();
        extras = CaptionVars.map(vars);
        varList.clear();
        if (vars != null) {
          varList.addAll(vars);
        }
        AppExecutors.main(() -> {
          if (isFinishing() || binding == null) {
            return;
          }
          paintTokens();
          paintPreview();
        });
      });
    }
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
    draft.finalizedText = CaptionTemplates.apply(source(), task, series, extras);
    if (draft.title == null || draft.title.isEmpty()) {
      draft.title = task != null ? task.title : "Untitled caption";
    }
    draft.updatedAt = System.currentTimeMillis();
    Draft snapshot = copy(draft);
    final Task linked = task;
    final long linkedTask = taskId;
    final boolean mark = toast && linkedTask > 0L && !pickText().isEmpty();
    if (series != null && !CaptionTemplates.isCanned(snapshot.variantA)) {
      series.caption = snapshot.variantA;
    }
    AppExecutors.disk().execute(() -> {
      AppDatabase.get(this).draftDao().update(snapshot);
      ScheduleCoordinator.rememberSeriesCaption(this, linked, snapshot.variantA);
      if (mark) {
        ScheduleCoordinator.markCaptionSaved(this, linkedTask);
      }
      if (toast) {
        AppExecutors.main(() ->
          Toast.makeText(
            this,
            mark ? R.string.caption_saved : R.string.saved,
            Toast.LENGTH_SHORT).show());
      }
    });
  }

  private void copyInstagram() {
    persist(false);
    String caption = pickText();
    if (caption.isEmpty()) {
      Toast.makeText(this, R.string.need_caption, Toast.LENGTH_SHORT).show();
      return;
    }
    WhatsAppRouter.copyToClipboard(this, InstagramStyle.toUnicode(caption));
    Toast.makeText(this, R.string.instagram_copied, Toast.LENGTH_LONG).show();
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

  private String source() {
    if (usingB) {
      String b = text(binding.inputB);
      if (!b.isEmpty()) {
        return b;
      }
    }
    return text(binding.inputA);
  }

  private String pickText() {
    return CaptionTemplates.apply(source(), task, series, extras);
  }

  private void paintTokens() {
    if (binding == null) {
      return;
    }
    binding.tokenRow.removeAllViews();
    if (varList.isEmpty()) {
      binding.tokenScroll.setVisibility(View.GONE);
      return;
    }
    binding.tokenScroll.setVisibility(View.VISIBLE);
    for (CaptionVar var : varList) {
      android.widget.TextView chip = new android.widget.TextView(this);
      chip.setText(CaptionVars.token(var.name));
      chip.setTextColor(getColor(R.color.gold));
      chip.setTextSize(13f);
      chip.setPadding(20, 12, 20, 12);
      chip.setBackgroundResource(R.drawable.bg_chip_gold);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMarginEnd(8);
      chip.setLayoutParams(params);
      chip.setOnClickListener(v -> insertToken(var.name));
      binding.tokenRow.addView(chip);
    }
  }

  private void insertToken(String name) {
    String token = CaptionVars.token(name);
    if (token.isEmpty() || binding == null) {
      return;
    }
    com.google.android.material.textfield.TextInputEditText input =
      usingB ? binding.inputB : binding.inputA;
    Editable editable = input.getText();
    if (editable == null) {
      input.setText(token);
      paintPreview();
      return;
    }
    int start = Math.max(input.getSelectionStart(), 0);
    int end = Math.max(input.getSelectionEnd(), start);
    editable.replace(start, end, token);
    paintPreview();
  }

  private void paintPreview() {
    if (binding == null) {
      return;
    }
    String filled = CaptionTemplates.apply(source(), task, series, extras);
    binding.previewFinal.setText(WhatsAppPreview.display(filled));
  }

  private void sizeColumns() {
    if (binding == null) {
      return;
    }
    int scrollW = binding.compareScroll.getWidth()
      - binding.compareScroll.getPaddingLeft()
      - binding.compareScroll.getPaddingRight();
    if (scrollW <= 0) {
      float density = getResources().getDisplayMetrics().density;
      scrollW = getResources().getDisplayMetrics().widthPixels - Math.round(32f * density);
    }
    boolean landscape =
      getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    int colW = landscape ? Math.max(scrollW / 2, 1) : Math.max(scrollW, 1);
    LinearLayout.LayoutParams left =
      new LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT);
    binding.colA.setLayoutParams(left);
    LinearLayout.LayoutParams right =
      new LinearLayout.LayoutParams(colW, LinearLayout.LayoutParams.WRAP_CONTENT);
    binding.colB.setLayoutParams(right);
    binding.colB.setVisibility(View.VISIBLE);
    binding.compareHint.setVisibility(landscape ? View.GONE : View.VISIBLE);
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
        if (loaded) {
          paintPreview();
        }
      }
    };
    input.addTextChangedListener(watcher);
    count.setText(getString(R.string.chars, 0));
  }

  private static String seedCaption(android.content.Context context, AppDatabase db, Task task) {
    Series series = CaptionTemplates.seriesOf(db, task);
    if (task != null && CaptionTemplates.isLive(task.type)) {
      String shared = CaptionTemplates.sharedTemplate(
        series == null ? "" : series.caption, "", task, series);
      if (shared != null && !shared.isEmpty()) {
        return shared;
      }
    }
    return CaptionTemplates.forTask(context, task, series);
  }

  private static void applyLingeredTemplate(AppDatabase db, Draft found, Task task) {
    if (found == null || task == null || !CaptionTemplates.isLive(task.type)) {
      return;
    }
    Series series = CaptionTemplates.seriesOf(db, task);
    String shared = CaptionTemplates.sharedTemplate(
      series == null ? "" : series.caption, "", task, series);
    found.variantA = CaptionTemplates.lingerDraft(found.variantA, shared);
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
