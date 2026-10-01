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

  /**
   * Bitmask of days in the month for {@link #KIND_MONTHLY}. Bit 0 = last day of
   * this month; bits 1–31 = that date. Empty with empty {@link #monthOrdinals}
   * falls back to {@link #tenth} / {@link #twentieth}.
   */
  public int monthDays;

  /**
   * Bitmask of ordinal weekdays for {@link #KIND_MONTHLY}: (ordinal 1–5) ×
   * Sunday–Saturday. 5 = last that weekday in the month.
   */
  public long monthOrdinals;

  public boolean enabled = true;

  @NonNull
  public String seedKey = "";

  /** New cards of this series skip the write-caption nags. */
  public boolean skipCaption;
}
