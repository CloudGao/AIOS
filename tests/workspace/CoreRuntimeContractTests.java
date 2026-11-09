package os.aios.core.workspace;
import org.json.*;

final class CoreRuntimeContractTests{
 static int run(WorkspaceStore store)throws Exception{int n=0;
  if(!os.aios.runtime.CoreContracts.canTransition(os.aios.runtime.CoreContracts.ExecutionState.SUBMITTING,os.aios.runtime.CoreContracts.ExecutionState.OUTCOME_UNKNOWN))throw new AssertionError("unknown transition");n++;
  boolean rejected=false;try{os.aios.runtime.CoreContracts.requireTransition("succeeded","running");}catch(IllegalStateException expected){rejected=true;}if(!rejected)throw new AssertionError("terminal mutation");n++;
  String sid="runtime-test";JSONObject first=store.recordExecution(new JSONObject().put("session_id",sid).put("request_id","write-1").put("param_fingerprint","a").put("state","submitting").put("side_effect","non_idempotent_write").put("receipt",new JSONObject()));store.recordExecution(new JSONObject().put("session_id",sid).put("request_id","write-1").put("param_fingerprint","a").put("state","outcome_unknown").put("side_effect","non_idempotent_write").put("receipt",new JSONObject()));if(!first.getString("request_id").equals("write-1"))throw new AssertionError("execution identity");n++;
  rejected=false;try{store.recordExecution(new JSONObject().put("session_id",sid).put("request_id","write-1").put("param_fingerprint","different").put("state","failed").put("side_effect","non_idempotent_write"));}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("fingerprint conflict");n++;
  JSONObject resource=new JSONObject().put("id","recording:1").put("uri","content://media/1").put("kind","audio").put("version","v1").put("permission_scope","aios:phone").put("trust","system_observation").put("mime","audio/wav");store.call("context.resource.upsert",resource);store.call("context.fragment.put",new JSONObject().put("id","fragment:1").put("resource_id","recording:1").put("resource_version","v1").put("processor_id","asr").put("processor_version","1").put("kind","transcript").put("locator","t=0..1000").put("text","项目会议记录").put("generation",1));JSONObject recall=store.call("recall.query",new JSONObject().put("query","会议").put("permission_scope","aios:phone")).getJSONObject("data");if(recall.getJSONArray("items").length()==0)throw new AssertionError("unified recall");n++;
  store.call("context.resource.invalidate",new JSONObject().put("id","recording:1").put("expected_version","v1").put("availability","deleted"));JSONObject after=store.call("recall.query",new JSONObject().put("query","会议").put("permission_scope","aios:phone")).getJSONObject("data");for(int i=0;i<after.getJSONArray("items").length();i++)if(after.getJSONArray("items").getJSONObject(i).optString("resource_id").equals("recording:1"))throw new AssertionError("deleted source resurrected");n++;
  return n;
 }
}

