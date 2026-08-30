package damjay.publicity.omnipost.share;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

public final class WhatsAppRouter {
  public static final String PACKAGE_WHATSAPP = "com.whatsapp";
  public static final String PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b";

  private WhatsAppRouter() {}

  public static void copyToClipboard(Context context, String text) {
    ClipboardManager clipboard =
      (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText("OmniPost", text == null ? "" : text));
    }
  }

  /**
   * Copies the caption, then launches WhatsApp with an explicit package (never the system
   * share sheet). WhatsApp's own chat picker is the only UI that should appear.
   */
  public static boolean sendExplicit(Context context, String text) {
    copyToClipboard(context, text);
    Intent send = new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(Intent.EXTRA_TEXT, text);
    send.putExtra("sms_body", text);
    send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (launch(context, send, PACKAGE_WHATSAPP)) {
      return true;
    }
    return launch(context, send, PACKAGE_WHATSAPP_BUSINESS);
  }

  private static boolean launch(Context context, Intent template, String pkg) {
    Intent intent = new Intent(template);
    intent.setPackage(pkg);
    try {
      context.startActivity(intent);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
