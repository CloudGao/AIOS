package os.aios.agent;
import android.content.SharedPreferences;import org.json.*;import os.aios.runtime.CoreRuntimeClient;
/** Durable close receipts: a physical display must disappear before its lease is returned. */
final class ScheduledScreenRelease {
 static SharedPreferences pending(AgentService s){return s.getSharedPreferences("schedule_screen_release",0);}
 static void after(AgentService s,String id,int display,String request,boolean notSubmitted){
  String receipt;
  try{receipt=new JSONObject().put("display",display).put("request_id",request).put("not_submitted",notSubmitted)
      .put("backend",DeviceHost.screens(s).implementationId()).toString();}catch(Exception e){android.util.Log.e("AIOSSchedule","Cannot encode screen release receipt",e);return;}
  if(!pending(s).edit().putString(id,receipt).commit()){android.util.Log.e("AIOSSchedule","Cannot persist screen release receipt");return;}
  new Thread(()->{try{reconcile(s);}catch(Exception e){android.util.Log.w("AIOSSchedule","Release receipt retained",e);}},"schedule-screen-release").start();
 }
 static synchronized void reconcile(AgentService s)throws Exception{
  os.aios.device.ScreenBackend backend=DeviceHost.screens(s);
  for(java.util.Map.Entry<String,?> entry:pending(s).getAll().entrySet()){
   Object stored=entry.getValue();
   // Old integer receipts are retained and reconciled, never erased by an upgrade.
   JSONObject receipt=stored instanceof Integer?new JSONObject().put("display",stored):new JSONObject((String)stored);
   if(!receipt.optString("backend",backend.implementationId()).equals(backend.implementationId()))continue;
   if(!receipt.optBoolean("not_submitted")&&backend.releaseState(receipt.optString("request_id"),receipt.optInt("display",-1))!=os.aios.device.ScreenBackend.ReleaseState.RELEASED)continue;
   new CoreRuntimeClient(s).call("internal","assistant.screen.release",new JSONObject().put("session_id",entry.getKey()));
   if(!pending(s).edit().remove(entry.getKey()).commit())throw new IllegalStateException("Cannot acknowledge release receipt");
  }
 }
}
