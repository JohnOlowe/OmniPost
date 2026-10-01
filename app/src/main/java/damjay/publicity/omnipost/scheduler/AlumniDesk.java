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
    return digits(member == null ? "" : member.phone).length() >= 7;
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
    return Member.isAlumni(member) && personWave(member) == monthWave(month1to12);
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
      ? "Tag them — number is on their card."
      : "No number — do not tag. Fill the number on this name; do not add a second card.";
    return photo + " " + tag + " ARO reaches out. President saves the contact.";
  }

  public static String photoBrief(Member member) {
    if (member == null) {
      return "";
    }
    String tag = hasPhone(member)
      ? "DM " + member.phone.trim() + "."
      : "No number in the database — do not tag. If they reply with a name that is already here, fill the number on that card.";
    return "Two days to the birthday. Ask for a picture. If they do not answer: NO PICTURE. "
      + tag
      + " Forward what you have to the designer.";
  }

  public static String monthBrief(String monthName, List<Member> birthdayMonth) {
    StringBuilder out = new StringBuilder();
    out.append("Welcome to ")
      .append(monthName)
      .append(" — birthday month.\n\n");
    out.append("Happy New Month. Please send a picture for your birthday flyer.\n");
    out.append("Say that in DMs and in the group.\n\n");
    out.append("First time in their DM: introduce yourself as the Alumni Relations Officer.\n");
    out.append("If WhatsApp is blocking you: send Happy Sunday, or Happy New Month by the 5th.\n");
    out.append("Do not tag anyone without a number. Never a second card for the same name.\n");
    out.append("ARO reaches out. President saves the contact.\n\n");
    appendLists(out, birthdayMonth, 40);
    return out.toString();
  }

  public static String waveBrief(String monthName, List<Member> wave) {
    StringBuilder out = new StringBuilder();
    out.append("Alumni DM wave · ")
      .append(monthName)
      .append(" (half the desk). Everyone gets a message once every two months.\n\n");
    out.append("The Alumni tab marks who is in this wave.\n");
    out.append("Personalized. First DM: you are the Alumni Relations Officer.\n");
    out.append("If WhatsApp blocks you: Happy Sunday, or Happy New Month by the 5th.\n");
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
      out.append(member.phone.trim());
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
    if (inWave(member, month1to12)) {
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
        out.append("• ").append(member.name).append(" · ").append(member.phone.trim()).append('\n');
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
