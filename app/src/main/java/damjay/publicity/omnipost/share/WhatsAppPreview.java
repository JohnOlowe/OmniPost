package damjay.publicity.omnipost.share;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

/**
 * Renders WhatsApp markup the way the post will look: *bold*, _italic_, *_both_*.
 * The caption box stays raw; the Final post box is this preview only.
 */
public final class WhatsAppPreview {
  private WhatsAppPreview() {}

  public static String plain(String source) {
    StringBuilder out = new StringBuilder();
    walk(source, (chunk, bold, italic) -> out.append(chunk));
    return out.toString();
  }

  public static CharSequence display(String source) {
    SpannableStringBuilder out = new SpannableStringBuilder();
    walk(source, (chunk, bold, italic) -> {
      int start = out.length();
      out.append(chunk);
      if (start == out.length()) {
        return;
      }
      int style = Typeface.NORMAL;
      if (bold && italic) {
        style = Typeface.BOLD_ITALIC;
      } else if (bold) {
        style = Typeface.BOLD;
      } else if (italic) {
        style = Typeface.ITALIC;
      }
      if (style != Typeface.NORMAL) {
        out.setSpan(new StyleSpan(style), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
    });
    return out;
  }

  private interface Sink {
    void accept(String chunk, boolean bold, boolean italic);
  }

  private static void walk(String source, Sink sink) {
    if (source == null || source.isEmpty()) {
      return;
    }
    int i = 0;
    int n = source.length();
    while (i < n) {
      if (i + 1 < n) {
        char a = source.charAt(i);
        char b = source.charAt(i + 1);
        if (a == '*' && b == '_') {
          int end = source.indexOf("_*", i + 2);
          if (end >= i + 2) {
            sink.accept(source.substring(i + 2, end), true, true);
            i = end + 2;
            continue;
          }
        }
        if (a == '_' && b == '*') {
          int end = source.indexOf("*_", i + 2);
          if (end >= i + 2) {
            sink.accept(source.substring(i + 2, end), true, true);
            i = end + 2;
            continue;
          }
        }
      }
      char c = source.charAt(i);
      if (c == '*') {
        int end = source.indexOf('*', i + 1);
        if (end > i) {
          sink.accept(source.substring(i + 1, end), true, false);
          i = end + 1;
          continue;
        }
      }
      if (c == '_') {
        int end = source.indexOf('_', i + 1);
        if (end > i) {
          sink.accept(source.substring(i + 1, end), false, true);
          i = end + 1;
          continue;
        }
      }
      int cp = source.codePointAt(i);
      sink.accept(new String(Character.toChars(cp)), false, false);
      i += Character.charCount(cp);
    }
  }
}
