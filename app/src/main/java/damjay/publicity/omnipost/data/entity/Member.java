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

  /** Per-person title. Empty = Mr / Ma from gender. */
  @NonNull
  public String honorific = "";

  /** Per-person Happy New Month. Empty = general template. */
  @NonNull
  public String captionHnm = "";

  /** Per-person details ask. Empty = general template. */
  @NonNull
  public String captionDetails = "";

  /** True after this number is already in the President's contacts. Skip Save All. */
  public boolean contactSaved;

  /** True when the number exists but is not on WhatsApp. Own People tab; off This month. */
  public boolean notOnWhatsApp;

  public static boolean isAlumni(Member member) {
    return member != null && KIND_ALUMNI.equals(member.kind);
  }

  public static boolean isPastor(Member member) {
    return isAlumni(member) && DESK_PASTOR.equals(member.desk);
  }

  public static String kindOf(Member member) {
    return isAlumni(member) ? KIND_ALUMNI : KIND_MEMBER;
  }

  /**
   * Birthday, photo nag, pastor desk, or skip-caption changes need the alarm
   * rebuild. A name / phone / caption tweak does not.
   */
  public static boolean scheduleFieldsDiffer(Member before, Member after) {
    if (before == null || after == null) {
      return true;
    }
    return before.birthMonth != after.birthMonth
      || before.birthDay != after.birthDay
      || before.skipCaption != after.skipCaption
      || before.notOnWhatsApp != after.notOnWhatsApp
      || !eq(before.desk, after.desk)
      || !eq(before.kind, after.kind)
      || !eq(before.photoStatus, after.photoStatus);
  }

  private static boolean eq(String a, String b) {
    String left = a == null ? "" : a;
    String right = b == null ? "" : b;
    return left.equals(right);
  }
}
