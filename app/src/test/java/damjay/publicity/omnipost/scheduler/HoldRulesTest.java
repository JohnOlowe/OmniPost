package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.data.entity.Series;
import damjay.publicity.omnipost.data.entity.Task;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class HoldRulesTest {
  @Test
  public void roundTripKeepsProgramAndTaskHolds() {
    Map<String, Long> holds = HoldRules.put(null, TaskTypes.WEDNESDAY_BIBLE_STUDY, 50_000L);
    holds = HoldRules.put(holds, HoldRules.taskKey(9L), 80_000L);
    Map<String, Long> got = HoldRules.parse(HoldRules.encode(holds));
    assertEquals(50_000L, got.get(TaskTypes.WEDNESDAY_BIBLE_STUDY).longValue());
    assertEquals(80_000L, got.get("t:9").longValue());
  }

  @Test
  public void quietUsesTaskHoldThenProgramHold() {
    Task task = weekly(TaskTypes.FRIDAY_PRAYER, 0L, 40_000L);
    task.id = 4L;
    Map<String, Long> holds = HoldRules.put(null, HoldRules.taskKey(4L), 70_000L);
    assertTrue(HoldRules.quiet(holds, task, 60_000L));
    assertFalse(HoldRules.quiet(holds, task, 70_000L));
    holds = HoldRules.put(null, TaskTypes.FRIDAY_PRAYER, 90_000L);
    assertTrue(HoldRules.quiet(holds, task, 80_000L));
    assertEquals(90_000L, HoldRules.programUntil(holds, task));
  }

  @Test
  public void skipMintDropsOccurrencesBeforeResumeMorning() {
    long resume = 100_000L;
    Map<String, Long> holds = HoldRules.put(null, TaskTypes.SUNDAY_SERVICE, resume);
    Task during = weekly(TaskTypes.SUNDAY_SERVICE, 0L, resume - 1L);
    Task after = weekly(TaskTypes.SUNDAY_SERVICE, 0L, resume);
    assertTrue(HoldRules.skipMint(holds, during));
    assertFalse(HoldRules.skipMint(holds, after));
    assertFalse(HoldRules.skipMint(holds, weekly(TaskTypes.FRIDAY_PRAYER, 0L, resume - 1L)));
  }

  @Test
  public void applyProgramsReplacesTheTickedSetAndKeepsSinglePosts() {
    Map<String, Long> current = HoldRules.put(null, TaskTypes.WEDNESDAY_BIBLE_STUDY, 10L);
    current = HoldRules.put(current, TaskTypes.FRIDAY_PRAYER, 10L);
    current = HoldRules.put(current, HoldRules.taskKey(3L), 99L);
    Map<String, Long> next = HoldRules.applyPrograms(
      current,
      Collections.singletonList(TaskTypes.SUNDAY_SERVICE),
      50L);
    assertFalse(next.containsKey(TaskTypes.WEDNESDAY_BIBLE_STUDY));
    assertFalse(next.containsKey(TaskTypes.FRIDAY_PRAYER));
    assertEquals(50L, next.get(TaskTypes.SUNDAY_SERVICE).longValue());
    assertEquals(99L, next.get("t:3").longValue());
  }

  @Test
  public void pruneDropsExpiredKeys() {
    Map<String, Long> holds = HoldRules.put(null, TaskTypes.FRIDAY_PRAYER, 10L);
    holds = HoldRules.put(holds, TaskTypes.WEDNESDAY_BIBLE_STUDY, 30L);
    Map<String, Long> live = HoldRules.prune(holds, 20L);
    assertFalse(live.containsKey(TaskTypes.FRIDAY_PRAYER));
    assertTrue(live.containsKey(TaskTypes.WEDNESDAY_BIBLE_STUDY));
  }

  @Test
  public void programsListBuiltinsThenSeries() {
    Series series = new Series();
    series.id = 12L;
    series.title = "Campus vigil";
    series.kind = Series.KIND_WEEKLY;
    List<HoldRules.Program> programs = HoldRules.programs(Collections.singletonList(series));
    assertEquals(HoldRules.BUILTIN_TYPES.length + 1, programs.size());
    assertEquals(TaskTypes.SUNDAY_SERVICE, programs.get(0).key);
    assertTrue(programs.get(0).label.contains("Saturday"));
    assertEquals("s:12", programs.get(programs.size() - 1).key);
    assertEquals("Campus vigil", programs.get(programs.size() - 1).label);
  }

  @Test
  public void liveStripsQuietTasks() {
    Task friday = weekly(TaskTypes.FRIDAY_PRAYER, 1L, 40_000L);
    Task fasting = weekly(TaskTypes.FASTING_DAY, 2L, 40_000L);
    Map<String, Long> holds = HoldRules.put(null, TaskTypes.FRIDAY_PRAYER, 80_000L);
    List<Task> live = HoldRules.live(java.util.Arrays.asList(friday, fasting), holds, 50_000L);
    assertEquals(1, live.size());
    assertEquals(fasting.id, live.get(0).id);
  }

  private static Task weekly(String type, long id, long postAt) {
    Task task = new Task();
    task.id = id;
    task.type = type;
    task.postAtMillis = postAt;
    task.status = TaskStatus.SCHEDULED;
    return task;
  }
}
