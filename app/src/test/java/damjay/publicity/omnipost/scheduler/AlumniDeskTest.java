package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AlumniDeskTest {
  @Test
  public void phoneNeedsSevenDigitsAndNeverTagsWithoutOne() {
    Member none = alumni("Ada", 8, 31);
    assertFalse(AlumniDesk.hasPhone(none));
    none.phone = "123";
    assertFalse(AlumniDesk.hasPhone(none));
    none.phone = "0803 123 4567";
    assertTrue(AlumniDesk.hasPhone(none));
    assertEquals("08031234567", AlumniDesk.digits(none.phone));
    none.phone = "+1 202 555 0100";
    assertTrue(AlumniDesk.hasPhone(none));
    assertEquals("12025550100", AlumniDesk.storePhone(none.phone));
    assertEquals("+1 2025550100", AlumniDesk.displayPhone(none.phone));
    none.phone = "1 (416) 555-0199";
    assertTrue(AlumniDesk.hasPhone(none));
    assertEquals("14165550199", AlumniDesk.whatsAppDigits(none.phone));
    none.phone = "+44 7700 900123";
    assertTrue(AlumniDesk.hasPhone(none));
    assertEquals("447700900123", AlumniDesk.storePhone(none.phone));
    assertEquals("+447700900123", AlumniDesk.displayPhone(none.phone));
    String brief = AlumniDesk.birthdayBrief(none);
    assertTrue(brief.contains("Tag them"));
    none.phone = "";
    assertTrue(AlumniDesk.birthdayBrief(none).contains("do not tag"));
  }

  @Test
  public void noPictureAndGotPictureChangeTheBirthdayBrief() {
    Member member = alumni("Ada", 8, 31);
    assertTrue(AlumniDesk.wantsPhoto(member));
    assertTrue(AlumniDesk.birthdayBrief(member).contains("NO PICTURE"));
    member.photoStatus = AlumniDesk.PHOTO_GOT;
    assertFalse(AlumniDesk.wantsPhoto(member));
    assertTrue(AlumniDesk.birthdayBrief(member).contains("Picture in"));
    member.photoStatus = AlumniDesk.PHOTO_NONE;
    assertFalse(AlumniDesk.wantsPhoto(member));
    assertTrue(AlumniDesk.birthdayBrief(member).contains("no-photo"));
  }

  @Test
  public void monthWaveSplitsTheRosterInHalf() {
    assertEquals(0, AlumniDesk.monthWave(1));
    assertEquals(1, AlumniDesk.monthWave(2));
    assertEquals(0, AlumniDesk.monthWave(9));
    List<Member> roster = AlumniRoster.members();
    int wave0 = 0;
    int wave1 = 0;
    for (Member member : roster) {
      if (AlumniDesk.personWave(member) == 0) {
        wave0++;
      } else {
        wave1++;
      }
    }
    assertEquals(222, wave0 + wave1);
    assertTrue(wave0 > 80);
    assertTrue(wave1 > 80);
  }

  @Test
  public void waveBriefDoesNotDumpEveryNamelessPerson() {
    List<Member> wave = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      wave.add(alumni("Person " + i, 1, 1));
    }
    Member numbered = alumni("Ada", 1, 1);
    numbered.phone = "08031234567";
    wave.add(numbered);
    String brief = AlumniDesk.waveBrief("October", wave);
    assertTrue(brief.contains("Alumni Relations Officer"));
    assertTrue(brief.contains("do not tag") || brief.contains("Do not tag"));
    assertTrue(brief.contains("Ada · 08031234567"));
    assertTrue(brief.contains("Open Birthdays"));
    assertFalse(brief.contains("Person 39"));
  }

  @Test
  public void previousPastorsSkipTheWaveKeepBirthdayLine() {
    Member pastor = alumni("Pastor Ada", 10, 2);
    pastor.desk = Member.DESK_PASTOR;
    pastor.phone = "08031234567";
    assertTrue(Member.isPastor(pastor));
    assertFalse(AlumniDesk.inWave(pastor, 10));
    assertFalse(AlumniDesk.inWave(pastor, 11));
    String line = AlumniDesk.rosterLine(pastor, 10);
    assertTrue(line.contains("pastor"));
    assertFalse(line.contains("wave"));
    assertEquals(AlumniCopy.KIND_BIRTHDAY, AlumniCopy.kindFor(pastor, 10));
    assertEquals(AlumniCopy.KIND_PHOTO, AlumniCopy.kindFor(pastor, 3));
  }

  @Test
  public void monthBriefListsEveryoneBornThatMonth() {
    Member ada = alumni("Ada", 10, 2);
    Member bob = alumni("Bob", 10, 9);
    ada.phone = "08031234567";
    List<Member> born = new ArrayList<>();
    born.add(ada);
    born.add(bob);
    String brief = AlumniDesk.monthBrief("October", born);
    assertTrue(brief.contains("birthday month"));
    assertTrue(brief.contains("Ada · 08031234567"));
    assertTrue(brief.contains("Bob"));
    assertTrue(brief.contains("Batches of 10"));
  }

  private static Member alumni(String name, int month, int day) {
    Member member = new Member();
    member.name = name;
    member.birthMonth = month;
    member.birthDay = day;
    member.kind = Member.KIND_ALUMNI;
    member.skipCaption = true;
    member.phone = "";
    member.photoStatus = "";
    return member;
  }
}
