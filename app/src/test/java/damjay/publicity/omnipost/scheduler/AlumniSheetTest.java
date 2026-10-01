package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import org.junit.Test;

public class AlumniSheetTest {
  @Test
  public void readsBToIWhenAIsEmpty() {
    String csv = ",First Name,Last Name,Gender,Email address,Phone number (preferably Whatsapp contact),Birthday (Just month and day),Position held(if any),Graduation set\n"
      + ",Ada,Okafor,Female,ada@x.com,08031234567,May 31st,Prayer sec,2015\n"
      + ",Tobi,Ade,M,,2348011111111,28/12,,2024 set\n"
      + ",Bola,Kuti,lady,b@x.com,8012223333,8/15/2002,President,1999/2000\n";
    AlumniSheet.Result got = AlumniSheet.parse(csv);
    assertEquals(3, got.rows.size());
    AlumniSheet.Row ada = got.rows.get(0);
    assertEquals("Ada Okafor", ada.displayName());
    assertEquals(Member.GENDER_FEMALE, ada.gender);
    assertEquals("2348031234567", ada.phone);
    assertEquals(5, ada.birthMonth);
    assertEquals(31, ada.birthDay);
    assertEquals("2015", ada.gradSet);
    AlumniSheet.Row tobi = got.rows.get(1);
    assertEquals(Member.GENDER_MALE, tobi.gender);
    assertEquals("2348011111111", tobi.phone);
    assertEquals(12, tobi.birthMonth);
    assertEquals(28, tobi.birthDay);
    AlumniSheet.Row bola = got.rows.get(2);
    assertEquals(Member.GENDER_FEMALE, bola.gender);
    assertEquals("2348012223333", bola.phone);
    assertEquals(8, bola.birthMonth);
    assertEquals(15, bola.birthDay);
    assertEquals("1999/2000", bola.gradSet);
  }

  @Test
  public void birthdayFormats() {
    assertBirth("May 31st", 5, 31);
    assertBirth("31st May", 5, 31);
    assertBirth("28/12", 12, 28);
    assertBirth("09/06", 6, 9);
    assertBirth("06/06/72", 6, 6);
    assertBirth("8/15/2002", 8, 15);
    assertBirth("Sept. 4", 9, 4);
    assertBirth("4th of Sept", 9, 4);
    assertBirth("31st of May", 5, 31);
    assertBirth("born 12 May", 5, 12);
    assertBirth("09.06", 6, 9);
    assertBirth("12-08", 8, 12);
    assertBirth("10th September", 9, 10);
    assertBirth("21st of June", 6, 21);
    assertBirth("February 13", 2, 13);
    assertBirth("Aug 26", 8, 26);
    assertBirth("13th February", 2, 13);
    assertBirth("26 Aug", 8, 26);
    assertBirth("the 21st of June", 6, 21);
    assertBirth("September 10th, 1990", 9, 10);
    assertBirth("10 September", 9, 10);
  }

  @Test
  public void genderIsFlexible() {
    assertEquals(Member.GENDER_FEMALE, AlumniSheet.genderOf("Female"));
    assertEquals(Member.GENDER_FEMALE, AlumniSheet.genderOf("F"));
    assertEquals(Member.GENDER_FEMALE, AlumniSheet.genderOf("ma"));
    assertEquals(Member.GENDER_MALE, AlumniSheet.genderOf("Male"));
    assertEquals(Member.GENDER_MALE, AlumniSheet.genderOf("m"));
    assertEquals(Member.GENDER_MALE, AlumniSheet.genderOf("Sir"));
    assertEquals("", AlumniSheet.genderOf(""));
  }

  @Test
  public void rosterAndSheetNamesStayDistinctAfterPair() {
    Member member = new Member();
    member.name = "Adeola Olowe";
    member.firstName = "Ada";
    member.lastName = "Okafor";
    assertEquals("Ada Okafor", AlumniSheet.sheetName(member));
    assertTrue(AlumniSheet.namesDiffer(member));
    member.name = "Ada Okafor";
    assertFalse(AlumniSheet.namesDiffer(member));
    member.lastName = "";
    member.firstName = "";
    assertFalse(AlumniSheet.namesDiffer(member));
  }

  @Test
  public void nigeriaPhoneShapes() {
    assertEquals("2348031234567", AlumniDesk.nigeriaDigits("0803 123 4567"));
    assertEquals("2348031234567", AlumniDesk.nigeriaDigits("+2348031234567"));
    assertEquals("2348031234567", AlumniDesk.nigeriaDigits("8031234567"));
    assertEquals("2348031234567", AlumniDesk.nigeriaDigits("2348031234567"));
    assertEquals("08031234567", AlumniDesk.displayPhone("2348031234567"));
  }

  private static void assertBirth(String raw, int month, int day) {
    int[] got = AlumniSheet.birthdayOf(raw);
    assertNotNull(raw, got);
    assertEquals(raw, month, got[0]);
    assertEquals(raw, day, got[1]);
    assertTrue(got[0] >= 1 && got[0] <= 12);
  }
}
