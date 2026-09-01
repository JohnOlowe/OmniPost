package damjay.publicity.omnipost.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
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
import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.scheduler.SeriesDefaults;
import damjay.publicity.omnipost.scheduler.Weekdays;
import damjay.publicity.omnipost.util.AppExecutors;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class SeriesEditor {
  private static final int[] DAY_IDS = {
    R.id.day_sun, R.id.day_mon, R.id.day_tue, R.id.day_wed,
    R.id.day_thu, R.id.day_fri, R.id.day_sat
  };

  private SeriesEditor() {}

  public static void createCountdown(Context context) {
    Series series = new Series();
    series.kind = Series.KIND_COUNTDOWN;
    series.caption = SeriesDefaults.blankCountdownCaption();
    series.postHour = ScheduleTimes.MONTH_POST_HOUR;
    show(context, series);
  }

  public static void createMonthly(Context context) {
    Series series = new Series();
    series.kind = Series.KIND_MONTHLY;
    series.caption = SeriesDefaults.blankNoticeCaption();
    series.postHour = ScheduleTimes.MONTH_POST_HOUR;
    series.lastOfPrevMonth = true;
    series.tenth = true;
    series.twentieth = true;
    show(context, series);
  }

  public static void createWeekly(Context context) {
    Series series = new Series();
    series.kind = Series.KIND_WEEKLY;
    series.caption = SeriesDefaults.blankWeeklyCaption();
    series.postHour = ScheduleTimes.WEEKLY_POST_HOUR;
    series.postMinute = ScheduleTimes.WEEKLY_POST_MINUTE;
    show(context, series);
  }

  public static void createDaily(Context context) {
    Series series = new Series();
    series.kind = Series.KIND_DAILY;
    series.caption = SeriesDefaults.blankDailyCaption();
    series.postHour = ScheduleTimes.MONTH_POST_HOUR;
    show(context, series);
  }

  public static void show(Context context, @Nullable Series existing) {
    if (context == null) {
      return;
    }
    Series source = existing == null ? new Series() : existing;
    View view = LayoutInflater.from(context).inflate(R.layout.dialog_series, null, false);
    TextInputEditText title = view.findViewById(R.id.series_name);
    TextInputEditText caption = view.findViewById(R.id.series_body);
    TextInputEditText vars = view.findViewById(R.id.series_vars);
    RadioGroup kindGroup = view.findViewById(R.id.series_kind);
    RadioButton kindCountdown = view.findViewById(R.id.radio_countdown);
    RadioButton kindMonthly = view.findViewById(R.id.radio_monthly);
    RadioButton kindWeekly = view.findViewById(R.id.radio_weekly);
    RadioButton kindDaily = view.findViewById(R.id.radio_daily);
    View blockCountdown = view.findViewById(R.id.block_countdown);
    View blockMonthly = view.findViewById(R.id.block_monthly);
    View blockWeekly = view.findViewById(R.id.block_weekly);
    View blockDaily = view.findViewById(R.id.block_daily);
    MaterialButton eventBtn = view.findViewById(R.id.btn_event);
    MaterialButton endBtn = view.findViewById(R.id.btn_end);
    MaterialButton timeBtn = view.findViewById(R.id.btn_post_time);
    MaterialButton varsBtn = view.findViewById(R.id.btn_vars);
    CheckBox optEve = view.findViewById(R.id.opt_eve);
    CheckBox opt10 = view.findViewById(R.id.opt_tenth);
    CheckBox opt20 = view.findViewById(R.id.opt_twentieth);
    CheckBox[] dayBoxes = new CheckBox[DAY_IDS.length];
    for (int i = 0; i < DAY_IDS.length; i++) {
      dayBoxes[i] = view.findViewById(DAY_IDS[i]);
    }

    AtomicLong eventAt = new AtomicLong(source.eventAtMillis);
    AtomicLong endAt = new AtomicLong(source.endAtMillis);
    AtomicInteger postHour = new AtomicInteger(source.postHour);
    AtomicInteger postMinute = new AtomicInteger(source.postMinute);
    if (eventAt.get() <= 0L) {
      Calendar start = Calendar.getInstance();
      start.add(Calendar.DAY_OF_MONTH, 7);
      start.set(Calendar.HOUR_OF_DAY, 0);
      start.set(Calendar.MINUTE, 0);
      start.set(Calendar.SECOND, 0);
      start.set(Calendar.MILLISECOND, 0);
      eventAt.set(start.getTimeInMillis());
    }
    if (endAt.get() <= 0L) {
      Calendar last = Calendar.getInstance();
      last.setTimeInMillis(eventAt.get());
      last.add(Calendar.DAY_OF_MONTH, 4);
      endAt.set(last.getTimeInMillis());
    }
    title.setText(source.title);
    caption.setText(source.caption);
    vars.setText(source.vars);
    checkKind(source.kind, kindWeekly, kindDaily, kindCountdown, kindMonthly);
    optEve.setChecked(source.lastOfPrevMonth);
    opt10.setChecked(source.tenth);
    opt20.setChecked(source.twentieth);
    paintDays(dayBoxes, source.weekdays);
    paintEvent(context, eventBtn, eventAt.get());
    paintEnd(context, endBtn, endAt.get());
    paintTime(context, timeBtn, postHour.get(), postMinute.get());
    paintKind(kindGroup, blockCountdown, blockMonthly, blockWeekly, blockDaily);
    kindGroup.setOnCheckedChangeListener((group, checkedId) -> {
      paintKind(kindGroup, blockCountdown, blockMonthly, blockWeekly, blockDaily);
      swapBlank(caption, checkedId);
    });
    eventBtn.setOnClickListener(v -> pickDay(context, eventAt.get(), chosen -> {
      eventAt.set(chosen);
      paintEvent(context, eventBtn, eventAt.get());
      if (endAt.get() < chosen) {
        Calendar last = Calendar.getInstance();
        last.setTimeInMillis(chosen);
        last.add(Calendar.DAY_OF_MONTH, 4);
        endAt.set(last.getTimeInMillis());
        paintEnd(context, endBtn, endAt.get());
      }
    }));
    endBtn.setOnClickListener(v -> pickDay(context, endAt.get(), chosen -> {
      long start = eventAt.get();
      endAt.set(Math.max(chosen, start));
      paintEnd(context, endBtn, endAt.get());
    }));
    timeBtn.setOnClickListener(v -> SnoozeChooser.pickClock(
      context,
      postHour.get(),
      postMinute.get(),
      (hour, minute) -> {
        postHour.set(hour);
        postMinute.set(minute);
        paintTime(context, timeBtn, hour, minute);
      }));
    varsBtn.setOnClickListener(v -> {
      Intent intent = new Intent(context, VariablesActivity.class);
      if (!(context instanceof android.app.Activity)) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      }
      context.startActivity(intent);
    });

    boolean editing = source.id > 0L;
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
      .setTitle(editing ? R.string.edit_series : R.string.add_series)
      .setView(view)
      .setPositiveButton(R.string.save, (d, w) -> saveFromForm(
        context,
        existing,
        title,
        caption,
        vars,
        kindGroup,
        eventAt.get(),
        endAt.get(),
        postHour.get(),
        postMinute.get(),
        optEve.isChecked(),
        opt10.isChecked(),
        opt20.isChecked(),
        readDays(dayBoxes)))
      .setNegativeButton(android.R.string.cancel, null);
    if (editing) {
      builder.setNeutralButton(R.string.delete, (d, w) -> confirmDelete(context, source));
    }
    builder.show();
  }

  private static void checkKind(
    String kind,
    RadioButton weekly,
    RadioButton daily,
    RadioButton countdown,
    RadioButton monthly) {
    if (Series.KIND_WEEKLY.equals(kind)) {
      weekly.setChecked(true);
    } else if (Series.KIND_DAILY.equals(kind)) {
      daily.setChecked(true);
    } else if (Series.KIND_MONTHLY.equals(kind)) {
      monthly.setChecked(true);
    } else {
      countdown.setChecked(true);
    }
  }

  private static void paintEvent(Context context, MaterialButton eventBtn, long millis) {
    eventBtn.setText(context.getString(R.string.dday_on, DateUtils.formatDayHeader(millis)));
  }

  private static void paintEnd(Context context, MaterialButton endBtn, long millis) {
    endBtn.setText(context.getString(R.string.end_on, DateUtils.formatDayHeader(millis)));
  }

  private static void paintTime(Context context, MaterialButton timeBtn, int hour, int minute) {
    timeBtn.setText(context.getString(R.string.post_at, Weekdays.clock(hour, minute)));
  }

  private static void paintKind(
    RadioGroup kindGroup,
    View blockCountdown,
    View blockMonthly,
    View blockWeekly,
    View blockDaily) {
    int id = kindGroup.getCheckedRadioButtonId();
    blockWeekly.setVisibility(id == R.id.radio_weekly ? View.VISIBLE : View.GONE);
    blockDaily.setVisibility(id == R.id.radio_daily ? View.VISIBLE : View.GONE);
    blockCountdown.setVisibility(id == R.id.radio_countdown ? View.VISIBLE : View.GONE);
    blockMonthly.setVisibility(id == R.id.radio_monthly ? View.VISIBLE : View.GONE);
  }

  private static void swapBlank(TextInputEditText caption, int checkedId) {
    String current = caption.getText() == null ? "" : caption.getText().toString();
    if (!current.isEmpty()
        && !current.equals(SeriesDefaults.blankCountdownCaption())
        && !current.equals(SeriesDefaults.blankNoticeCaption())
        && !current.equals(SeriesDefaults.blankWeeklyCaption())
        && !current.equals(SeriesDefaults.blankDailyCaption())) {
      return;
    }
    if (checkedId == R.id.radio_monthly) {
      caption.setText(SeriesDefaults.blankNoticeCaption());
    } else if (checkedId == R.id.radio_weekly) {
      caption.setText(SeriesDefaults.blankWeeklyCaption());
    } else if (checkedId == R.id.radio_daily) {
      caption.setText(SeriesDefaults.blankDailyCaption());
    } else {
      caption.setText(SeriesDefaults.blankCountdownCaption());
    }
  }

  private static void paintDays(CheckBox[] boxes, int mask) {
    int[] days = Weekdays.days();
    for (int i = 0; i < boxes.length && i < days.length; i++) {
      boxes[i].setChecked(Weekdays.has(mask, days[i]));
    }
  }

  private static int readDays(CheckBox[] boxes) {
    int mask = Weekdays.NONE;
    int[] days = Weekdays.days();
    for (int i = 0; i < boxes.length && i < days.length; i++) {
      mask = Weekdays.with(mask, days[i], boxes[i].isChecked());
    }
    return mask;
  }

  private static String kindFrom(RadioGroup kindGroup) {
    int id = kindGroup.getCheckedRadioButtonId();
    if (id == R.id.radio_weekly) {
      return Series.KIND_WEEKLY;
    }
    if (id == R.id.radio_daily) {
      return Series.KIND_DAILY;
    }
    if (id == R.id.radio_monthly) {
      return Series.KIND_MONTHLY;
    }
    return Series.KIND_COUNTDOWN;
  }

  private static void saveFromForm(
    Context context,
    @Nullable Series existing,
    TextInputEditText title,
    TextInputEditText caption,
    TextInputEditText vars,
    RadioGroup kindGroup,
    long eventAt,
    long endAt,
    int postHour,
    int postMinute,
    boolean lastOfPrev,
    boolean day10,
    boolean day20,
    int weekdays) {
    String name = title.getText() == null ? "" : title.getText().toString().trim();
    if (name.isEmpty()) {
      Toast.makeText(context, R.string.need_title, Toast.LENGTH_SHORT).show();
      return;
    }
    String kind = kindFrom(kindGroup);
    if (Series.KIND_WEEKLY.equals(kind) && weekdays == Weekdays.NONE) {
      Toast.makeText(context, R.string.need_weekday, Toast.LENGTH_SHORT).show();
      return;
    }
    if (Series.KIND_MONTHLY.equals(kind) && !lastOfPrev && !day10 && !day20) {
      Toast.makeText(context, R.string.need_month_slot, Toast.LENGTH_SHORT).show();
      return;
    }
    Series series = existing == null ? new Series() : existing;
    series.title = name;
    series.kind = kind;
    series.caption = caption.getText() == null ? "" : caption.getText().toString();
    series.vars = vars.getText() == null ? "" : vars.getText().toString();
    series.eventAtMillis = eventAt;
    series.endAtMillis = Math.max(endAt, eventAt);
    series.postHour = postHour;
    series.postMinute = postMinute;
    series.lastOfPrevMonth = lastOfPrev;
    series.tenth = day10;
    series.twentieth = day20;
    series.weekdays = Series.KIND_WEEKLY.equals(kind) ? weekdays : Weekdays.NONE;
    series.enabled = true;
    Context app = context.getApplicationContext();
    AppExecutors.disk().execute(() -> {
      ScheduleCoordinator.saveSeries(app, series);
      AppExecutors.main(() ->
        Toast.makeText(context, R.string.series_saved, Toast.LENGTH_SHORT).show());
    });
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
