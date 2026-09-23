package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
}
