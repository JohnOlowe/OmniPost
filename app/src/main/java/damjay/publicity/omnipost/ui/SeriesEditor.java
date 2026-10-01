package damjay.publicity.omnipost.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.MonthSlots;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.ScheduleTimes;
import damjay.publicity.omnipost.scheduler.SeriesDefaults;
import damjay.publicity.omnipost.scheduler.Weekdays;
import damjay.publicity.omnipost.util.AppExecutors;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
    series.lastOfPrevMonth = false;
    series.tenth = false;
    series.twentieth = false;
    series.monthDays = MonthSlots.withDay(0, 1, true);
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
    CheckBox skipCaption = view.findViewById(R.id.skip_caption);
    CheckBox optEve = view.findViewById(R.id.opt_eve);
    CheckBox optLast = view.findViewById(R.id.opt_last_of_month);
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
    skipCaption.setChecked(source.skipCaption);
    optEve.setChecked(source.lastOfPrevMonth);
    paintDays(dayBoxes, source.weekdays);
    CheckBox[] monthDayBoxes = fillMonthDayGrid(context, view.findViewById(R.id.month_days));
    int monthDays = MonthSlots.daysOf(source);
    paintMonthDays(monthDayBoxes, optLast, monthDays);
    AtomicInteger nthOrdinal = new AtomicInteger(1);
    AtomicInteger nthWeekday = new AtomicInteger(Calendar.MONDAY);
    AtomicLong nthMask = new AtomicLong(source.monthOrdinals);
    LinearLayout nthList = view.findViewById(R.id.nth_list);
    MaterialButton addNth = view.findViewById(R.id.btn_add_nth);
    fillNthPickers(context, view.findViewById(R.id.nth_ordinals), view.findViewById(R.id.nth_weekdays),
      nthOrdinal, nthWeekday, addNth);
    paintNthList(context, nthList, nthMask);
    addNth.setOnClickListener(v -> {
      nthMask.set(MonthSlots.withOrdinal(nthMask.get(), nthOrdinal.get(), nthWeekday.get(), true));
      paintNthList(context, nthList, nthMask);
    });
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
        readMonthDays(monthDayBoxes, optLast),
        nthMask.get(),
        skipCaption.isChecked(),
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

  private static CheckBox[] fillMonthDayGrid(Context context, GridLayout grid) {
    CheckBox[] boxes = new CheckBox[32];
    if (grid == null) {
      return boxes;
    }
    grid.removeAllViews();
    float density = context.getResources().getDisplayMetrics().density;
    int min = (int) (36 * density);
    for (int d = 1; d <= 31; d++) {
      CheckBox box = new CheckBox(context);
      box.setText(String.valueOf(d));
      box.setTextColor(context.getColor(R.color.cream));
      box.setTextSize(12f);
      box.setMinHeight(min);
      box.setPadding(0, 0, 0, 0);
      GridLayout.LayoutParams params = new GridLayout.LayoutParams(
        GridLayout.spec((d - 1) / 7, 1f),
        GridLayout.spec((d - 1) % 7, 1f));
      params.width = 0;
      params.height = GridLayout.LayoutParams.WRAP_CONTENT;
      box.setLayoutParams(params);
      grid.addView(box);
      boxes[d] = box;
    }
    return boxes;
  }

  private static void paintMonthDays(CheckBox[] boxes, CheckBox last, int mask) {
    for (int d = 1; d <= 31; d++) {
      if (boxes[d] != null) {
        boxes[d].setChecked(MonthSlots.hasDay(mask, d));
      }
    }
    if (last != null) {
      last.setChecked(MonthSlots.hasLast(mask));
    }
  }

  private static int readMonthDays(CheckBox[] boxes, CheckBox last) {
    int mask = 0;
    for (int d = 1; d <= 31; d++) {
      if (boxes[d] != null && boxes[d].isChecked()) {
        mask = MonthSlots.withDay(mask, d, true);
      }
    }
    if (last != null && last.isChecked()) {
      mask = MonthSlots.withDay(mask, MonthSlots.LAST_OF_MONTH, true);
    }
    return mask;
  }

  private static void fillNthPickers(
    Context context,
    LinearLayout ordinals,
    LinearLayout weekdays,
    AtomicInteger nthOrdinal,
    AtomicInteger nthWeekday,
    MaterialButton addNth) {
    if (ordinals != null) {
      ordinals.removeAllViews();
      int[] values = {1, 2, 3, 4, MonthSlots.LAST_ORDINAL};
      for (int value : values) {
        MaterialButton chip = pickerChip(context, MonthSlots.ordinalLabel(value));
        chip.setOnClickListener(v -> {
          nthOrdinal.set(value);
          paintPickerRow(ordinals, value);
          paintAddNth(context, addNth, nthOrdinal.get(), nthWeekday.get());
        });
        chip.setTag(value);
        ordinals.addView(chip);
      }
      paintPickerRow(ordinals, nthOrdinal.get());
    }
    if (weekdays != null) {
      weekdays.removeAllViews();
      for (int day : Weekdays.days()) {
        MaterialButton chip = pickerChip(context, Weekdays.shortName(day));
        int weekday = day;
        chip.setOnClickListener(v -> {
          nthWeekday.set(weekday);
          paintPickerRow(weekdays, weekday);
          paintAddNth(context, addNth, nthOrdinal.get(), nthWeekday.get());
        });
        chip.setTag(weekday);
        weekdays.addView(chip);
      }
      paintPickerRow(weekdays, nthWeekday.get());
    }
    paintAddNth(context, addNth, nthOrdinal.get(), nthWeekday.get());
  }

  private static MaterialButton pickerChip(Context context, String label) {
    MaterialButton chip = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    chip.setText(label);
    chip.setTextColor(context.getColor(R.color.cream));
    chip.setTextSize(12f);
    chip.setAllCaps(false);
    chip.setPadding(20, 8, 20, 8);
    chip.setInsetTop(0);
    chip.setInsetBottom(0);
    chip.setMinHeight(0);
    chip.setMinimumHeight(0);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMarginEnd(8);
    chip.setLayoutParams(params);
    return chip;
  }

  private static void paintPickerRow(LinearLayout row, int selected) {
    if (row == null) {
      return;
    }
    for (int i = 0; i < row.getChildCount(); i++) {
      View child = row.getChildAt(i);
      if (!(child instanceof MaterialButton)) {
        continue;
      }
      MaterialButton chip = (MaterialButton) child;
      boolean on = child.getTag() instanceof Integer && ((Integer) child.getTag()) == selected;
      chip.setTextColor(chip.getContext().getColor(on ? R.color.gold : R.color.cream));
    }
  }

  private static void paintAddNth(Context context, MaterialButton addNth, int ordinal, int weekday) {
    if (addNth == null) {
      return;
    }
    addNth.setText(context.getString(
      R.string.month_nth_add_one,
      MonthSlots.ordinalLabel(ordinal) + " " + Weekdays.longName(weekday)));
  }

  private static void paintNthList(Context context, LinearLayout list, AtomicLong mask) {
    if (list == null) {
      return;
    }
    list.removeAllViews();
    long value = mask.get();
    for (int ordinal = 1; ordinal <= MonthSlots.LAST_ORDINAL; ordinal++) {
      for (int weekday = Calendar.SUNDAY; weekday <= Calendar.SATURDAY; weekday++) {
        if (!MonthSlots.hasOrdinal(value, ordinal, weekday)) {
          continue;
        }
        final int ord = ordinal;
        final int day = weekday;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = new TextView(context);
        label.setText(MonthSlots.ordinalLabel(ord) + " " + Weekdays.longName(day));
        label.setTextColor(context.getColor(R.color.cream));
        label.setTextSize(14f);
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(grow);
        MaterialButton remove = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        remove.setText(R.string.remove);
        remove.setTextColor(context.getColor(R.color.gold));
        remove.setTextSize(12f);
        remove.setAllCaps(false);
        remove.setOnClickListener(v -> {
          mask.set(MonthSlots.withOrdinal(mask.get(), ord, day, false));
          paintNthList(context, list, mask);
        });
        row.addView(label);
        row.addView(remove);
        list.addView(row);
      }
    }
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
    int monthDays,
    long monthOrdinals,
    boolean skipCaption,
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
    series.monthDays = Series.KIND_MONTHLY.equals(kind) ? monthDays : 0;
    series.monthOrdinals = Series.KIND_MONTHLY.equals(kind) ? monthOrdinals : 0L;
    series.tenth = MonthSlots.hasDay(series.monthDays, 10);
    series.twentieth = MonthSlots.hasDay(series.monthDays, 20);
    series.weekdays = Series.KIND_WEEKLY.equals(kind) ? weekdays : Weekdays.NONE;
    series.skipCaption = skipCaption;
    series.enabled = true;
    if (Series.KIND_MONTHLY.equals(kind) && !MonthSlots.any(series)) {
      Toast.makeText(context, R.string.need_month_slot, Toast.LENGTH_SHORT).show();
      return;
    }
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
