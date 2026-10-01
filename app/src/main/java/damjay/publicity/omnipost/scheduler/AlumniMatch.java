package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Attach a sheet row to a roster card. Roster name and birthday stay;
 * the sheet fills phone / gender / email.
 */
public final class AlumniMatch {
  private AlumniMatch() {}

  public static final class Suggestion {
    public Member roster;
    public int score;
    public boolean birthdaySame;
    public boolean birthdayClash;
  }

  public static List<Suggestion> suggest(AlumniSheet.Row row, List<Member> roster, int limit) {
    List<Suggestion> out = new ArrayList<>();
    if (row == null || roster == null) {
      return out;
    }
    String sheetName = row.displayName();
    for (Member member : roster) {
      if (!Member.isAlumni(member)) {
        continue;
      }
      Suggestion item = score(sheetName, row.birthMonth, row.birthDay, member);
      if (item.score <= 0) {
        continue;
      }
      out.add(item);
    }
    Collections.sort(out, Comparator.comparingInt((Suggestion s) -> s.score).reversed());
    int cap = limit < 1 ? 8 : limit;
    if (out.size() > cap) {
      return new ArrayList<>(out.subList(0, cap));
    }
    return out;
  }

  public static Suggestion score(String sheetName, int sheetMonth, int sheetDay, Member roster) {
    Suggestion item = new Suggestion();
    item.roster = roster;
    if (roster == null) {
      return item;
    }
    List<String> a = tokens(sheetName);
    List<String> b = tokens(roster.name);
    if (a.isEmpty() || b.isEmpty()) {
      return item;
    }
    Set<String> shared = new HashSet<>(a);
    shared.retainAll(new HashSet<>(b));
    int max = Math.max(a.size(), b.size());
    item.score = (shared.size() * 50) / max;
    if (shared.size() == a.size() && shared.size() == b.size()) {
      item.score += 25;
    }
    if (last(a).equals(last(b)) && !last(a).isEmpty()) {
      item.score += 20;
    }
    if (prefix3(a.get(0)).equals(prefix3(b.get(0))) && prefix3(a.get(0)).length() >= 3) {
      item.score += 10;
    }
    boolean sheetBday = sheetMonth > 0 && sheetDay > 0;
    boolean rosterBday = roster.birthMonth > 0 && roster.birthDay > 0;
    if (sheetBday && rosterBday) {
      if (sheetMonth == roster.birthMonth && sheetDay == roster.birthDay) {
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
    List<String> out = new ArrayList<>();
    if (name == null) {
      return out;
    }
    String n = name.toLowerCase(Locale.US).replaceAll("[^a-z]+", " ").trim();
    if (n.isEmpty()) {
      return out;
    }
    for (String part : n.split(" ")) {
      if (part.isEmpty() || drop(part)) {
        continue;
      }
      out.add(part);
    }
    return out;
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
