package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class BirthdayHorizonTest {
  private static Calendar utc(int year, int month, int day) {
    Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    c.clear();
    c.setTimeZone(TimeZone.getTimeZone("UTC"));
    c.set(year, month, day, 12, 0, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c;
  }

  private static Member person(String name, int month, int day) {
    Member member = new Member();
    member.name = name;
    member.birthMonth = month;
    member.birthDay = day;
    return member;
  }

  @Test
  public void todayWeekMonthAndAllSliceTheRoster() {
    Calendar now = utc(2026, Calendar.AUGUST, 26);
    Member today = person("Bob", 8, 26);
    Member week = person("Ada", 8, 31);
    Member later = person("Cara", 9, 8);
    List<Member> all = Arrays.asList(today, week, later);

    assertTrue(BirthdayHorizon.inWindow(today, now, BirthdayHorizon.TODAY));
    assertFalse(BirthdayHorizon.inWindow(week, now, BirthdayHorizon.TODAY));
    assertTrue(BirthdayHorizon.inWindow(week, now, BirthdayHorizon.WEEK));
    assertFalse(BirthdayHorizon.inWindow(later, now, BirthdayHorizon.WEEK));
    assertTrue(BirthdayHorizon.inWindow(later, now, BirthdayHorizon.TWO_WEEKS));
    assertTrue(BirthdayHorizon.inWindow(today, now, BirthdayHorizon.MONTH));
    assertTrue(BirthdayHorizon.inWindow(week, now, BirthdayHorizon.MONTH));
    assertFalse(BirthdayHorizon.inWindow(later, now, BirthdayHorizon.MONTH));

    List<BirthdayHorizon.Section> todaySections =
      BirthdayHorizon.group(all, now, BirthdayHorizon.TODAY);
    assertEquals(1, todaySections.size());
    assertEquals("Today", todaySections.get(0).title);
    assertEquals("Bob", todaySections.get(0).members.get(0).name);

    List<BirthdayHorizon.Section> weekSections =
      BirthdayHorizon.group(all, now, BirthdayHorizon.WEEK);
    assertEquals(2, weekSections.size());
    assertEquals("Ada", weekSections.get(1).members.get(0).name);

    List<BirthdayHorizon.Section> everyone = BirthdayHorizon.group(all, now, BirthdayHorizon.ALL);
    assertEquals(2, everyone.size());
    assertEquals("August", everyone.get(0).title);
    assertEquals(2, everyone.get(0).members.size());
    assertEquals("September", everyone.get(1).title);
  }

  @Test
  public void todayStillCountsAfterTheMorningNag() {
    Calendar noon = utc(2026, Calendar.AUGUST, 26);
    noon.set(Calendar.HOUR_OF_DAY, 12);
    Member bob = person("Bob", 8, 26);
    assertTrue(BirthdayHorizon.inWindow(bob, noon, BirthdayHorizon.TODAY));
    assertEquals("Today", BirthdayHorizon.group(
      java.util.Collections.singletonList(bob), noon, BirthdayHorizon.TODAY).get(0).title);
  }

  @Test
  public void groupingAFullRosterKeepsWeekAndAll() {
    Calendar now = utc(2026, Calendar.AUGUST, 26);
    List<Member> roster = new ArrayList<>();
    for (int i = 0; i < 250; i++) {
      roster.add(person("P" + i, (i % 12) + 1, (i % 28) + 1));
    }
    List<BirthdayHorizon.Section> week =
      BirthdayHorizon.group(roster, now, BirthdayHorizon.WEEK);
    List<BirthdayHorizon.Section> everyone =
      BirthdayHorizon.group(roster, now, BirthdayHorizon.ALL);
    int weekPeople = 0;
    for (BirthdayHorizon.Section section : week) {
      weekPeople += section.members.size();
    }
    int allPeople = 0;
    for (BirthdayHorizon.Section section : everyone) {
      allPeople += section.members.size();
    }
    assertEquals(250, allPeople);
    assertTrue(weekPeople <= 250);
    assertTrue(weekPeople < allPeople);
  }

  @Test
  public void copyListFollowsTheHorizonAndKeepsDates() {
    Calendar now = utc(2026, Calendar.AUGUST, 26);
    Member today = person("Bob", 8, 26);
    Member week = person("Ada", 8, 31);
    Member later = person("Cara", 9, 8);
    List<Member> all = Arrays.asList(today, week, later);
    List<BirthdayHorizon.Section> month =
      BirthdayHorizon.group(all, now, BirthdayHorizon.MONTH);
    String text = BirthdayHorizon.copyList("Alumni · This month", month);
    assertTrue(text.startsWith("Alumni · This month"));
    assertTrue(text.contains("Bob"));
    assertTrue(text.contains("Ada"));
    assertFalse(text.contains("Cara"));
    assertEquals(2, BirthdayHorizon.copyCount(month));
    assertEquals("", BirthdayHorizon.copyList("x", Collections.emptyList()));
  }

  @Test
  public void searchStaysOnTheCurrentPeopleTab() {
    Member alum = person("Ada Okafor", 5, 31);
    alum.kind = Member.KIND_ALUMNI;
    alum.phone = "2348031234567";
    Member pastor = person("Pastor Ada", 6, 1);
    pastor.kind = Member.KIND_ALUMNI;
    pastor.desk = Member.DESK_PASTOR;
    pastor.phone = "2348011111111";
    Member none = person("Ada None", 8, 2);
    none.kind = Member.KIND_ALUMNI;
    Member fellow = person("Ada Fellow", 7, 2);
    List<Member> all = Arrays.asList(alum, pastor, none, fellow);
    List<Member> alumniHits = BirthdayHorizon.searchHits(all, "Ada", true, false, false);
    assertEquals(1, alumniHits.size());
    assertEquals("Ada Okafor", alumniHits.get(0).name);
    List<Member> pastorHits = BirthdayHorizon.searchHits(all, "Ada", true, true, false);
    assertEquals(1, pastorHits.size());
    assertEquals("Pastor Ada", pastorHits.get(0).name);
    List<Member> noNumber = BirthdayHorizon.searchHits(all, "Ada", true, false, true);
    assertEquals(1, noNumber.size());
    assertEquals("Ada None", noNumber.get(0).name);
    List<Member> memberHits = BirthdayHorizon.searchHits(all, "Ada", false, false);
    assertEquals(1, memberHits.size());
    assertEquals("Ada Fellow", memberHits.get(0).name);
  }

  @Test
  public void allPeopleIncludesThoseWithNoBirthday() {
    Calendar now = utc(2026, Calendar.OCTOBER, 1);
    Member dated = person("Ada Okafor", 5, 31);
    Member undated = person("Wave Pastor", 0, 0);
    undated.kind = Member.KIND_ALUMNI;
    List<BirthdayHorizon.Section> all =
      BirthdayHorizon.group(Arrays.asList(dated, undated), now, BirthdayHorizon.ALL);
    assertEquals(2, all.size());
    assertEquals("No date yet", all.get(0).title);
    assertEquals("Wave Pastor", all.get(0).members.get(0).name);
    List<BirthdayHorizon.Section> week =
      BirthdayHorizon.group(Arrays.asList(dated, undated), now, BirthdayHorizon.WEEK);
    for (BirthdayHorizon.Section section : week) {
      for (Member member : section.members) {
        assertFalse("Wave Pastor".equals(member.name));
      }
    }
  }

  @Test
  public void nameMatchesFirstLastPhoneAndHonorific() {
    Member member = person("Adeola Olowe", 10, 1);
    member.firstName = "Adeola";
    member.lastName = "Olowe";
    member.phone = "2348031234567";
    member.honorific = "Mr";
    assertTrue(BirthdayHorizon.nameMatches(member, "adeola"));
    assertTrue(BirthdayHorizon.nameMatches(member, "OLOWE"));
    assertTrue(BirthdayHorizon.nameMatches(member, "803"));
    assertTrue(BirthdayHorizon.nameMatches(member, "mr"));
    assertFalse(BirthdayHorizon.nameMatches(member, "Tobi"));
    assertTrue(BirthdayHorizon.nameMatches(member, "  "));
  }
}
