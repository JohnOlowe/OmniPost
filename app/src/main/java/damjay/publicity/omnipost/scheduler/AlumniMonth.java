package damjay.publicity.omnipost.scheduler;

import damjay.publicity.omnipost.data.entity.AlumniSend;
import damjay.publicity.omnipost.data.entity.Member;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Who gets DMs this month: birthday people (always) plus half the rest. */
public final class AlumniMonth {
  private AlumniMonth() {}

  public static int yearMonth(Calendar now) {
    Calendar c = now == null ? Calendar.getInstance() : now;
    return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1;
  }

  public static int monthOf(int yearMonth) {
    int month = yearMonth % 100;
    if (month < 1) {
      return 1;
    }
    if (month > 12) {
      return 12;
    }
    return month;
  }

  public static List<Member> birthdayPeople(List<Member> all, int month1to12) {
    List<Member> out = new ArrayList<>();
    if (all == null) {
      return out;
    }
    for (Member member : all) {
      if (Member.isAlumni(member) && member.birthMonth == month1to12) {
        out.add(member);
      }
    }
    return out;
  }

  public static List<Member> wavePeople(List<Member> all, int month1to12) {
    List<Member> out = new ArrayList<>();
    if (all == null) {
      return out;
    }
    for (Member member : all) {
      if (AlumniDesk.inWave(member, month1to12) && member.birthMonth != month1to12) {
        out.add(member);
      }
    }
    return out;
  }

  public static boolean sent(List<AlumniSend> sends, long memberId, String kind) {
    if (sends == null) {
      return false;
    }
    for (AlumniSend send : sends) {
      if (send != null && send.memberId == memberId && kind.equals(send.kind)) {
        return true;
      }
    }
    return false;
  }

  public static Set<Long> sentIds(List<AlumniSend> sends, String kind) {
    Set<Long> out = new HashSet<>();
    if (sends == null) {
      return out;
    }
    for (AlumniSend send : sends) {
      if (send != null && kind.equals(send.kind)) {
        out.add(send.memberId);
      }
    }
    return out;
  }

  /** People still owed at least one DM this month. */
  public static int peopleLeft(
    List<Member> birthday, List<Member> wave, List<AlumniSend> sends) {
    int n = 0;
    if (birthday != null) {
      for (Member member : birthday) {
        if (owesBirthday(member, sends)) {
          n++;
        }
      }
    }
    if (wave != null) {
      for (Member member : wave) {
        if (!sent(sends, member.id, AlumniSend.HNM)) {
          n++;
        }
      }
    }
    return n;
  }

  public static boolean owesBirthday(Member member, List<AlumniSend> sends) {
    if (member == null) {
      return false;
    }
    if (!Member.isPastor(member) && !sent(sends, member.id, AlumniSend.HNM)) {
      return true;
    }
    return !sent(sends, member.id, AlumniSend.DETAILS);
  }

  public static List<Member> pendingBirthday(List<Member> birthday, List<AlumniSend> sends) {
    List<Member> out = new ArrayList<>();
    if (birthday == null) {
      return out;
    }
    for (Member member : birthday) {
      if (owesBirthday(member, sends)) {
        out.add(member);
      }
    }
    return out;
  }

  public static List<Member> sentBirthday(List<Member> birthday, List<AlumniSend> sends) {
    List<Member> out = new ArrayList<>();
    if (birthday == null) {
      return out;
    }
    for (Member member : birthday) {
      if (member != null && !owesBirthday(member, sends)) {
        out.add(member);
      }
    }
    return out;
  }

  public static List<Member> pendingWave(List<Member> wave, List<AlumniSend> sends) {
    List<Member> out = new ArrayList<>();
    if (wave == null) {
      return out;
    }
    for (Member member : wave) {
      if (!sent(sends, member.id, AlumniSend.HNM)) {
        out.add(member);
      }
    }
    return out;
  }

  public static List<Member> sentWave(List<Member> wave, List<AlumniSend> sends) {
    List<Member> out = new ArrayList<>();
    if (wave == null) {
      return out;
    }
    for (Member member : wave) {
      if (sent(sends, member.id, AlumniSend.HNM)) {
        out.add(member);
      }
    }
    return out;
  }
}
