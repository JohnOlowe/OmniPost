package damjay.publicity.omnipost.scheduler;

import static org.junit.Assert.assertEquals;

import damjay.publicity.omnipost.data.entity.Task;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TaskSearchTest {
  @Test
  public void searchMatchesTitleOrDescriptionAndStaysOnThisList() {
    Task sunday = new Task();
    sunday.title = "Sunday Service";
    Task friday = new Task();
    friday.title = "Friday Prayer";
    friday.description = "fasting caption";
    List<Task> source = Arrays.asList(sunday, friday);
    List<Task> byTitle = TaskSearch.matches(source, "sun");
    assertEquals(1, byTitle.size());
    assertEquals("Sunday Service", byTitle.get(0).title);
    List<Task> byBody = TaskSearch.matches(source, "FAST");
    assertEquals(1, byBody.size());
    assertEquals("Friday Prayer", byBody.get(0).title);
    assertEquals(2, TaskSearch.matches(source, " ").size());
  }
}
