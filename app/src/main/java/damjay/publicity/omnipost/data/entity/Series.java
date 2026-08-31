package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "series")
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

  /** D-Day for a countdown. Unused for monthly notices. */
  public long eventAtMillis;

  public int postHour = 7;
  public int postMinute = 0;

  public boolean lastOfPrevMonth = true;
  public boolean day10 = true;
  public boolean day20 = true;
  public boolean enabled = true;

  @NonNull
  public String seedKey = "";
}
