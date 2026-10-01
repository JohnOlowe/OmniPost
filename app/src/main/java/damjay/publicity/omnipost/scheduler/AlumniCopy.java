package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/** Personal DMs for the alumni desk. Sir/Ma, their name, and a wording tweak so WhatsApp does not eat identical walls of text. */
public final class AlumniCopy {
  public static final String OFFICER = "Olowe John";
  public static final String ROLE = "Alumni Relations Officer, FSFUI";

  public static final String KIND_GROUP = "group";
  public static final String KIND_BIRTHDAY = "birthday";
  public static final String KIND_WAVE = "wave";
  public static final String KIND_PHOTO = "photo";
  public static final String KIND_DETAILS = "details";

  private AlumniCopy() {}

  public static String honorific(Member member) {
    if (member == null || member.gender == null) {
      return "";
    }
    if (Member.GENDER_FEMALE.equals(member.gender)) {
      return "Ma";
    }
    if (Member.GENDER_MALE.equals(member.gender)) {
      return "Sir";
    }
    return "";
  }

  public static String firstName(Member member) {
    if (member == null) {
      return "";
    }
    if (member.firstName != null && !member.firstName.trim().isEmpty()) {
      return member.firstName.trim();
    }
    String name = member.name == null ? "" : member.name.trim();
    if (name.isEmpty()) {
      return "";
    }
    int space = name.indexOf(' ');
    if (space <= 0) {
      return name;
    }
    return name.substring(0, space).trim();
  }

  public static String greetingName(Member member) {
    String title = honorific(member);
    String first = firstName(member);
    if (!title.isEmpty() && !first.isEmpty()) {
      return title + " " + first;
    }
    if (!title.isEmpty()) {
      return title;
    }
    return first;
  }

  public static int variant(Member member) {
    String name = member == null || member.name == null ? "" : member.name.trim().toLowerCase(Locale.US);
    return Math.abs(name.hashCode()) % 3;
  }

  public static String kindFor(Member member, int month1to12) {
    if (member == null) {
      return KIND_WAVE;
    }
    if (AlumniDesk.wantsPhoto(member) && member.birthMonth == month1to12) {
      return KIND_BIRTHDAY;
    }
    if (member.birthMonth == month1to12) {
      return KIND_BIRTHDAY;
    }
    if (Member.isPastor(member)) {
      return KIND_PHOTO;
    }
    return KIND_WAVE;
  }

  public static boolean wantsHnm(Member member, String mode) {
    if (KIND_PHOTO.equals(mode) || KIND_DETAILS.equals(mode)) {
      return false;
    }
    return !Member.isPastor(member);
  }

  public static boolean wantsDetails(Member member, String mode, int month1to12) {
    if (KIND_DETAILS.equals(mode) || KIND_BIRTHDAY.equals(mode)) {
      return true;
    }
    if (KIND_PHOTO.equals(mode) || KIND_WAVE.equals(mode)) {
      return false;
    }
    return member != null && member.birthMonth == month1to12;
  }

  public static String dm(Member member, int month1to12) {
    return dm(member, month1to12, Calendar.getInstance(), null);
  }

  public static String dm(Member member, int month1to12, Calendar now) {
    return dm(member, month1to12, now, null);
  }

  public static String dm(Member member, int month1to12, Calendar now, Map<String, String> bag) {
    String kind = kindFor(member, month1to12);
    if (KIND_PHOTO.equals(kind)) {
      return photo(member, now, bag);
    }
    if (KIND_BIRTHDAY.equals(kind) && Member.isPastor(member)) {
      return details(member, month1to12, now, bag);
    }
    return hnm(member, month1to12, now, bag);
  }

  public static String hnm(Member member, int month1to12) {
    return hnm(member, month1to12, Calendar.getInstance(), null);
  }

  public static String hnm(Member member, int month1to12, Calendar now, Map<String, String> bag) {
    return AlumniTemplates.fill(
      AlumniTemplates.pick(bag, AlumniTemplates.hnmKey(member)),
      member,
      month1to12,
      now,
      bag);
  }

  public static String details(Member member, int month1to12) {
    return details(member, month1to12, Calendar.getInstance(), null);
  }

  public static String details(Member member, int month1to12, Calendar now, Map<String, String> bag) {
    return AlumniTemplates.fill(
      AlumniTemplates.pick(bag, AlumniTemplates.DETAILS),
      member,
      month1to12,
      now,
      bag);
  }

  public static String photo(Member member) {
    return photo(member, Calendar.getInstance(), null);
  }

  public static String photo(Member member, Calendar now, Map<String, String> bag) {
    int month = member == null ? 1 : Math.max(1, member.birthMonth);
    return AlumniTemplates.fill(
      AlumniTemplates.pick(bag, AlumniTemplates.PHOTO),
      member,
      month,
      now,
      bag);
  }

  public static String group(int month1to12, Calendar now) {
    return group(month1to12, now, null);
  }

  public static String group(int month1to12, Calendar now, Map<String, String> bag) {
    return AlumniTemplates.fill(
      AlumniTemplates.pick(bag, AlumniTemplates.GROUP),
      null,
      month1to12,
      now,
      bag);
  }

  static String monthName(int month1to12, Calendar now) {
    Calendar c = now == null ? Calendar.getInstance() : (Calendar) now.clone();
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.MONTH, Math.max(0, month1to12 - 1));
    return DateUtils.monthName(c);
  }
}
