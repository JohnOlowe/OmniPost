package damjay.publicity.omnipost.scheduler;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sheet rows that did not match a roster name yet. */
public final class AlumniPending {
  private static final String FILE = "alumni_pending.json";

  private AlumniPending() {}

  public static List<AlumniSheet.Row> load(Context context) {
    List<AlumniSheet.Row> out = new ArrayList<>();
    if (context == null) {
      return out;
    }
    File file = new File(context.getApplicationContext().getFilesDir(), FILE);
    if (!file.exists()) {
      return out;
    }
    try {
      byte[] bytes = read(file);
      JSONArray arr = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) {
          continue;
        }
        AlumniSheet.Row row = new AlumniSheet.Row();
        row.firstName = o.optString("firstName", "");
        row.lastName = o.optString("lastName", "");
        row.gender = o.optString("gender", "");
        row.email = o.optString("email", "");
        row.phone = o.optString("phone", "");
        row.position = o.optString("position", "");
        row.gradSet = o.optString("gradSet", "");
        row.birthMonth = o.optInt("birthMonth", 0);
        row.birthDay = o.optInt("birthDay", 0);
        if (!row.displayName().isEmpty()) {
          out.add(row);
        }
      }
    } catch (Exception ignored) {
      return out;
    }
    return out;
  }

  public static void save(Context context, List<AlumniSheet.Row> rows) {
    if (context == null) {
      return;
    }
    File file = new File(context.getApplicationContext().getFilesDir(), FILE);
    JSONArray arr = new JSONArray();
    if (rows != null) {
      for (AlumniSheet.Row row : rows) {
        if (row == null) {
          continue;
        }
        try {
          JSONObject o = new JSONObject();
          o.put("firstName", nz(row.firstName));
          o.put("lastName", nz(row.lastName));
          o.put("gender", nz(row.gender));
          o.put("email", nz(row.email));
          o.put("phone", nz(row.phone));
          o.put("position", nz(row.position));
          o.put("gradSet", nz(row.gradSet));
          o.put("birthMonth", row.birthMonth);
          o.put("birthDay", row.birthDay);
          arr.put(o);
        } catch (Exception ignored) {
          // skip
        }
      }
    }
    try {
      write(file, arr.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // leave previous file
    }
  }

  public static void clear(Context context) {
    save(context, new ArrayList<>());
  }

  private static String nz(String value) {
    return value == null ? "" : value;
  }

  private static byte[] read(File file) throws Exception {
    java.io.FileInputStream in = new java.io.FileInputStream(file);
    try {
      byte[] buf = new byte[(int) file.length()];
      int off = 0;
      while (off < buf.length) {
        int n = in.read(buf, off, buf.length - off);
        if (n < 0) {
          break;
        }
        off += n;
      }
      return buf;
    } finally {
      in.close();
    }
  }

  private static void write(File file, byte[] bytes) throws Exception {
    java.io.FileOutputStream out = new java.io.FileOutputStream(file);
    try {
      out.write(bytes);
    } finally {
      out.close();
    }
  }
}
