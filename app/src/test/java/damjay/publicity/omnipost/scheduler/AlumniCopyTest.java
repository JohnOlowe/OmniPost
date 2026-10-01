package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
    assertTrue(adaHnm.contains("Ma Ada Okafor"));
    assertTrue(adaHnm.contains("Happy New Month"));
    assertFalse(adaHnm.toLowerCase().contains("picture"));
    assertTrue(adaDetails.contains("Ma Ada Okafor"));
    assertTrue(adaDetails.toLowerCase().contains("picture"));
    assertTrue(adaDetails.toLowerCase().contains("birth date")
      || adaDetails.toLowerCase().contains("name written"));
    assertTrue(tobiHnm.contains("Mr Tobi Ade"));
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
    assertTrue(photo.contains("Ma Ada Okafor"));
    assertTrue(photo.toLowerCase().contains("picture"));
    assertFalse(photo.contains("Independence"));
  }

  @Test
  public void customTemplateFillsWhoAndMonth() {
    Member ada = person("Ada Okafor", Member.GENDER_FEMALE, 10, 2);
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    String got = AlumniTemplates.fill("Hello {who}, welcome to {month}.", ada, 10, oct, null);
    assertTrue(got.contains("Hello Ma Ada Okafor, welcome to October."));
    assertFalse(got.contains("{who}"));
  }

  @Test
  public void captionTokensFollowTheChosenRosterName() {
    Member member = person("Adeola Olowe", Member.GENDER_FEMALE, 10, 2);
    member.firstName = "Ada";
    member.lastName = "Okafor";
    assertEquals("Adeola", AlumniCopy.firstName(member));
    assertEquals("Olowe", AlumniCopy.lastName(member));
    assertEquals("Adeola Olowe", AlumniCopy.fullName(member));
    assertTrue(AlumniCopy.greetingName(member).contains("Adeola Olowe"));
    member.name = "Ada Okafor";
    assertEquals("Ada", AlumniCopy.firstName(member));
    assertEquals("Okafor", AlumniCopy.lastName(member));
  }

  @Test
  public void honorificOverrideUsesFullName() {
    Member ada = person("Adeola Olowe", Member.GENDER_FEMALE, 10, 2);
    ada.honorific = "Dr";
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    assertTrue(AlumniCopy.greetingName(ada).equals("Dr Adeola Olowe"));
    String hnm = AlumniCopy.hnm(ada, 10, oct, null);
    assertTrue(hnm.contains("Dr Adeola Olowe"));
    assertFalse(hnm.contains("Ma Adeola"));
  }

  @Test
  public void perPersonCaptionBeatsGeneralTemplate() {
    Member ada = person("Ada Okafor", Member.GENDER_FEMALE, 10, 2);
    ada.captionHnm = "Ping {who} only.";
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    assertTrue(AlumniCopy.hnm(ada, 10, oct, null).equals("Ping Ma Ada Okafor only."));
  }

  @Test
  public void addressStylesGenderAndNest() {
    Member ada = person("Ada Okwuoma", Member.GENDER_FEMALE, 10, 2);
    Member tobi = person("Tobi Ade", Member.GENDER_MALE, 3, 4);
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    assertEquals("ma, Ada", AlumniTemplates.fill("{dear}", ada, 10, oct, null));
    assertEquals("Mr Tobi", AlumniTemplates.fill("{dear}", tobi, 10, oct, null));
    assertEquals("ma, Ada Okwuoma", AlumniTemplates.fill("{formal}", ada, 10, oct, null));
    assertEquals("Mr Tobi Ade", AlumniTemplates.fill("{formal}", tobi, 10, oct, null));
    assertEquals("Ma Ada", AlumniTemplates.fill("{titled}", ada, 10, oct, null));
    assertEquals("Mr", AlumniTemplates.fill("{title_only}", tobi, 10, oct, null));
    assertEquals("ma,", AlumniTemplates.fill("{title_only}", ada, 10, oct, null));
    List<AlumniAddress> extra = new ArrayList<>(AlumniAddress.defaults());
    extra.add(new AlumniAddress("line", "Line", "Mr {first}", "new month to you {dear}"));
    assertEquals(
      "new month to you ma, Ada",
      AlumniTemplates.fill("{line}", ada, 10, oct, null, extra));
    ada.honorific = "Dr";
    assertEquals("Dr Ada", AlumniTemplates.fill("{title} {first}", ada, 10, oct, null));
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
