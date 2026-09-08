package damjay.publicity.omnipost.share;

/**
 * WhatsApp markup → Unicode letters Instagram can paste.
 * *bold* is sans-serif bold (the Instagram look). _italic_ stays serif italic.
 * *_both_* (either order) stays bold-italic.
 */
public final class InstagramStyle {
  private InstagramStyle() {}

  public static String toUnicode(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int i = 0;
    int n = source.length();
    while (i < n) {
      if (i + 1 < n) {
        char a = source.charAt(i);
        char b = source.charAt(i + 1);
        if (a == '*' && b == '_') {
          int end = source.indexOf("_*", i + 2);
          if (end >= i + 2) {
            out.append(style(source.substring(i + 2, end), true, true));
            i = end + 2;
            continue;
          }
        }
        if (a == '_' && b == '*') {
          int end = source.indexOf("*_", i + 2);
          if (end >= i + 2) {
            out.append(style(source.substring(i + 2, end), true, true));
            i = end + 2;
            continue;
          }
        }
      }
      char c = source.charAt(i);
      if (c == '*') {
        int end = source.indexOf('*', i + 1);
        if (end > i) {
          out.append(style(source.substring(i + 1, end), true, false));
          i = end + 1;
          continue;
        }
      }
      if (c == '_') {
        int end = source.indexOf('_', i + 1);
        if (end > i) {
          out.append(style(source.substring(i + 1, end), false, true));
          i = end + 1;
          continue;
        }
      }
      int cp = source.codePointAt(i);
      out.appendCodePoint(cp);
      i += Character.charCount(cp);
    }
    return out.toString();
  }

  static String style(String source, boolean bold, boolean italic) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < source.length()) {
      int cp = source.codePointAt(i);
      out.append(map(cp, bold, italic));
      i += Character.charCount(cp);
    }
    return out.toString();
  }

  private static String map(int cp, boolean bold, boolean italic) {
    if (cp >= 'A' && cp <= 'Z') {
      int offset = cp - 'A';
      if (bold && italic) {
        return new String(Character.toChars(0x1D468 + offset));
      }
      if (bold) {
        return new String(Character.toChars(0x1D5D4 + offset));
      }
      if (italic) {
        return new String(Character.toChars(0x1D434 + offset));
      }
    }
    if (cp >= 'a' && cp <= 'z') {
      int offset = cp - 'a';
      if (bold && italic) {
        return new String(Character.toChars(0x1D482 + offset));
      }
      if (bold) {
        return new String(Character.toChars(0x1D5EE + offset));
      }
      if (italic) {
        if (cp == 'h') {
          return "\u210E";
        }
        return new String(Character.toChars(0x1D44E + offset));
      }
    }
    if (bold && cp >= '0' && cp <= '9') {
      return new String(Character.toChars(0x1D7EC + (cp - '0')));
    }
    return new String(Character.toChars(cp));
  }
}
