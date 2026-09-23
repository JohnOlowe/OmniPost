package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "members")
public class Member {
  public static final String KIND_MEMBER = "member";
  public static final String KIND_ALUMNI = "alumni";

  @PrimaryKey(autoGenerate = true)
  public long id;

  @NonNull
  public String name = "";

  public int birthMonth;
  public int birthDay;

  @NonNull
  public String notes = "";

  @NonNull
  public String kind = KIND_MEMBER;

  /** Alumni greetings are forwarded in WhatsApp; no caption to write. */
  public boolean skipCaption;

  public static boolean isAlumni(Member member) {
    return member != null && KIND_ALUMNI.equals(member.kind);
  }

  public static String kindOf(Member member) {
    return isAlumni(member) ? KIND_ALUMNI : KIND_MEMBER;
  }
}
