package os.aios.agent;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import os.aios.core.*;
public final class MarkdownVisualTests extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{Throwable[] failure={null};runOnMainSync(()->{try{
  for(String lang:new String[]{"en","zh-CN","zh-TW","ar"})for(boolean dark:new boolean[]{false,true}){
   Configuration cfg=new Configuration(getTargetContext().getResources().getConfiguration());cfg.setLocale(java.util.Locale.forLanguageTag(lang));cfg.fontScale=1.6f;cfg.uiMode=(cfg.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
   Context c=new ContextThemeWrapper(getTargetContext().createConfigurationContext(cfg),getTargetContext().getResources().getIdentifier("AiosSystemTheme","style",getTargetContext().getPackageName()));LinearLayout root=AiosUi.column(c);root.setLayoutDirection(cfg.getLayoutDirection());root.setPadding(16,16,16,16);root.setBackgroundColor(AiosTheme.background(c));
   root.addView(AiosUi.assistantText(c,"## Markdown\n**Bold 粗體** · *Italic* · ***Both***\n\n- First\n- `**literal**`\n\n> Quote 引用\n\n```java\n**code stays literal**\n```\n\n[Example](https://example.com) · ~~old~~"));
   InterventionCards.add(c,root,MarkdownApprovalTests.row(c,"decision"));InterventionCards.add(c,root,MarkdownApprovalTests.row(c,"handoff"));
   int width=AiosUi.dp(c,320);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));root.layout(0,0,width,root.getMeasuredHeight());Bitmap bitmap=Bitmap.createBitmap(width,root.getMeasuredHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
   java.io.File folder=new java.io.File(getTargetContext().getCacheDir(),"markdown-acceptance");if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Cannot create fixture directory");java.io.File file=new java.io.File(folder,"markdown-"+lang+"-"+(dark?"dark":"light")+".png");try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
  }
 }catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);out.putString("stream","PASS: 8 native Android renders, 320dp, 1.6 font scale, four locales, day/night\n");finish(-1,out);
 }catch(Throwable t){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(t));finish(0,out);}}
}
