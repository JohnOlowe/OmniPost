package damjay.publicity.omnipost.ui;

import android.content.Context;
import android.content.Intent;
import damjay.publicity.omnipost.MainActivity;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.util.ExtraKeys;
import damjay.publicity.omnipost.util.Prefs;

/** Fellowship vs Alumni homes. Launch remembers the last one. */
public final class Homes {
  private Homes() {}

  public static Intent fellowship(Context ctx) {
    Intent intent = new Intent(ctx, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    return intent;
  }

  public static Intent alumni(Context ctx) {
    Intent intent = new Intent(ctx, AlumniHomeActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    return intent;
  }

  public static Intent alumniForTask(Context ctx, Task task) {
    if (task != null && TaskTypes.ALUMNI_BIRTHDAY_PACK.equals(task.type)) {
      return alumniBirthdayDay(ctx, task);
    }
    Intent intent = alumni(ctx);
    if (task != null) {
      intent.putExtra(ExtraKeys.TASK_ID, task.id);
      intent.putExtra(ExtraKeys.MEMBER_ID, task.memberId);
      intent.putExtra(ExtraKeys.TASK_TYPE, task.type == null ? "" : task.type);
    }
    return intent;
  }

  public static Intent alumniBirthdayDay(Context ctx, Task pack) {
    Intent intent = new Intent(ctx, AlumniBirthdayDayActivity.class);
    if (pack != null) {
      intent.putExtra(ExtraKeys.TASK_ID, pack.id);
      intent.putExtra(ExtraKeys.DAY_KEY, TaskTypes.dayKey(pack));
    }
    return intent;
  }

  public static boolean isAlumniTask(Task task) {
    return task != null && TaskTypes.isAlumniDesk(task.type);
  }

  /** Birthday flyer caption — Write caption, like Fellowship. DMs stay copy-ready. */
  public static boolean writesAlumniCaption(Task task) {
    return task != null
        && TaskTypes.ALUMNI_BIRTHDAY.equals(task.type)
        && !task.skipCaption;
  }

  public static boolean isAlumniDmDesk(Task task) {
    return isAlumniTask(task) && !writesAlumniCaption(task);
  }

  public static void rememberFellowship(Context ctx) {
    Prefs.setLastHome(ctx, Prefs.HOME_FELLOWSHIP);
  }

  public static void rememberAlumni(Context ctx) {
    Prefs.setLastHome(ctx, Prefs.HOME_ALUMNI);
  }
}
