package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "drafts")
public class Draft {
  @PrimaryKey(autoGenerate = true)
  public long id;

  public long taskId;

  @NonNull
  public String title = "";

  @NonNull
  public String variantA = "";

  @NonNull
  public String variantB = "";

  @NonNull
  public String finalizedText = "";

  public long updatedAt;
}
