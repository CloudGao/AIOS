package os.aios.miniapp;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.*;

public final class MiniAppAcceptance extends Instrumentation {
 Store store;int checks;JSONArray failures=new JSONArray();String suffix=Long.toString(System.currentTimeMillis());HostActivity host;
 interface Test {void run()throws Exception;}
 void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
 void group(String name,Test t){try{t.run();}catch(Throwable e){failures.put(name+": "+e.toString());android.util.Log.e("MiniAppAcceptance",name,e);}}
 void rejects(Test t)throws Exception {boolean rejected=false;try{t.run();}catch(Exception e){rejected=true;}check(rejected,"Expected rejection");}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 JSONObject manifest(String id,int version)throws Exception {return new JSONObject().put("id",id).put("name","验收小程序").put("version",version).put("entry","index.html").put("capabilities",new JSONArray().put("notifications.cancel"));}
 String html="<!doctype html><html><meta name='viewport' content='width=device-width'><script src='/_ling/ling.js'></script><body>MiniApp acceptance<script>window.ready=true;window.openDb=()=>new Promise((resolve,reject)=>{let r=indexedDB.open('acceptance',1);r.onupgradeneeded=()=>r.result.createObjectStore('events');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error)});window.putEvent=async()=>{let d=await openDb();await new Promise((resolve,reject)=>{let t=d.transaction('events','readwrite');t.objectStore('events').put('saved event','day');t.oncomplete=resolve;t.onerror=()=>reject(t.error)});d.close();window.saved=true};window.getEvent=async()=>{let d=await openDb();let t=d.transaction('events');let r=t.objectStore('events').get('day');r.onsuccess=()=>{window.eventValue=r.result||'empty';d.close()}};</script></body></html>";
 JSONObject install(String id,int version,int expected,String token)throws Exception {
  JSONObject p=new JSONObject().put("id",id).put("expected_version",expected).put("request_id",token);
  JSONObject bundle=new JSONObject().put("manifest",manifest(id,version)).put("files",new JSONObject().put("index.html",android.util.Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP)));
  JSONObject result=store.install(p,bundle);check(store.install(p,bundle).getInt("version")==version,"install replay returns same receipt");
  bundle.getJSONObject("files").put("index.html",android.util.Base64.encodeToString((html+"changed").getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP));rejects(()->store.install(p,bundle));return result;
 }
 void open(String id)throws Exception {host=(HostActivity)startActivitySync(new Intent(getTargetContext(),HostActivity.class).putExtra("app_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(()->host.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));waitForIdleSync();await("window.ready===true");}
 String js(String expression)throws Exception {CountDownLatch latch=new CountDownLatch(1);String[] value={null};runOnMainSync(()->{if(host.web==null){latch.countDown();return;}host.web.evaluateJavascript(expression,v->{value[0]=v;latch.countDown();});});if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("JavaScript timeout");return value[0];}
 void await(String expression)throws Exception {long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if("true".equals(js(expression)))return;Thread.sleep(150);}throw new IllegalStateException("Condition timeout: "+expression+" resumed="+host.resumed+"; "+js("JSON.stringify({text:document.body.innerText,catalog:window.catalogResult,error:window.bridgeError,raw:window.lastRaw,secure:window.isSecureContext,random:typeof crypto.randomUUID})"));}
 void close()throws Exception {if(host!=null){runOnMainSync(()->{host.destroyWeb();host.finish();});waitForIdleSync();host=null;}}
 void delete(String id)throws Exception {CompletableFuture<?>[] f={null};runOnMainSync(()->f[0]=LibraryActivity.uninstall(getTargetContext(),id));f[0].get(30,TimeUnit.SECONDS);}
 public void onStart(){String a="mini-test-a",b="mini-test-b";try{
  store=Store.get(getTargetContext());
  group("clean previous fixtures",()->{for(String id:List.of(a,b)){try{store.app(id);delete(id);}catch(IllegalArgumentException absent){}}});
  group("validation and publication",()->{
   rejects(()->Store.path("../secret"));rejects(()->Store.path("a/../../x"));rejects(()->Store.path("/absolute"));rejects(()->Store.id("OTHER_APP"));
   install(a,1,0,"a"+suffix);check(store.publish("a"+suffix).getInt("version")==1,"publish retry");
   rejects(()->store.begin(new JSONObject().put("manifest",manifest(a,2)).put("expected_version",0).put("request_id","stale"+suffix)));
   rejects(()->store.begin(new JSONObject().put("manifest",manifest(a,2)).put("expected_version",1).put("request_id","a"+suffix)));
   rejects(()->store.write(new JSONObject().put("stage_id","a"+suffix).put("path","index.html").put("base64","")));
   install(b,1,0,"b"+suffix);check(!Store.origin(store.app(a)).equals(Store.origin(store.app(b))),"distinct origin");
   check(!Store.profile(store.app(a)).equals(Store.profile(store.app(b))),"distinct profile");
  });
  group("launcher directory and identity",()->{
   JSONObject entry=LauncherProvider.entry(store.app(a));
   check(entry.getString("id").equals(a),"directory ID");
   check(entry.getString("generation").equals(store.app(a).getString("generation")),"directory generation");
   check(!entry.has("grants")&&!entry.has("versions")&&!entry.has("files"),"directory excludes private management state");
   check(entry.getString("state").equals("installed"),"directory installed state");
   String old=store.app(a).getString("generation");
   check(!old.equals(store.app(b).getString("generation")),"independent launcher identities");
  });
  group("WebView local persistence and isolation",()->{
   open(a);check("true".equals(js("typeof ling==='object'")),"SDK loaded");js("localStorage.setItem('secret','A');putEvent();true");await("window.saved===true");close();
   open(b);check("null".equals(js("localStorage.getItem('secret')")),"localStorage isolated");js("localStorage.setItem('own','B');getEvent();true");await("window.eventValue==='empty'");close();
   open(a);check("\"A\"".equals(js("localStorage.getItem('secret')")),"localStorage retained");js("getEvent();true");await("window.eventValue==='saved event'");check(true,"IndexedDB retained");
  });
  group("Bridge catalog and authorization",()->{
   if(host==null)open(a);
   js("LingBridge.addEventListener('message', e=>window.lastRaw=e.data);try{ling.capabilities().then(r=>window.catalogResult=r).catch(e=>window.bridgeError=String(e))}catch(e){window.bridgeError=String(e)};true");await("window.catalogResult?.state==='succeeded'");
   js("ling.call('sms.compose',{number:'123',text:'not sent'}).then(r=>window.denied=r);true");await("window.denied?.state==='permission_required'");
   js("ling.call('shell.exec',{command:'id'}).then(r=>window.unsupported=r);true");await("window.unsupported?.state==='not_supported'");
   js("ling.call('notifications.cancel',{id:'fixture'},{requestId:'cancel1'}).then(r=>window.cancelResult=r);true");
   long end=System.currentTimeMillis()+10000;boolean clicked=false;
   while(System.currentTimeMillis()<end&&!clicked){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("允许"))if("允许".contentEquals(n.getText()==null?"":n.getText()))clicked=n.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(!clicked)Thread.sleep(200);}
   check(clicked,"native consent shown");await("window.cancelResult?.state==='succeeded'");check(store.permitted(a,store.app(a).getString("generation"),"notifications.cancel"),"grant persisted");
   js("ling.call('notifications.cancel',{id:'fixture'},{requestId:'cancel1'}).then(r=>window.retryResult=r);true");await("window.retryResult?.state==='succeeded'");
   js("ling.call('notifications.cancel',{id:'DIFFERENT'},{requestId:'cancel1'}).then(r=>window.conflictResult=r);true");await("window.conflictResult?.state==='failed'");
   js("ling.result('cancel1').then(r=>window.savedResult=r);true");await("window.savedResult?.state==='succeeded'");
   check(true,"conflict did not corrupt receipt");
  });
  group("update rollback and uninstall",()->{
   close();String gen=store.app(a).getString("generation");install(a,2,1,"upgrade"+suffix);check(gen.equals(store.app(a).getString("generation")),"update retains generation");
   open(a);check("\"A\"".equals(js("localStorage.getItem('secret')")),"update retains data");close();store.rollback(a,2,1);check(store.app(a).getJSONObject("grants").length()==0,"rollback clears grants");
   open(a);js("getEvent();true");await("window.eventValue==='saved event'");close();delete(a);rejects(()->store.app(a));
   install(a,1,0,"reinstall"+suffix);check(!gen.equals(store.app(a).getString("generation")),"reinstall fresh generation");open(a);check("null".equals(js("localStorage.getItem('secret')")),"uninstall cleared storage");close();
   check(store.app(b).getJSONObject("manifest").getInt("version")==1,"other app retained");open(b);check("\"B\"".equals(js("localStorage.getItem('own')")),"other app browser data retained");close();
  });
 }catch(Throwable e){failures.put("setup: "+e.toString());}finally{
  group("cleanup",()->{close();for(String id:List.of(a,b)){try{store.app(id);delete(id);}catch(IllegalArgumentException absent){}}});
  Bundle out=new Bundle();out.putInt("checks",checks);out.putString("failures",failures.toString());out.putString("stream","MiniApp checks="+checks+" failures="+failures+"\n");finish(failures.length()==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
 }}
}
