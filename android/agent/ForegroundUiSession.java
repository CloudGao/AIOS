package os.aios.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.Display;
import android.view.accessibility.*;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.Set;

/** One explicit foreground lease; never shares background session observation credentials. */
final class ForegroundUiSession {
 final AgentService owner;final UiSessionManager manager;final SharedPreferences saved;
 String threadId="";
 String id="",target="",state="closed",detail="",observation="",pausedRequest="",image="",imageError="",leaseRequest="";
 JSONObject observedTree,screenshotTree,capturedImage;boolean contentDirty;
 UiObservationFrame observationFrame;
 UiGestureSpec activeGesture;GestureDescription.StrokeDescription[] activeHeld;
 long launchStarted,observedAt;int generation,windowId=-1,width,height,observedWidth,observedHeight,observedRotation;boolean screenshotPending;
 ForegroundUiSession(AgentService owner,UiSessionManager manager){this.owner=owner;this.manager=manager;saved=owner.getSharedPreferences("foreground_ui_session",0);
  if(saved.getBoolean("unfinished",false)){id=saved.getString("id","");target=saved.getString("target","");state="paused";detail="服务已重启，请重新请求继续前台任务";pausedRequest=request();}
 }
 String request(){try{String value=AssistantConversation.state(owner).optString("request_id");return value.isEmpty()?"no-active-request":value;}catch(Exception e){return "no-active-request";}}
 boolean blocksRequest(String pkg){return target.equals(pkg)&&!pausedRequest.isEmpty()&&pausedRequest.equals(request());}
 void releasePreviousRequest(String pkg){if(target.equals(pkg)&&!leaseRequest.equals(request())){invalidate();set("closed","新请求重新选择操作屏幕");}}
 boolean owns(String pkg){refreshLaunch();return target.equals(pkg)&&ForegroundUiPolicy.holds(state);}
 void refreshLaunch(){
  if(!state.equals("waiting_for_app"))return;
  try{root();set("active","前台控制已就绪");}
  catch(Exception e){if(ForegroundUiPolicy.expired(state,launchStarted,SystemClock.elapsedRealtime())){invalidate();set("failed","打开前台应用超时："+e.getMessage());}}
 }
 void set(String next,String message){state=next;detail=message;saved.edit().putString("id",id).putString("target",target).putBoolean("unfinished",!next.equals("closed")).apply();}
 void contentChanged(){if(state.equals("active"))contentDirty=true;}
 void invalidate(){if(activeGesture!=null&&activeHeld!=null&&channel()!=null)try{channel().dispatchGesture(activeGesture.foregroundRelease(Display.DEFAULT_DISPLAY,activeHeld),null,manager.handler);}catch(Exception ignored){}activeGesture=null;activeHeld=null;observationFrame=null;capturedImage=null;observedTree=null;screenshotTree=null;contentDirty=false;observation="";image="";imageError="";screenshotPending=false;generation++;}
 void userTakeover(String reason){if(!state.equals("active")&&!state.equals("waiting_for_app"))return;pausedRequest=request();invalidate();set("paused",reason);}
 void windowChanged(){if(!state.equals("active"))return;invalidate();try{root();}catch(ObservationUnavailable e){contentDirty=true;}catch(Exception e){userTakeover("前台页面已改变，请重新请求继续");}}
 static final class ObservationUnavailable extends IllegalStateException {}
 ForegroundUiService channel(){return ForegroundUiService.instance;}
 AccessibilityNodeInfo root()throws Exception{
  if(!Authorization.enabled(owner))throw new SecurityException("AIOS 服务已停用");
  if(owner.getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException("手机已锁定");
  ForegroundUiService service=channel();if(service==null)throw new IllegalStateException("请先在系统无障碍设置启用 AIOS 前台控制");
  if(!service.observesPhysicalInput)throw new IllegalStateException("当前系统不支持不拦截用户触摸的观察模式");
  if(manager.owner(target)!=null)throw new IllegalStateException("等待后台任务释放应用");
  AccessibilityNodeInfo active=service.getRootInActiveWindow();
  AccessibilityWindowInfo activeWindow=active==null?null:active.getWindow();
  if(activeWindow!=null&&activeWindow.getDisplayId()==Display.DEFAULT_DISPLAY
    &&activeWindow.getType()==AccessibilityWindowInfo.TYPE_SYSTEM
    &&!("AIOS Assistant Panel".contentEquals(activeWindow.getTitle()==null?"":activeWindow.getTitle())
      &&active.getPackageName()!=null&&owner.getPackageName().contentEquals(active.getPackageName())))
   throw new IllegalStateException("系统交互页面正在前台，请先完成系统交接");
  AccessibilityWindowInfo selected=null;
  for(AccessibilityWindowInfo window:service.getWindows()){
   if(window.getDisplayId()!=Display.DEFAULT_DISPLAY||window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
   if(selected==null||(!selected.isFocused()&&window.isFocused())
     ||(selected.isFocused()==window.isFocused()&&window.getLayer()>selected.getLayer()))selected=window;
  }
  AccessibilityNodeInfo root=selected==null?null:selected.getRoot();
  if(root==null&&target.equals(UiScreenContext.mainPackage(owner)))throw new ObservationUnavailable();
  if(root==null||root.getPackageName()==null||!target.contentEquals(root.getPackageName()))throw new IllegalStateException("目标应用不在主屏前台");
  return root;
 }
 Display physicalDisplay(){Display display=owner.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);if(display==null)throw new IllegalStateException("主屏暂不可用");return display;}
 Point displayPixels(){Point size=new Point();physicalDisplay().getRealSize(size);return size;}
 JSONObject observationRequired()throws Exception{return status().put("state","observation_required").put("executed",false).put("next_step","Read tree or screenshot for this session, then pass its observation_id. Use original display_pixel coordinates and re-evaluate the target; never replay old coordinates.");}
 JSONObject status()throws Exception{
  refreshLaunch();Point pixels=displayPixels();
  String userState=state.equals("closed")||state.equals("failed")?"stopped":state.equals("paused")||state.equals("permission_required")||state.equals("not_supported")?"paused":"running";
  return new JSONObject().put("session_id",id).put("task_id",id).put("package",target).put("app_label",target).put("task_name","前台任务").put("mode","foreground").put("display_id",0).put("coordinate_space","display_pixels").put("width",pixels.x).put("height",pixels.y).put("rotation",physicalDisplay().getRotation()).put("state",state).put("user_state",userState).put("detail",detail).put("foreground",target.equals(UiScreenContext.mainPackage(owner))).put("requires_observation",observation.isEmpty()).put("generation",generation).put("max_concurrent_ui_tasks",1);
 }
 JSONObject create(JSONObject q)throws Exception{
  JSONObject unavailable=AgentCapabilities.guard(owner,"screen.foreground.create");
  if(unavailable!=null)return unavailable;
  if(owner.getSystemService(KeyguardManager.class).isKeyguardLocked()){userTakeover("主屏已锁定，等待用户恢复");return status().put("state","device_locked").put("executed",false);}
  String next=q.getString("package"),current=request();
  if(ForegroundUiPolicy.blocked(pausedRequest,current))return status().put("state","user_takeover").put("executed",false);
  refreshLaunch();
  if(ForegroundUiPolicy.busy(state,target,next,leaseRequest,current))return status().put("state","foreground_session_busy").put("executed",false);
  if(!target.equals(next)||!ForegroundUiPolicy.holds(state)||!leaseRequest.equals(current)||id.isEmpty())id="foreground:"+UUID.randomUUID();
  threadId=q.optString("_caller_thread",threadId);target=next;leaseRequest=current;invalidate();pausedRequest="";
  if(channel()==null){set("permission_required","请在系统无障碍设置启用 AIOS 前台控制，然后继续");return status();}
  if(!channel().observesPhysicalInput){set("not_supported","系统未启用触摸观察能力，未接管主屏输入");return status();}
  try{
   if(target.equals(UiScreenContext.mainPackage(owner))&&manager.owner(target)==null){launchStarted=SystemClock.elapsedRealtime();set("waiting_for_app","等待当前主屏页面可读取");return status().put("executed",false);}
   Intent intent=owner.getPackageManager().getLaunchIntentForPackage(target);
   if(intent==null){set("failed","应用没有启动入口");return status().put("executed",false);}
   launchStarted=SystemClock.elapsedRealtime();set("waiting_for_app","正在打开前台应用");
   JSONObject launch=manager.open(intent,"foreground","",q.optString("_caller_thread",UiSessionManager.caller.get()==null?"":UiSessionManager.caller.get()));String result=launch.optString("state");
   if(!result.equals("handed_to_app")&&!result.equals("handover_requested")){set("failed","启动未被系统接受："+result);return status().put("launch_result",launch).put("executed",false);}
   return status().put("completed_operation_verified",false);
  }catch(Exception e){invalidate();set("failed","打开前台应用失败："+e.getMessage());return status().put("executed",false);}
 }
 JSONObject observe(AccessibilityNodeInfo root)throws Exception{observedTree=owner.node(root,0,new int[]{500});contentDirty=false;Point pixels=displayPixels();observedWidth=pixels.x;observedHeight=pixels.y;observedRotation=physicalDisplay().getRotation();windowId=root.getWindowId();observation=UUID.randomUUID().toString();observedAt=SystemClock.elapsedRealtime();JSONObject result=status().put("captured_at_elapsed_ms",observedAt);observationFrame=new UiObservationFrame(result);return result.put("observation_id",observation).put("observation_token",observation).put("observation",observation);}
 JSONObject dispatch(JSONObject q)throws Exception{
  String op=q.getString("op");
  if(op.equals("status"))return status();
  if(op.equals("pause")||op.equals("handover")){userTakeover("前台任务已暂停并交还用户");return status();}
  if(op.equals("close")){invalidate();set("closed","前台任务已关闭");return status();}
  JSONObject unavailable=AgentCapabilities.guard(owner,"screen.foreground."+op);
  if(unavailable!=null){invalidate();return unavailable.put("session_id",id);}
  if(!leaseRequest.equals(request()))return status().put("state","screen_route_required").put("executed",false).put("next_step","This foreground lease belongs to an earlier request. Call create with the target package; the host will decide the current screen. Never reuse its observation or input before rerouting.");
  if(op.equals("resume")||op.equals("launch")){if(!pausedRequest.isEmpty()&&pausedRequest.equals(request()))return status().put("state","user_takeover").put("executed",false);return create(new JSONObject().put("package",target));}
  status();if(!state.equals("active"))return status().put("executed",false);
  AccessibilityNodeInfo root;
  try{root=root();}catch(ObservationUnavailable e){return status().put("state","observation_unavailable").put("executed",false).put("next_step","Same main application, tree temporarily unavailable. Retry observation only; input was not submitted.");}catch(Exception e){userTakeover(e.getMessage());return status().put("executed",false);}
  if(op.equals("tree")){
   JSONArray windows=new JSONArray().put(owner.node(root,0,new int[]{500}));
   for(AccessibilityWindowInfo window:channel().getWindows())if(window.getDisplayId()==Display.DEFAULT_DISPLAY&&window.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD){
    AccessibilityNodeInfo keyboard=window.getRoot();if(keyboard!=null)windows.put(new JSONObject().put("window_type","input_method").put("root",owner.node(keyboard,0,new int[]{200})));
   }
   Point size=displayPixels();UiObservationFrame.sanitize(windows,size.x,size.y);return observe(root).put("windows",windows);
  }
  if(op.equals("screenshot")){
   if(!imageError.isEmpty()){String reason=imageError;imageError="";return status().put("state","observation_unavailable").put("reason",reason).put("executed",false);}
   if(!image.isEmpty()){Point pixels=displayPixels();if(width!=pixels.x||height!=pixels.y){invalidate();return observationRequired();}JSONObject capturedTree=screenshotTree;image="";JSONObject result=observe(root);for(java.util.Iterator<String> keys=capturedImage.keys();keys.hasNext();){String key=keys.next();result.put(key,capturedImage.get(key));}observationFrame=new UiObservationFrame(result);observedTree=capturedTree;contentDirty=true;return result;}
   if(!screenshotPending){screenshotTree=owner.node(root,0,new int[]{500});screenshotPending=true;final int epoch=generation;final int window=root.getWindowId();
    channel().takeScreenshot(Display.DEFAULT_DISPLAY,owner.getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
     public void onSuccess(AccessibilityService.ScreenshotResult result){
      try{if(epoch!=generation)return;Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());if(hardware==null)throw new IllegalStateException("截图不可用");Bitmap bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);hardware.recycle();
       try{if(root().getWindowId()!=window)throw new IllegalStateException("截图期间页面改变");capturedImage=owner.encodeScreenshot(bitmap,new JSONObject());image=capturedImage.getString("png_base64");width=bitmap.getWidth();height=bitmap.getHeight();}finally{bitmap.recycle();}
      }catch(Exception e){if(epoch==generation)imageError=e.getMessage();}finally{result.getHardwareBuffer().close();if(epoch==generation)screenshotPending=false;}
     }
     public void onFailure(int error){if(epoch==generation){screenshotPending=false;imageError="系统拒绝截图："+error;}}
    });
   }
   return status().put("state","observation_pending").put("executed",false);
  }
  String supplied=UiObservation.supplied(q.optString("observation_id"),q.optString("observation_token"),q.optString("observation"));
  Point pixels=displayPixels();
  if(observation.isEmpty()||!observation.equals(supplied)||SystemClock.elapsedRealtime()-observedAt>60000||root.getWindowId()!=windowId||!UiDisplayGeometry.unchanged(observedWidth,observedHeight,observedRotation,pixels.x,pixels.y,physicalDisplay().getRotation()))return observationRequired();
  if(observationFrame==null)return observationRequired();
  q=observationFrame.input(q);
  if(contentDirty){
   // Content events mark the page dirty; rotating banners must not invalidate an unchanged target.
   // Window changes and physical user input still invalidate immediately; scrolling must preserve the target.
   JSONObject currentTree=owner.node(root,0,new int[]{500});
   boolean unchanged=observedTree!=null&&observedTree.toString().equals(currentTree.toString());
   if(!unchanged&&(!Set.of("tap","double_tap","long_press").contains(op)
     ||!UiActionReadback.sameTarget(observedTree,currentTree,q.getInt("x"),q.getInt("y"))))return observationRequired();
  }
  invalidate();ForegroundUiService service=channel();boolean accepted=false;
  if(op.equals("back"))accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
  else if(op.equals("text")){
   AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
   if(focus==null||!focus.isEditable()||focus.isPassword())return status().put("state","editable_field_required").put("executed",false);
   String value=q.getString("text");if(value.length()>12000)throw new IllegalArgumentException("Text too long");
   if(!q.optBoolean("replace",false))value=(focus.getText()==null?"":focus.getText().toString())+value;
   Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);accepted=focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
  }else if(op.equals("gesture")){
   UiGestureSpec spec=UiGestureSpec.parse(q,pixels.x,pixels.y);final String submittedSession=id;
   if(spec.holdMs==0)accepted=service.dispatchGesture(spec.foregroundDirect(Display.DEFAULT_DISPLAY),new AccessibilityService.GestureResultCallback(){public void onCancelled(GestureDescription value){if(id.equals(submittedSession)&&state.equals("active"))userTakeover("手势被系统或用户中断");}},manager.handler);
   else{GestureDescription.StrokeDescription[] held=spec.foregroundHold();activeGesture=spec;activeHeld=held;final int gestureEpoch=generation;GestureDescription.Builder hold=new GestureDescription.Builder().setDisplayId(Display.DEFAULT_DISPLAY);for(GestureDescription.StrokeDescription stroke:held)hold.addStroke(stroke);accepted=service.dispatchGesture(hold.build(),new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription value){if(gestureEpoch!=generation||!id.equals(submittedSession)||!state.equals("active")){if(activeGesture==spec){try{service.dispatchGesture(spec.foregroundRelease(Display.DEFAULT_DISPLAY,held),null,manager.handler);}catch(Exception ignored){}activeGesture=null;activeHeld=null;}return;}if(!service.dispatchGesture(spec.foregroundContinue(Display.DEFAULT_DISPLAY,held),new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription ignored){if(activeGesture==spec){activeGesture=null;activeHeld=null;}}public void onCancelled(GestureDescription ignored){if(activeGesture==spec){activeGesture=null;activeHeld=null;}if(gestureEpoch==generation&&id.equals(submittedSession)&&state.equals("active"))userTakeover("手势被系统或用户中断");}},manager.handler)){activeGesture=null;activeHeld=null;userTakeover("连续拖动未被系统接受");}}public void onCancelled(GestureDescription value){if(activeGesture==spec){activeGesture=null;activeHeld=null;}if(gestureEpoch==generation&&id.equals(submittedSession)&&state.equals("active"))userTakeover("手势被系统或用户中断");}},manager.handler);}
  }else if(op.equals("tap")||op.equals("double_tap")||op.equals("long_press")||op.equals("swipe")){
   int x=q.getInt(op.equals("swipe")?"x1":"x"),y=q.getInt(op.equals("swipe")?"y1":"y");int x2=op.equals("swipe")?q.getInt("x2"):x,y2=op.equals("swipe")?q.getInt("y2"):y;
   if(!UiDisplayGeometry.contains(pixels.x,pixels.y,x,y)||!UiDisplayGeometry.contains(pixels.x,pixels.y,x2,y2))throw new IllegalArgumentException("Coordinates outside the physical display");
   if(op.equals("swipe"))UiSessionController.strict(q,Set.of("op","session_id","observation_id","observation_token","observation","x1","y1","x2","y2","duration_ms"));
   Path path=new Path();path.moveTo(x,y);if(op.equals("swipe"))path.lineTo(x2,y2);
   int duration=op.equals("long_press")?650:op.equals("swipe")?q.optInt("duration_ms",400):70;if(duration<100&&op.equals("swipe")||duration>5000)throw new IllegalArgumentException("Swipe duration must be 100-5000ms");GestureDescription.Builder builder=new GestureDescription.Builder().setDisplayId(Display.DEFAULT_DISPLAY).addStroke(new GestureDescription.StrokeDescription(path,0,duration));if(op.equals("double_tap")){UiSessionController.strict(q,Set.of("op","session_id","observation_id","observation_token","observation","x","y","interval_ms"));int interval=q.optInt("interval_ms",120);if(interval<40||interval>300)throw new IllegalArgumentException("Double tap interval must be 40-300ms");builder.addStroke(new GestureDescription.StrokeDescription(path,duration+interval,70));}GestureDescription gesture=builder.build();
   final String submittedSession=id;
   accepted=service.dispatchGesture(gesture,new AccessibilityService.GestureResultCallback(){public void onCancelled(GestureDescription gesture){if(id.equals(submittedSession)&&state.equals("active"))userTakeover("手势被系统或用户中断");}},manager.handler);
  }else throw new IllegalArgumentException("Unsupported foreground operation: "+op);
  return status().put("state",accepted?"action_submitted":"action_rejected").put("executed",accepted).put("completed_operation_verified",false);
 }
}
