package damjay.publicity.omnipost.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.SeriesDefaults;
import damjay.publicity.omnipost.util.AppExecutors;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicLong;

public final class SeriesEditor {
  private SeriesEditor() {}

  public static void createCountdown(Context context) {
    Series series = new Series();
    series.kind = Series.KIND_COUNTDOWN;
    series.caption = SeriesDefaults.blankCountdownCaption();
    show(context, series);
  }

  public static void createMonthly(Context context) {
    Series series = new Series();
    series.kind = Series.KIND_MONTHLY;
    series.caption = SeriesDefaults.blankNoticeCaption();
    series.lastOfPrevMonth = true;
    series.day10 = true;
    series.day20 = true;
    show(context, series);
  }

  public static void show(Context context, @Nullable Series existing) {
    Series source = existing == null ? new Series() : existing;
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_series, null, false);
    TextInputEditText title = view.findViewById(R.id.input_title);
    TextInputEditText caption = view.findViewById(R.id.input_caption);
    RadioGroup kind = view.findViewById(R.id.kind);
    RadioButton kindCountdown = view.findViewById(R.id.kind_countdown);
    RadioButton kindMonthly = view.findViewById(R.id.kind_monthly);
    View blockCountdown = view.findViewById(R.id.block_countdown);
    View blockMonthly = view.findViewById(R.id.block_monthly);
    MaterialButton eventBtn = view.findViewById(R.id.btn_event);
    CheckBox optEve = view.findViewById(R.id.opt_eve);
    CheckBox opt10 = view.findViewById(R.id.opt_10);
    CheckBox opt20 = view.findViewById(R.id.opt_20);

    AtomicLong eventAt = new AtomicLong(source.eventAtMillis);
    if (eventAt.get() <= 0L) {
      Calendar start = Calendar.getInstance();
      start.add(Calendar.DAY_OF_MONTH, 7);
      start.set(Calendar.HOUR_OF_DAY, 0);
      start.set(Calendar.MINUTE, 0);
      start.set(Calendar.SECOND, 0);
      start.set(Calendar.MILLISECOND, 0);
      eventAt.set(start.getTimeInMillis());
    }
    title.setText(source.title);
    caption.setText(source.caption);
    boolean monthly = Series.KIND_MONTHLY.equals(source.kind);
    kindMonthly.setChecked(monthly);
    kindCountdown.setChecked(!monthly);
    optEve.setChecked(source.lastOfPrevMonth);
    opt10.setChecked(source.day10);
    opt20.setChecked(source.day20);
    Runnable paintEvent = () -> eventBtn.setText(
      context.getString(R.string.dday_on, DateUtils.formatDayHeader(eventAt.get())));
    paintEvent.run();
    Runnable paintKind = () -> {
      boolean isMonthly = kindMonthly.isChecked();
      blockCountdown.setVisibility(isMonthly ? View.GONE : View.VISIBLE);
      blockMonthly.setVisibility(isMonthly ? View.VISIBLE : View.GONE);
    };
    paintKind.run();
    kind.setOnCheckedChangeListener((g, id) -> {
      paintKind.run();
      String current = caption.getText() == null ? "" : caption.getText().toString();
      if (id == R.id.kind_monthly
          && (current.isEmpty() || current.equals(SeriesDefaults.blankCountdownCaption()))) {
        caption.setText(SeriesDefaults.blankNoticeCaption());
      } else if (id == R.id.kind_countdown
          && (current.isEmpty() || current.equals(SeriesDefaults.blankNoticeCaption()))) {
        caption.setText(SeriesDefaults.blankCountdownCaption());
      }
    });
    eventBtn.setOnClickListener(v -> pickDay(context, eventAt.get(), millis -> {
      eventAt.set(millis);
      paintEvent.run();
    }));

    boolean editing = source.id > 0L;
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
      .setTitle(editing ? R.string.edit_series : R.string.add_series)
      .setView(view)
      .setPositiveButton(R.string.save, (d, w) -> {
        String name = title.getText() == null ? "" : title.getText().toString().trim();
        if (name.isEmpty()) {
          Toast.makeText(context, R.string.need_title, Toast.LENGTH_SHORT).show();
          return;
        }
        Series series = existing == null ? new Series() : existing;
        series.title = name;
        series.kind = kindMonthly.isChecked() ? Series.KIND_MONTHLY : Series.KIND_COUNTDOWN;
        series.caption = caption.getText() == null ? "" : caption.getText().toString();
        series.eventAtMillis = eventAt.get();
        series.lastOfPrevMonth = optEve.isChecked();
        series.day10 = opt10.isChecked();
        series.day20 = opt20.isChecked();
        series.enabled = true;
        if (Series.KIND_MONTHLY.equals(series.kind)
            && !series.lastOfPrevMonth
            && !series.day10
            && !series.day20) {
          Toast.makeText(context, R.string.need_month_slot, Toast.LENGTH_SHORT).show();
          return;
        }
        Context app = context.getApplicationContext();
        AppExecutors.disk().execute(() -> {
          ScheduleCoordinator.saveSeries(app, series);
          AppExecutors.main(() ->
            Toast.makeText(context, R.string.series_saved, Toast.LENGTH_SHORT).show());
        });
      })
      .setNegativeButton(android.R.string.cancel, null);
    if (editing) {
      builder.setNeutralButton(R.string.delete, (d, w) -> confirmDelete(context, source));
    }
    builder.show();
  }

  private static void confirmDelete(Context context, Series series) {
    new MaterialAlertDialogBuilder(context)
      .setTitle(R.string.delete_series_title)
      .setMessage(context.getString(R.string.delete_series_body, series.title))
      .setPositiveButton(R.string.delete, (d, w) -> {
        Context app = context.getApplicationContext();
        AppExecutors.disk().execute(() -> ScheduleCoordinator.deleteSeries(app, series.id));
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  private static void pickDay(Context context, long initial, SnoozeChooser.Callback callback) {
    Calendar start = Calendar.getInstance();
    if (initial > 0L) {
      start.setTimeInMillis(initial);
    }
    new DatePickerDialog(
      context,
      (view, year, month, dayOfMonth) -> {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.YEAR, year);
        day.set(Calendar.MONTH, month);
        day.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        callback.onChosen(day.getTimeInMillis());
      },
      start.get(Calendar.YEAR),
      start.get(Calendar.MONTH),
      start.get(Calendar.DAY_OF_MONTH))
      .show();
  }
}
