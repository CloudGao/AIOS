package os.aios.workspace;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native, offline Markdown presentation. Raw HTML is always literal text. */
public final class MarkdownView extends LinearLayout {
    public interface Actions {
        void toggle(int line);
        void open(String uri);
        View attachment(String id);
    }
    private static final Pattern CHECK=Pattern.compile("^(\\s*[-*+]\\s+)\\[([ xX])]\\s?(.*)$");
    private static final Pattern INLINE=Pattern.compile("(!?\\[([^]\\n]*)]\\(([^\\s]+?)\\))|(`([^`]+)`)|(\\*\\*(.+?)\\*\\*)|(~~(.+?)~~)|(\\*([^*]+)\\*)|(https?://[^\\s<>]+)");
    public static String toggleLine(String markdown,int line) {
        String[] lines=markdown.split("\n",-1);
        if(line<0||line>=lines.length)throw new IllegalArgumentException("Invalid checklist line");
        Matcher m=CHECK.matcher(lines[line]);if(!m.matches())throw new IllegalArgumentException("Not a checklist item");
        int mark=m.start(2);lines[line]=lines[line].substring(0,mark)+(m.group(2).equals(" ")?"x":" ")+lines[line].substring(mark+1);
        return String.join("\n",lines);
    }
    public MarkdownView(Context context,String markdown,Actions actions,boolean editable) {
        super(context);setOrientation(VERTICAL);
        String[] lines=markdown.split("\n",-1);boolean code=false;StringBuilder block=new StringBuilder();
        for(int i=0;i<lines.length;i++) {
            String line=lines[i];
            if(line.startsWith("```")) {
                if(code){addCode(block.toString());block.setLength(0);}code=!code;continue;
            }
            if(code){block.append(line).append('\n');continue;}
            Matcher check=CHECK.matcher(line);
            Matcher asset=Pattern.compile("^!?\\[[^]]*]\\(aios-attachment:([a-zA-Z0-9-]+)\\)$").matcher(line.trim());
            if(asset.matches()){addView(actions.attachment(asset.group(1)));continue;}
            if(check.matches()) {
                CheckBox box=os.aios.core.DisplayText.bind(new CheckBox(context));box.setButtonTintList(android.content.res.ColorStateList.valueOf(os.aios.core.AiosTheme.accent(getContext())));box.setText(inline(check.group(3),actions));box.setTextColor(os.aios.core.AiosTheme.text(getContext()));box.setTextSize(16);box.setChecked(!check.group(2).equals(" "));box.setEnabled(editable);
                final int index=i;box.setOnCheckedChangeListener((b,value)->actions.toggle(index));addView(box);continue;
            }
            TextView text=os.aios.core.DisplayText.bind(new TextView(context));text.setTextSize(16);text.setTextColor(os.aios.core.AiosTheme.text(getContext()));text.setLineSpacing(dp(3),1.16f);text.setPadding(0,dp(4),0,dp(4));text.setTextIsSelectable(true);
            Matcher heading=Pattern.compile("^(#{1,6})\\s+(.+)$").matcher(line);
            if(heading.matches()){text.setPadding(0,dp(14),0,dp(6));text.setTextSize(25-heading.group(1).length()*2);text.setTypeface(null,Typeface.BOLD);line=heading.group(2);}
            else if(line.startsWith("> ")){line="│  "+line.substring(2);text.setTextColor(os.aios.core.AiosTheme.muted(getContext()));}
            else if(line.matches("^\\s*[-*+] .*$"))line=line.replaceFirst("[-*+] ","•  ");
            if(line.matches("^(-{3,}|\\*{3,})$")){View rule=new View(context);rule.setBackgroundColor(os.aios.core.AiosTheme.outline(getContext()));addView(rule,new LayoutParams(-1,2));continue;}
            text.setText(inline(line,actions));addView(text);
        }
        if(code)addCode(block.toString());
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void addCode(String code) {
        TextView text=os.aios.core.DisplayText.bind(new TextView(getContext()));text.setText(code);text.setTextSize(14);text.setTypeface(Typeface.MONOSPACE);text.setBackgroundColor(os.aios.core.AiosTheme.surface(getContext()));text.setPadding(dp(16),dp(14),dp(16),dp(14));text.setTextIsSelectable(true);addView(text);
    }
    static CharSequence inline(String input,Actions actions) {
        // Attachment navigation retains its existing workspace handler.
        if(input.contains("aios-attachment:"))return legacyInline(input,actions);
        SpannableStringBuilder out=new SpannableStringBuilder(os.aios.core.MarkdownText.render(input));
        for(android.text.style.URLSpan link:out.getSpans(0,out.length(),android.text.style.URLSpan.class)){
            int start=out.getSpanStart(link),end=out.getSpanEnd(link);String uri=link.getURL();out.removeSpan(link);
            out.setSpan(new ClickableSpan(){public void onClick(View view){actions.open(uri);}},start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }
    private static CharSequence legacyInline(String input,Actions actions) {
        SpannableStringBuilder out=new SpannableStringBuilder();Matcher m=INLINE.matcher(input);int cursor=0;
        while(m.find()) {
            out.append(input.substring(cursor,m.start()));int start=out.length();Object span;
            if(m.group(1)!=null){out.append(m.group(2).isEmpty()?m.group(3):m.group(2));String uri=m.group(3);span=new ClickableSpan(){public void onClick(View v){actions.open(uri);}};}
            else if(m.group(4)!=null){out.append(m.group(5));span=new TypefaceSpan("monospace");}
            else if(m.group(6)!=null){out.append(m.group(7));span=new StyleSpan(Typeface.BOLD);}
            else if(m.group(8)!=null){out.append(m.group(9));span=new StrikethroughSpan();}
            else if(m.group(10)!=null){out.append(m.group(11));span=new StyleSpan(Typeface.ITALIC);}
            else {String uri=m.group(12);out.append(uri);span=new ClickableSpan(){public void onClick(View v){actions.open(uri);}};}
            out.setSpan(span,start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);cursor=m.end();
        }
        return out.append(input.substring(cursor));
    }
}
