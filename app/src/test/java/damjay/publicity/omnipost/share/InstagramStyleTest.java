package damjay.publicity.omnipost.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import damjay.publicity.omnipost.scheduler.CaptionTemplates;
import org.junit.Test;

public class InstagramStyleTest {
  @Test
  public void asterisksBecomeSansSerifBoldLetters() {
    assertEquals(cp(0x1D5D4), InstagramStyle.toUnicode("*A*"));
    assertEquals(cp(0x1D5EE), InstagramStyle.toUnicode("*a*"));
    assertEquals(cp(0x1D7F5), InstagramStyle.toUnicode("*9*"));
    assertEquals(
      InstagramStyle.style("MEET OUR GUEST MINISTER", true, false) + "!",
      InstagramStyle.toUnicode("*MEET OUR GUEST MINISTER!*"));
  }

  @Test
  public void serifBoldIsAvailableAsAnOption() {
    InstagramStyle.Faces serif =
      new InstagramStyle.Faces(
        InstagramStyle.FACE_SERIF, InstagramStyle.FACE_SERIF, InstagramStyle.FACE_SERIF);
    assertEquals(cp(0x1D400), InstagramStyle.toUnicode("*A*", serif));
    assertEquals(cp(0x1D468), InstagramStyle.toUnicode("*_A_*", serif));
  }

  @Test
  public void underscoresBecomeItalicLetters() {
    assertEquals(cp(0x1D434), InstagramStyle.toUnicode("_A_"));
    assertEquals("\u210E", InstagramStyle.toUnicode("_h_"));
  }

  @Test
  public void nestedMarkersBecomeSansSerifBoldItalic() {
    assertEquals(cp(0x1D63C), InstagramStyle.toUnicode("*_A_*"));
    assertEquals(cp(0x1D656), InstagramStyle.toUnicode("*_a_*"));
    String got = InstagramStyle.toUnicode("*_No Cross_*");
    assertEquals(InstagramStyle.style("No Cross", true, true), got);
    assertEquals(InstagramStyle.style("Both", true, true), InstagramStyle.toUnicode("_*Both*_"));
  }

  @Test
  public void markupRoundTripKeepsStarsAndUnderscores() {
    String[] samples = new String[] {
      "*MEET OUR GUEST MINISTER!*",
      "_See you there._",
      "*_No Cross, No Crown._*",
      "Hello *bold* and _italic_ mix."
    };
    for (String sample : samples) {
      assertEquals(sample, InstagramStyle.toMarkup(InstagramStyle.toUnicode(sample)));
    }
  }

  @Test
  public void toMarkupReadsSerifOrSansLetters() {
    InstagramStyle.Faces serif =
      new InstagramStyle.Faces(
        InstagramStyle.FACE_SERIF, InstagramStyle.FACE_SERIF, InstagramStyle.FACE_SERIF);
    assertEquals("*Hello*", InstagramStyle.toMarkup(InstagramStyle.toUnicode("*Hello*", serif)));
    assertEquals("*Hello*", InstagramStyle.toMarkup(InstagramStyle.toUnicode("*Hello*")));
    assertEquals("_Hi_", InstagramStyle.toMarkup(InstagramStyle.toUnicode("_Hi_")));
  }

  @Test
  public void countdownCaptionConvertsWithoutLeavingMarkup() {
    String converted = InstagramStyle.toUnicode(CaptionTemplates.countdownCaption(9));
    assertFalse(converted.contains("*"));
    assertFalse(converted.contains("_"));
    assertTrue(converted.startsWith(InstagramStyle.style("IT'S 9 DAYS TO GO!", true, false)));
    assertTrue(converted.contains("9th – 13th September, 2026"));
    assertTrue(converted.contains("Department/Level:"));
    assertTrue(converted.contains(InstagramStyle.style("Date:", true, false)));
    assertTrue(converted.contains(InstagramStyle.style("Pray, plan and prepare!", false, true)));
  }

  @Test
  public void birthdayNoticeConvertsBoldNotice() {
    String converted = InstagramStyle.toUnicode(CaptionTemplates.birthdayNoticeCaption("August"));
    assertTrue(converted.startsWith(InstagramStyle.style("NOTICE!", true, false)));
    assertTrue(converted.contains(InstagramStyle.style("Month of August", true, false)));
    assertTrue(converted.contains("wa.me/2349112413798"));
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }
}
