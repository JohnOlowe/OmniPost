package damjay.publicity.omnipost.share;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.widget.TextView;
import damjay.publicity.omnipost.R;

/**
 * Renders WhatsApp markup the way the post will look: *bold*, _italic_, *_both_*.
 * Previews drop the markers. Caption boxes keep them and paint the words inside.
 */
public final class WhatsAppPreview {
  private static final Object ATTACHED = new Object();

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
      apply(out, start, out.length(), bold, italic);
    });
    return out;
  }

  /** Fill a display TextView (markers stripped, words styled). */
  public static void show(TextView view, String raw) {
    if (view == null) {
      return;
    }
    view.setText(display(raw == null ? "" : raw));
  }

  /** Style *bold* / _italic_ inside an editable box without removing the markers. */
  public static void paint(Spannable text) {
    if (text == null || text.length() == 0) {
      return;
    }
    StyleSpan[] old = text.getSpans(0, text.length(), StyleSpan.class);
    for (StyleSpan span : old) {
      text.removeSpan(span);
    }
    scan(text.toString(), (from, to, bold, italic) -> apply(text, from, to, bold, italic));
  }

  public static void attach(TextView view) {
    if (view == null || view.getTag(R.id.markup_attached) == ATTACHED) {
      return;
    }
    view.setTag(R.id.markup_attached, ATTACHED);
    view.addTextChangedListener(new TextWatcher() {
      private boolean busy;

      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        if (busy || s == null) {
          return;
        }
        busy = true;
        paint(s);
        busy = false;
      }
    });
    CharSequence now = view.getText();
    if (now instanceof Spannable) {
      paint((Spannable) now);
    }
  }

  static int[][] marks(String source) {
    java.util.ArrayList<int[]> out = new java.util.ArrayList<>();
    scan(source, (from, to, bold, italic) -> {
      int style = 0;
      if (bold) {
        style |= 1;
      }
      if (italic) {
        style |= 2;
      }
      out.add(new int[] {from, to, style});
    });
    return out.toArray(new int[0][]);
  }

  private static void apply(Spannable text, int start, int end, boolean bold, boolean italic) {
    if (text == null || end <= start) {
      return;
    }
    int from = Math.max(0, start);
    int to = Math.min(text.length(), end);
    if (to <= from) {
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
      text.setSpan(new StyleSpan(style), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private interface Sink {
    void accept(String chunk, boolean bold, boolean italic);
  }

  private interface RangeSink {
    void accept(int from, int to, boolean bold, boolean italic);
  }

  private static void walk(String source, Sink sink) {
    if (source == null || source.isEmpty()) {
      return;
    }
    int i = 0;
    int n = source.length();
    while (i < n) {
      int[] hit = nextMark(source, i);
      if (hit != null) {
        sink.accept(source.substring(hit[0], hit[1]), hit[2] == 1, hit[3] == 1);
        i = hit[4];
        continue;
      }
      int cp = source.codePointAt(i);
      sink.accept(new String(Character.toChars(cp)), false, false);
      i += Character.charCount(cp);
    }
  }

  private static void scan(String source, RangeSink sink) {
    if (source == null || source.isEmpty()) {
      return;
    }
    int i = 0;
    int n = source.length();
    while (i < n) {
      int[] hit = nextMark(source, i);
      if (hit != null) {
        sink.accept(hit[0], hit[1], hit[2] == 1, hit[3] == 1);
        i = hit[4];
        continue;
      }
      i += Character.charCount(source.codePointAt(i));
    }
  }

  /**
   * @return {@code [innerStart, innerEnd, bold, italic, nextIndex]} or null
   */
  private static int[] nextMark(String source, int i) {
    int n = source.length();
    if (i + 1 < n) {
      char a = source.charAt(i);
      char b = source.charAt(i + 1);
      if (a == '*' && b == '_') {
        int end = source.indexOf("_*", i + 2);
        if (end >= i + 2) {
          return new int[] {i + 2, end, 1, 1, end + 2};
        }
      }
      if (a == '_' && b == '*') {
        int end = source.indexOf("*_", i + 2);
        if (end >= i + 2) {
          return new int[] {i + 2, end, 1, 1, end + 2};
        }
      }
    }
    char c = source.charAt(i);
    if (c == '*') {
      int end = source.indexOf('*', i + 1);
      if (end > i) {
        return new int[] {i + 1, end, 1, 0, end + 1};
      }
    }
    if (c == '_') {
      int end = source.indexOf('_', i + 1);
      if (end > i) {
        return new int[] {i + 1, end, 0, 1, end + 1};
      }
    }
    return null;
  }
}
