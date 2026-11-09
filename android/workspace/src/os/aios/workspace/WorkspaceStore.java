package os.aios.workspace;
import android.content.Context;import org.json.JSONObject;import os.aios.runtime.CoreRuntimeClient;
/** Thin client for system-level Workspace, Memory, Context and record services. */
public final class WorkspaceStore implements AutoCloseable{
 public static final String CHANGED="os.aios.workspace.CHANGED";private final CoreRuntimeClient runtime;
 public WorkspaceStore(Context context){runtime=new CoreRuntimeClient(context);}
 public JSONObject call(String op,JSONObject p)throws Exception{return runtime.call("workspace",op,p);}
 public JSONObject dream(String op,JSONObject p)throws Exception{return runtime.call("dream",op,p);}
 public String recordSkillEvidence(JSONObject p)throws Exception{return runtime.call("internal","skill.evidence",p).getString("id");}
 public String recordSkillEvidence(String tool,String pkg,String version)throws Exception{return runtime.call("internal","skill.evidence",new JSONObject().put("tool",tool).put("package",pkg).put("version",version)).getString("id");}
 public JSONObject recordExecution(JSONObject p)throws Exception{return runtime.call("internal","execution.record",p);}
 static String nullable(JSONObject p,String key)throws Exception{return !p.has(key)||p.isNull(key)||p.optString(key).isEmpty()?null:p.getString(key);}
 public void close(){}
}
