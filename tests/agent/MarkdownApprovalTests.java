package os.aios.agent;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import os.aios.core.*;

public final class MarkdownApprovalTests extends Instrumentation {
 int checks;
 void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 Spanned render(String s){return (Spanned)MarkdownText.render(s);}
 void plain(String input,String expected){check(render(input).toString().equals(expected),input+" -> "+render(input));}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();try{
  plain("**粗体** 与 *斜体* 与 ***嵌套***","粗体 与 斜体 与 嵌套");
  Spanned nested=render("***nested***");int styles=0;for(StyleSpan s:nested.getSpans(0,nested.length(),StyleSpan.class))styles|=s.getStyle();check(styles==Typeface.BOLD_ITALIC,"nested bold italic");
  plain("\\*literal\\* and `**code**`","*literal* and **code**");
  plain("```java\n**literal**\n```","**literal**");plain("~~~\n*x*\n~~~","*x*");
  plain("# Heading\n\n- first\n- **second**","Heading\n\n• first\n• second");
  plain("3. three\n4. four","3. three\n4. four");
  plain("[label](https://example.com/a_(b))","label");
  check(render("[x](https://example.com)").getSpans(0,1,URLSpan.class).length==1,"link retained");
  check(render("[x](javascript:alert)").getSpans(0,1,URLSpan.class).length==0,"non navigable scheme");
  plain("<script>x</script>","<script>x</script>");plain("**unfinished","**unfinished");plain("","");
  check(render("~~old~~").getSpans(0,3,StrikethroughSpan.class).length==1,"strike");
  check(!render("| A | B |\n|---|---|\n| **one** | two |").toString().contains("---"),"table delimiter removed");
  check(render("`***literal***`").getSpans(0,13,TypefaceSpan.class).length==1,"code monospace");
  for(String tag:new String[]{"en","zh-CN","zh-TW","ar","fr"})for(float scale:new float[]{1f,1.6f,2f})for(int width:new int[]{240,320,420}){
   Throwable[] error={null};runOnMainSync(()->{try{
    Configuration config=new Configuration(getTargetContext().getResources().getConfiguration());config.setLocale(java.util.Locale.forLanguageTag(tag));config.fontScale=scale;
    Context c=getTargetContext().createConfigurationContext(config);LinearLayout root=AiosUi.column(c);root.setLayoutDirection(tag.equals("ar")?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
    InterventionCards.add(c,root,row(c,"decision"));InterventionCards.add(c,root,row(c,"handoff"));
    int pixels=AiosUi.dp(c,width);root.measure(View.MeasureSpec.makeMeasureSpec(pixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,pixels,root.getMeasuredHeight());bounds(root,pixels);
    int[] attachments={0};os.aios.workspace.MarkdownView md=new os.aios.workspace.MarkdownView(c,"***both***\n[image](aios-attachment:fixture)",new os.aios.workspace.MarkdownView.Actions(){public void toggle(int line){}public void open(String uri){}public View attachment(String id){attachments[0]++;return new View(c);}},false);
    check(((TextView)md.getChildAt(0)).getText().toString().equals("both"),"history user nested emphasis");check(attachments[0]==1,"history attachment handler retained");
   }catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(tag+" "+scale+" "+width,error[0]);
  }
  result.putString("stream","PASS: "+checks+" Markdown and approval layout assertions\n");finish(-1,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));finish(0,result);}}
 static JSONObject row(Context c,String kind)throws Exception{
  return new JSONObject().put("id","markdown-layout-fixture").put("revision",1).put("state","waiting_user").put("arguments",new JSONObject().put("kind",kind).put("title","审批排版 / Approval layout").put("reason","请确认此操作，长文字应该完整换行显示。").put("action_summary","A long action summary remains inside this narrow card.").put("handoff_reason","system_control").put("options",new JSONArray().put(new JSONObject().put("id","allow").put("label",c.getString(c.getResources().getIdentifier("assistant_done_return_to_assistant_721e8","string",c.getPackageName()))+" · Long option that must wrap without truncation")).put(new JSONObject().put("id","deny").put("label","保持当前状态 / Keep the current state"))));
 }
 void bounds(View view,int outer){
  if(view instanceof TextView){TextView text=(TextView)view;check(text.getWidth()>0,"positive text width");check(text.getWidth()<=outer,"within card width");if(text.isClickable()){check(text.getMaxLines()>1,"buttons multiline");check(text.getEllipsize()==null,"buttons no ellipsis");check(text.getHeight()>=AiosUi.dp(view.getContext(),48),"48dp action target");}if(text.getLayout()!=null){for(int i=0;i<text.getLineCount();i++)check(text.getLayout().getEllipsisCount(i)==0,"no clipped label");check(text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"all lines fit");}}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;int previous=0;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);check(child.getLeft()>=0&&child.getRight()<=group.getWidth(),"child horizontal bounds");if(group instanceof LinearLayout&&((LinearLayout)group).getOrientation()==LinearLayout.VERTICAL){check(child.getTop()>=previous,"no vertical overlap");previous=child.getBottom();}bounds(child,group.getWidth());}}
 }
}
