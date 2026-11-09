package os.aios.agent;
import android.app.*;
import android.content.*;
import android.view.WindowManager;
import org.json.*;
/** Host-owned screen decisions. Model mode is a preference, not permission to move an app. */
final class UiScreenRouter {
 final UiSessionManager manager;
 String request="",approvedPackage="",approvedMode="",choiceId="",choiceThread="",choicePackage="";
 JSONObject choiceResult;
 AlertDialog dialog;
 UiScreenRouter(UiSessionManager m){manager=m;}
 String request()throws Exception{return request(choiceThread);}
 String request(String thread)throws Exception{if(manager.service.scheduledAssistants!=null){JSONObject e=manager.service.scheduledAssistants.internal("thread",new JSONObject().put("thread_id",thread));if(e.length()>0)return e.getString("id");}return AssistantConversation.state(manager.service).optString("request_id");}
 String mode(String pkg,String requested,String thread)throws Exception{
  if(!requested.equals("background")&&!requested.equals("foreground"))throw new IllegalArgumentException("Invalid UI mode");
  String now=request(thread);if(!request.equals(now)){dismiss();request=now;approvedPackage="";approvedMode="";choiceId="";choiceResult=null;}
  if(manager.service.scheduledAssistants!=null){JSONObject scheduled=manager.service.scheduledAssistants.internal("thread",new JSONObject().put("thread_id",thread));if(scheduled.length()>0){String approved=manager.service.interventions.approvedScreen(thread,scheduled.getString("id"),pkg);if(approved.equals("background"))return "background";return pkg.equals(UiScreenContext.mainPackage(manager.service))?"choice":"background";}}
  if(manager.owner(pkg)!=null)return "background";
  if(pkg.equals(approvedPackage)&&thread.equals(choiceThread))return approvedMode;
  String recovered=manager.service.interventions.approvedScreen(thread,now,pkg);if(!recovered.isEmpty())return recovered;
  JSONObject current=AssistantConversation.state(manager.service);
  JSONObject origin=current.optJSONObject("ui_origin");
  boolean main=pkg.equals(UiScreenContext.mainPackage(manager.service));
  boolean same=!thread.isEmpty()&&thread.equals(current.optString("thread_id"))&&origin!=null&&origin.optString("virtual_session").isEmpty()&&pkg.equals(origin.optString("assistant_package"));
  return UiScreenPolicy.mode(false,main,same);
 }
 interface Continuation {JSONObject run(String mode)throws Exception;}
 JSONObject choose(String pkg,String thread,Continuation next)throws Exception{
  String now=request(thread);if(!request.equals(now)){dismiss();request=now;approvedPackage="";approvedMode="";}

  choicePackage=pkg;
  choiceId="screen-choice:"+java.util.UUID.randomUUID();choiceThread=thread;choiceResult=null;
  final String expected=request(thread),id=choiceId;
  String label=pkg;try{label=String.valueOf(manager.service.getPackageManager().getApplicationLabel(manager.service.getPackageManager().getApplicationInfo(pkg,0)));}catch(Exception ignored){}
  JSONObject envelope=manager.service.codexTools.activeEnvelope;
  if(envelope==null||!thread.equals(envelope.optString("thread_id")))throw new IllegalStateException("Screen choice requires an active owned tool call");
  JSONObject args=new JSONObject().put("kind","decision").put("title","选择操作屏幕").put("reason",label+"：请选择本次操作的位置").put("action_summary",new JSONObject().put("package",pkg)).put("dedupe_key",choiceId).put("options",new JSONArray().put(new JSONObject().put("id","background").put("label","移到后台")).put(new JSONObject().put("id","foreground").put("label","在前台操作")).put(new JSONObject().put("id","cancel").put("label","取消")));
  if(manager.service.scheduledAssistants!=null&&manager.service.scheduledAssistants.internal("thread",new JSONObject().put("thread_id",thread)).length()>0)args.getJSONArray("options").remove(1);
  JSONObject result=manager.service.interventions.request(envelope,args);String intervention=result.getString("intervention_id");
  manager.service.interventions.store.annotateScreen(intervention,pkg);
  return result;

 }
 JSONObject pending()throws Exception{return new JSONObject().put("state","screen_choice_required").put("session_id",choiceId).put("package",choicePackage).put("executed",false).put("next_step","A native screen-choice dialog is open. Wait for the user's choice and read status with this session_id. Do not create another session, move the app or send input. The choice result supplies the actual session_id.");}
 JSONObject status(String id,String thread)throws Exception{
  if(id.isEmpty()||!id.equals(choiceId))return null;
  if(!thread.isEmpty()&&!choiceThread.equals(thread))return new JSONObject().put("state","session_owned_by_other_conversation").put("executed",false);
  if(!request.equals(request())){dismiss();return new JSONObject().put("state","screen_choice_expired").put("executed",false);}
  return choiceResult==null?pending():new JSONObject(choiceResult.toString());
 }
 void answer(String expected,String id,String pkg,String mode,Continuation next){
  dismiss();
  try{
   JSONObject current=AssistantConversation.state(manager.service);
   if(!expected.equals(request())||!id.equals(choiceId)||current.optBoolean("cancel_requested")||!Authorization.enabled(manager.service)||manager.service.getSystemService(KeyguardManager.class).isKeyguardLocked()){cancel(id);return;}
   approvedPackage=pkg;approvedMode=mode;
   if(mode.equals("background")){
    if(manager.sessions.size()>=UiSessionManager.MAX_SLOTS){choiceResult=manager.listing().put("state","slot_limit").put("executed",false);return;}
    if(pkg.equals(UiScreenContext.mainPackage(manager.service)))manager.service.startActivity(
     new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
     ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
   }
   choiceResult=next.run(mode);
  }catch(Exception e){try{choiceResult=new JSONObject().put("state","screen_choice_failed").put("reason",String.valueOf(e.getMessage())).put("executed",false);}catch(Exception ignored){}}
 }
 void cancel(String id){dialog=null;if(!id.equals(choiceId))return;try{choiceResult=new JSONObject().put("state","user_cancelled").put("executed",false);}catch(Exception ignored){}}
 void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
}
