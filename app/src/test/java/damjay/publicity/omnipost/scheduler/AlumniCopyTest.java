package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class AlumniCopyTest {
  @Test
  public void happyNewMonthAndDetailsAreSeparateMessages() {
    Member ada = person("Ada Okafor", Member.GENDER_FEMALE, 10, 2);
    Member tobi = person("Tobi Ade", Member.GENDER_MALE, 3, 4);
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    String adaHnm = AlumniCopy.hnm(ada, 10, oct, null);
    String adaDetails = AlumniCopy.details(ada, 10, oct, null);
    String tobiHnm = AlumniCopy.hnm(tobi, 10, oct, null);
    assertTrue(adaHnm.contains("Ma Ada"));
    assertTrue(adaHnm.contains("Happy New Month"));
    assertFalse(adaHnm.toLowerCase().contains("picture"));
    assertTrue(adaDetails.contains("Ma Ada"));
    assertTrue(adaDetails.toLowerCase().contains("picture"));
    assertTrue(adaDetails.toLowerCase().contains("birth date")
      || adaDetails.toLowerCase().contains("name written"));
    assertTrue(tobiHnm.contains("Sir Tobi"));
    assertTrue(tobiHnm.contains("Alumni Relations Officer") || tobiHnm.contains("Olowe John"));
    assertFalse(tobiHnm.toLowerCase().contains("picture"));
    assertFalse(adaHnm.equals(tobiHnm));
    assertFalse(adaHnm.contains("Independence"));
    assertTrue(AlumniCopy.wantsHnm(ada, AlumniCopy.KIND_BIRTHDAY));
    assertTrue(AlumniCopy.wantsDetails(ada, AlumniCopy.KIND_BIRTHDAY, 10));
    assertTrue(AlumniCopy.wantsHnm(tobi, AlumniCopy.KIND_WAVE));
    assertFalse(AlumniCopy.wantsDetails(tobi, AlumniCopy.KIND_WAVE, 10));
  }

  @Test
  public void previousPastorsSkipHnmKeepDetails() {
    Member pastor = person("Pastor Ada", Member.GENDER_FEMALE, 10, 2);
    pastor.desk = Member.DESK_PASTOR;
    assertFalse(AlumniCopy.wantsHnm(pastor, AlumniCopy.KIND_BIRTHDAY));
    assertTrue(AlumniCopy.wantsDetails(pastor, AlumniCopy.KIND_BIRTHDAY, 10));
    assertFalse(AlumniCopy.wantsHnm(pastor, AlumniCopy.KIND_WAVE));
  }

  @Test
  public void groupGreetingAddsIndependenceOnlyInOctober() {
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    String october = AlumniCopy.group(10, oct);
    String november = AlumniCopy.group(11, oct);
    assertTrue(october.contains("Independence"));
    assertTrue(october.contains("Olowe John"));
    assertFalse(november.contains("Independence"));
  }

  @Test
  public void photoReminderStaysShort() {
    Member ada = person("Ada Okafor", Member.GENDER_FEMALE, 10, 2);
    String photo = AlumniCopy.photo(ada);
    assertTrue(photo.contains("Ma Ada"));
    assertTrue(photo.toLowerCase().contains("picture"));
    assertFalse(photo.contains("Independence"));
  }

  @Test
  public void customTemplateFillsWhoAndMonth() {
    Member ada = person("Ada Okafor", Member.GENDER_FEMALE, 10, 2);
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    String got = AlumniTemplates.fill("Hello {who}, welcome to {month}.", ada, 10, oct, null);
    assertTrue(got.contains("Hello Ma Ada, welcome to October."));
    assertFalse(got.contains("{who}"));
  }

  private static Member person(String name, String gender, int month, int day) {
    Member member = new Member();
    member.name = name;
    member.firstName = name.split(" ")[0];
    member.lastName = name.split(" ")[1];
    member.gender = gender;
    member.kind = Member.KIND_ALUMNI;
    member.birthMonth = month;
    member.birthDay = day;
    member.skipCaption = true;
    return member;
  }

  private static Calendar cal(int year, int month, int day) {
    TimeZone utc = TimeZone.getTimeZone("UTC");
    Calendar c = Calendar.getInstance(utc);
    c.clear();
    c.setTimeZone(utc);
    c.set(year, month, day, 8, 0, 0);
    return c;
  }
}
