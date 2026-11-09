package os.aios.agent;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import os.aios.core.*;
/** Real Android overlay, native selection action mode, and clipboard round trip. */
public final class InteractionUiTests extends Instrumentation {
 int checks;AssistantSelectionPanel panel;TextView message;EditText input;WindowManager wm;Throwable failure;
 void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 void press(View view){int[] xy=new int[2];main(()->view.getLocationOnScreen(xy));long down=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,xy[0]+30,xy[1]+25,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();SystemClock.sleep(900);event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,xy[0]+30,xy[1]+25,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();SystemClock.sleep(800);}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void main(Runnable work){runOnMainSync(()->{try{work.run();}catch(Throwable t){failure=t;}});if(failure!=null)throw new RuntimeException(failure);}
 public void onStart(){Bundle out=new Bundle();Context c=getTargetContext();android.content.ClipboardManager clipboard=c.getSystemService(android.content.ClipboardManager.class);ClipData previous=null;boolean captured=false;
 try{
  main(()->{wm=c.getSystemService(WindowManager.class);panel=new AssistantSelectionPanel(c);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(AiosTheme.surface(c));message=new TextView(c);MarkdownText.bind(message,"COPY_ACCEPTANCE **bold** and [link](https://example.com)");message.setTextSize(22);panel.addView(message,new LinearLayout.LayoutParams(-1,180));input=AssistantTextInput.create(c,"");panel.addView(input,new LinearLayout.LayoutParams(-1,160));WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,500,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER;p.setTitle("AIOS interaction acceptance");wm.addView(panel,p);});Thread.sleep(800);
  previous=clipboard.getPrimaryClip();captured=true;
  press(message);
  main(()->{out.putString("selection_state","window="+message.hasWindowFocus()+" focus="+message.hasFocus()+" selectable="+message.isTextSelectable()+" range="+message.getSelectionStart()+":"+message.getSelectionEnd()+" attached="+message.isAttachedToWindow());check(panel.selecting(),"overlay hosts native selection toolbar");check(message.hasSelection(),"long press selects message text");});
  main(()->{message.onTextContextMenuItem(android.R.id.selectAll);check(message.onTextContextMenuItem(android.R.id.copy),"native copy action");});Thread.sleep(200);
  check(clipboard.hasPrimaryClip(),"copy reaches clipboard");String copied=clipboard.getPrimaryClip().getItemAt(0).coerceToText(c).toString();check(copied.contains("COPY_ACCEPTANCE")&&copied.contains("bold"),"copied rendered markdown");
  main(()->{panel.finishSelection();input.requestFocus();check(input.onTextContextMenuItem(android.R.id.paste),"native paste action");check(input.getText().toString().equals(copied),"paste round trip preserves text");input.setSelection(0,input.length());});press(input);main(()->{check(panel.selecting(),"input native toolbar available");panel.finishSelection();});
  out.putString("result","PASS "+checks+" checks");
 }catch(Throwable t){out.putString("error",android.util.Log.getStackTraceString(t));}finally{ClipData restore=previous;boolean restoreClipboard=captured;runOnMainSync(()->{if(panel!=null&&panel.isAttachedToWindow())wm.removeViewImmediate(panel);if(restoreClipboard){if(restore!=null)clipboard.setPrimaryClip(restore);else clipboard.clearPrimaryClip();}});finish(out.containsKey("error")?0:-1,out);}}
}
