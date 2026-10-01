package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alumni Relations Officer desk: birthday-month photos, no-tag without a
 * number, and a once-in-two-months DM wave. One card per wave so 260 names
 * cannot flood AlarmManager.
 */
public final class AlumniDesk {
  public static final String PHOTO_GOT = "got";
  public static final String PHOTO_NONE = "none";

  private AlumniDesk() {}

  public static boolean hasPhone(Member member) {
    return hasPhone(member == null ? "" : member.phone);
  }

  public static boolean hasPhone(String phone) {
    String d = whatsAppDigits(phone);
    return d.length() >= 10 && d.length() <= 15;
  }

  public static String digits(String phone) {
    if (phone == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < phone.length(); i++) {
      char c = phone.charAt(i);
      if (c >= '0' && c <= '9') {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Digits for storage and wa.me. Nigeria stays 234… . +1 / 1 + area code stays
   * NANP. Other +country numbers keep their country code.
   */
  public static String whatsAppDigits(String phone) {
    if (phone == null) {
      return "";
    }
    String trimmed = phone.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    boolean plus = trimmed.startsWith("+") || trimmed.startsWith("00");
    String d = digits(trimmed);
    if (d.startsWith("00") && d.length() > 2) {
      d = d.substring(2);
      plus = true;
    }
    if (d.startsWith("2340") && d.length() >= 14) {
      return "234" + d.substring(4, 14);
    }
    if (d.startsWith("234") && d.length() >= 13) {
      return d.substring(0, 13);
    }
    if (isNanp(d)) {
      return d.substring(0, 11);
    }
    if (plus && d.length() >= 10 && d.length() <= 15) {
      return d;
    }
    return nigeriaDigits(trimmed);
  }

  /** Empty if the number is not usable. Otherwise {@link #whatsAppDigits}. */
  public static String storePhone(String phone) {
    String d = whatsAppDigits(phone);
    return d.length() >= 10 && d.length() <= 15 ? d : "";
  }

  private static boolean isNanp(String d) {
    return d != null
      && d.length() >= 11
      && d.charAt(0) == '1'
      && d.charAt(1) >= '2'
      && d.charAt(1) <= '9';
  }

  /** 234 + 10-digit national number. Accepts 0803…, 803…, 234803…, +234 803… */
  public static String nigeriaDigits(String phone) {
    String d = digits(phone);
    if (d.startsWith("2340") && d.length() >= 14) {
      d = "234" + d.substring(4);
    }
    if (d.startsWith("234") && d.length() >= 13) {
      return d.substring(0, 13);
    }
    if (d.startsWith("0") && d.length() >= 11) {
      return "234" + d.substring(1, 11);
    }
    if (d.length() == 10) {
      return "234" + d;
    }
    return d;
  }

  public static String displayPhone(String phone) {
    String d = whatsAppDigits(phone);
    if (d.length() == 13 && d.startsWith("234")) {
      return "0" + d.substring(3);
    }
    if (isNanp(d)) {
      return "+1 " + d.substring(1);
    }
    if (d.length() >= 10) {
      return "+" + d;
    }
    return phone == null ? "" : phone.trim();
  }

  public static boolean photoSettled(Member member) {
    if (member == null || member.photoStatus == null) {
      return false;
    }
    return PHOTO_GOT.equals(member.photoStatus) || PHOTO_NONE.equals(member.photoStatus);
  }

  public static boolean wantsPhoto(Member member) {
    return Member.isAlumni(member) && !photoSettled(member);
  }

  /** Jan/Mar/… = wave 0, Feb/Apr/… = wave 1. */
  public static int monthWave(int month1to12) {
    int month = month1to12;
    if (month < 1) {
      month = 1;
    }
    return (month - 1) % 2;
  }

  public static int personWave(Member member) {
    String name = member == null || member.name == null ? "" : member.name.trim().toLowerCase(Locale.US);
    return name.hashCode() & 1;
  }

  public static boolean inWave(Member member, int month1to12) {
    return Member.isAlumni(member)
        && !Member.isPastor(member)
        && hasPhone(member)
        && personWave(member) == monthWave(month1to12);
  }

  public static int photoSpinnerIndex(Member member) {
    if (member == null || member.photoStatus == null) {
      return 0;
    }
    if (PHOTO_GOT.equals(member.photoStatus)) {
      return 1;
    }
    if (PHOTO_NONE.equals(member.photoStatus)) {
      return 2;
    }
    return 0;
  }

  public static String photoStatusFromIndex(int index) {
    if (index == 1) {
      return PHOTO_GOT;
    }
    if (index == 2) {
      return PHOTO_NONE;
    }
    return "";
  }

  public static String birthdayBrief(Member member) {
    if (member == null) {
      return "";
    }
    String photo;
    if (PHOTO_GOT.equals(member.photoStatus)) {
      photo = "Picture in. Forward the flyer to your DM, not the group.";
    } else if (PHOTO_NONE.equals(member.photoStatus)) {
      photo = "NO PICTURE. Use the no-photo design. Send their details to the designer and ask media for the file.";
    } else {
      photo = "If they still have not sent a picture: NO PICTURE. Send details to the designer. Ask media for the design.";
    }
    String tag = hasPhone(member)
      ? "Tag them — number is on their card (" + displayPhone(member.phone) + ")."
      : "No number — do not tag. Fill the number on this name; do not add a second card.";
    return photo + " " + tag + " ARO reaches out. President saves the contact.";
  }

  public static String photoBrief(Member member) {
    if (member == null) {
      return "";
    }
    String tag = hasPhone(member)
      ? "DM " + displayPhone(member.phone) + "."
      : "No number in the database — do not tag. If they reply with a name that is already here, fill the number on that card.";
    return "Two days to the birthday. Ask for a picture. If they do not answer: NO PICTURE. "
      + tag
      + " Forward what you have to the designer.";
  }

  public static String monthBrief(String monthName, List<Member> birthdayMonth) {
    StringBuilder out = new StringBuilder();
    out.append("First outreach · ").append(monthName).append(" birthday month.\n\n");
    out.append("1. Copy the group greeting from the desk (October includes Independence Day).\n");
    out.append("2. DM birthday-month people: intro as Alumni Relations Officer + picture ask. Batches of 10–15, not 100 identical texts.\n");
    out.append("3. Each caption already has Mr/Ma, their full name, and a wording tweak.\n");
    out.append("If WhatsApp blocks you: Happy Sunday, or finish by the 5th.\n");
    out.append("Do not tag anyone without a number. Never a second card for the same name.\n");
    out.append("ARO reaches out. President saves the contact.\n\n");
    appendLists(out, birthdayMonth, 40);
    return out.toString();
  }

  public static String waveBrief(String monthName, List<Member> wave) {
    StringBuilder out = new StringBuilder();
    out.append("Alumni DM wave · ")
      .append(monthName)
      .append(" (half the desk). Everyone once every two months.\n\n");
    out.append("Open the desk on this card. Batches of 10–15. Skip birthday-month people — they already got the picture ask.\n");
    out.append("First DM: you are the Alumni Relations Officer. Captions use Mr/Ma and their full name.\n");
    out.append("If WhatsApp blocks you: Happy Sunday, or finish by the 5th.\n");
    out.append("Beyond Limits: ARO asks for support; the President asks former presidents.\n");
    out.append("No number → do not tag.\n\n");
    appendLists(out, wave, 12);
    return out.toString();
  }

  public static String rosterLine(Member member, int month1to12) {
    if (member == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    if (hasPhone(member)) {
      out.append(displayPhone(member.phone));
    } else if (Member.isAlumni(member)) {
      out.append("no number");
    }
    if (PHOTO_GOT.equals(member.photoStatus)) {
      appendDot(out, "picture in");
    } else if (PHOTO_NONE.equals(member.photoStatus)) {
      appendDot(out, "NO PICTURE");
    } else if (Member.isAlumni(member)) {
      appendDot(out, "need picture");
    }
    if (Member.isAlumni(member) && member.birthMonth == month1to12) {
      appendDot(out, "birthday month");
    }
    if (Member.isPastor(member)) {
      appendDot(out, "pastor");
    } else if (inWave(member, month1to12)) {
      appendDot(out, "wave");
    }
    return out.toString();
  }

  static void appendLists(StringBuilder out, List<Member> people, int unnamedCap) {
    List<Member> tag = new ArrayList<>();
    List<Member> skip = new ArrayList<>();
    if (people != null) {
      for (Member member : people) {
        if (member == null) {
          continue;
        }
        if (hasPhone(member)) {
          tag.add(member);
        } else {
          skip.add(member);
        }
      }
    }
    out.append("Tag (").append(tag.size()).append("):\n");
    if (tag.isEmpty()) {
      out.append("— none with a number yet.\n");
    } else {
      for (Member member : tag) {
        out.append("• ").append(member.name).append(" · ").append(displayPhone(member.phone)).append('\n');
      }
    }
    out.append("\nNo number — do not tag (").append(skip.size()).append("):\n");
    if (skip.isEmpty()) {
      out.append("— none.\n");
    } else if (unnamedCap >= 0 && skip.size() > unnamedCap) {
      out.append("• ")
        .append(skip.size())
        .append(" people. Open Birthdays → Alumni. Do not tag them.\n");
    } else {
      for (Member member : skip) {
        out.append("• ").append(member.name).append('\n');
      }
    }
  }

  private static void appendDot(StringBuilder out, String bit) {
    if (out.length() > 0) {
      out.append(" · ");
    }
    out.append(bit);
  }
}
