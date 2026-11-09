package os.aios.speech;

import android.text.Html;
import android.text.Spanned;

/** Offline, escaped Markdown presentation. No WebView or executable content. */
public final class MinutesMarkdown {
 public static Spanned render(String source) {
  StringBuilder out=new StringBuilder(); boolean code=false;
  for(String line:source.split("\n",-1)) {
   if(line.trim().startsWith("```")){code=!code;continue;}
   String safe=Html.escapeHtml(line);
   if(code){out.append("<tt>").append(safe).append("</tt><br>");continue;}
   safe=safe.replaceAll("`([^`]+)`","<tt>$1</tt>")
     .replaceAll("\\*\\*(.+?)\\*\\*","<b>$1</b>")
     .replaceAll("__(.+?)__","<b>$1</b>")
     .replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)","<i>$1</i>")
     .replaceAll("\\[([^]]+)\\]\\((https?://[^ )]+)\\)","$1 ($2)");
   if(line.matches("^#{1,6} .*")) {
    int level=Math.min(4,line.indexOf(' ')+1);
    out.append("<h").append(level).append(">").append(safe.replaceFirst("^#+ +","")).append("</h").append(level).append(">");
   } else if(line.matches("^\\s*[-*+] +.*")) {
    out.append("&#8226; ").append(safe.replaceFirst("^\\s*[-*+] +","")).append("<br>");
   } else if(line.startsWith("> ")) {
    out.append("<blockquote>").append(safe.replaceFirst("^&gt; +","")).append("</blockquote>");
   } else if(line.matches("^\\s*[-*_]{3,}\\s*$")||line.matches("^\\|?[ :|-]+\\|[ :|-]*$")) {
    out.append("<br>");
   } else out.append(safe).append("<br>");
  }
  return Html.fromHtml(out.toString(),Html.FROM_HTML_MODE_COMPACT);
 }
}
