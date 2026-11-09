package os.aios.automation;
import android.content.Context;import org.json.JSONObject;import os.aios.runtime.CoreRuntimeClient;
/** UI/Harness client. Scheduling and ledgers live in AIOSCoreServices. */
public final class AutomationService{
 private static AutomationService instance;private final CoreRuntimeClient runtime;private AutomationService(Context c){runtime=new CoreRuntimeClient(c);}
 public static synchronized void initialize(Context c,Object ignored){if(instance==null)instance=new AutomationService(c);}
 public static synchronized AutomationService get(){if(instance==null)throw new IllegalStateException("AIOS Core Services client not initialized");return instance;}
 public JSONObject call(String action,JSONObject p)throws Exception{return runtime.call("automation",action,p);}
 public JSONObject putAuthorization(JSONObject grant)throws Exception{return runtime.call("automation","authorization.put",grant);}
 public void reconcileAsync(){try{runtime.call("automation","reconcile",new JSONObject());}catch(Exception e){android.util.Log.e("AIOSAutomation","Core reconcile request failed",e);}}
}
