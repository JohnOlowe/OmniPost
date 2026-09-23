package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Member;
import java.util.List;
import org.junit.Test;

public class AlumniRosterTest {
  @Test
  public void rosterHasMayFirstAndDecemberThirtyFirst() {
    List<Member> members = AlumniRoster.members();
    assertEquals(AlumniRoster.size(), members.size());
    assertEquals(222, members.size());
    boolean mayFirst = false;
    boolean decLast = false;
    for (Member member : members) {
      assertEquals(Member.KIND_ALUMNI, member.kind);
      assertTrue(member.skipCaption);
      if ("Adetunji Bakinson".equals(member.name)
        && member.birthMonth == 5
        && member.birthDay == 1) {
        mayFirst = true;
      }
      if ("Agunmaro Funmilayo".equals(member.name)
        && member.birthMonth == 12
        && member.birthDay == 31) {
        decLast = true;
      }
    }
    assertTrue(mayFirst);
    assertTrue(decLast);
  }
}
