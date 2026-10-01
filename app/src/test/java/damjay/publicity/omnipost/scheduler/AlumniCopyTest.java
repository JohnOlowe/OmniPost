package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class AlumniCopyTest {
  @Test
  public void sirMaAndNameAndAWordingTweak() {
    Member ada = person("Ada Okafor", Member.GENDER_FEMALE, 10, 2);
    Member tobi = person("Tobi Ade", Member.GENDER_MALE, 3, 4);
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    String adaDm = AlumniCopy.dm(ada, 10, oct);
    String tobiDm = AlumniCopy.dm(tobi, 10, oct);
    assertTrue(adaDm.contains("Ma Ada"));
    assertTrue(adaDm.contains("birthday month"));
    assertTrue(adaDm.contains("Olowe John"));
    assertTrue(adaDm.contains("picture"));
    assertTrue(tobiDm.contains("Sir Tobi"));
    assertTrue(tobiDm.contains("Alumni Relations Officer"));
    assertFalse(tobiDm.contains("birthday month"));
    assertFalse(adaDm.equals(tobiDm));
    assertFalse(adaDm.contains("Independence"));
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
