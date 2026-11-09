package os.aios.agent;
import android.app.*;import android.content.*;import android.database.sqlite.*;import android.os.*;import org.json.*;
/** Exercise the real coordinator against isolated records; no native tool is delivered. */
public final class HandoffRepeatTests extends Instrumentation {
 int checks;void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();String prefix="handoff-test-"+java.util.UUID.randomUUID()+"-";Context target=getTargetContext();java.util.Set<String> prefs=new java.util.HashSet<>();java.util.List<String> ids=new java.util.ArrayList<>();InterventionCoordinator coordinator=null;
 try{
  class Host extends AgentService{Host(){attachBaseContext(target);}public SharedPreferences getSharedPreferences(String name,int mode){prefs.add(prefix+name);return target.getSharedPreferences(prefix+name,mode);}public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return target.openOrCreateDatabase(prefix+name,mode,factory);}public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler){return target.openOrCreateDatabase(prefix+name,mode,factory,handler);}public java.io.File getDatabasePath(String name){return target.getDatabasePath(prefix+name);}public void sendBroadcast(Intent i){}}
  Host host=new Host();JSONObject current=new JSONObject().put("conversation_id","fixture-conversation").put("request_id","fixture-request").put("thread_id","fixture-thread").put("status","running");host.getSharedPreferences("assistant_conversation",0).edit().putString("state",current.toString()).commit();
  coordinator=new InterventionCoordinator(host);java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1);coordinator.delivery.execute(()->{try{gate.await();}catch(InterruptedException expected){}});
  for(int cycle=0;cycle<12;cycle++){
   String id=prefix+cycle;ids.add(id);JSONObject args=new JSONObject().put("kind","handoff").put("handoff_reason","system_permission").put("ui_session_id","").put("title","Interaction acceptance");
   JSONObject row=new JSONObject().put("id",id).put("thread_id","fixture-thread").put("assistant_request_id","fixture-request").put("conversation_id","fixture-conversation").put("turn_id","fixture-turn").put("call_id",id).put("arguments",args).put("snapshot",args.toString()).put("state","waiting_user").put("revision",1);
   coordinator.store.create(row,id);coordinator.begin(id,1);for(int i=0;i<5;i++)check(coordinator.begin(id,1).getLong("revision")==2,"repeat open does not revise same handoff");
   boolean rejected=false;try{coordinator.returnHandoff(id,"wrong-screen");}catch(SecurityException expected){rejected=true;}check(rejected,"wrong screen rejected");
   coordinator.returnHandoff(id,"");for(int i=0;i<5;i++)coordinator.returnHandoff(id,"");row=coordinator.store.get(id);check(row.getLong("revision")==3&&row.getJSONObject("result").getString("selected_option").equals("returned"),"repeat return has one result");
   rejected=false;try{coordinator.resolve(id,2,"cancel");}catch(IllegalStateException expected){rejected=true;}check(rejected,"ordinary stale decision remains rejected");
  }
  current.put("request_id","different-request");host.getSharedPreferences("assistant_conversation",0).edit().putString("state",current.toString()).commit();boolean rejected=false;try{coordinator.returnHandoff(ids.get(0),"");}catch(IllegalStateException expected){rejected=true;}check(rejected,"old task cannot return current task");
  out.putString("result","PASS "+checks+" checks");
 }catch(Throwable t){out.putString("error",android.util.Log.getStackTraceString(t));}finally{if(coordinator!=null){coordinator.delivery.shutdownNow();coordinator.close();}for(String id:ids)target.getSystemService(NotificationManager.class).cancel(id,31);target.deleteDatabase(prefix+"interventions.db");for(String name:prefs)target.deleteSharedPreferences(name);finish(out.containsKey("error")?0:-1,out);}}
}
