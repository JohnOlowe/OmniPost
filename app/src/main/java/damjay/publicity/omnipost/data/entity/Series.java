package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "post_series")
public class Series {
  public static final String KIND_COUNTDOWN = "COUNTDOWN";
  public static final String KIND_MONTHLY = "MONTHLY";
  public static final String KIND_WEEKLY = "WEEKLY";
  public static final String KIND_DAILY = "DAILY";

  @PrimaryKey(autoGenerate = true)
  public long id;

  @NonNull
  public String title = "";

  @NonNull
  public String kind = KIND_COUNTDOWN;

  @NonNull
  public String caption = "";

  /** D-Day / first day for a countdown. Unused for monthly notices. */
  public long eventAtMillis;

  /** Last day of the event; `{range}` fills from eventAt through this. */
  public long endAtMillis;

  /**
   * Custom caption variables, one {@code name=value} per line.
   * `{name}` in the template is replaced with {@code value}.
   */
  @NonNull
  public String vars = "";

  public int postHour = 7;
  public int postMinute = 0;

  /**
   * Bitmask of {@link java.util.Calendar} weekdays for {@link #KIND_WEEKLY}
   * (Sunday = bit 0). Ignored for other kinds.
   */
  public int weekdays;

  public boolean lastOfPrevMonth = true;
  public boolean tenth = true;
  public boolean twentieth = true;
  public boolean enabled = true;

  @NonNull
  public String seedKey = "";
}
