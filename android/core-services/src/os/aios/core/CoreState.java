package os.aios.core;
import android.content.Context;import org.json.JSONObject;
import os.aios.core.automation.AutomationService;import os.aios.core.events.EventHub;import os.aios.core.workspace.WorkspaceStore;
final class CoreState{
 static synchronized CoreFeatures.Documents documents()throws Exception{return CoreFeatures.documents(context);}
 private static os.aios.core.search.SearchCoordinator search;private static Context context;private static WorkspaceStore workspace;private static ContextProcessingService processing;
 private static boolean initialized,migrated;
 static synchronized void prepare(Context c){context=os.aios.host.HostBindings.applicationContext(c);if(!migrated){try{CoreMigration.run(context);migrated=true;}catch(Exception e){throw new IllegalStateException("Core state migration failed",e);}}}
 static synchronized void initialize(Context c){if(initialized)return;prepare(c);EventHub.initialize(context);if(workspace==null)workspace=new WorkspaceStore(context);AutomationService.initialize(context,new AutomationService.Host(){
  public boolean authorized(){return CoreState.authorized();}
  public Object read(String capability,JSONObject arguments)throws Exception{JSONObject result=workspace.call(capability.substring("workspace.".length()),arguments);if(!"succeeded".equals(result.optString("state")))throw new IllegalStateException(result.toString());return result.get("data");}
 });if(processing==null)processing=new ContextProcessingService(context);if(search==null)search=new os.aios.core.search.SearchCoordinator(context,workspace);initialized=true;}
 static os.aios.core.search.SearchCoordinator search(){return search;}
 static synchronized WorkspaceStore workspace(){if(workspace==null)throw new IllegalStateException("Core state unavailable before user unlock");return workspace;}
 static AutomationService automation(){return AutomationService.get();}
 static ContextProcessingService processing(){if(processing==null)throw new IllegalStateException("Core state unavailable before user unlock");return processing;}
 static boolean authorized(){return context.getSharedPreferences("policy",0).getBoolean("enabled",true);}
 static boolean setAuthorized(boolean value){boolean saved=context.getSharedPreferences("policy",0).edit().putBoolean("enabled",value).commit();if(saved){if(!value)CoreFeatures.revoke();context.sendBroadcast(new android.content.Intent("os.aios.core.POLICY_CHANGED").setPackage(os.aios.host.HostBindings.AGENT_PACKAGE),os.aios.host.HostBindings.CORE_PERMISSION);}return saved;}
}
