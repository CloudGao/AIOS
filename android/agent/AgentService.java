package os.aios.agent;
import android.app.*;
import android.os.*;
import android.content.*;
import os.aios.device.ScreenBackend;
import android.net.*;
import android.graphics.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class AgentService extends android.app.Service {
 static volatile AgentService instance;

 volatile String state="ready", error="";
 volatile boolean running=true;
 String targetPackage="com.android.settings";
 Context context;
 PlatformServices platform;
 UiSessionManager ui;
 InterventionCoordinator interventions;
 ExecutionPower executionPower;
 os.aios.workspace.WorkspaceStore workspace;
 BroadcastReceiver events;
 volatile LocalServerSocket listener;
 CodexToolGateway codexTools;
 ScheduledAssistantCoordinator scheduledAssistants;
 AssistantCodexBridge assistantCodex;
 FeatureConsumer minutesCodex;
 public IBinder onBind(Intent intent) { return codexTools.messenger.getBinder(); }
 public void onCreate() {
  super.onCreate();
  instance=this;
  context=this;
  platform=new PlatformServices(this);
  codexTools=new CodexToolGateway(this);
  assistantCodex=new AssistantCodexBridge(this);
  workspace=new os.aios.workspace.WorkspaceStore(this);
  try{workspace.call("runtime.harness.register",new JSONObject().put("harness_id","codex-app-server").put("version",1).put("capabilities",new JSONObject().put("create",true).put("continue",true).put("read",true).put("stream",true).put("interrupt",true).put("history_export",true).put("checkpoint_restore",false)));}catch(Exception e){android.util.Log.e("AIOSRuntime","Harness registration failed",e);}
  EventStore.initialize(this);
  minutesCodex=AgentFeatureBindings.minutes(this);
  ui=new UiSessionManager(this);
  interventions=new InterventionCoordinator(this);executionPower=new ExecutionPower(this);
  events=new BroadcastReceiver(){public void onReceive(Context c,Intent i){try{executionPower.refresh();if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())&&ui!=null){boolean mainActive=ui.foreground.state.equals("active")||ui.foreground.state.equals("waiting_for_app");ui.foreground.userTakeover("主屏已关闭或锁定，等待用户恢复");if(mainActive)interventions.mainScreenLocked();}if(!AssistantConversation.CHANGED.equals(i.getAction()))EventStore.add(i.getAction(),new JSONObject().put("level",i.getIntExtra("level",-1)).put("plugged",i.getIntExtra("plugged",-1)));}catch(Exception ignored){}}};
  IntentFilter filter=new IntentFilter();filter.addAction(AssistantConversation.CHANGED);filter.addAction(Intent.ACTION_BATTERY_CHANGED);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_POWER_CONNECTED);filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
  registerReceiver(events,filter,Context.RECEIVER_NOT_EXPORTED);
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("control","AIOS 手机助手",NotificationManager.IMPORTANCE_LOW));
  refreshForeground();
  assistantCodex.start();if(minutesCodex!=null)minutesCodex.start();interventions.recover();
  scheduledAssistants=new ScheduledAssistantCoordinator(this);scheduledAssistants.start();
  new Thread(this::serve,"aios-native-control").start();
 }
 final Map<String,Integer> foregroundUses=new HashMap<>();
 synchronized String acquireForeground(int type){
  String token=UUID.randomUUID().toString();foregroundUses.put(token,type);
  try{refreshForeground();return token;}catch(RuntimeException denied){foregroundUses.remove(token);throw denied;}
 }
 synchronized void releaseForeground(String token){if(token!=null&&foregroundUses.remove(token)!=null&&running)refreshForeground();}
 private synchronized void refreshForeground(){
  int types=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
  for(int type:foregroundUses.values())types|=type;
  String detail=(types&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)!=0?"正在录音":(types&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)!=0?"正在获取位置":"手机工具与纪要服务正在运行";
  startForeground(1,new Notification.Builder(this,"control").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle(os.aios.core.DisplayText.clean("AIOS 手机助手")).setContentText(os.aios.core.DisplayText.clean(detail)).build(),types);
 }
 public int onStartCommand(Intent i,int flags,int id) {if(i!=null&&i.getBooleanExtra("session_command",false))ui.command(i);else if(i!=null&&"close".equals(i.getStringExtra("ui_card_action")))try{ui.call(new JSONObject().put("op","close").put("session_id",i.getStringExtra("session_id")));}catch(Exception e){android.util.Log.e("AIOSUi","Unable to close virtual-screen slot",e);}return Authorization.enabled(this)?START_STICKY:START_NOT_STICKY;}
 public void onDestroy() { running=false; if(scheduledAssistants!=null)scheduledAssistants.close(); if(executionPower!=null)executionPower.close(); if(interventions!=null)interventions.close(); if(minutesCodex!=null)minutesCodex.close(); if(assistantCodex!=null)assistantCodex.close(); if(codexTools!=null)codexTools.close(); if(workspace!=null){synchronized(workspace){workspace.close();}} EventStore.shutdown(); if(platform!=null)platform.close(); if(instance==this)instance=null; running=false; if(events!=null)unregisterReceiver(events); if(ui!=null)ui.destroy();try {if(listener!=null)listener.close();}catch(IOException ignored){}super.onDestroy(); }
 void serve() {
  try(LocalServerSocket server=new LocalServerSocket("aios_cc_poc")) {
   listener=server;
   while(running) {
    try(LocalSocket socket=server.accept()) {
     int peer=socket.getPeerCredentials().getUid();
     if(peer!=0 && peer!=2000 && peer!=android.os.Process.myUid())continue;
     socket.setSoTimeout(20000);
     BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
     JSONObject result;
     try { result=dispatchExternal(new JSONObject(in.readLine()),peer); }
     catch(Throwable e) { result=new JSONObject().put("error",e.toString()).put("state",e instanceof IllegalArgumentException?"failed":e instanceof SecurityException?"permission_required":"outcome_unknown").put("session_state",state); }
     socket.getOutputStream().write((result.toString()+"\n").getBytes("UTF-8"));
    }catch(IOException disconnected){/* One diagnostic peer must not stop the assistant service. */}
   }
  } catch(Exception e) { android.util.Log.e("AIOS_CC", "bridge failed",e); }
  finally { stopSelf(); }
 }
 JSONObject status() throws Exception { return ui.call(new JSONObject().put("op","status")); }
 JSONObject dispatchExternal(JSONObject q,int peer) throws Exception {
  boolean adbEnabled=android.provider.Settings.Global.getInt(getContentResolver(),android.provider.Settings.Global.ADB_ENABLED,0)==1;
  if((peer!=0&&peer!=2000)||!DeviceHost.debuggableSystem()||!adbEnabled)
   throw new SecurityException("External debugging requires an ADB root/shell peer, a debuggable build and enabled Android ADB");
  if(q.optString("op").equals("assistant")){
   return new JSONObject().put("state","succeeded").put("data",AssistantConversation.dispatch(this,q.getString("action"),q.optJSONObject("params")==null?new JSONObject():q.getJSONObject("params")));
  }
  return dispatch(q);
 }
 JSONObject dispatch(JSONObject q) throws Exception {
  String op=q.getString("op");
  if(op.equals("authorization"))return Authorization.status(this);
  if(!Authorization.enabled(this))return Authorization.denied();
  if(op.equals("minutes")) {
   JSONObject unavailable=AgentCapabilities.guard(this,"minutes."+q.getString("action"));if(unavailable!=null)return unavailable;
   android.os.Bundle args=new android.os.Bundle();args.putString("json",q.optJSONObject("params")==null?"{}":q.getJSONObject("params").toString());
   android.os.Bundle result=getContentResolver().call(android.net.Uri.parse("content://os.aios.speech.minutes"),q.getString("action"),q.optJSONObject("params")==null?null:q.getJSONObject("params").optString("id",null),args);
   return new JSONObject().put("state","succeeded").put("data",new JSONObject(result.getString("json")));
  }
  if(op.equals("assistant"))throw new SecurityException("Assistant state is device-local; use the gated ADB debug entry point");
  if(op.equals("automation")&&q.optString("action").equals("sources"))return EventStore.sourceStatus();
  if(op.equals("automation")){try{return new JSONObject().put("state","succeeded").put("data",os.aios.automation.AutomationService.get().call(q.getString("action"),q.optJSONObject("params")==null?new JSONObject():q.getJSONObject("params")));}catch(Exception e){return new JSONObject().put("state","failed").put("error",e.getMessage());}}
  if(op.equals("workspace")) {
   try {return workspace.call(q.getString("action"),q.optJSONObject("params")==null?new JSONObject():q.getJSONObject("params"));}
   catch(IllegalArgumentException|IllegalStateException|org.json.JSONException e){return new JSONObject().put("state","failed").put("error",e.getMessage());}
  }
  if(op.equals("service"))return platform.execute(q.getString("service"),q.optJSONObject("params")==null?new JSONObject():q.getJSONObject("params"));
  return ui.call(q);
 }
 final Object screenshotLock=new Object();
 JSONObject nativeOperation(UiSessionController ui, JSONObject q) throws Exception {
  ScreenBackend.Session session=ui.session;
  String op=q.getString("op");
  switch(op) {
   case "tree":
    JSONArray windows=new JSONArray();
    int[] budget=new int[]{500};
    for(int pass=0;pass<2&&windows.length()==0;pass++){
     if(pass>0)ui.restoreProxy(session);
     for(android.view.accessibility.AccessibilityWindowInfo win:session.getAccessibilityWindows()){
      if(win.getDisplayId()!=ui.displayId||ui.displayId<=0)throw new SecurityException("Wrong display observation");
      android.view.accessibility.AccessibilityNodeInfo root=win.getRoot();
      if(root!=null)windows.put(node(root,0,budget));
     }
    }
    UiObservationFrame.sanitize(windows,session.getDisplaySize().getWidth(),session.getDisplaySize().getHeight());
    return ui.status().put("windows",windows).put("truncated",budget[0]<=0).put("captured_at_elapsed_ms",SystemClock.elapsedRealtime());
   case "launch": session.launchApplication(ui.target);break;
   case "tap": session.tap(q.getInt("x"),q.getInt("y"));break;
   case "double_tap": session.doubleTap(q.getInt("x"),q.getInt("y"),q.optInt("interval_ms",120));break;
   case "long_press": session.longPress(q.getInt("x"),q.getInt("y"));break;
   case "swipe": session.performGesture(new int[]{4},new int[]{2},new int[]{q.getInt("x1"),q.getInt("x2")},new int[]{q.getInt("y1"),q.getInt("y2")},0,q.optInt("duration_ms",400));break;
   case "gesture": UiGestureSpec g=UiGestureSpec.parse(q,session.getDisplaySize().getWidth(),session.getDisplaySize().getHeight());session.performGesture(g.ids,g.counts,g.xs,g.ys,g.holdMs,g.durationMs);break;
   case "text": session.insertText(q.getString("text"),q.optBoolean("replace",false),true);break;
   case "back": session.back();break;
   case "screenshot":
    synchronized(screenshotLock) {
    try(ScreenBackend.Frame frame=session.capture()) {
     if(frame==null)return ui.blocked("observation_unavailable").put("observation_source","screenshot");
     return encodeScreenshot(frame.bitmap,ui.status()).put("frame_timestamp_ns",frame.timestampNs);
    }
    }
   default: throw new IllegalArgumentException("Unsupported op: "+op);
  }
  return ui.status();
 }
 JSONObject encodeScreenshot(Bitmap source,JSONObject result)throws Exception{
  int[] size=UiObservationFrame.imageSize(source.getWidth(),source.getHeight());
  Bitmap image=Bitmap.createScaledBitmap(source,size[0],size[1],true);
  try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);
   return result.put("png_base64",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP))
    .put("width",size[0]).put("height",size[1]).put("display_width",source.getWidth()).put("display_height",source.getHeight())
    .put("coordinate_space","image_pixels").put("captured_at_elapsed_ms",SystemClock.elapsedRealtime())
    .put("coordinate_instruction","Use coordinates from this exact image. The host maps this observation credential to display pixels; do not manually rescale.");
  }finally{if(image!=source)image.recycle();}
 }
 JSONObject node(android.view.accessibility.AccessibilityNodeInfo n,int depth,int[] budget)throws Exception{
  budget[0]--;Rect bounds=new Rect();n.getBoundsInScreen(bounds);
  JSONObject r=new JSONObject().put("class",String.valueOf(n.getClassName())).put("id",n.getViewIdResourceName()).put("text",n.isPassword()?"[redacted]":String.valueOf(n.getText())).put("description",String.valueOf(n.getContentDescription())).put("clickable",n.isClickable()).put("enabled",n.isEnabled()).put("focused",n.isFocused()).put("visible_to_user",n.isVisibleToUser()).put("selected",n.isSelected()).put("checked",n.isChecked()).put("editable",n.isEditable()).put("scrollable",n.isScrollable()).put("bounds",new JSONArray(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom}));
  JSONArray children=new JSONArray();if(depth<20)for(int j=0;j<n.getChildCount()&&budget[0]>0;j++){android.view.accessibility.AccessibilityNodeInfo child=n.getChild(j);if(child!=null)children.put(node(child,depth+1,budget));}
  return r.put("children",children);
 }
}
