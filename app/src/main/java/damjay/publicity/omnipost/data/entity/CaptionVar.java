package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
  tableName = "caption_vars",
  indices = {@Index(value = {"name"}, unique = true)}
)
public class CaptionVar {
  @PrimaryKey(autoGenerate = true)
  public long id;

  /** Token written as {@code {name}} in any caption. */
  @NonNull
  public String name = "";

  /** Human label so the list is not a pile of raw keys. */
  @NonNull
  public String label = "";

  /** Replaces {@code {name}}. May be several lines. */
  @NonNull
  public String value = "";
}
