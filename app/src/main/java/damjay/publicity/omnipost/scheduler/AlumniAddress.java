package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * How a caption names someone. Male and female values may nest
 * {@code {first}} {@code {full}} {@code {title}} and other address tokens.
 */
public final class AlumniAddress {
  public String name = "";
  public String label = "";
  public String male = "";
  public String female = "";

  public static final String JSON_KEY = "_address";

  public AlumniAddress(String name, String label, String male, String female) {
    this.name = name == null ? "" : name;
    this.label = label == null ? "" : label;
    this.male = male == null ? "" : male;
    this.female = female == null ? "" : female;
  }

  public String pick(Member member) {
    boolean woman = member != null && Member.GENDER_FEMALE.equals(member.gender);
    if (woman && !blank(female)) {
      return female;
    }
    if (!woman && !blank(male)) {
      return male;
    }
    if (!blank(male)) {
      return male;
    }
    return female == null ? "" : female;
  }

  public static List<AlumniAddress> defaults() {
    List<AlumniAddress> out = new ArrayList<>();
    out.add(new AlumniAddress(
      "dear",
      "Casual — Mr Ada / ma, Ada",
      "Mr {first}",
      "ma, {first}"));
    out.add(new AlumniAddress(
      "formal",
      "Full name — Mr Ada Okwuoma / ma, Ada Okwuoma",
      "Mr {full}",
      "ma, {full}"));
    out.add(new AlumniAddress(
      "titled",
      "Title plus first — Mr Ada / Ma Ada",
      "{title} {first}",
      "{title} {first}"));
    out.add(new AlumniAddress(
      "title_only",
      "Title only — Mr / ma,",
      "Mr",
      "ma,"));
    return out;
  }

  public static boolean reserved(String name) {
    String key = CaptionVars.normalizeName(name);
    if (key.isEmpty()) {
      return true;
    }
    if (CaptionVars.isReserved(key)) {
      return true;
    }
    for (String item : AlumniTemplates.KEYS) {
      if (item.equals(key)) {
        return true;
      }
    }
    return "who".equals(key)
        || "first".equals(key)
        || "last".equals(key)
        || "full".equals(key)
        || "name".equals(key)
        || "title".equals(key)
        || "title_lc".equals(key)
        || "nth".equals(key)
        || "intro".equals(key)
        || "birthday".equals(key)
        || "independence".equals(key);
  }

  public static List<AlumniAddress> fromJson(JSONArray arr) {
    List<AlumniAddress> out = new ArrayList<>();
    if (arr == null) {
      return out;
    }
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o == null) {
        continue;
      }
      AlumniAddress item = new AlumniAddress(
        o.optString("name", ""),
        o.optString("label", ""),
        o.optString("male", ""),
        o.optString("female", ""));
      item.name = CaptionVars.normalizeName(item.name);
      if (item.name.isEmpty() || reserved(item.name) || !CaptionTemplates.isTokenName(item.name)) {
        continue;
      }
      out.add(item);
    }
    return out;
  }

  public static JSONArray toJson(List<AlumniAddress> list) {
    JSONArray arr = new JSONArray();
    if (list == null) {
      return arr;
    }
    for (AlumniAddress item : list) {
      if (item == null || item.name == null || item.name.isEmpty()) {
        continue;
      }
      try {
        JSONObject o = new JSONObject();
        o.put("name", item.name);
        o.put("label", item.label == null ? "" : item.label);
        o.put("male", item.male == null ? "" : item.male);
        o.put("female", item.female == null ? "" : item.female);
        arr.put(o);
      } catch (Exception ignored) {
        // skip
      }
    }
    return arr;
  }

  private static boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
