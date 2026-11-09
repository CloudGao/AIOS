package os.aios.miniapp;
import android.app.*;
import android.os.*;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.xmlpull.v1.XmlSerializer;
import android.util.Xml;
import java.io.StringWriter;

/** Independent test process: read the active window without waiting for animated UI to idle. */
public final class MiniAppUiProbe extends Instrumentation {
 Bundle args;public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 boolean setText(AccessibilityNodeInfo n,String value){if(n.isEditable()){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null&&setText(c,value))return true;}return false;}
 void node(XmlSerializer xml,AccessibilityNodeInfo n)throws Exception {
  xml.startTag(null,"node");xml.attribute(null,"text",n.getText()==null?"":n.getText().toString());xml.attribute(null,"content-desc",n.getContentDescription()==null?"":n.getContentDescription().toString());xml.attribute(null,"class",String.valueOf(n.getClassName()));Rect b=new Rect();n.getBoundsInScreen(b);xml.attribute(null,"bounds",b.toShortString());xml.attribute(null,"clickable",String.valueOf(n.isClickable()));xml.attribute(null,"resource-id",n.getViewIdResourceName()==null?"":n.getViewIdResourceName());
  for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)node(xml,c);}xml.endTag(null,"node");
 }
 void drag(String spec) {
  String[] parts=spec.split(",");float x=Float.parseFloat(parts[0]),y=Float.parseFloat(parts[1]),tx=Float.parseFloat(parts[2]),ty=Float.parseFloat(parts[3]);
  long down=SystemClock.uptimeMillis();inject(down,android.view.MotionEvent.ACTION_DOWN,x,y);SystemClock.sleep(850);
  for(int i=1;i<=30;i++){inject(down,android.view.MotionEvent.ACTION_MOVE,x+(tx-x)*i/30,y+(ty-y)*i/30);SystemClock.sleep(30);}
  SystemClock.sleep(1200);try{java.io.File file=new java.io.File(getContext().getFilesDir(),"drag.png");try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}catch(Exception e){throw new RuntimeException(e);}inject(down,android.view.MotionEvent.ACTION_UP,tx,ty);SystemClock.sleep(800);
 }
 void inject(long down,int action,float x,float y){android.view.MotionEvent e=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);if(!getUiAutomation().injectInputEvent(e,true))throw new IllegalStateException("Input injection rejected");e.recycle();}
 void checkPermissions() {
  android.content.Context c=getContext();String permission="os.aios.permission.READ_MINIAPP_LAUNCHER";
  if(c.checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)throw new AssertionError("Probe unexpectedly trusted");
  if(c.getPackageManager().checkPermission(permission,"com.android.launcher3")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new AssertionError("Launcher is not authorized");
  if(c.getPackageManager().checkPermission(permission,"com.android.shell")==android.content.pm.PackageManager.PERMISSION_GRANTED)throw new AssertionError("Shell is authorized");
  boolean denied=false;try{c.getContentResolver().call(android.net.Uri.parse("content://os.aios.miniapp.launcher"),"list",null,null);}catch(SecurityException expected){denied=true;}if(!denied)throw new AssertionError("Directory was exposed");
  for(String activity:new String[]{"HostActivity","ManageActivity"}){
   denied=false;try{c.startActivity(new android.content.Intent().setClassName("os.aios.miniapp","os.aios.miniapp."+activity).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}catch(SecurityException expected){denied=true;}if(!denied)throw new AssertionError(activity+" was exposed");
  }
 }
 public void onStart(){Bundle out=new Bundle();try{if(args.containsKey("permissions")){checkPermissions();out.putString("stream","PASS: trusted Launcher granted; probe and shell denied; directory and activities enforce permission");finish(Activity.RESULT_OK,out);return;}if(args.containsKey("drag"))drag(args.getString("drag"));AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)throw new IllegalStateException("No active window");if(args.containsKey("query")&&!setText(root,args.getString("query")))throw new IllegalStateException("No editable search field");StringWriter buffer=new StringWriter();XmlSerializer xml=Xml.newSerializer();xml.setOutput(buffer);xml.startDocument("UTF-8",true);xml.startTag(null,"hierarchy");node(xml,root);xml.endTag(null,"hierarchy");xml.endDocument();out.putString("stream",buffer.toString());finish(Activity.RESULT_OK,out);}catch(Exception e){out.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
