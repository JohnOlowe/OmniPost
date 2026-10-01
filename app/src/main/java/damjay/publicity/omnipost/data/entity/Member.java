package damjay.publicity.omnipost.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "members")
public class Member {
  public static final String KIND_MEMBER = "member";
  public static final String KIND_ALUMNI = "alumni";
  public static final String GENDER_MALE = "male";
  public static final String GENDER_FEMALE = "female";
  public static final String DESK_PASTOR = "pastor";

  @PrimaryKey(autoGenerate = true)
  public long id;

  @NonNull
  public String name = "";

  public int birthMonth;
  public int birthDay;

  @NonNull
  public String notes = "";

  @NonNull
  public String phone = "";

  @NonNull
  public String firstName = "";

  @NonNull
  public String lastName = "";

  @NonNull
  public String gender = "";

  @NonNull
  public String email = "";

  @NonNull
  public String positionHeld = "";

  @NonNull
  public String gradSet = "";

  /** Empty = still chasing. {@code got} / {@code none} settle the photo nag. */
  @NonNull
  public String photoStatus = "";

  /** True after the first personal DM of this tenure has gone out. */
  public boolean introduced;

  @NonNull
  public String kind = KIND_MEMBER;

  /** {@link #DESK_PASTOR} = previous pastors. They keep birthdays, not the monthly DM wave. */
  @NonNull
  public String desk = "";

  /** Alumni greetings are forwarded in WhatsApp; no caption to write. */
  public boolean skipCaption;

  public static boolean isAlumni(Member member) {
    return member != null && KIND_ALUMNI.equals(member.kind);
  }

  public static boolean isPastor(Member member) {
    return isAlumni(member) && DESK_PASTOR.equals(member.desk);
  }

  public static String kindOf(Member member) {
    return isAlumni(member) ? KIND_ALUMNI : KIND_MEMBER;
  }
}
