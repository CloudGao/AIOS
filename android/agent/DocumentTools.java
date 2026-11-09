package os.aios.agent;
import android.content.Context;
import org.json.*;
import os.aios.runtime.*;

/** Model parameters cannot select the trusted document owner or submission identity. */
final class DocumentTools {
    static JSONObject call(Context c,String action,JSONObject p)throws Exception{
        String owner=UiSessionManager.caller.get();if(owner==null||owner.isBlank())throw new SecurityException("document_host_identity_required");
        JSONObject args=new JSONObject(p.toString());args.put("_caller_thread",owner);
        if(action.equals("save")){
            AgentService agent=AgentService.instance;JSONObject envelope=agent==null||agent.codexTools==null?null:agent.codexTools.activeEnvelope;
            if(envelope==null)throw new SecurityException("document_host_identity_required");args.put("request_id",envelope.getString("request_id"));
        }
        JSONObject result=new CoreRuntimeClient(c).call("documents",action,args);localize(c,result);return result;
    }
    private static void localize(Context c,JSONObject r)throws Exception{String key=r.optString("message_key");if(key.equals("document_operation_failed"))r.put("message",c.getString(os.aios.runtime.R.string.document_operation_failed));if(key.equals("document_save_unknown"))r.put("message",c.getString(os.aios.runtime.R.string.document_save_unknown));if(r.optJSONObject("result")!=null)localize(c,r.getJSONObject("result"));}
}
