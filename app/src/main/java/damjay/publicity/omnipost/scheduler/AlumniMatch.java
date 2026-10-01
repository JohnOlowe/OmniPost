package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Attach a sheet row to a roster card. Roster name and birthday stay;
 * the sheet fills phone / gender / email.
 */
public final class AlumniMatch {
  private static final Object LOCK = new Object();
  private static List<Indexed> cached = Collections.emptyList();
  private static boolean hasRoster;

  private AlumniMatch() {}

  public static final class Suggestion {
    public Member roster;
    public int score;
    public boolean birthdaySame;
    public boolean birthdayClash;
  }

  static final class Indexed {
    final Member member;
    final List<String> tokens;
    final String last;
    final String prefix;

    Indexed(Member member, List<String> tokens) {
      this.member = member;
      this.tokens = tokens;
      this.last = last(tokens);
      this.prefix = tokens.isEmpty() ? "" : prefix3(tokens.get(0));
    }
  }

  public static void rememberRoster(List<Member> members) {
    List<Indexed> next = new ArrayList<>();
    if (members != null) {
      for (Member member : members) {
        if (!Member.isAlumni(member)) {
          continue;
        }
        List<String> tokens = tokens(member.name);
        if (tokens.isEmpty()) {
          continue;
        }
        next.add(new Indexed(member, tokens));
      }
    }
    synchronized (LOCK) {
      cached = next;
      hasRoster = true;
    }
  }

  public static boolean hasRoster() {
    synchronized (LOCK) {
      return hasRoster;
    }
  }

  public static List<Suggestion> suggest(AlumniSheet.Row row, int limit) {
    List<Indexed> roster;
    synchronized (LOCK) {
      roster = cached;
    }
    return rank(row, roster, limit);
  }

  public static List<Suggestion> suggest(AlumniSheet.Row row, List<Member> roster, int limit) {
    List<Indexed> indexed = new ArrayList<>();
    if (roster != null) {
      for (Member member : roster) {
        if (!Member.isAlumni(member)) {
          continue;
        }
        List<String> tokens = tokens(member.name);
        if (tokens.isEmpty()) {
          continue;
        }
        indexed.add(new Indexed(member, tokens));
      }
    }
    return rank(row, indexed, limit);
  }

  private static List<Suggestion> rank(AlumniSheet.Row row, List<Indexed> roster, int limit) {
    List<Suggestion> out = new ArrayList<>();
    if (row == null || roster == null || roster.isEmpty()) {
      return out;
    }
    List<String> sheetTokens = tokens(row.displayName());
    if (sheetTokens.isEmpty()) {
      return out;
    }
    String sheetLast = last(sheetTokens);
    String sheetPrefix = prefix3(sheetTokens.get(0));
    for (Indexed item : roster) {
      Suggestion suggestion = score(
        sheetTokens,
        sheetLast,
        sheetPrefix,
        row.birthMonth,
        row.birthDay,
        item);
      if (suggestion.score <= 0) {
        continue;
      }
      out.add(suggestion);
    }
    Collections.sort(out, Comparator.comparingInt((Suggestion s) -> s.score).reversed());
    int cap = limit < 1 ? 8 : limit;
    if (out.size() > cap) {
      return new ArrayList<>(out.subList(0, cap));
    }
    return out;
  }

  public static Suggestion score(String sheetName, int sheetMonth, int sheetDay, Member roster) {
    List<String> sheetTokens = tokens(sheetName);
    List<String> rosterTokens = tokens(roster == null ? "" : roster.name);
    return score(
      sheetTokens,
      last(sheetTokens),
      sheetTokens.isEmpty() ? "" : prefix3(sheetTokens.get(0)),
      sheetMonth,
      sheetDay,
      new Indexed(roster, rosterTokens));
  }

  private static Suggestion score(
    List<String> sheetTokens,
    String sheetLast,
    String sheetPrefix,
    int sheetMonth,
    int sheetDay,
    Indexed indexed) {
    Suggestion item = new Suggestion();
    item.roster = indexed.member;
    if (indexed.member == null || sheetTokens.isEmpty() || indexed.tokens.isEmpty()) {
      return item;
    }
    Set<String> shared = new HashSet<>(sheetTokens);
    shared.retainAll(indexed.tokens);
    int max = Math.max(sheetTokens.size(), indexed.tokens.size());
    item.score = (shared.size() * 50) / max;
    if (shared.size() == sheetTokens.size() && shared.size() == indexed.tokens.size()) {
      item.score += 25;
    }
    if (!sheetLast.isEmpty() && sheetLast.equals(indexed.last)) {
      item.score += 20;
    }
    if (sheetPrefix.length() >= 3 && sheetPrefix.equals(indexed.prefix)) {
      item.score += 10;
    }
    boolean sheetBday = sheetMonth > 0 && sheetDay > 0;
    boolean rosterBday = indexed.member.birthMonth > 0 && indexed.member.birthDay > 0;
    if (sheetBday && rosterBday) {
      if (sheetMonth == indexed.member.birthMonth && sheetDay == indexed.member.birthDay) {
        item.birthdaySame = true;
        item.score += 30;
      } else {
        item.birthdayClash = true;
        item.score -= 8;
      }
    }
    if (item.score < 0) {
      item.score = 0;
    }
    if (item.score > 100) {
      item.score = 100;
    }
    return item;
  }

  public static List<String> tokens(String name) {
    List<String> out = new ArrayList<>(4);
    if (name == null || name.isEmpty()) {
      return out;
    }
    StringBuilder buf = new StringBuilder();
    int n = name.length();
    for (int i = 0; i < n; i++) {
      char c = name.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        c = (char) (c + 32);
      }
      if (c >= 'a' && c <= 'z') {
        buf.append(c);
      } else if (buf.length() > 0) {
        addToken(out, buf);
        buf.setLength(0);
      }
    }
    if (buf.length() > 0) {
      addToken(out, buf);
    }
    return out;
  }

  private static void addToken(List<String> out, StringBuilder buf) {
    String part = buf.toString();
    if (!drop(part)) {
      out.add(part);
    }
  }

  private static boolean drop(String part) {
    return part.equals("mr")
        || part.equals("mrs")
        || part.equals("miss")
        || part.equals("ms")
        || part.equals("dr")
        || part.equals("prof")
        || part.equals("pastor")
        || part.equals("pst")
        || part.equals("rev")
        || part.equals("sir")
        || part.equals("ma");
  }

  private static String last(List<String> tokens) {
    return tokens.isEmpty() ? "" : tokens.get(tokens.size() - 1);
  }

  private static String prefix3(String token) {
    if (token == null) {
      return "";
    }
    return token.length() <= 3 ? token : token.substring(0, 3);
  }
}
