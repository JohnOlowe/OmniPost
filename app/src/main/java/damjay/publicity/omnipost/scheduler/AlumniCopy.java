package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.Calendar;
import java.util.Locale;

/** Personal DMs for the alumni desk. Sir/Ma, their name, and a wording tweak so WhatsApp does not eat identical walls of text. */
public final class AlumniCopy {
  public static final String OFFICER = "Olowe John";
  public static final String ROLE = "Alumni Relations Officer, FSFUI";

  public static final String KIND_GROUP = "group";
  public static final String KIND_BIRTHDAY = "birthday";
  public static final String KIND_WAVE = "wave";
  public static final String KIND_PHOTO = "photo";

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
    return KIND_WAVE;
  }

  public static String dm(Member member, int month1to12) {
    return dm(member, month1to12, Calendar.getInstance());
  }

  public static String dm(Member member, int month1to12, Calendar now) {
    String kind = kindFor(member, month1to12);
    if (KIND_BIRTHDAY.equals(kind)) {
      return birthdayMonth(member, month1to12, now);
    }
    return wave(member, month1to12, now);
  }

  public static String photo(Member member) {
    String who = greetingName(member);
    if (who.isEmpty()) {
      who = "friend";
    }
    int v = variant(member);
    if (v == 1) {
      return "Good day "
        + who
        + ", just a gentle reminder about the picture for your birthday celebration. 🙏🏽\n\n"
        + "Kindly send it in when you can, so we can have everything ready in good time. God bless you! ❤️";
    }
    if (v == 2) {
      return "Hello "
        + who
        + " 🙏🏽 A quick reminder: we still need a picture for your birthday flyer.\n\n"
        + "Whenever you can send it in, we will take it from there. God bless you! ❤️";
    }
    return "Good morning "
      + who
      + ", just a gentle reminder about the picture for your birthday celebration. 🙏🏽\n\n"
      + "Kindly send it in when you can, so we can have everything ready in good time. God bless you! ❤️";
  }

  public static String group(int month1to12, Calendar now) {
    String month = monthName(month1to12, now);
    boolean october = month1to12 == 10;
    String independence = october ? ", and happy Independence Day to us all. 🇳🇬" : ".";
    return "Happy New Month, dear FSFUI Alumni! 🎉🙏🏽\n\n"
      + "Welcome to "
      + month
      + independence
      + "\n\n"
      + "This is my first official greeting to you as your Alumni Relations Officer, and it comes with gratitude. Thank you for the love and support you constantly show the house.\n\n"
      + "May the Lord keep you, your families, and all that concerns you this month. Expect to hear from me as we celebrate God's goodness in your lives.\n\n"
      + "God bless you richly. ❤️\n\n"
      + "_"
      + OFFICER
      + "\n"
      + ROLE
      + "_";
  }

  static String birthdayMonth(Member member, int month1to12, Calendar now) {
    String who = greetingName(member);
    String month = monthName(month1to12, now);
    boolean first = member == null || !member.introduced;
    StringBuilder out = new StringBuilder();
    out.append("Happy New Month");
    if (!who.isEmpty()) {
      out.append(", ").append(who);
    }
    out.append("! 🎉\n\n");
    if (first) {
      out.append("My name is ")
        .append(OFFICER)
        .append(", the Alumni Relations Officer of FSFUI. It is a real pleasure to finally greet you personally.\n\n");
      out.append("And what a month to start with: it's your birthday month! 🎉✨\n\n");
    } else {
      out.append("Welcome to your birthday month! 🎉✨\n\n");
    }
    out.append(body(member, month, month1to12)).append("\n\n");
    out.append("We would love to celebrate you specially in the alumni family as we always do. Kindly send me a lovely picture of yourself at your convenience, and do confirm your birth date and how you would like your name written.\n\n");
    out.append("The Lord bless you and give you a beautiful ").append(month).append(". ❤️");
    return out.toString();
  }

  static String wave(Member member, int month1to12, Calendar now) {
    String who = greetingName(member);
    String month = monthName(month1to12, now);
    boolean first = member == null || !member.introduced;
    StringBuilder out = new StringBuilder();
    out.append("Happy New Month");
    if (!who.isEmpty()) {
      out.append(", ").append(who);
    }
    out.append("! 🎉\n\n");
    if (first) {
      out.append("I am ")
        .append(OFFICER)
        .append(", the new Alumni Relations Officer of FSFUI. Part of my joy this tenure is staying in touch with our alumni personally, so you will be hearing from me from time to time.\n\n");
    }
    out.append(body(member, month, month1to12)).append("\n\n");
    out.append("Welcome to ").append(month).append(". The Lord bless and keep you and your family this month. ❤️");
    return out.toString();
  }

  static String body(Member member, String month, int month1to12) {
    int v = variant(member);
    String nth = DateUtils.ordinal(month1to12);
    if (v == 1) {
      return "*Happy New Month, and welcome to "
        + month
        + "!* ✨🍂\n\n"
        + "As "
        + month
        + " opens, we pray this becomes a season of abundant increase for you — much better days and overwhelming favour.\n\n"
        + "May your walk with God grow even deeper this month, and may His guiding presence stay with you through the rest of the year.\n\n"
        + "Happy New Month from all of us at FSFUI! ❤️";
    }
    if (v == 2) {
      return "*Welcome into "
        + month
        + "!* 🍂\n\n"
        + "We pray this "
        + nth
        + " month is a season of increase for you: better days, overflowing favour, and a stronger walk with God.\n\n"
        + "May His presence keep you and yours through the rest of the year.\n\n"
        + "Happy New Month from everyone at FSFUI! ❤️";
    }
    return "*Welcome to the month of "
      + month
      + "!* 🍂✨\n\n"
      + "As you step into the "
      + nth
      + " month of the year, we pray this becomes a season of abundant increase for you. May this be a month of much better days and overwhelming favour!\n\n"
      + "We pray that your commitment to God grows even stronger this month, and that guiding presence stays with you through the rest of the year.\n\n"
      + "Happy New Month from all of us at FSFUI! ❤️";
  }

  static String monthName(int month1to12, Calendar now) {
    Calendar c = now == null ? Calendar.getInstance() : (Calendar) now.clone();
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.MONTH, Math.max(0, month1to12 - 1));
    return DateUtils.monthName(c);
  }
}
