package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;import org.json.*;
public final class SchedulerOutboxProbe extends Instrumentation {
 String verifyIds=""; public void onCreate(Bundle b){super.onCreate(b);verifyIds=b.getString("verify_ids","");start();}
 public void onStart(){Bundle out=new Bundle();try{os.aios.runtime.CoreRuntimeClient client=new os.aios.runtime.CoreRuntimeClient(getTargetContext());
  JSONArray a=client.call("internal","assistant.result.pending",new JSONObject()).getJSONArray("items");int missingThread=0,missingTurn=0;JSONArray ids=new JSONArray(),retained=new JSONArray();
  for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);ids.put(e.getString("id"));if(e.optString("thread_id").isBlank())missingThread++;if(e.optString("turn_id").isBlank())missingTurn++;}
  if(!verifyIds.isBlank())for(String id:verifyIds.split(",")){JSONObject e=client.call("automation","execution.get",new JSONObject().put("id",id));retained.put(new JSONObject().put("id",e.getString("id")).put("state",e.getString("state")).put("pending_result",e.optBoolean("pending_result")));}
  out.putString("result",new JSONObject().put("pending_ids",ids).put("retained",retained).put("pending_count",a.length()).put("missing_thread",missingThread).put("missing_turn",missingTurn).toString());finish(-1,out);
 }catch(Throwable e){out.putString("error",e.toString());finish(0,out);}}
}
