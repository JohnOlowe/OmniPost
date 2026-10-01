package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
  tableName = "alumni_sends",
  indices = {@Index(value = {"memberId", "yearMonth", "kind"}, unique = true)}
)
public class AlumniSend {
  public static final String HNM = "hnm";
  public static final String DETAILS = "details";

  @PrimaryKey(autoGenerate = true)
  public long id;

  public long memberId;
  public int yearMonth;

  @NonNull
  public String kind = HNM;

  public long sentAt;
}
