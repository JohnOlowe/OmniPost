package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.AlumniSend;
import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class AlumniMonthTest {
  @Test
  public void yearMonthIsYearTimes100PlusMonth() {
    Calendar oct = cal(2026, Calendar.OCTOBER, 1);
    assertEquals(202610, AlumniMonth.yearMonth(oct));
    assertEquals(10, AlumniMonth.monthOf(202610));
  }

  @Test
  public void birthdayPeopleAlwaysTwoMessagesPastorsSkipHnm() {
    Member ada = alumni("Ada Okafor", 10);
    Member pastor = alumni("Pastor Ada", 10);
    pastor.desk = Member.DESK_PASTOR;
    Member waveOnly = alumni("Tobi Ade", 3);
    List<Member> all = new ArrayList<>();
    all.add(ada);
    all.add(pastor);
    all.add(waveOnly);
    List<Member> birthday = AlumniMonth.birthdayPeople(all, 10);
    assertEquals(2, birthday.size());
    assertTrue(AlumniMonth.owesBirthday(ada, new ArrayList<>()));
    assertTrue(AlumniMonth.owesBirthday(pastor, new ArrayList<>()));
    List<AlumniSend> sentHnm = new ArrayList<>();
    sentHnm.add(send(ada.id, AlumniSend.HNM));
    assertTrue(AlumniMonth.owesBirthday(ada, sentHnm));
    sentHnm.add(send(ada.id, AlumniSend.DETAILS));
    assertFalse(AlumniMonth.owesBirthday(ada, sentHnm));
    List<AlumniSend> pastorDetails = new ArrayList<>();
    pastorDetails.add(send(pastor.id, AlumniSend.DETAILS));
    assertFalse(AlumniMonth.owesBirthday(pastor, pastorDetails));
  }

  @Test
  public void waveExcludesBirthdayPeopleAndPastors() {
    List<Member> all = new ArrayList<>();
    Member birthday = alumni("Ada Okafor", 10);
    birthday.id = 1;
    Member pastor = alumni("Pastor Tobi", 3);
    pastor.id = 2;
    pastor.desk = Member.DESK_PASTOR;
    Member waveA = alumni("Chioma Bello", 4);
    waveA.id = 3;
    Member waveB = alumni("Emeka Bello", 5);
    waveB.id = 4;
    all.add(birthday);
    all.add(pastor);
    all.add(waveA);
    all.add(waveB);
    List<Member> wave = AlumniMonth.wavePeople(all, 10);
    for (Member member : wave) {
      assertFalse(Member.isPastor(member));
      assertTrue(member.birthMonth != 10);
      assertTrue(AlumniDesk.inWave(member, 10));
    }
    assertFalse(contains(wave, birthday.id));
    assertFalse(contains(wave, pastor.id));
  }

  @Test
  public void peopleLeftCountsBirthdayUntilBothMessagesAndWaveUntilHnm() {
    Member ada = alumni("Ada Okafor", 10);
    ada.id = 11;
    Member tobi = alumni("Tobi Ade", 2);
    tobi.id = 12;
    List<Member> birthday = new ArrayList<>();
    birthday.add(ada);
    List<Member> wave = new ArrayList<>();
    wave.add(tobi);
    List<AlumniSend> none = new ArrayList<>();
    assertEquals(2, AlumniMonth.peopleLeft(birthday, wave, none));
    List<AlumniSend> adaHnm = new ArrayList<>();
    adaHnm.add(send(ada.id, AlumniSend.HNM));
    assertEquals(2, AlumniMonth.peopleLeft(birthday, wave, adaHnm));
    adaHnm.add(send(ada.id, AlumniSend.DETAILS));
    assertEquals(1, AlumniMonth.peopleLeft(birthday, wave, adaHnm));
    adaHnm.add(send(tobi.id, AlumniSend.HNM));
    assertEquals(0, AlumniMonth.peopleLeft(birthday, wave, adaHnm));
  }

  @Test
  public void sentChipHidesPeopleWhoStillOweAMessage() {
    Member ada = alumni("Ada Okafor", 10);
    ada.id = 21;
    Member done = alumni("Chioma Bello", 10);
    done.id = 22;
    Member waveOpen = alumni("Tobi Ade", 2);
    waveOpen.id = 23;
    Member waveDone = alumni("Emeka Bello", 3);
    waveDone.id = 24;
    List<Member> birthday = new ArrayList<>();
    birthday.add(ada);
    birthday.add(done);
    List<Member> wave = new ArrayList<>();
    wave.add(waveOpen);
    wave.add(waveDone);
    List<AlumniSend> sends = new ArrayList<>();
    sends.add(send(ada.id, AlumniSend.HNM));
    sends.add(send(done.id, AlumniSend.HNM));
    sends.add(send(done.id, AlumniSend.DETAILS));
    sends.add(send(waveDone.id, AlumniSend.HNM));
    List<Member> toSendBday = AlumniMonth.pendingBirthday(birthday, sends);
    List<Member> sentBday = AlumniMonth.sentBirthday(birthday, sends);
    List<Member> toSendWave = AlumniMonth.pendingWave(wave, sends);
    List<Member> sentWave = AlumniMonth.sentWave(wave, sends);
    assertEquals(1, toSendBday.size());
    assertEquals(ada.id, toSendBday.get(0).id);
    assertEquals(1, sentBday.size());
    assertEquals(done.id, sentBday.get(0).id);
    assertEquals(1, toSendWave.size());
    assertEquals(waveOpen.id, toSendWave.get(0).id);
    assertEquals(1, sentWave.size());
    assertEquals(waveDone.id, sentWave.get(0).id);
  }

  private static boolean contains(List<Member> list, long id) {
    for (Member member : list) {
      if (member.id == id) {
        return true;
      }
    }
    return false;
  }

  private static AlumniSend send(long memberId, String kind) {
    AlumniSend send = new AlumniSend();
    send.memberId = memberId;
    send.kind = kind;
    send.yearMonth = 202610;
    return send;
  }

  @Test
  public void thisMonthSkipsPeopleWithoutANumber() {
    Member numbered = alumni("Ada Okafor", 10);
    numbered.id = 31;
    Member none = alumni("No Number Ada", 10);
    none.id = 32;
    none.phone = "";
    Member waveNone = alumni("Wave None", 3);
    waveNone.id = 33;
    waveNone.phone = "";
    List<Member> all = new ArrayList<>();
    all.add(numbered);
    all.add(none);
    all.add(waveNone);
    List<Member> birthday = AlumniMonth.birthdayPeople(all, 10);
    assertEquals(1, birthday.size());
    assertEquals(numbered.name, birthday.get(0).name);
    List<Member> wave = AlumniMonth.wavePeople(all, 10);
    for (Member member : wave) {
      assertTrue(AlumniDesk.hasPhone(member));
    }
    assertFalse(contains(wave, none.id));
    assertFalse(AlumniDesk.inWave(waveNone, 10));
  }

  @Test
  public void thisMonthSkipsNumbersNotOnWhatsApp() {
    Member numbered = alumni("Ada Okafor", 10);
    numbered.id = 41;
    Member noWa = alumni("No Wa Ada", 10);
    noWa.id = 42;
    noWa.notOnWhatsApp = true;
    Member waveNoWa = alumni("Wave No Wa", 3);
    waveNoWa.id = 43;
    waveNoWa.notOnWhatsApp = true;
    List<Member> all = new ArrayList<>();
    all.add(numbered);
    all.add(noWa);
    all.add(waveNoWa);
    List<Member> birthday = AlumniMonth.birthdayPeople(all, 10);
    assertEquals(1, birthday.size());
    assertEquals(numbered.name, birthday.get(0).name);
    assertFalse(AlumniDesk.canWhatsApp(noWa));
    assertFalse(AlumniDesk.inWave(waveNoWa, 10));
    List<Member> wave = AlumniMonth.wavePeople(all, 10);
    assertFalse(contains(wave, waveNoWa.id));
    for (Member member : wave) {
      assertTrue(AlumniDesk.canWhatsApp(member));
    }
  }

  private static Member alumni(String name, int month) {
    Member member = new Member();
    member.name = name;
    member.kind = Member.KIND_ALUMNI;
    member.birthMonth = month;
    member.birthDay = 2;
    member.phone = "2348031234567";
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
