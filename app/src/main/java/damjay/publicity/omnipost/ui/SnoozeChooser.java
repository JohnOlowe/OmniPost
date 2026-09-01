package damjay.publicity.omnipost.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import damjay.publicity.omnipost.R;
import damjay.publicity.omnipost.scheduler.DateUtils;
import java.util.Calendar;

public final class SnoozeChooser {
  public interface Callback {
    void onChosen(long untilMillis);
  }

  public interface ClockCallback {
    void onChosen(int hour, int minute);
  }

  private SnoozeChooser() {}

  public static void show(Context context, Callback callback) {
    CharSequence[] items = new CharSequence[] {
      context.getString(R.string.snooze_1h),
      context.getString(R.string.snooze_3h),
      context.getString(R.string.snooze_tonight),
      context.getString(R.string.snooze_tomorrow),
      context.getString(R.string.snooze_pick)
    };
    new MaterialAlertDialogBuilder(context)
      .setTitle(R.string.flyer_not_ready)
      .setItems(items, (d, which) -> {
        switch (which) {
          case 0:
            callback.onChosen(DateUtils.hoursFromNow(1));
            break;
          case 1:
            callback.onChosen(DateUtils.hoursFromNow(3));
            break;
          case 2:
            callback.onChosen(DateUtils.nextClock(20, 0));
            break;
          case 3:
            callback.onChosen(DateUtils.nextClock(7, 0));
            break;
          default:
            pickDateTime(context, callback);
            break;
        }
      })
      .setNegativeButton(android.R.string.cancel, null)
      .show();
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
