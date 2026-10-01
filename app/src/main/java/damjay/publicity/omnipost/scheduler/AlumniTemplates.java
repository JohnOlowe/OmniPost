package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editable alumni captions. Tokens: {who} {first} {month} {nth} {officer} {role}
 * {intro} {independence} {birthday}
 */
public final class AlumniTemplates {
  public static final String OFFICER = "officer";
  public static final String ROLE = "role";
  public static final String MALE_TITLE = "male_title";
  public static final String FEMALE_TITLE = "female_title";
  public static final String INTRO = "intro";
  public static final String HNM = "hnm";
  public static final String HNM_B = "hnm_b";
  public static final String HNM_C = "hnm_c";
  public static final String DETAILS = "details";
  public static final String PHOTO = "photo";
  public static final String GROUP = "group";

  public static final String[] KEYS = {
    OFFICER, ROLE, MALE_TITLE, FEMALE_TITLE, INTRO, HNM, HNM_B, HNM_C, DETAILS, PHOTO, GROUP
  };

  private AlumniTemplates() {}

  public static String label(String key) {
    if (OFFICER.equals(key)) {
      return "Officer name";
    }
    if (ROLE.equals(key)) {
      return "Role line";
    }
    if (MALE_TITLE.equals(key)) {
      return "Male title (Mr)";
    }
    if (FEMALE_TITLE.equals(key)) {
      return "Female title (Ma)";
    }
    if (INTRO.equals(key)) {
      return "First hello";
    }
    if (HNM.equals(key)) {
      return "Happy New Month A";
    }
    if (HNM_B.equals(key)) {
      return "Happy New Month B";
    }
    if (HNM_C.equals(key)) {
      return "Happy New Month C";
    }
    if (DETAILS.equals(key)) {
      return "Ask for details";
    }
    if (PHOTO.equals(key)) {
      return "Picture reminder";
    }
    if (GROUP.equals(key)) {
      return "Group Happy New Month";
    }
    return key == null ? "" : key;
  }

  public static String hint(String key) {
    if (OFFICER.equals(key) || ROLE.equals(key)) {
      return "Used wherever a caption has {officer} or {role}.";
    }
    if (INTRO.equals(key)) {
      return "Only on the first personal DM. Leave {intro} in a caption to place it.";
    }
    if (DETAILS.equals(key)) {
      return "Second message: picture, birth date, how they want the name written.";
    }
    if (PHOTO.equals(key)) {
      return "Follow-up if they have not sent a picture yet.";
    }
    if (GROUP.equals(key)) {
      return "{independence} becomes the Independence Day line in October, or a full stop.";
    }
    return "{who} {first} {month} {nth} {officer} {role} {intro} {birthday} {independence}";
  }

  public static String fallback(String key) {
    if (OFFICER.equals(key)) {
      return AlumniCopy.OFFICER;
    }
    if (ROLE.equals(key)) {
      return AlumniCopy.ROLE;
    }
    if (MALE_TITLE.equals(key)) {
      return "Mr";
    }
    if (FEMALE_TITLE.equals(key)) {
      return "Ma";
    }
    if (INTRO.equals(key)) {
      return "I am {officer}, the new Alumni Relations Officer of FSFUI. Part of my joy this tenure is staying in touch with our alumni personally, so you will be hearing from me from time to time.\n";
    }
    if (HNM.equals(key)) {
      return "Happy New Month, {who}! 🎉\n\n"
        + "{intro}*Welcome to the month of {month}!* 🍂✨\n\n"
        + "As you step into the {nth} month of the year, we pray this becomes a season of abundant increase for you. May this be a month of much better days and overwhelming favour!\n\n"
        + "We pray that your commitment to God grows even stronger this month, and that guiding presence stays with you through the rest of the year.\n\n"
        + "Happy New Month from all of us at FSFUI! ❤️\n\n"
        + "Welcome to {month}. The Lord bless and keep you and your family this month. ❤️";
    }
    if (HNM_B.equals(key)) {
      return "Happy New Month, {who}! 🎉\n\n"
        + "{intro}*Happy New Month, and welcome to {month}!* ✨🍂\n\n"
        + "As {month} opens, we pray this becomes a season of abundant increase for you — much better days and overwhelming favour.\n\n"
        + "May your walk with God grow even deeper this month, and may His guiding presence stay with you through the rest of the year.\n\n"
        + "Happy New Month from all of us at FSFUI! ❤️\n\n"
        + "Welcome to {month}. The Lord bless and keep you and your family this month. ❤️";
    }
    if (HNM_C.equals(key)) {
      return "Happy New Month, {who}! 🎉\n\n"
        + "{intro}*Welcome into {month}!* 🍂\n\n"
        + "We pray this {nth} month is a season of increase for you: better days, overflowing favour, and a stronger walk with God.\n\n"
        + "May His presence keep you and yours through the rest of the year.\n\n"
        + "Happy New Month from everyone at FSFUI! ❤️\n\n"
        + "Welcome to {month}. The Lord bless and keep you and your family this month. ❤️";
    }
    if (DETAILS.equals(key)) {
      return "Good day {who},\n\n"
        + "We would love to celebrate you specially in the alumni family as we always do. Kindly send me a lovely picture of yourself at your convenience, and do confirm your birth date and how you would like your name written.\n\n"
        + "The Lord bless you and give you a beautiful {month}. ❤️";
    }
    if (PHOTO.equals(key)) {
      return "Good day {who}, just a gentle reminder about the picture for your birthday celebration. 🙏🏽\n\n"
        + "Kindly send it in when you can, so we can have everything ready in good time. God bless you! ❤️";
    }
    if (GROUP.equals(key)) {
      return "Happy New Month, dear FSFUI Alumni! 🎉🙏🏽\n\n"
        + "Welcome to {month}{independence}\n\n"
        + "This is my first official greeting to you as your Alumni Relations Officer, and it comes with gratitude. Thank you for the love and support you constantly show the house.\n\n"
        + "May the Lord keep you, your families, and all that concerns you this month. Expect to hear from me as we celebrate God's goodness in your lives.\n\n"
        + "God bless you richly. ❤️\n\n"
        + "_{officer}\n{role}_";
    }
    return "";
  }

  public static String pick(Map<String, String> bag, String key) {
    if (bag != null) {
      String custom = bag.get(key);
      if (custom != null && !custom.trim().isEmpty()) {
        return custom;
      }
    }
    return fallback(key);
  }

  public static Map<String, String> defaults() {
    Map<String, String> out = new LinkedHashMap<>();
    for (String key : KEYS) {
      out.put(key, fallback(key));
    }
    return out;
  }

  public static String hnmKey(Member member) {
    int v = AlumniCopy.variant(member);
    if (v == 1) {
      return HNM_B;
    }
    if (v == 2) {
      return HNM_C;
    }
    return HNM;
  }

  public static String fill(
    String template,
    Member member,
    int month1to12,
    Calendar now,
    Map<String, String> bag) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    String officer = pick(bag, OFFICER);
    String role = pick(bag, ROLE);
    String who = AlumniCopy.greetingName(member, bag);
    if (who.isEmpty()) {
      who = "friend";
    }
    String first = AlumniCopy.firstName(member);
    String month = AlumniCopy.monthName(month1to12, now);
    String nth = DateUtils.ordinal(month1to12);
    String independence = month1to12 == 10
      ? ", and happy Independence Day to us all. 🇳🇬"
      : ".";
    String birthday = member != null && member.birthMonth == month1to12
      ? "And what a month to start with: it's your birthday month! 🎉✨"
      : "";
    String intro = "";
    if (member != null && !member.introduced) {
      intro = apply(
        pick(bag, INTRO),
        who,
        first,
        month,
        nth,
        officer,
        role,
        independence,
        birthday,
        "");
    }
    return squeeze(apply(
      template, who, first, month, nth, officer, role, independence, birthday, intro));
  }

  private static String apply(
    String template,
    String who,
    String first,
    String month,
    String nth,
    String officer,
    String role,
    String independence,
    String birthday,
    String intro) {
    String out = template == null ? "" : template;
    out = out.replace("{who}", who == null ? "" : who);
    out = out.replace("{first}", first == null ? "" : first);
    out = out.replace("{month}", month == null ? "" : month);
    out = out.replace("{nth}", nth == null ? "" : nth);
    out = out.replace("{officer}", officer == null ? "" : officer);
    out = out.replace("{role}", role == null ? "" : role);
    out = out.replace("{independence}", independence == null ? "" : independence);
    out = out.replace("{birthday}", birthday == null ? "" : birthday);
    String introText = intro == null ? "" : intro.trim();
    if (introText.isEmpty()) {
      out = out.replace("{intro}", "");
    } else {
      out = out.replace("{intro}", introText + "\n\n");
    }
    return out;
  }

  static String squeeze(String text) {
    if (text == null) {
      return "";
    }
    String t = text.replace("\r\n", "\n").trim();
    while (t.contains("\n\n\n")) {
      t = t.replace("\n\n\n", "\n\n");
    }
    return t.trim();
  }
}
