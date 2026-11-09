package os.aios.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

/** User-enabled Android accessibility channel. Observes real touches without consuming them. */
public final class ForegroundUiService extends AccessibilityService {
 static volatile ForegroundUiService instance;
 static volatile long physicalInputGeneration;
 boolean observesPhysicalInput;
 @Override protected void onServiceConnected(){
  observesPhysicalInput=DeviceHost.configureForeground(this);
  instance=this;
  AgentCapabilities.refresh(this);
 }
 private ForegroundUiSession session(){AgentService owner=AgentService.instance;return owner==null||owner.ui==null?null:owner.ui.foreground;}
 @Override public void onMotionEvent(MotionEvent event){
  // Accessibility-generated strokes have a virtual device id. A physical pointer always wins.
 if(DeviceHost.physicalPointerDown(event)){
   physicalInputGeneration++;
   ForegroundUiSession s=session();if(s!=null)s.userTakeover("检测到用户操作，前台任务已暂停");
  }
 }
 @Override protected boolean onKeyEvent(KeyEvent event){if(event.getDeviceId()>=0&&event.getAction()==KeyEvent.ACTION_DOWN){physicalInputGeneration++;ForegroundUiSession s=session();if(s!=null)s.userTakeover("检测到用户按键，前台任务已暂停");}return false;}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){ForegroundUiSession s=session();if(s!=null){if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)s.windowChanged();else if(event.getPackageName()!=null&&s.target.contentEquals(event.getPackageName())){if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED||event.getEventType()==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)s.contentChanged();}}}
 @Override public void onInterrupt(){ForegroundUiSession s=session();if(s!=null)s.userTakeover("前台控制被系统中断");}
 @Override public void onDestroy(){instance=null;AgentCapabilities.refresh(this);ForegroundUiSession s=session();if(s!=null)s.userTakeover("前台控制服务已断开");super.onDestroy();}
}
