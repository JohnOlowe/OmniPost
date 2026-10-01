package damjay.publicity.omnipost.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.util.Prefs;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnoozeChooser {
  public interface Callback {
    void onChosen(long untilMillis);
  }

  public interface ClockCallback {
    void onChosen(int hour, int minute);
  }

  public static final long THIRTY_MIN_MS = 30L * 60_000L;
  public static final long ONE_HOUR_MS = 60L * 60_000L;
  public static final long THREE_HOUR_MS = 3L * 60L * 60_000L;

  static final class Option {
    final String label;
    final long at;

    Option(String label, long at) {
      this.label = label;
      this.at = at;
    }
  }

  private SnoozeChooser() {}

  public static void show(Context context, Callback callback) {
    show(context, System.currentTimeMillis(), callback);
  }

  public static void show(Context context, long originMillis, Callback callback) {
    long now = System.currentTimeMillis();
    List<Option> options = options(context, originMillis, now);
    CharSequence[] items = new CharSequence[options.size() + 1];
    for (int i = 0; i < options.size(); i++) {
      items[i] = options.get(i).label;
    }
    items[options.size()] = context.getString(R.string.snooze_pick);
    new MaterialAlertDialogBuilder(context)
      .setTitle(R.string.nag_later)
      .setItems(items, (d, which) -> {
        if (which < 0) {
          return;
        }
        if (which >= options.size()) {
          pickDateTime(context, callback);
          return;
        }
        callback.onChosen(options.get(which).at);
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }

  static List<Option> options(Context context, long originMillis, long now) {
    int tonightH = Prefs.tonightHour(context);
    int tonightM = Prefs.tonightMinute(context);
    int morningH = Prefs.morningHour(context);
    int morningM = Prefs.morningMinute(context);
    int lateH = Prefs.lateHour(context);
    Map<Long, String> unique = new LinkedHashMap<>();
    put(unique, DateUtils.plusMinutes(now, 30),
      context.getString(R.string.nag_in_minutes, 30, DateUtils.prettyClock(DateUtils.plusMinutes(now, 30))));
    put(unique, DateUtils.plusMinutes(now, 60),
      context.getString(R.string.nag_in_hour, DateUtils.prettyClock(DateUtils.plusMinutes(now, 60))));
    long two = DateUtils.plusMinutes(now, 120);
    Calendar twoCal = Calendar.getInstance();
    twoCal.setTimeInMillis(two);
    Calendar nowCal = Calendar.getInstance();
    nowCal.setTimeInMillis(now);
    boolean farTwo = nowCal.get(Calendar.HOUR_OF_DAY) >= lateH
      && (twoCal.get(Calendar.HOUR_OF_DAY) >= 22
        || twoCal.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR)
        || twoCal.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR));
    if (farTwo) {
      long morning = DateUtils.nextClock(now, morningH, morningM);
      put(unique, morning,
        context.getString(R.string.snooze_tomorrow_at, DateUtils.prettyClock(morning)));
    } else {
      put(unique, two, context.getString(R.string.nag_in_hours, 2, DateUtils.prettyClock(two)));
    }
    long evening = DateUtils.tonightOrMorning(now, tonightH, tonightM, morningH, morningM, lateH);
    Calendar eve = Calendar.getInstance();
    eve.setTimeInMillis(evening);
    boolean tomorrow = eve.get(Calendar.DAY_OF_YEAR) != nowCal.get(Calendar.DAY_OF_YEAR)
      || eve.get(Calendar.YEAR) != nowCal.get(Calendar.YEAR);
    put(unique, evening, tomorrow
      ? context.getString(R.string.snooze_tomorrow_at, DateUtils.prettyClock(evening))
      : context.getString(R.string.snooze_tonight_at, DateUtils.prettyClock(evening)));
    long originThirty = originMillis + THIRTY_MIN_MS;
    if (originThirty > now + 60_000L) {
      put(unique, originThirty,
        context.getString(R.string.nag_by, DateUtils.prettyClock(originThirty)));
    }
    long originHour = originMillis + ONE_HOUR_MS;
    if (originHour > now + 60_000L) {
      put(unique, originHour,
        context.getString(R.string.nag_by, DateUtils.prettyClock(originHour)));
    }
    List<Option> out = new ArrayList<>();
    for (Map.Entry<Long, String> entry : unique.entrySet()) {
      out.add(new Option(entry.getValue(), entry.getKey()));
    }
    return out;
  }

  private static void put(Map<Long, String> unique, long at, String label) {
    long key = at / 60_000L * 60_000L;
    if (!unique.containsKey(key)) {
      unique.put(key, label);
    }
  }

  public static void pickClock(Context context, int hour, int minute, ClockCallback callback) {
    int h = Math.max(0, Math.min(23, hour));
    int m = Math.max(0, Math.min(59, minute));
    new TimePickerDialog(
      context,
      (view, hourOfDay, minuteOfHour) -> callback.onChosen(hourOfDay, minuteOfHour),
      h,
      m,
      false)
      .show();
  }

  public static void pickDateTime(Context context, Callback callback) {
    Calendar start = Calendar.getInstance();
    start.add(Calendar.HOUR_OF_DAY, 1);
    new DatePickerDialog(
      context,
      (view, year, month, dayOfMonth) -> {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.YEAR, year);
        day.set(Calendar.MONTH, month);
        day.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        new TimePickerDialog(
          context,
          (timeView, hourOfDay, minute) -> {
            day.set(Calendar.HOUR_OF_DAY, hourOfDay);
            day.set(Calendar.MINUTE, minute);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            confirm(context, day.getTimeInMillis(), callback);
          },
          start.get(Calendar.HOUR_OF_DAY),
          start.get(Calendar.MINUTE),
          false)
          .show();
      },
      start.get(Calendar.YEAR),
      start.get(Calendar.MONTH),
      start.get(Calendar.DAY_OF_MONTH))
      .show();
  }

  public static void confirm(Context context, long millis, Callback callback) {
    String message = DateUtils.formatStamp(millis) + "\n" + DateUtils.formatUntil(millis);
    new MaterialAlertDialogBuilder(context)
      .setTitle(R.string.confirm_time)
      .setMessage(message)
      .setPositiveButton(R.string.use_this_time, (d, w) -> callback.onChosen(millis))
      .setNegativeButton(android.R.string.cancel, null)
      .show();
  }
}
