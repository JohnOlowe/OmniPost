package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
  tableName = "tasks",
  indices = {
    @Index(value = {"occurrenceKey"}, unique = true),
    @Index(value = {"status"}),
    @Index(value = {"memberId"})
  }
)
public class Task {
  @PrimaryKey(autoGenerate = true)
  public long id;

  @NonNull
  public String type = "";

  @NonNull
  public String title = "";

  @NonNull
  public String description = "";

  public long draftAtMillis;
  public long postAtMillis;

  @NonNull
  public String status = "SCHEDULED";

  @NonNull
  public String occurrenceKey = "";

  public long memberId;
  public long linkedDraftId;
  public long postedAtMillis;
  public long snoozeUntilMillis;
  public boolean timesLocked;
}
