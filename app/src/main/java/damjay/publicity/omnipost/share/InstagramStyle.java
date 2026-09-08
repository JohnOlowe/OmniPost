package damjay.publicity.omnipost.share;

import java.util.ArrayList;
import java.util.List;

/**
 * WhatsApp markup ↔ Unicode letters Instagram can paste.
 * Default: *bold* and *_both_* are sans-serif; _italic_ stays serif italic.
 */
public final class InstagramStyle {
  public static final String FACE_SANS = "sans";
  public static final String FACE_SERIF = "serif";

  private static final int NONE = 0;
  private static final int BOLD = 1;
  private static final int ITALIC = 2;
  private static final int BOTH = 3;

  private InstagramStyle() {}

  public static final class Faces {
    public final String bold;
    public final String italic;
    public final String both;

    public Faces(String bold, String italic, String both) {
      this.bold = normalize(bold, FACE_SANS);
      this.italic = normalize(italic, FACE_SERIF);
      this.both = normalize(both, FACE_SANS);
    }

    public static Faces defaults() {
      return new Faces(FACE_SANS, FACE_SERIF, FACE_SANS);
    }

    public static String normalize(String face, String fallback) {
      if (FACE_SERIF.equals(face) || FACE_SANS.equals(face)) {
        return face;
      }
      return fallback;
    }
  }

  public static String toUnicode(String source) {
    return toUnicode(source, Faces.defaults());
  }

  public static String toUnicode(String source, Faces faces) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    Faces use = faces == null ? Faces.defaults() : faces;
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
            out.append(style(source.substring(i + 2, end), true, true, use));
            i = end + 2;
            continue;
          }
        }
        if (a == '_' && b == '*') {
          int end = source.indexOf("*_", i + 2);
          if (end >= i + 2) {
            out.append(style(source.substring(i + 2, end), true, true, use));
            i = end + 2;
            continue;
          }
        }
      }
      char c = source.charAt(i);
      if (c == '*') {
        int end = source.indexOf('*', i + 1);
        if (end > i) {
          out.append(style(source.substring(i + 1, end), true, false, use));
          i = end + 1;
          continue;
        }
      }
      if (c == '_') {
        int end = source.indexOf('_', i + 1);
        if (end > i) {
          out.append(style(source.substring(i + 1, end), false, true, use));
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

  /** Turn Instagram / Unicode letters back into *bold* _italic_ *_both_*. */
  public static String toMarkup(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    List<Glyph> glyphs = new ArrayList<>();
    int i = 0;
    while (i < source.length()) {
      int cp = source.codePointAt(i);
      glyphs.add(decode(cp));
      i += Character.charCount(cp);
    }
    int n = glyphs.size();
    int[] styles = new int[n];
    for (int g = 0; g < n; g++) {
      styles[g] = glyphs.get(g).style;
    }
    inherit(glyphs, styles);
    StringBuilder out = new StringBuilder();
    int g = 0;
    while (g < n) {
      int style = styles[g];
      if (style == NONE) {
        out.appendCodePoint(glyphs.get(g).cp);
        g++;
        continue;
      }
      int end = g + 1;
      while (end < n && styles[end] == style) {
        end++;
      }
      String inner = slice(glyphs, g, end);
      if (style == BOTH) {
        out.append("*_").append(inner).append("_*");
      } else if (style == BOLD) {
        out.append('*').append(inner).append('*');
      } else {
        out.append('_').append(inner).append('_');
      }
      g = end;
    }
    return out.toString();
  }

  public static String style(String source, boolean bold, boolean italic) {
    return style(source, bold, italic, Faces.defaults());
  }

  public static String style(String source, boolean bold, boolean italic, Faces faces) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    Faces use = faces == null ? Faces.defaults() : faces;
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < source.length()) {
      int cp = source.codePointAt(i);
      out.append(map(cp, bold, italic, use));
      i += Character.charCount(cp);
    }
    return out.toString();
  }

  private static String map(int cp, boolean bold, boolean italic, Faces faces) {
    boolean sans = sansFace(bold, italic, faces);
    if (cp >= 'A' && cp <= 'Z') {
      return new String(Character.toChars(upperBase(bold, italic, sans) + (cp - 'A')));
    }
    if (cp >= 'a' && cp <= 'z') {
      if (italic && !bold && !sans && cp == 'h') {
        return "\u210E";
      }
      return new String(Character.toChars(lowerBase(bold, italic, sans) + (cp - 'a')));
    }
    if (bold && cp >= '0' && cp <= '9') {
      int base = sans ? 0x1D7EC : 0x1D7CE;
      return new String(Character.toChars(base + (cp - '0')));
    }
    return new String(Character.toChars(cp));
  }

  private static boolean sansFace(boolean bold, boolean italic, Faces faces) {
    if (bold && italic) {
      return FACE_SANS.equals(faces.both);
    }
    if (bold) {
      return FACE_SANS.equals(faces.bold);
    }
    return FACE_SANS.equals(faces.italic);
  }

  private static int upperBase(boolean bold, boolean italic, boolean sans) {
    if (bold && italic) {
      return sans ? 0x1D63C : 0x1D468;
    }
    if (bold) {
      return sans ? 0x1D5D4 : 0x1D400;
    }
    return sans ? 0x1D608 : 0x1D434;
  }

  private static int lowerBase(boolean bold, boolean italic, boolean sans) {
    if (bold && italic) {
      return sans ? 0x1D656 : 0x1D482;
    }
    if (bold) {
      return sans ? 0x1D5EE : 0x1D41A;
    }
    return sans ? 0x1D622 : 0x1D44E;
  }

  private static Glyph decode(int cp) {
    if (cp == 0x210E) {
      return new Glyph('h', ITALIC);
    }
    Glyph g = letters(cp, 0x1D400, 'A', BOLD);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D41A, 'a', BOLD);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D434, 'A', ITALIC);
    if (g != null) {
      return g;
    }
    if (cp >= 0x1D44E && cp <= 0x1D467 && cp != 0x1D455) {
      return new Glyph('a' + (cp - 0x1D44E), ITALIC);
    }
    g = letters(cp, 0x1D468, 'A', BOTH);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D482, 'a', BOTH);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D5A0, 'A', NONE);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D5BA, 'a', NONE);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D5D4, 'A', BOLD);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D5EE, 'a', BOLD);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D608, 'A', ITALIC);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D622, 'a', ITALIC);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D63C, 'A', BOTH);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D656, 'a', BOTH);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D670, 'A', NONE);
    if (g != null) {
      return g;
    }
    g = letters(cp, 0x1D68A, 'a', NONE);
    if (g != null) {
      return g;
    }
    g = digits(cp, 0x1D7CE, BOLD);
    if (g != null) {
      return g;
    }
    g = digits(cp, 0x1D7E2, NONE);
    if (g != null) {
      return g;
    }
    g = digits(cp, 0x1D7EC, BOLD);
    if (g != null) {
      return g;
    }
    return new Glyph(cp, NONE);
  }

  private static Glyph letters(int cp, int base, char origin, int style) {
    int off = cp - base;
    if (off >= 0 && off <= 25) {
      return new Glyph(origin + off, style);
    }
    return null;
  }

  private static Glyph digits(int cp, int base, int style) {
    int off = cp - base;
    if (off >= 0 && off <= 9) {
      return new Glyph('0' + off, style);
    }
    return null;
  }

  private static void inherit(List<Glyph> glyphs, int[] styles) {
    int n = styles.length;
    for (int i = 0; i < n; i++) {
      if (styles[i] != NONE) {
        continue;
      }
      int cp = glyphs.get(i).cp;
      if (cp == '\n' || cp == '\r' || Character.isLetterOrDigit(cp)) {
        continue;
      }
      int left = i == 0 ? NONE : styles[i - 1];
      int right = i == n - 1 ? NONE : styles[i + 1];
      if (cp == ' ' || cp == '\t') {
        if (left != NONE && left == right) {
          styles[i] = left;
        }
        continue;
      }
      if (left != NONE) {
        styles[i] = left;
      } else if (right != NONE) {
        styles[i] = right;
      }
    }
  }

  private static String slice(List<Glyph> glyphs, int from, int to) {
    StringBuilder out = new StringBuilder();
    for (int i = from; i < to; i++) {
      out.appendCodePoint(glyphs.get(i).cp);
    }
    return out.toString();
  }

  private static final class Glyph {
    final int cp;
    final int style;

    Glyph(int cp, int style) {
      this.cp = cp;
      this.style = style;
    }
  }
}
