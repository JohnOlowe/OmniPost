package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AlumniMatchTest {
  @Test
  public void closeNamesScoreAndBirthdaySameBeatsAClash() {
    Member roster = alumni("Adetunji Bakinson", 5, 1);
    AlumniSheet.Row same = row("Tunji Bakinson", 5, 1);
    AlumniSheet.Row clash = row("Tunji Bakinson", 8, 12);
    AlumniMatch.Suggestion hit = AlumniMatch.score(same.displayName(), same.birthMonth, same.birthDay, roster);
    AlumniMatch.Suggestion miss = AlumniMatch.score(clash.displayName(), clash.birthMonth, clash.birthDay, roster);
    assertTrue(hit.birthdaySame);
    assertTrue(miss.birthdayClash);
    assertTrue(hit.score > miss.score);
    assertTrue(hit.score >= 40);
  }

  @Test
  public void suggestRanksRosterCardsAndIgnoresMembers() {
    List<Member> roster = new ArrayList<>();
    roster.add(alumni("Chioma Okafor", 3, 4));
    Member member = new Member();
    member.name = "Chioma Okafor";
    member.kind = Member.KIND_MEMBER;
    roster.add(member);
    roster.add(alumni("Tobi Ade", 12, 28));
    AlumniSheet.Row row = row("Chioma Ada Okafor", 3, 4);
    List<AlumniMatch.Suggestion> got = AlumniMatch.suggest(row, roster, 8);
    assertEquals(1, got.size());
    assertEquals("Chioma Okafor", got.get(0).roster.name);
    assertTrue(got.get(0).birthdaySame);
  }

  @Test
  public void contactsCsvPutsFullNameInFirstAndAlumnusInLast() {
    Member ada = alumni("Ada Okafor", 5, 31);
    ada.phone = "2348031234567";
    ada.email = "ada@x.com";
    Member pastor = alumni("Pastor Tobi Ade", 1, 2);
    pastor.desk = Member.DESK_PASTOR;
    pastor.phone = "08011111111";
    Member none = alumni("No Number", 6, 6);
    List<Member> people = new ArrayList<>();
    people.add(ada);
    people.add(pastor);
    people.add(none);
    String csv = AlumniContacts.csv(people);
    assertTrue(csv.startsWith("First Name,Last Name,Phone 1 - Type,Phone 1 - Value,E-mail 1 - Value,Notes"));
    assertTrue(csv.contains("Ada Okafor,FSFUI Alumnus,Mobile,08031234567,ada@x.com"));
    assertTrue(csv.contains("Pastor Tobi Ade,FSFUI Alumnus"));
    assertTrue(csv.contains("Previous pastor"));
    assertFalse(csv.contains("No Number"));
    assertEquals(2, AlumniContacts.withPhone(people));
    ada.contactSaved = true;
    String skipped = AlumniContacts.csv(people);
    assertFalse(skipped.contains("Ada Okafor"));
    assertTrue(skipped.contains("Pastor Tobi Ade"));
    assertEquals(1, AlumniContacts.withPhone(people));
    assertEquals(2, AlumniContacts.withPhone(people, true));
  }

  private static Member alumni(String name, int month, int day) {
    Member member = new Member();
    member.name = name;
    member.birthMonth = month;
    member.birthDay = day;
    member.kind = Member.KIND_ALUMNI;
    member.skipCaption = true;
    return member;
  }

  private static AlumniSheet.Row row(String name, int month, int day) {
    AlumniSheet.Row row = new AlumniSheet.Row();
    int space = name.indexOf(' ');
    if (space > 0) {
      row.firstName = name.substring(0, space);
      row.lastName = name.substring(space + 1);
    } else {
      row.firstName = name;
    }
    row.birthMonth = month;
    row.birthDay = day;
    return row;
  }
}
