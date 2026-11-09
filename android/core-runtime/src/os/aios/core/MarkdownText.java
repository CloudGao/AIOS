package os.aios.core;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.*;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.strikethrough.*;
import org.commonmark.ext.gfm.tables.*;

/** Offline presentation only: never rewrites messages or executes HTML/loads images. */
public final class MarkdownText {
    private static final Parser PARSER = Parser.builder().extensions(java.util.List.of(
            StrikethroughExtension.create(), TablesExtension.create())).build();
    private MarkdownText() {}

    public static void bind(TextView view, CharSequence source) {
        view.setText(render(source == null ? "" : source.toString()));
        view.setTextIsSelectable(true);

    }
    public static CharSequence render(String source) {
        Renderer renderer = new Renderer();
        renderer.node(PARSER.parse(source == null ? "" : source), 0);
        while (renderer.out.length() > 0 && renderer.out.charAt(renderer.out.length()-1) == '\n')
            renderer.out.delete(renderer.out.length()-1, renderer.out.length());
        return renderer.out;
    }
    private static final class Renderer {
        final SpannableStringBuilder out = new SpannableStringBuilder();
        void span(Object value, int start) {
            if (out.length() > start) out.setSpan(value, start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        void newline(int count) {
            int have=0;
            for(int i=out.length()-1;i>=0&&out.charAt(i)=='\n';i--)have++;
            if(out.length()>0)for(int i=have;i<count;i++)out.append('\n');
        }
        void children(Node n,int depth) {for(Node c=n.getFirstChild();c!=null;c=c.getNext())node(c,depth);}
        void code(String value) {int start=out.length();out.append(value);span(new TypefaceSpan("monospace"),start);}
        void node(Node n,int depth) {
            if(n instanceof Text){out.append(((Text)n).getLiteral());return;}
            if(n instanceof Code){code(((Code)n).getLiteral());return;}
            if(n instanceof SoftLineBreak || n instanceof HardLineBreak){out.append('\n');return;}
            if(n instanceof FencedCodeBlock){newline(2);code(((FencedCodeBlock)n).getLiteral());newline(2);return;}
            if(n instanceof IndentedCodeBlock){newline(2);code(((IndentedCodeBlock)n).getLiteral());newline(2);return;}
            if(n instanceof HtmlInline){out.append(((HtmlInline)n).getLiteral());return;}
            if(n instanceof HtmlBlock){newline(2);out.append(((HtmlBlock)n).getLiteral());newline(2);return;}
            if(n instanceof ThematicBreak){newline(2);out.append("────────");newline(2);return;}
            if(n instanceof BulletList || n instanceof OrderedList) {
                newline(depth==0?2:1);int number=n instanceof OrderedList?((OrderedList)n).getMarkerStartNumber():0;
                for(Node item=n.getFirstChild();item!=null;item=item.getNext()){
                    newline(1);for(int i=0;i<depth;i++)out.append("  ");
                    out.append(n instanceof OrderedList?Integer.toString(number++)+". ":"• ");children(item,depth+1);
                }
                newline(depth==0?2:1);return;
            }
            if(n instanceof TableBlock){newline(2);children(n,depth);newline(2);return;}
            if(n instanceof TableRow){children(n,depth);newline(1);return;}
            if(n instanceof TableCell){if(n.getPrevious()!=null)out.append("  │  ");int start=out.length();children(n,depth);if(((TableCell)n).isHeader())span(new StyleSpan(Typeface.BOLD),start);return;}
            boolean block=n instanceof Paragraph||n instanceof Heading||n instanceof BlockQuote;
            if(block && !(n.getParent() instanceof ListItem))newline(2);
            int start=out.length();children(n,depth);
            if(n instanceof StrongEmphasis)span(new StyleSpan(Typeface.BOLD),start);
            if(n instanceof Emphasis)span(new StyleSpan(Typeface.ITALIC),start);
            if(n instanceof Strikethrough)span(new StrikethroughSpan(),start);
            if(n instanceof Heading){span(new StyleSpan(Typeface.BOLD),start);span(new RelativeSizeSpan(1.55f-(((Heading)n).getLevel()-1)*.09f),start);}
            if(n instanceof BlockQuote)span(new QuoteSpan(),start);
            if(n instanceof Link){String url=((Link)n).getDestination();String scheme=android.net.Uri.parse(url).getScheme();if(scheme!=null&&java.util.Set.of("https","http","mailto","tel").contains(scheme.toLowerCase(java.util.Locale.ROOT)))span(new URLSpan(url),start);}
            if(block)newline(n.getParent() instanceof ListItem?1:2);
        }
    }
}
