package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CaptionVars {
  private CaptionVars() {}

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
    for (CaptionVar var : vars) {
      if (var == null) {
        continue;
      }
      String name = normalizeName(var.name);
      if (!CaptionTemplates.isTokenName(name)) {
        continue;
      }
      out.put(name, var.value == null ? "" : var.value);
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
}
