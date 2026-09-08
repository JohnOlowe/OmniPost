package damjay.publicity.omnipost.util;

import android.content.Context;
import damjay.publicity.omnipost.data.AppDatabase;
import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.notify.NotificationHelper;
import damjay.publicity.omnipost.scheduler.AlarmScheduler;
import damjay.publicity.omnipost.scheduler.DateUtils;
import damjay.publicity.omnipost.scheduler.ScheduleCoordinator;
import damjay.publicity.omnipost.scheduler.TaskStatus;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import damjay.publicity.omnipost.share.InstagramStyle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** File copy of the desk so uninstall does not wipe settings, templates, and drafts. */
public final class DeskBackup {
  public static final String FORMAT = "omnipost-backup";
  public static final int VERSION = 1;

  private DeskBackup() {}

  public static final class Snapshot {
    public String alertMode = Prefs.MODE_ESCALATE;
    public int nagMinutes = 5;
    public int warningMinutes = 30;
    public int draftHour = 20;
    public boolean seedCaptions;
    public boolean deskOngoing = true;
    public boolean fullScreen = true;
    public String igBold = InstagramStyle.FACE_SANS;
    public String igItalic = InstagramStyle.FACE_SERIF;
    public String igBoth = InstagramStyle.FACE_SANS;
    public final List<Series> series = new ArrayList<>();
    public final List<CaptionVar> vars = new ArrayList<>();
    public final List<Member> members = new ArrayList<>();
    public final List<Draft> drafts = new ArrayList<>();
    public final List<Task> customTasks = new ArrayList<>();
  }

  public static String fileName() {
    return "OmniPost-desk-" + DateUtils.dayKey(Calendar.getInstance()) + ".json";
  }

  public static Snapshot collect(Context context) {
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    Snapshot snap = new Snapshot();
    snap.alertMode = Prefs.alertMode(app);
    snap.nagMinutes = Prefs.nagMinutes(app);
    snap.warningMinutes = Prefs.warningMinutes(app);
    snap.draftHour = Prefs.draftHour(app);
    snap.seedCaptions = Prefs.seedCaptions(app);
    snap.deskOngoing = Prefs.deskOngoing(app);
    snap.fullScreen = Prefs.fullScreen(app);
    snap.igBold = Prefs.instagramBoldFace(app);
    snap.igItalic = Prefs.instagramItalicFace(app);
    snap.igBoth = Prefs.instagramBothFace(app);
    addAll(snap.series, db.seriesDao().getAllSync());
    addAll(snap.vars, db.captionVarDao().getAllSync());
    addAll(snap.members, db.memberDao().getAllSync());
    addAll(snap.drafts, db.draftDao().getAllSync());
    List<Task> custom = db.taskDao().getCustomSync();
    if (custom != null) {
      for (Task task : custom) {
        if (task != null && !TaskStatus.POSTED.equals(task.status)) {
          snap.customTasks.add(task);
        }
      }
    }
    return snap;
  }

  public static void apply(Context context, Snapshot snap) {
    if (snap == null) {
      throw new IllegalArgumentException("backup");
    }
    Context app = context.getApplicationContext();
    AppDatabase db = AppDatabase.get(app);
    List<Task> active = db.taskDao().getActiveSync();
    if (active != null) {
      for (Task task : active) {
        if (task == null) {
          continue;
        }
        AlarmScheduler.cancelTask(app, task.id);
        NotificationHelper.hush(app, task.id);
      }
    }
    db.runInTransaction(() -> replaceAll(db, snap));
    Prefs.setAlertMode(app, normalizeMode(snap.alertMode));
    Prefs.setNagMinutes(app, clamp(snap.nagMinutes, 1, 60, 5));
    Prefs.setWarningMinutes(app, clamp(snap.warningMinutes, 1, 180, 30));
    Prefs.setDraftHour(app, clamp(snap.draftHour, 0, 23, 20));
    Prefs.setSeedCaptions(app, snap.seedCaptions);
    Prefs.setDeskOngoing(app, snap.deskOngoing);
    Prefs.setFullScreen(app, snap.fullScreen);
    Prefs.setInstagramBoldFace(app, snap.igBold);
    Prefs.setInstagramItalicFace(app, snap.igItalic);
    Prefs.setInstagramBothFace(app, snap.igBoth);
    Prefs.setSeriesDefaultsInstalled(app, true);
    ScheduleCoordinator.bootstrap(app);
  }

  public static void write(OutputStream out, Snapshot snap) throws IOException {
    if (out == null) {
      throw new IOException("backup");
    }
    out.write(encode(snap).getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  public static Snapshot read(InputStream in) throws IOException {
    if (in == null) {
      throw new IOException("backup");
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    int n;
    while ((n = in.read(chunk)) >= 0) {
      buf.write(chunk, 0, n);
    }
    return decode(new String(buf.toByteArray(), StandardCharsets.UTF_8));
  }

  public static String encode(Snapshot snap) {
    Snapshot src = snap == null ? new Snapshot() : snap;
    try {
      JSONObject root = new JSONObject();
      root.put("format", FORMAT);
      root.put("version", VERSION);
      root.put("exportedAt", System.currentTimeMillis());
      JSONObject prefs = new JSONObject();
      prefs.put("alertMode", src.alertMode == null ? Prefs.MODE_ESCALATE : src.alertMode);
      prefs.put("nagMinutes", src.nagMinutes);
      prefs.put("warningMinutes", src.warningMinutes);
      prefs.put("draftHour", src.draftHour);
      prefs.put("seedCaptions", src.seedCaptions);
      prefs.put("deskOngoing", src.deskOngoing);
      prefs.put("fullScreen", src.fullScreen);
      prefs.put("igBold", src.igBold == null ? InstagramStyle.FACE_SANS : src.igBold);
      prefs.put("igItalic", src.igItalic == null ? InstagramStyle.FACE_SERIF : src.igItalic);
      prefs.put("igBoth", src.igBoth == null ? InstagramStyle.FACE_SANS : src.igBoth);
      root.put("prefs", prefs);
      root.put("series", encodeSeries(src.series));
      root.put("vars", encodeVars(src.vars));
      root.put("members", encodeMembers(src.members));
      root.put("drafts", encodeDrafts(src.drafts));
      root.put("customTasks", encodeTasks(src.customTasks));
      return root.toString();
    } catch (JSONException e) {
      throw new IllegalStateException("backup", e);
    }
  }

  public static Snapshot decode(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("backup");
    }
    try {
      JSONObject root = new JSONObject(json);
      if (!FORMAT.equals(root.optString("format"))) {
        throw new IllegalArgumentException("backup");
      }
      if (root.optInt("version", 0) < 1) {
        throw new IllegalArgumentException("backup");
      }
      Snapshot snap = new Snapshot();
      JSONObject prefs = root.optJSONObject("prefs");
      if (prefs != null) {
        snap.alertMode = prefs.optString("alertMode", Prefs.MODE_ESCALATE);
        snap.nagMinutes = prefs.optInt("nagMinutes", 5);
        snap.warningMinutes = prefs.optInt("warningMinutes", 30);
        snap.draftHour = prefs.optInt("draftHour", 20);
        snap.seedCaptions = prefs.optBoolean("seedCaptions", false);
        snap.deskOngoing = prefs.optBoolean("deskOngoing", true);
        snap.fullScreen = prefs.optBoolean("fullScreen", true);
        snap.igBold = InstagramStyle.Faces.normalize(
          prefs.optString("igBold", InstagramStyle.FACE_SANS), InstagramStyle.FACE_SANS);
        snap.igItalic = InstagramStyle.Faces.normalize(
          prefs.optString("igItalic", InstagramStyle.FACE_SERIF), InstagramStyle.FACE_SERIF);
        snap.igBoth = InstagramStyle.Faces.normalize(
          prefs.optString("igBoth", InstagramStyle.FACE_SANS), InstagramStyle.FACE_SANS);
      }
      decodeSeries(arr(root, "series"), snap.series);
      decodeVars(arr(root, "vars"), snap.vars);
      decodeMembers(arr(root, "members"), snap.members);
      decodeDrafts(arr(root, "drafts"), snap.drafts);
      decodeTasks(arr(root, "customTasks"), snap.customTasks);
      return snap;
    } catch (JSONException e) {
      throw new IllegalArgumentException("backup", e);
    }
  }

  private static void replaceAll(AppDatabase db, Snapshot snap) {
    db.taskDao().deleteAll();
    db.draftDao().deleteAll();
    db.seriesDao().deleteAll();
    db.memberDao().deleteAll();
    db.captionVarDao().deleteAll();
    for (Series series : snap.series) {
      if (series == null) {
        continue;
      }
      series.id = 0L;
      db.seriesDao().insert(series);
    }
    java.util.HashSet<String> names = new java.util.HashSet<>();
    for (CaptionVar item : snap.vars) {
      if (item == null || item.name == null || item.name.trim().isEmpty()) {
        continue;
      }
      item.name = item.name.trim();
      if (!names.add(item.name)) {
        continue;
      }
      item.id = 0L;
      db.captionVarDao().insert(item);
    }
    for (Member member : snap.members) {
      if (member == null) {
        continue;
      }
      member.id = 0L;
      db.memberDao().insert(member);
    }
    for (Draft draft : snap.drafts) {
      if (draft == null) {
        continue;
      }
      draft.id = 0L;
      draft.taskId = 0L;
      db.draftDao().insert(draft);
    }
    for (Task task : snap.customTasks) {
      if (task == null || !TaskTypes.isCustom(task.type) || TaskTypes.TEST.equals(task.type)) {
        continue;
      }
      task.id = 0L;
      task.memberId = 0L;
      task.seriesId = 0L;
      task.linkedDraftId = 0L;
      db.taskDao().insert(task);
    }
  }

  private static JSONArray encodeSeries(List<Series> list) throws JSONException {
    JSONArray out = new JSONArray();
    if (list == null) {
      return out;
    }
    for (Series series : list) {
      if (series == null) {
        continue;
      }
      JSONObject o = new JSONObject();
      o.put("title", nz(series.title));
      o.put("kind", nz(series.kind));
      o.put("caption", nz(series.caption));
      o.put("eventAtMillis", series.eventAtMillis);
      o.put("endAtMillis", series.endAtMillis);
      o.put("vars", nz(series.vars));
      o.put("postHour", series.postHour);
      o.put("postMinute", series.postMinute);
      o.put("weekdays", series.weekdays);
      o.put("lastOfPrevMonth", series.lastOfPrevMonth);
      o.put("tenth", series.tenth);
      o.put("twentieth", series.twentieth);
      o.put("enabled", series.enabled);
      o.put("seedKey", nz(series.seedKey));
      out.put(o);
    }
    return out;
  }

  private static JSONArray encodeVars(List<CaptionVar> list) throws JSONException {
    JSONArray out = new JSONArray();
    if (list == null) {
      return out;
    }
    for (CaptionVar item : list) {
      if (item == null) {
        continue;
      }
      JSONObject o = new JSONObject();
      o.put("name", nz(item.name));
      o.put("label", nz(item.label));
      o.put("value", nz(item.value));
      out.put(o);
    }
    return out;
  }

  private static JSONArray encodeMembers(List<Member> list) throws JSONException {
    JSONArray out = new JSONArray();
    if (list == null) {
      return out;
    }
    for (Member member : list) {
      if (member == null) {
        continue;
      }
      JSONObject o = new JSONObject();
      o.put("name", nz(member.name));
      o.put("birthMonth", member.birthMonth);
      o.put("birthDay", member.birthDay);
      o.put("notes", nz(member.notes));
      out.put(o);
    }
    return out;
  }

  private static JSONArray encodeDrafts(List<Draft> list) throws JSONException {
    JSONArray out = new JSONArray();
    if (list == null) {
      return out;
    }
    for (Draft draft : list) {
      if (draft == null) {
        continue;
      }
      JSONObject o = new JSONObject();
      o.put("title", nz(draft.title));
      o.put("variantA", nz(draft.variantA));
      o.put("variantB", nz(draft.variantB));
      o.put("finalizedText", nz(draft.finalizedText));
      o.put("updatedAt", draft.updatedAt);
      out.put(o);
    }
    return out;
  }

  private static JSONArray encodeTasks(List<Task> list) throws JSONException {
    JSONArray out = new JSONArray();
    if (list == null) {
      return out;
    }
    for (Task task : list) {
      if (task == null) {
        continue;
      }
      JSONObject o = new JSONObject();
      o.put("type", nz(task.type));
      o.put("title", nz(task.title));
      o.put("description", nz(task.description));
      o.put("draftAtMillis", task.draftAtMillis);
      o.put("postAtMillis", task.postAtMillis);
      o.put("status", nz(task.status));
      o.put("occurrenceKey", nz(task.occurrenceKey));
      o.put("postedAtMillis", task.postedAtMillis);
      o.put("snoozeUntilMillis", task.snoozeUntilMillis);
      o.put("captionSavedAt", task.captionSavedAt);
      o.put("timesLocked", task.timesLocked);
      out.put(o);
    }
    return out;
  }

  private static void decodeSeries(JSONArray arr, List<Series> out) {
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o == null) {
        continue;
      }
      Series series = new Series();
      series.title = str(o, "title");
      series.kind = str(o, "kind");
      if (series.kind.isEmpty()) {
        series.kind = Series.KIND_COUNTDOWN;
      }
      series.caption = str(o, "caption");
      series.eventAtMillis = o.optLong("eventAtMillis", 0L);
      series.endAtMillis = o.optLong("endAtMillis", 0L);
      series.vars = str(o, "vars");
      series.postHour = o.optInt("postHour", 7);
      series.postMinute = o.optInt("postMinute", 0);
      series.weekdays = o.optInt("weekdays", 0);
      series.lastOfPrevMonth = o.optBoolean("lastOfPrevMonth", true);
      series.tenth = o.optBoolean("tenth", true);
      series.twentieth = o.optBoolean("twentieth", true);
      series.enabled = o.optBoolean("enabled", true);
      series.seedKey = str(o, "seedKey");
      out.add(series);
    }
  }

  private static void decodeVars(JSONArray arr, List<CaptionVar> out) {
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o == null) {
        continue;
      }
      CaptionVar item = new CaptionVar();
      item.name = str(o, "name");
      item.label = str(o, "label");
      item.value = str(o, "value");
      out.add(item);
    }
  }

  private static void decodeMembers(JSONArray arr, List<Member> out) {
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o == null) {
        continue;
      }
      Member member = new Member();
      member.name = str(o, "name");
      member.birthMonth = o.optInt("birthMonth", 0);
      member.birthDay = o.optInt("birthDay", 0);
      member.notes = str(o, "notes");
      out.add(member);
    }
  }

  private static void decodeDrafts(JSONArray arr, List<Draft> out) {
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o == null) {
        continue;
      }
      Draft draft = new Draft();
      draft.title = str(o, "title");
      draft.variantA = str(o, "variantA");
      draft.variantB = str(o, "variantB");
      draft.finalizedText = str(o, "finalizedText");
      draft.updatedAt = o.optLong("updatedAt", 0L);
      out.add(draft);
    }
  }

  private static void decodeTasks(JSONArray arr, List<Task> out) {
    for (int i = 0; i < arr.length(); i++) {
      JSONObject o = arr.optJSONObject(i);
      if (o == null) {
        continue;
      }
      Task task = new Task();
      task.type = str(o, "type");
      task.title = str(o, "title");
      task.description = str(o, "description");
      task.draftAtMillis = o.optLong("draftAtMillis", 0L);
      task.postAtMillis = o.optLong("postAtMillis", 0L);
      task.status = str(o, "status");
      if (task.status.isEmpty()) {
        task.status = TaskStatus.SCHEDULED;
      }
      task.occurrenceKey = str(o, "occurrenceKey");
      task.postedAtMillis = o.optLong("postedAtMillis", 0L);
      task.snoozeUntilMillis = o.optLong("snoozeUntilMillis", 0L);
      task.captionSavedAt = o.optLong("captionSavedAt", 0L);
      task.timesLocked = o.optBoolean("timesLocked", false);
      out.add(task);
    }
  }

  private static JSONArray arr(JSONObject root, String key) {
    JSONArray arr = root.optJSONArray(key);
    return arr == null ? new JSONArray() : arr;
  }

  private static String str(JSONObject o, String key) {
    if (!o.has(key) || o.isNull(key)) {
      return "";
    }
    String value = o.optString(key, "");
    return value == null || "null".equals(value) ? "" : value;
  }

  private static String nz(String value) {
    return value == null ? "" : value;
  }

  private static String normalizeMode(String mode) {
    if (Prefs.MODE_BOTH.equals(mode)
      || Prefs.MODE_SOUND.equals(mode)
      || Prefs.MODE_VIBRATE.equals(mode)
      || Prefs.MODE_ESCALATE.equals(mode)) {
      return mode;
    }
    return Prefs.MODE_ESCALATE;
  }

  private static int clamp(int value, int min, int max, int fallback) {
    if (value < min || value > max) {
      return fallback;
    }
    return value;
  }

  private static <T> void addAll(List<T> dest, List<T> src) {
    if (src != null) {
      dest.addAll(src);
    }
  }
}
