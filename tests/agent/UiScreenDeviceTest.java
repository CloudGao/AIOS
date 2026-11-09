package os.aios.agent;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.util.*;
/** Runs real display/session transitions; never submits purchases or changes user app data. */
public final class UiScreenDeviceTest extends Instrumentation {
 AgentService service;String thread;JSONObject original;final JSONArray results=new JSONArray();final List<String> created=new ArrayList<>();int checks;
 void check(boolean ok,String name)throws Exception{results.put(new JSONObject().put("test",name).put("passed",ok));checks++;if(!ok)throw new AssertionError(name);}
 JSONObject call(JSONObject q)throws Exception{UiSessionManager.caller.set(thread);try{return service.ui.call(q);}finally{UiSessionManager.caller.remove();}}
 JSONObject op(String name,String id)throws Exception{return call(new JSONObject().put("op",name).put("session_id",id));}
 JSONObject create(String pkg)throws Exception{return call(new JSONObject().put("op","create").put("package",pkg).put("mode","background"));}
 void origin(String pkg)throws Exception{JSONObject state=new JSONObject(original.toString()).put("request_id",UUID.randomUUID().toString()).put("ui_origin",new JSONObject().put("assistant_package",pkg).put("virtual_session",""));getTargetContext().getSharedPreferences("assistant_conversation",0).edit().putString("state",state.toString()).commit();}
 JSONObject active(JSONObject result)throws Exception{for(int i=0;i<120;i++){if(result.optString("state").equals("active"))return result;if(result.optString("state").equals("needs_user_consent"))allowOnce();Thread.sleep(500);result=op("status",result.getString("session_id"));}throw new AssertionError("not active: "+result);}
 void allowOnce(){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();if(root==null||!"com.android.virtualdevicemanager".contentEquals(root.getPackageName()))return;for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("仅限这一次")){for(int i=0;i<3&&n!=null;i++,n=n.getParent())if(n.isClickable()){n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);return;}}}
 void launch(String pkg)throws Exception{Intent i=getTargetContext().getPackageManager().getLaunchIntentForPackage(pkg);getTargetContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());Thread.sleep(1000);}
 void home()throws Exception{getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());Thread.sleep(700);}
 void close(String id)throws Exception{op("close",id);created.remove(id);Thread.sleep(300);}
 void observe(JSONObject session,String name)throws Exception{String id=session.getString("session_id");JSONObject tree=op("tree",id);check(tree.optInt("display_id",-1)==session.getInt("display_id"),name+" tree stays on display");if(tree.optString("state").equals("observation_unavailable"))check(!tree.optString("next_step").contains("leave the foreground"),name+" no false foreground advice");JSONObject shot=op("screenshot",id);for(int i=0;i<8&&shot.optString("state").equals("observation_pending");i++){Thread.sleep(500);shot=op("screenshot",id);}check(!shot.optString("png_base64").isEmpty(),name+" screenshot available");android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(android.util.Base64.decode(shot.getString("png_base64"),0),0,android.util.Base64.decode(shot.getString("png_base64"),0).length);try{check(bitmap.getWidth()==shot.getInt("width")&&bitmap.getHeight()==shot.getInt("height"),name+" exact model image dimensions");check(Math.max(bitmap.getWidth(),bitmap.getHeight())<=2048,name+" no runtime downscale");check(shot.getString("coordinate_space").equals("image_pixels"),name+" image coordinate contract");}finally{bitmap.recycle();}}
 JSONObject findText(JSONObject node,String text)throws Exception{if(text.equals(node.optString("text"))||text.equals(node.optString("description")))return node;JSONArray children=node.optJSONArray("children");if(children!=null)for(int i=0;i<children.length();i++){JSONObject found=findText(children.getJSONObject(i),text);if(found!=null)return found;}return null;}
 void tapStopwatch(JSONObject session)throws Exception{JSONObject tree=op("tree",session.getString("session_id")),target=null;JSONArray windows=tree.optJSONArray("windows");if(windows!=null)for(int i=0;i<windows.length()&&target==null;i++)target=findText(windows.getJSONObject(i),"秒表");check(target!=null,"main stopwatch target observed");JSONArray b=target.getJSONArray("bounds");JSONObject result=call(new JSONObject().put("op","tap").put("session_id",session.getString("session_id")).put("observation_id",tree.getString("observation_id")).put("x",(b.getInt(0)+b.getInt(2))/2).put("y",(b.getInt(1)+b.getInt(3))/2));check(result.optBoolean("executed"),"main-screen observed tap accepted");check(op("status",session.getString("session_id")).getInt("display_id")==0,"main input stays on main");JSONObject shot=op("screenshot",session.getString("session_id"));int ix=(int)(((b.getInt(0)+b.getInt(2))/2.0)*shot.getInt("width")/shot.getInt("display_width")),iy=(int)(((b.getInt(1)+b.getInt(3))/2.0)*shot.getInt("height")/shot.getInt("display_height"));JSONObject imageTap=call(new JSONObject().put("op","tap").put("session_id",session.getString("session_id")).put("observation_id",shot.getString("observation_id")).put("x",ix).put("y",iy));check(imageTap.optBoolean("executed"),"main image credential click accepted");check(imageTap.optString("input_outcome").equals("submitted_once"),"action separate from readback");}
 void fallback(JSONObject session)throws Exception{
  String id=session.getString("session_id");JSONObject request=new JSONObject().put("op","tree").put("session_id",id);
  JSONObject empty=new JSONObject().put("state","observation_unavailable").put("session_state","active").put("session_id",id);
  JSONObject recovered=UiObservationRecovery.resolve(request,empty,q->q.optString("op").equals("tree")?new JSONObject(empty.toString()):op("screenshot",id),Thread::sleep);
  check(recovered.has("png_base64")&&!recovered.optBoolean("tree_available",true),"injected empty tree recovers actual same-screen image");
  check(recovered.getInt("display_id")==session.getInt("display_id"),"fallback never moves display");
  check(recovered.getString("coordinate_space").equals("image_pixels"),"fallback image owns coordinate space");
  JSONObject receipt=call(new JSONObject().put("op","tap").put("session_id",id).put("observation_id",recovered.getString("observation_id")).put("x",20).put("y",20));
  check(receipt.optBoolean("executed"),"fallback credential accepts actual image-coordinate input");
  check(receipt.optString("input_outcome").equals("submitted_once"),"fallback input submitted once");
 }
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle output=new Bundle();Throwable failure=null;
 try{
  getTargetContext().startForegroundService(new Intent(getTargetContext(),AgentService.class));for(int i=0;i<80&&AgentService.instance==null;i++){Thread.sleep(200);if(i%10==9)getTargetContext().startForegroundService(new Intent(getTargetContext(),AgentService.class));}service=AgentService.instance;if(service==null)throw new AssertionError("service unavailable");
  String enabled=android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(),"enabled_accessibility_services");
  if(enabled!=null&&enabled.contains("os.aios.agent/")){
   // Instrumentation force-stops its target, so reconnect an already user-enabled service.
   UiAutomation automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);automation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS");
   try{String filtered=String.join(":",Arrays.stream(enabled.split(":")).filter(x->!x.startsWith("os.aios.agent/")).toArray(String[]::new));android.provider.Settings.Secure.putString(getTargetContext().getContentResolver(),"enabled_accessibility_services",filtered);Thread.sleep(300);android.provider.Settings.Secure.putString(getTargetContext().getContentResolver(),"enabled_accessibility_services",enabled);}finally{automation.dropShellPermissionIdentity();}
   for(int i=0;i<30&&ForegroundUiService.instance==null;i++)Thread.sleep(200);
  }
  original=AssistantConversation.state(getTargetContext());thread=original.getString("thread_id");home();origin("com.android.launcher3");
  JSONObject before=call(new JSONObject().put("op","list"));
  JSONArray baseline=before.getJSONArray("sessions");for(int i=0;i<baseline.length();i++){JSONObject row=baseline.getJSONObject(i);if(row.optString("mode").equals("background")&&row.optString("state").equals("paused")){String previous=thread;thread=row.getString("thread_id");try{active(op("resume",row.getString("session_id")));}finally{thread=previous;}}}
  int existing=before.getInt("slot_count");check(existing<3,"baseline preserves original slots and leaves a shared slot for the fixture");
  results.put(new JSONObject().put("quota_before",new os.aios.runtime.CoreRuntimeClient(getTargetContext()).call("automation","assistant.status",new JSONObject())));String[] packages={"com.android.deskclock","com.android.calculator2","com.android.settings","org.lineageos.jelly"};Set<Integer> displays=new HashSet<>();
  for(int i=0;i<3-existing;i++){JSONObject s=create(packages[i]);created.add(s.getString("session_id"));s=active(s);check(s.getInt("display_id")>0&&s.getString("mode").equals("background"),"virtual slot "+i);check(displays.add(s.getInt("display_id")),"independent display "+i);check(create(packages[i]).getString("session_id").equals(s.getString("session_id")),"repeat create reuses slot "+i);results.put(new JSONObject().put("quota_after_"+i,new os.aios.runtime.CoreRuntimeClient(getTargetContext()).call("automation","assistant.status",new JSONObject())));if(i==0){observe(s,"clock background");fallback(s);}}
  JSONObject fourth=create("org.lineageos.jelly");if(fourth.has("session_id"))created.add(fourth.getString("session_id"));results.put(new JSONObject().put("quota_after_overflow",new os.aios.runtime.CoreRuntimeClient(getTargetContext()).call("automation","assistant.status",new JSONObject())));check(fourth.optString("state").equals("waiting_resource")&&!fourth.optBoolean("executed"),"fourth user screen waits without taking scheduler reservation: "+fourth.toString());check(call(new JSONObject().put("op","list")).getInt("slot_count")==3,"three shared slots retained");check(call(new JSONObject().put("op","list")).getInt("active_count")==3,"three native shared displays concurrently active");
  String keep=created.get(0),real=thread;thread="other-thread";check(op("tree",keep).optString("state").equals("session_owned_by_other_conversation"),"cross-thread denied");thread=real;
  for(String id:new ArrayList<>(created))close(id);
  launch("com.android.deskclock");
  getTargetContext().startForegroundService(new Intent(getTargetContext(),AssistantOrbService.class));Thread.sleep(700);
  check(UiScreenContext.capture(getTargetContext()).optString("assistant_package").equals("com.android.deskclock"),"real assistant captures main app");
  origin("com.android.deskclock");JSONObject front=active(create("com.android.deskclock"));created.add(front.getString("session_id"));check(front.getInt("display_id")==0,"assistant on target uses main");observe(front,"clock foreground");tapStopwatch(front);
  String previousFront=front.getString("session_id");origin("com.android.deskclock");check(op("tree",previousFront).optString("state").equals("screen_route_required"),"second request cannot reuse stale main lease");
  front=active(create("com.android.deskclock"));created.remove(previousFront);created.add(front.getString("session_id"));check(front.getInt("display_id")==0&&!front.getString("session_id").equals(previousFront),"second main request reroutes with fresh lease");
  close(front.getString("session_id"));getTargetContext().stopService(new Intent(getTargetContext(),AssistantOrbService.class));Thread.sleep(300);
  origin("com.android.deskclock");JSONObject other=active(create("com.android.settings"));created.add(other.getString("session_id"));check(other.getInt("display_id")>0,"other target uses virtual");check(UiScreenContext.mainPackage(getTargetContext()).equals("com.android.deskclock"),"other target preserves main");close(other.getString("session_id"));
  // Direct instrumentation has no owned model tool envelope. Current intervention routing
  // must reject this call instead of showing the obsolete standalone choice dialog.
  launch("com.android.deskclock");origin("com.android.launcher3");boolean rejected=false;
  try{create("com.android.deskclock");}catch(Exception expected){rejected=String.valueOf(expected).contains("active owned tool call");}
  check(rejected,"screen conflict without owned tool envelope fails closed");
  home();check(call(new JSONObject().put("op","list")).getInt("slot_count")==existing,"original slots preserved");
 }catch(Throwable e){failure=e;results.put(e.toString());}
 finally{
  if(service!=null)for(String id:new ArrayList<>(created))try{close(id);}catch(Exception ignored){}
  if(original!=null)getTargetContext().getSharedPreferences("assistant_conversation",0).edit().putString("state",original.toString()).commit();
 }
 try{output.putString("stream",new JSONObject().put("checks",checks).put("results",results).toString());}catch(Exception ignored){output.putString("stream",results.toString());}finish(failure==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,output);
 }
}
