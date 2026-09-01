package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "post_series")
public class Series {
  public static final String KIND_COUNTDOWN = "COUNTDOWN";
  public static final String KIND_MONTHLY = "MONTHLY";

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

  /** Last day of the event; the range token fills from eventAt through this. */
  public long endAtMillis;

  /**
   * Custom caption variables, one {@code name=value} per line.
   * A token of that name in curly braces is replaced with the value.
   */
  @NonNull
  public String vars = "";

  public int postHour = 7;
  public int postMinute = 0;

  public boolean lastOfPrevMonth = true;
  public boolean tenth = true;
  public boolean twentieth = true;
  public boolean enabled = true;

  @NonNull
  public String seedKey = "";
}
