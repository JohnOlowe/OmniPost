package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Personal DMs for the alumni desk. Mr/Ma, full name, and a wording tweak so WhatsApp does not eat identical walls of text. */
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
    return honorific(member, null);
  }

  public static String honorific(Member member, Map<String, String> bag) {
    if (member != null && member.honorific != null && !member.honorific.trim().isEmpty()) {
      return member.honorific.trim();
    }
    if (member == null || member.gender == null) {
      return "";
    }
    if (Member.GENDER_FEMALE.equals(member.gender)) {
      return AlumniTemplates.pick(bag, AlumniTemplates.FEMALE_TITLE);
    }
    if (Member.GENDER_MALE.equals(member.gender)) {
      return AlumniTemplates.pick(bag, AlumniTemplates.MALE_TITLE);
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

  public static String lastName(Member member) {
    if (member == null) {
      return "";
    }
    if (member.lastName != null && !member.lastName.trim().isEmpty()) {
      return member.lastName.trim();
    }
    String name = member.name == null ? "" : member.name.trim();
    int space = name.lastIndexOf(' ');
    if (space <= 0) {
      return "";
    }
    return name.substring(space + 1).trim();
  }

  public static String fullName(Member member) {
    if (member == null) {
      return "";
    }
    if (member.name != null && !member.name.trim().isEmpty()) {
      return member.name.trim();
    }
    String first = firstName(member);
    String last = lastName(member);
    if (first.isEmpty()) {
      return last;
    }
    if (last.isEmpty()) {
      return first;
    }
    return first + " " + last;
  }

  public static Map<String, String> personTokens(
    Member member, Map<String, String> bag, List<AlumniAddress> addresses) {
    Map<String, String> out = new LinkedHashMap<>();
    String title = honorific(member, bag);
    out.put("title", title);
    out.put("title_lc", title.toLowerCase(Locale.ROOT));
    out.put("first", firstName(member));
    out.put("last", lastName(member));
    String full = fullName(member);
    out.put("full", full);
    out.put("name", full);
    out.put("who", greetingName(member, bag));
    out.put("officer", AlumniTemplates.pick(bag, AlumniTemplates.OFFICER));
    out.put("role", AlumniTemplates.pick(bag, AlumniTemplates.ROLE));
    out.put("male_title", AlumniTemplates.pick(bag, AlumniTemplates.MALE_TITLE));
    out.put("female_title", AlumniTemplates.pick(bag, AlumniTemplates.FEMALE_TITLE));
    List<AlumniAddress> styles = addresses == null || addresses.isEmpty()
      ? AlumniAddress.defaults()
      : addresses;
    for (AlumniAddress style : styles) {
      if (style == null || style.name == null || style.name.isEmpty()) {
        continue;
      }
      if (AlumniAddress.reserved(style.name)) {
        continue;
      }
      out.put(style.name, style.pick(member));
    }
    return out;
  }

  public static String greetingName(Member member) {
    return greetingName(member, null);
  }

  public static String greetingName(Member member, Map<String, String> bag) {
    String title = honorific(member, bag);
    String full = fullName(member);
    if (full.isEmpty()) {
      full = firstName(member);
    }
    if (!title.isEmpty() && !full.isEmpty()) {
      return title + " " + full;
    }
    if (!title.isEmpty()) {
      return title;
    }
    return full;
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
    return hnm(member, month1to12, now, bag, null);
  }

  public static String hnm(
    Member member,
    int month1to12,
    Calendar now,
    Map<String, String> bag,
    List<AlumniAddress> addresses) {
    String template = member != null && member.captionHnm != null && !member.captionHnm.trim().isEmpty()
      ? member.captionHnm
      : AlumniTemplates.pick(bag, AlumniTemplates.hnmKey(member));
    return AlumniTemplates.fill(template, member, month1to12, now, bag, addresses);
  }

  public static String details(Member member, int month1to12) {
    return details(member, month1to12, Calendar.getInstance(), null);
  }

  public static String details(Member member, int month1to12, Calendar now, Map<String, String> bag) {
    return details(member, month1to12, now, bag, null);
  }

  public static String details(
    Member member,
    int month1to12,
    Calendar now,
    Map<String, String> bag,
    List<AlumniAddress> addresses) {
    String template = member != null && member.captionDetails != null && !member.captionDetails.trim().isEmpty()
      ? member.captionDetails
      : AlumniTemplates.pick(bag, AlumniTemplates.DETAILS);
    return AlumniTemplates.fill(template, member, month1to12, now, bag, addresses);
  }

  public static String photo(Member member) {
    return photo(member, Calendar.getInstance(), null);
  }

  public static String photo(Member member, Calendar now, Map<String, String> bag) {
    return photo(member, now, bag, null);
  }

  public static String photo(
    Member member, Calendar now, Map<String, String> bag, List<AlumniAddress> addresses) {
    int month = member == null ? 1 : Math.max(1, member.birthMonth);
    return AlumniTemplates.fill(
      AlumniTemplates.pick(bag, AlumniTemplates.PHOTO),
      member,
      month,
      now,
      bag,
      addresses);
  }

  public static String group(int month1to12, Calendar now) {
    return group(month1to12, now, null);
  }

  public static String group(int month1to12, Calendar now, Map<String, String> bag) {
    return group(month1to12, now, bag, null);
  }

  public static String group(
    int month1to12, Calendar now, Map<String, String> bag, List<AlumniAddress> addresses) {
    return AlumniTemplates.fill(
      AlumniTemplates.pick(bag, AlumniTemplates.GROUP),
      null,
      month1to12,
      now,
      bag,
      addresses);
  }

  static String monthName(int month1to12, Calendar now) {
    Calendar c = now == null ? Calendar.getInstance() : (Calendar) now.clone();
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.MONTH, Math.max(0, month1to12 - 1));
    return DateUtils.monthName(c);
  }
}
