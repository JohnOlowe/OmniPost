package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import damjay.publicity.omnipost.data.entity.Member;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Google Contacts CSV: First Name = full name, Last Name = FSFUI Alumnus.
 */
public final class AlumniContacts {
  public static final String LAST_NAME = "FSFUI Alumnus";

  private AlumniContacts() {}

  public static String csv(List<Member> members) {
    StringBuilder out = new StringBuilder();
    out.append("First Name,Last Name,Phone 1 - Type,Phone 1 - Value,E-mail 1 - Value,Notes\n");
    if (members == null) {
      return out.toString();
    }
    for (Member member : members) {
      if (member == null || !Member.isAlumni(member) || !AlumniDesk.hasPhone(member)) {
        continue;
      }
      String notes = Member.isPastor(member) ? "Previous pastor" : "";
      if (member.gradSet != null && !member.gradSet.isEmpty()) {
        notes = notes.isEmpty() ? member.gradSet : notes + " · " + member.gradSet;
      }
      out.append(csvCell(member.name))
        .append(',')
        .append(csvCell(LAST_NAME))
        .append(',')
        .append("Mobile,")
        .append(csvCell(AlumniDesk.displayPhone(member.phone)))
        .append(',')
        .append(csvCell(member.email))
        .append(',')
        .append(csvCell(notes))
        .append('\n');
    }
    return out.toString();
  }

  public static File writeCache(Context context, String csv) throws Exception {
    File dir = new File(context.getCacheDir(), "share");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new java.io.IOException("share");
    }
    File file = new File(dir, "FSFUI-alumni-contacts.csv");
    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
    try {
      out.write((csv == null ? "" : csv).getBytes(StandardCharsets.UTF_8));
    } finally {
      out.close();
    }
    return file;
  }

  public static int withPhone(List<Member> members) {
    int n = 0;
    if (members == null) {
      return 0;
    }
    for (Member member : members) {
      if (Member.isAlumni(member) && AlumniDesk.hasPhone(member)) {
        n++;
      }
    }
    return n;
  }

  static String csvCell(String value) {
    String v = value == null ? "" : value;
    if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }
}
