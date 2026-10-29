package damjay.publicity.omnipost.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.HoldRules;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.util.AppExecutors;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class HoldChooser {
  public interface Saved {
    void onSaved();
  }

  private HoldChooser() {}

  public static String summary(Context ctx) {
    if (ctx == null) {
      return "";
    }
    long now = System.currentTimeMillis();
    Map<String, Long> holds = HoldRules.prune(Prefs.holds(ctx), now);
    if (!HoldRules.any(holds, now)) {
      return ctx.getString(R.string.hold_none);
    }
    List<HoldRules.Program> programs = HoldRules.programs(null);
    List<String> labels = HoldRules.activeLabels(holds, now, programs);
    String when = DateUtils.formatHoldDay(HoldRules.latestUntil(holds, now));
    if (labels.isEmpty()) {
      int count = HoldRules.activeCount(holds, now);
      return ctx.getString(R.string.hold_summary_count, count, when);
    }
    if (labels.size() <= 3) {
      StringBuilder joined = new StringBuilder();
      for (int i = 0; i < labels.size(); i++) {
        if (i > 0) {
          joined.append(", ");
        }
        joined.append(labels.get(i));
      }
      return ctx.getString(R.string.hold_summary, joined.toString(), when);
    }
    return ctx.getString(R.string.hold_summary_count, labels.size(), when);
  }

  public static void show(Context context, @Nullable Saved saved) {
    if (context == null) {
      return;
    }
    Context app = context.getApplicationContext();
    AppExecutors.disk().execute(() -> {
      List<Series> series = AppDatabase.get(app).seriesDao().getAllSync();
      Map<String, Long> holds = HoldRules.prune(Prefs.holds(app), System.currentTimeMillis());
      List<HoldRules.Program> programs = HoldRules.programs(series);
      AppExecutors.main(() -> showPrograms(context, programs, holds, saved));
    });
  }

  public static void forTask(Context context, Task task, @Nullable Saved saved) {
    if (context == null || task == null) {
      return;
    }
    if (!HoldRules.canHoldProgram(task)) {
      pickDate(context, HoldRules.defaultUntil(System.currentTimeMillis()), until ->
        saveKey(context, HoldRules.taskKey(task.id), until, saved));
      return;
    }
    String programName = task.seriesId > 0L
      ? (task.title == null || task.title.isEmpty() ? TaskTypes.label(task.type) : task.title)
      : HoldRules.builtinLabel(task.type);
    if (programName == null || programName.isEmpty()) {
      programName = task.title == null ? "" : task.title;
    }
    CharSequence[] items = new CharSequence[] {
      context.getString(R.string.hold_this_post),
      context.getString(R.string.hold_this_program, programName)
    };
    new MaterialAlertDialogBuilder(context)
      .setTitle(R.string.hold_until_action)
      .setItems(items, (d, which) -> {
        String key = which == 0 ? HoldRules.taskKey(task.id) : HoldRules.programKey(task);
        pickDate(context, HoldRules.defaultUntil(System.currentTimeMillis()), until ->
          saveKey(context, key, until, saved));
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  public static void resumeKey(Context context, String key, @Nullable Saved saved) {
    if (context == null || key == null || key.isEmpty()) {
      return;
    }
    Context app = context.getApplicationContext();
    AppExecutors.disk().execute(() -> {
      Map<String, Long> next = HoldRules.put(Prefs.holds(app), key, 0L);
      Prefs.setHolds(app, next);
      ScheduleCoordinator.bootstrap(app);
      AppExecutors.main(() -> {
        Toast.makeText(context, R.string.hold_resumed, Toast.LENGTH_SHORT).show();
        if (saved != null) {
          saved.onSaved();
        }
      });
    });
  }

  private static void showPrograms(
    Context context,
    List<HoldRules.Program> programs,
    Map<String, Long> holds,
    @Nullable Saved saved) {
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_hold, null, false);
    LinearLayout list = view.findViewById(R.id.hold_programs);
    MaterialButton dateBtn = view.findViewById(R.id.btn_hold_date);
    long now = System.currentTimeMillis();
    long initial = HoldRules.latestUntil(holds, now);
    if (initial <= now) {
      initial = HoldRules.defaultUntil(now);
    }
    AtomicLong until = new AtomicLong(DateUtils.startOfDay(initial));
    List<CheckBox> boxes = new ArrayList<>();
    for (HoldRules.Program program : programs) {
      CheckBox box = new CheckBox(context);
      box.setText(program.label);
      box.setTag(program.key);
      Long heldUntil = holds.get(program.key);
      box.setChecked(heldUntil != null && heldUntil > now);
      list.addView(box);
      boxes.add(box);
    }
    Runnable paintDate = () ->
      dateBtn.setText(context.getString(R.string.hold_pick_date, DateUtils.formatHoldDay(until.get())));
    paintDate.run();
    dateBtn.setOnClickListener(v -> pickDate(context, until.get(), picked -> {
      until.set(picked);
      paintDate.run();
    }));
    new MaterialAlertDialogBuilder(context)
      .setTitle(R.string.hold_title)
      .setView(view)
      .setPositiveButton(R.string.hold_action, (d, w) -> {
        List<String> selected = new ArrayList<>();
        for (CheckBox box : boxes) {
          if (box.isChecked()) {
            selected.add(String.valueOf(box.getTag()));
          }
        }
        savePrograms(context, selected, until.get(), saved);
      })
      .setNeutralButton(R.string.hold_resume_all, (d, w) -> resumeAll(context, saved))
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private static void pickDate(Context context, long initial, HoldCallback callback) {
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(initial > 0L ? initial : HoldRules.defaultUntil(System.currentTimeMillis()));
    DatePickerDialog picker = new DatePickerDialog(
      context,
      (v, year, month, day) -> {
        Calendar chosen = Calendar.getInstance();
        chosen.set(Calendar.YEAR, year);
        chosen.set(Calendar.MONTH, month);
        chosen.set(Calendar.DAY_OF_MONTH, day);
        callback.onChosen(DateUtils.startOfDay(chosen).getTimeInMillis());
      },
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH),
      cal.get(Calendar.DAY_OF_MONTH));
    picker.getDatePicker().setMinDate(
      DateUtils.startOfDay(System.currentTimeMillis()) + 24L * 60L * 60L * 1000L);
    picker.show();
  }

  private static void savePrograms(
    Context context, List<String> selected, long until, @Nullable Saved saved) {
    Context app = context.getApplicationContext();
    AppExecutors.disk().execute(() -> {
      Map<String, Long> next = HoldRules.applyPrograms(Prefs.holds(app), selected, until);
      Prefs.setHolds(app, HoldRules.prune(next, System.currentTimeMillis()));
      ScheduleCoordinator.bootstrap(app);
      AppExecutors.main(() -> {
        if (selected == null || selected.isEmpty()) {
          Toast.makeText(context, R.string.hold_resumed, Toast.LENGTH_SHORT).show();
        } else {
          Toast.makeText(
            context,
            context.getString(R.string.hold_toast, DateUtils.formatHoldDay(until)),
            Toast.LENGTH_LONG)
            .show();
        }
        if (saved != null) {
          saved.onSaved();
        }
      });
    });
  }

  private static void saveKey(Context context, String key, long until, @Nullable Saved saved) {
    Context app = context.getApplicationContext();
    AppExecutors.disk().execute(() -> {
      Map<String, Long> next = HoldRules.put(Prefs.holds(app), key, until);
      Prefs.setHolds(app, next);
      ScheduleCoordinator.bootstrap(app);
      AppExecutors.main(() -> {
        Toast.makeText(
          context,
          context.getString(R.string.hold_toast, DateUtils.formatHoldDay(until)),
          Toast.LENGTH_LONG)
          .show();
        if (saved != null) {
          saved.onSaved();
        }
      });
    });
  }

  private static void resumeAll(Context context, @Nullable Saved saved) {
    Context app = context.getApplicationContext();
    AppExecutors.disk().execute(() -> {
      Prefs.setHolds(app, HoldRules.clearPrograms(Prefs.holds(app)));
      ScheduleCoordinator.bootstrap(app);
      AppExecutors.main(() -> {
        Toast.makeText(context, R.string.hold_resumed, Toast.LENGTH_SHORT).show();
        if (saved != null) {
          saved.onSaved();
        }
      });
    });
  }

  private interface HoldCallback {
    void onChosen(long untilMillis);
  }
}
