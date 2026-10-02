package damjay.publicity.omnipost.share;

import static org.junit.Assert.assertEquals;

import damjay.publicity.omnipost.data.entity.Task;
import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import damjay.publicity.omnipost.scheduler.TaskTypes;
import org.junit.Test;

public class WhatsAppPreviewTest {
  @Test
  public void previewDropsMarkersButKeepsWords() {
    assertEquals("NOTICE!", WhatsAppPreview.plain("*NOTICE!*"));
    assertEquals("Month of September", WhatsAppPreview.plain("*Month of September*"));
    assertEquals("No Cross, No Crown.", WhatsAppPreview.plain("*_No Cross, No Crown._*"));
  }

  @Test
  public void marksKeepInnerRangesSoBoxesCanStayRaw() {
    int[][] bold = WhatsAppPreview.marks("*NOTICE!*");
    assertEquals(1, bold.length);
    assertEquals(1, bold[0][0]);
    assertEquals(8, bold[0][1]);
    assertEquals(1, bold[0][2]);
    int[][] both = WhatsAppPreview.marks("*_Hi_*");
    assertEquals(1, both.length);
    assertEquals(2, both[0][0]);
    assertEquals(4, both[0][1]);
    assertEquals(3, both[0][2]);
  }

  @Test
  public void monthPlaceholderFillsBeforePreview() {
    Task notice = new Task();
    notice.type = TaskTypes.BIRTHDAY_NOTICE;
    notice.occurrenceKey = "BIRTHDAY_NOTICE|2026-09|EVE|2026-08-31";
    String filled = CaptionTemplates.apply("Celebrating *Month of {month}*", notice);
    assertEquals("Celebrating Month of September", WhatsAppPreview.plain(filled));
    assertEquals("Celebrating *Month of September*", filled);
  }
}
