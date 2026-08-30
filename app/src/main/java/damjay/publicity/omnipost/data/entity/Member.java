package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "members")
public class Member {
  @PrimaryKey(autoGenerate = true)
  public long id;

  @NonNull
  public String name = "";

  public int birthMonth;
  public int birthDay;

  @NonNull
  public String notes = "";
}
