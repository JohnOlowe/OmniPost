package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Google Sheet → alumni cards. Column A is often empty; B–I are first name,
 * last name, gender, email, phone, birthday, position, graduation set.
 * Merge onto the name that is already here — never a second card.
 */
public final class AlumniSheet {
  private AlumniSheet() {}

  public static final class Row implements Serializable {
    private static final long serialVersionUID = 1L;
    public String firstName = "";
    public String lastName = "";
    public String gender = "";
    public String email = "";
    public String phone = "";
    public String position = "";
    public String gradSet = "";
    public int birthMonth;
    public int birthDay;

    public String displayName() {
      return AlumniSheet.displayName(firstName, lastName);
    }
  }

  public static final class Result {
    public final List<Row> rows = new ArrayList<>();
    public int skipped;
  }

  public static String displayName(String first, String last) {
    String a = first == null ? "" : first.trim();
    String b = last == null ? "" : last.trim();
    if (a.isEmpty()) {
      return b;
    }
    if (b.isEmpty()) {
      return a;
    }
    return a + " " + b;
  }

  public static Result parse(String raw) {
    Result out = new Result();
    if (raw == null || raw.isEmpty()) {
      return out;
    }
    String text = raw;
    if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
      text = text.substring(1);
    }
    char delim = detectDelim(text);
    String[] lines = text.split("\r?\n", -1);
    int[] cols = null;
    for (String line : lines) {
      if (line == null) {
        continue;
      }
      List<String> cells = split(line, delim);
      if (isEmptyRow(cells)) {
        continue;
      }
      if (cols == null && looksLikeHeader(cells)) {
        cols = mapHeader(cells);
        continue;
      }
      if (cols == null) {
        cols = defaultMap(cells);
      }
      Row row = toRow(cells, cols);
      if (row == null || row.displayName().isEmpty()) {
        out.skipped++;
        continue;
      }
      out.rows.add(row);
    }
    return out;
  }

  public static String genderOf(String raw) {
    if (raw == null) {
      return "";
    }
    String t = raw.trim().toLowerCase(Locale.US);
    if (t.isEmpty()) {
      return "";
    }
    if (t.startsWith("f")
        || t.contains("female")
        || t.contains("woman")
        || t.equals("ma")
        || t.contains("lady")
        || t.contains("sister")
        || t.equals("sis")
        || t.equals("girl")) {
      return Member.GENDER_FEMALE;
    }
    if (t.startsWith("m")
        || t.contains("male")
        || t.contains("man")
        || t.equals("sir")
        || t.contains("brother")
        || t.equals("bro")
        || t.equals("guy")) {
      return Member.GENDER_MALE;
    }
    return "";
  }

  public static int[] birthdayOf(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    if (t.isEmpty() || t.equals("-") || t.equalsIgnoreCase("nil") || t.equalsIgnoreCase("n/a")) {
      return null;
    }
    t = t.toLowerCase(Locale.US);
    t = t.replaceAll("(?i)(\\d+)(st|nd|rd|th)", "$1");
    t = t.replace(',', ' ');
    t = t.replaceAll("\\b(of|the|born|birthday|bday|on|day)\\b", " ");
    t = t.replace('.', ' ');
    t = t.replace('-', ' ');
    t = t.replace('/', ' ');
    t = t.replaceAll("\\s+", " ").trim();
    int named = namedMonth(t);
    if (named > 0) {
      int day = firstDayNumber(t);
      if (day == 0) {
        day = firstDayNumber(t.replaceAll("[a-z]+", " "));
      }
      if (day >= 1 && day <= 31) {
        return new int[] {named, day};
      }
    }
    String[] parts = t.split(" ");
    List<Integer> nums = new ArrayList<>();
    for (String part : parts) {
      if (part == null || part.isEmpty()) {
        continue;
      }
      try {
        nums.add(Integer.parseInt(part.trim()));
      } catch (NumberFormatException ignored) {
        // month names already handled
      }
    }
    if (nums.size() < 2) {
      return null;
    }
    int a = nums.get(0);
    int b = nums.get(1);
    int month;
    int day;
    if (a > 12 && b >= 1 && b <= 12) {
      day = a;
      month = b;
    } else if (b > 12 && a >= 1 && a <= 12) {
      month = a;
      day = b;
    } else if (a >= 1 && a <= 12 && b >= 1 && b <= 31) {
      // Nigeria writes day/month.
      day = a;
      month = b;
    } else {
      return null;
    }
    if (month < 1 || month > 12 || day < 1 || day > 31) {
      return null;
    }
    return new int[] {month, day};
  }

  static char detectDelim(String text) {
    int comma = 0;
    int semi = 0;
    int tab = 0;
    int n = Math.min(text.length(), 4000);
    for (int i = 0; i < n; i++) {
      char c = text.charAt(i);
      if (c == ',') {
        comma++;
      } else if (c == ';') {
        semi++;
      } else if (c == '\t') {
        tab++;
      }
    }
    if (tab > comma && tab > semi) {
      return '\t';
    }
    if (semi > comma) {
      return ';';
    }
    return ',';
  }

  static List<String> split(String line, char delim) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean quote = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (quote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else {
          quote = !quote;
        }
      } else if (c == delim && !quote) {
        out.add(cur.toString().trim());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString().trim());
    return out;
  }

  private static boolean isEmptyRow(List<String> cells) {
    if (cells == null || cells.isEmpty()) {
      return true;
    }
    for (String cell : cells) {
      if (cell != null && !cell.trim().isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private static boolean looksLikeHeader(List<String> cells) {
    for (String cell : cells) {
      String n = norm(cell);
      if (n.contains("first name")
          || n.contains("last name")
          || n.equals("gender")
          || n.contains("graduation")
          || n.contains("whatsapp contact")) {
        return true;
      }
    }
    return false;
  }

  private static int[] mapHeader(List<String> cells) {
    int[] cols = new int[] {-1, -1, -1, -1, -1, -1, -1, -1};
    for (int i = 0; i < cells.size(); i++) {
      String n = norm(cells.get(i));
      if (n.contains("first") && n.contains("name") && cols[0] < 0) {
        cols[0] = i;
      } else if ((n.equals("last name") || n.equals("surname") || n.startsWith("last")) && cols[1] < 0) {
        cols[1] = i;
      } else if ((n.contains("gender") || n.equals("sex")) && cols[2] < 0) {
        cols[2] = i;
      } else if ((n.contains("email") || n.contains("e-mail") || n.contains("mail")) && cols[3] < 0) {
        cols[3] = i;
      } else if ((n.contains("phone") || n.contains("whatsapp") || n.contains("number")) && cols[4] < 0) {
        cols[4] = i;
      } else if (n.contains("birth") && cols[5] < 0) {
        cols[5] = i;
      } else if (n.contains("position") && cols[6] < 0) {
        cols[6] = i;
      } else if ((n.contains("grad") || n.contains("set")) && cols[7] < 0) {
        cols[7] = i;
      }
    }
    return cols;
  }

  /** A empty → B–I. Otherwise treat the first cell as first name. */
  private static int[] defaultMap(List<String> cells) {
    int start = 0;
    if (!cells.isEmpty() && (cells.get(0) == null || cells.get(0).trim().isEmpty())) {
      start = 1;
    }
    return new int[] {
      start, start + 1, start + 2, start + 3, start + 4, start + 5, start + 6, start + 7
    };
  }

  private static Row toRow(List<String> cells, int[] cols) {
    Row row = new Row();
    row.firstName = cell(cells, cols[0]);
    row.lastName = cell(cells, cols[1]);
    row.gender = genderOf(cell(cells, cols[2]));
    row.email = cell(cells, cols[3]);
    row.phone = AlumniDesk.nigeriaDigits(cell(cells, cols[4]));
    int[] bday = birthdayOf(cell(cells, cols[5]));
    if (bday != null) {
      row.birthMonth = bday[0];
      row.birthDay = bday[1];
    }
    row.position = cell(cells, cols[6]);
    row.gradSet = cell(cells, cols[7]);
    return row;
  }

  private static String cell(List<String> cells, int index) {
    if (cells == null || index < 0 || index >= cells.size() || cells.get(index) == null) {
      return "";
    }
    return cells.get(index).trim();
  }

  private static String norm(String cell) {
    return cell == null ? "" : cell.trim().toLowerCase(Locale.US);
  }

  private static int namedMonth(String t) {
    String n = t.toLowerCase(Locale.US);
    String[] names = {
      "january", "february", "march", "april", "may", "june",
      "july", "august", "september", "october", "november", "december"
    };
    String[] shortNames = {
      "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    };
    for (int i = 0; i < 12; i++) {
      if (containsWord(n, names[i]) || containsWord(n, shortNames[i])) {
        return i + 1;
      }
    }
    if (containsWord(n, "sept")) {
      return 9;
    }
    return 0;
  }

  private static boolean containsWord(String hay, String needle) {
    int at = hay.indexOf(needle);
    if (at < 0) {
      return false;
    }
    boolean before = at == 0 || !Character.isLetter(hay.charAt(at - 1));
    int end = at + needle.length();
    boolean after = end == hay.length() || !Character.isLetter(hay.charAt(end));
    return before && after;
  }

  private static int firstDayNumber(String t) {
    String[] parts = t.split("[/ ]");
    for (String part : parts) {
      try {
        int n = Integer.parseInt(part.trim());
        if (n >= 1 && n <= 31) {
          return n;
        }
      } catch (NumberFormatException ignored) {
        // continue
      }
    }
    return 0;
  }
}
