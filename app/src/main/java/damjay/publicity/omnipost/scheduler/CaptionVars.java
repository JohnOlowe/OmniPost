package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CaptionVars {
  public static final class Builtin {
    public final String name;
    public final String label;
    public final String hint;

    public Builtin(String name, String label, String hint) {
      this.name = name;
      this.label = label;
      this.hint = hint;
    }
  }

  private static final String[] RESERVED = {
    "today", "today_weekday", "today_month", "today_year",
    "now", "clock",
    "date", "due", "weekday", "month", "Month", "year",
    "start", "end", "range",
    "Days", "days", "away", "name"
  };

  private CaptionVars() {}

  public static List<Builtin> builtins() {
    List<Builtin> out = new ArrayList<>();
    out.add(new Builtin("today", "Today",
      "The calendar day right now. {date} is the post's due day — this is not that."));
    out.add(new Builtin("today_weekday", "Today's weekday",
      "Monday, Tuesday, … for the clock on this phone."));
    out.add(new Builtin("today_month", "Today's month",
      "September, October, … for today."));
    out.add(new Builtin("today_year", "Today's year",
      "The year on this phone right now."));
    out.add(new Builtin("now", "Now",
      "Current date and time."));
    out.add(new Builtin("clock", "Current time",
      "The time of day right now."));
    out.add(new Builtin("date", "Due date",
      "When this post is due. Use {today} if you want today's date."));
    out.add(new Builtin("weekday", "Due weekday",
      "Weekday of the due date."));
    out.add(new Builtin("month", "Due month",
      "Month name for the post (birthday NOTICE fills the month being celebrated)."));
    out.add(new Builtin("year", "Due year",
      "Year of the event or due date."));
    out.add(new Builtin("start", "Event start",
      "First day of a countdown or event."));
    out.add(new Builtin("end", "Event end",
      "Last day of a countdown or event."));
    out.add(new Builtin("range", "Event range",
      "Start through end, e.g. 9th – 13th September, 2026."));
    out.add(new Builtin("Days", "Countdown headline",
      "D-DAY, 1 DAY TO GO, 9 DAYS TO GO."));
    out.add(new Builtin("days", "Days remaining",
      "The number only."));
    out.add(new Builtin("away", "Days-away phrase",
      "is here / is 1 day away / is 9 days away."));
    out.add(new Builtin("name", "Name",
      "The series title, or the member's name on a birthday."));
    out.add(new Builtin("range:today:date", "Range you compose",
      "Write {range:today:date}, {range:start:end}, or {range:today:today+6}. Offsets: {today+3} {date-1}."));
    return out;
  }

  public static boolean isReserved(String name) {
    String key = normalizeName(name);
    for (String reserved : RESERVED) {
      if (reserved.equals(key)) {
        return true;
      }
    }
    return false;
  }

  public static Map<String, String> map(AppDatabase db) {
    if (db == null) {
      return new LinkedHashMap<>();
    }
    return map(db.captionVarDao().getAllSync());
  }

  public static Map<String, String> map(List<CaptionVar> vars) {
    Map<String, String> out = new LinkedHashMap<>();
    if (vars == null) {
      return out;
    }
    for (CaptionVar item : vars) {
      if (item == null) {
        continue;
      }
      String name = normalizeName(item.name);
      if (!CaptionTemplates.isTokenName(name) || isReserved(name)) {
        continue;
      }
      out.put(name, item.value == null ? "" : item.value);
    }
    return out;
  }

  public static String normalizeName(String raw) {
    if (raw == null) {
      return "";
    }
    String name = raw.trim();
    if (name.length() >= 2 && name.charAt(0) == '{' && name.charAt(name.length() - 1) == '}') {
      name = name.substring(1, name.length() - 1).trim();
    }
    return name;
  }

  public static String token(String name) {
    String key = normalizeName(name);
    if (key.isEmpty()) {
      return "";
    }
    return "{" + key + "}";
  }

  /**
   * Fill {@code {tokens}}, including nested values, {@code {today+3}}, and
   * {@code {range:today:date}}. Stops if a cycle does not settle.
   */
  public static String expand(
    String template,
    Map<String, String> tokens,
    Calendar today,
    Calendar due,
    Calendar start,
    Calendar end) {
    if (template == null || template.isEmpty()) {
      return template == null ? "" : template;
    }
    String out = template;
    for (int pass = 0; pass < 8; pass++) {
      String next = passOnce(out, tokens, today, due, start, end);
      if (next.equals(out)) {
        return out;
      }
      out = next;
    }
    return out;
  }

  static String passOnce(
    String source,
    Map<String, String> tokens,
    Calendar today,
    Calendar due,
    Calendar start,
    Calendar end) {
    String out = replaceBraces(source, today, due, start, end);
    out = applyLongest(out, tokens);
    return out;
  }

  static String applyLongest(String template, Map<String, String> tokens) {
    if (template == null || template.isEmpty() || tokens == null || tokens.isEmpty()) {
      return template == null ? "" : template;
    }
    List<String> keys = new ArrayList<>(tokens.keySet());
    Collections.sort(keys, Comparator.comparingInt(String::length).reversed());
    String out = template;
    for (String key : keys) {
      if (key == null || key.isEmpty()) {
        continue;
      }
      String value = tokens.get(key);
      out = out.replace("{" + key + "}", value == null ? "" : value);
    }
    return out;
  }

  private static String replaceBraces(
    String source,
    Calendar today,
    Calendar due,
    Calendar start,
    Calendar end) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < source.length()) {
      int open = source.indexOf('{', i);
      if (open < 0) {
        out.append(source, i, source.length());
        break;
      }
      out.append(source, i, open);
      int close = source.indexOf('}', open + 1);
      if (close < 0) {
        out.append(source, open, source.length());
        break;
      }
      String inner = source.substring(open + 1, close);
      String special = resolveSpecial(inner, today, due, start, end);
      if (special != null) {
        out.append(special);
      } else {
        out.append('{').append(inner).append('}');
      }
      i = close + 1;
    }
    return out.toString();
  }

  static String resolveSpecial(
    String inner,
    Calendar today,
    Calendar due,
    Calendar start,
    Calendar end) {
    if (inner == null) {
      return null;
    }
    String spec = inner.trim();
    if (spec.length() >= 6 && spec.regionMatches(true, 0, "range:", 0, 6)) {
      String rest = spec.substring(6);
      int colon = rest.indexOf(':');
      if (colon <= 0 || colon == rest.length() - 1) {
        return null;
      }
      Calendar from = dateRef(rest.substring(0, colon), today, due, start, end);
      Calendar to = dateRef(rest.substring(colon + 1), today, due, start, end);
      if (from == null) {
        return null;
      }
      return DateUtils.prettyRange(from, to);
    }
    if (!hasOffset(spec)) {
      return null;
    }
    Calendar day = dateRef(spec, today, due, start, end);
    if (day == null) {
      return null;
    }
    return DateUtils.prettyDate(day);
  }

  static boolean hasOffset(String spec) {
    if (spec == null) {
      return false;
    }
    int plus = spec.lastIndexOf('+');
    int minus = spec.lastIndexOf('-');
    int cut = Math.max(plus, minus);
    return cut > 0;
  }

  static Calendar dateRef(
    String spec,
    Calendar today,
    Calendar due,
    Calendar start,
    Calendar end) {
    if (spec == null) {
      return null;
    }
    String raw = spec.trim();
    if (raw.isEmpty()) {
      return null;
    }
    int delta = 0;
    String base = raw;
    int plus = raw.lastIndexOf('+');
    int minus = raw.lastIndexOf('-');
    int cut = Math.max(plus, minus);
    if (cut > 0) {
      base = raw.substring(0, cut).trim();
      try {
        delta = Integer.parseInt(raw.substring(cut + 1).trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
      if (raw.charAt(cut) == '-') {
        delta = -delta;
      }
    }
    Calendar pick = null;
    if ("today".equals(base)) {
      pick = today;
    } else if ("date".equals(base) || "due".equals(base)) {
      pick = due;
    } else if ("start".equals(base)) {
      pick = start;
    } else if ("end".equals(base)) {
      pick = end;
    }
    if (pick == null) {
      return null;
    }
    Calendar out = (Calendar) pick.clone();
    if (delta != 0) {
      out.add(Calendar.DAY_OF_MONTH, delta);
    }
    return DateUtils.startOfDay(out);
  }

  public static String weekdayName(Calendar day) {
    if (day == null) {
      return "";
    }
    String name = day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US);
    return name == null ? "" : name;
  }

  public static String yearName(Calendar day) {
    if (day == null) {
      return "";
    }
    return String.valueOf(day.get(Calendar.YEAR));
  }

  public static List<String> reservedNames() {
    return Arrays.asList(RESERVED);
  }
}
