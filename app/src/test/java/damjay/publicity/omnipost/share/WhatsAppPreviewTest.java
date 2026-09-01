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
  public void monthPlaceholderFillsBeforePreview() {
    Task notice = new Task();
    notice.type = TaskTypes.BIRTHDAY_NOTICE;
    notice.occurrenceKey = "BIRTHDAY_NOTICE|2026-09|EVE|2026-08-31";
    String filled = CaptionTemplates.apply("Celebrating *Month of {month}*", notice);
    assertEquals("Celebrating Month of September", WhatsAppPreview.plain(filled));
    assertEquals("Celebrating *Month of September*", filled);
  }
}
