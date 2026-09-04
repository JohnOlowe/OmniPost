package damjay.publicity.omnipost.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import damjay.publicity.omnipost.data.entity.CaptionVar;
import damjay.publicity.omnipost.data.entity.Draft;
import damjay.publicity.omnipost.data.entity.Member;
import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.Test;

public class DeskBackupTest {
  @Test
  public void fileNameIsAJsonDeskCopy() {
    String name = DeskBackup.fileName();
    assertTrue(name.startsWith("OmniPost-desk-"));
    assertTrue(name.endsWith(".json"));
  }

  @Test
  public void roundTripKeepsCaptionsSettingsAndPeople() throws Exception {
    DeskBackup.Snapshot snap = new DeskBackup.Snapshot();
    snap.alertMode = Prefs.MODE_VIBRATE;
    snap.nagMinutes = 10;
    snap.warningMinutes = 15;
    snap.draftHour = 19;
    snap.seedCaptions = true;
    snap.deskOngoing = false;
    snap.fullScreen = true;

    Series series = new Series();
    series.title = "Beyond Limit";
    series.kind = Series.KIND_COUNTDOWN;
    series.caption = "*IT'S {Days}!*\n\nSee you at \"church\".\nLine 3";
    series.vars = "theme=Grace\nvenue=The Court";
    series.eventAtMillis = 1_725_000_000_000L;
    series.endAtMillis = 1_726_000_000_000L;
    series.postHour = 7;
    series.postMinute = 30;
    series.weekdays = 0;
    series.enabled = true;
    series.seedKey = "countdown";
    snap.series.add(series);

    CaptionVar var = new CaptionVar();
    var.name = "theme";
    var.label = "Theme";
    var.value = "Grace\nand peace";
    snap.vars.add(var);

    Member member = new Member();
    member.name = "Ada";
    member.birthMonth = 3;
    member.birthDay = 12;
    member.notes = "Peeps group";
    snap.members.add(member);

    Draft draft = new Draft();
    draft.title = "Sunday";
    draft.variantA = "*Come and worship*";
    draft.variantB = "_Come and worship_";
    draft.finalizedText = "Come and worship";
    draft.updatedAt = 99L;
    snap.drafts.add(draft);

    Task task = new Task();
    task.type = TaskTypes.ONE_OFF;
    task.title = "Flyer drop";
    task.description = "One-off";
    task.draftAtMillis = 10L;
    task.postAtMillis = 20L;
    task.status = "SCHEDULED";
    task.occurrenceKey = "ONE_OFF|20|1";
    task.timesLocked = true;
    task.captionSavedAt = 5L;
    snap.customTasks.add(task);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DeskBackup.write(out, snap);
    String json = out.toString("UTF-8");
    assertTrue(json.contains(DeskBackup.FORMAT));
    assertTrue(json.contains("{Days}"));
    assertFalse(json.contains("last_nag"));

    DeskBackup.Snapshot got = DeskBackup.read(new ByteArrayInputStream(out.toByteArray()));
    assertEquals(Prefs.MODE_VIBRATE, got.alertMode);
    assertEquals(10, got.nagMinutes);
    assertEquals(15, got.warningMinutes);
    assertEquals(19, got.draftHour);
    assertTrue(got.seedCaptions);
    assertFalse(got.deskOngoing);
    assertTrue(got.fullScreen);
    assertEquals(1, got.series.size());
    assertEquals("Beyond Limit", got.series.get(0).title);
    assertEquals(series.caption, got.series.get(0).caption);
    assertEquals("countdown", got.series.get(0).seedKey);
    assertEquals(7, got.series.get(0).postHour);
    assertEquals(30, got.series.get(0).postMinute);
    assertEquals("Grace\nand peace", got.vars.get(0).value);
    assertEquals("Ada", got.members.get(0).name);
    assertEquals(3, got.members.get(0).birthMonth);
    assertEquals("*Come and worship*", got.drafts.get(0).variantA);
    assertEquals(99L, got.drafts.get(0).updatedAt);
    assertEquals(TaskTypes.ONE_OFF, got.customTasks.get(0).type);
    assertEquals("Flyer drop", got.customTasks.get(0).title);
    assertEquals(5L, got.customTasks.get(0).captionSavedAt);
    assertTrue(got.customTasks.get(0).timesLocked);
  }

  @Test
  public void decodeRejectsGarbage() {
    assertRejected(null);
    assertRejected("");
    assertRejected("{");
    assertRejected("{}");
    assertRejected("{\"format\":\"nope\",\"version\":1}");
    assertRejected("{\"format\":\"omnipost-backup\",\"version\":0}");
  }

  private static void assertRejected(String json) {
    try {
      DeskBackup.decode(json);
      fail("expected reject for " + json);
    } catch (IllegalArgumentException expected) {
      assertTrue(true);
    }
  }
}
