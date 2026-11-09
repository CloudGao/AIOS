package os.aios.agent;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

final class InterventionCoordinator implements AutoCloseable {
 final AgentService service;final InterventionStore store;
 final ExecutorService delivery=Executors.newSingleThreadExecutor();
 static volatile String visibleThread="";

 InterventionCoordinator(AgentService s){service=s;store=new InterventionStore(s);s.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("interventions","需要你处理",NotificationManager.IMPORTANCE_HIGH));}
 JSONObject current(String thread)throws Exception {
  if(service.scheduledAssistants!=null){JSONObject e=service.scheduledAssistants.internal("thread",new JSONObject().put("thread_id",thread));if(e.length()>0)return new JSONObject().put("thread_id",thread).put("conversation_id","schedule:"+e.getString("rule_id")+":"+thread).put("request_id",e.getString("id")).put("cancel_requested",!Set.of("running","waiting_user","preparing").contains(e.getString("state")));}
  return AssistantConversation.state(service);
 }
 synchronized boolean waiting(String thread)throws Exception{
  if(thread.isEmpty())return false;JSONObject current=current(thread);boolean waiting=false;
  JSONArray rows=store.list(thread);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!Set.of("waiting_user","handoff_active").contains(row.optString("state")))continue;
   JSONObject arguments=row.getJSONObject("arguments");String sid=arguments.optString("ui_session_id");boolean screenLost=arguments.optString("kind").equals("handoff")&&!arguments.optString("handoff_reason").equals("system_permission")&&(service.ui.find(sid)==null||(!arguments.optString("handoff_reason").equals("system_control")&&service.ui.find(sid).session==null));
   boolean currentRequest=Authorization.enabled(service)&&!current.optBoolean("cancel_requested")&&row.getString("assistant_request_id").equals(current.optString("request_id"));
   if(screenLost||!currentRequest){JSONObject result=screenLost&&currentRequest?new JSONObject().put("state","screen_unavailable").put("intervention_id",row.getString("id")).put("executed",false).put("requires_observation",true).put("next_step","The original user-controlled screen was lost. Read current session state and recover without assuming the user completed the requested action."):null;JSONObject invalid=store.transition(row.getString("id"),row.getLong("revision"),"invalidated",result);changed(invalid);if(result!=null)deliver(row.getString("id"));continue;}waiting=true;
  }return waiting;
 }

 JSONObject request(JSONObject envelope,JSONObject args)throws Exception{
  String thread=envelope.getString("thread_id");JSONObject current=current(thread);
  if(thread.isBlank()||!thread.equals(current.optString("thread_id"))||current.optBoolean("cancel_requested"))throw new SecurityException("Request does not own active thread");
  String kind=args.getString("kind");if(!Set.of("decision","handoff").contains(kind))throw new IllegalArgumentException("Invalid intervention kind");
  if(args.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>12000)throw new IllegalArgumentException("Confirmation too large");
  if(args.optString("title").isBlank()||args.optString("title").length()>160||args.optString("reason").length()>1500)throw new IllegalArgumentException("Invalid confirmation description");
  JSONArray options=args.optJSONArray("options");if(kind.equals("decision")){if(options==null||options.length()<2||options.length()>5)throw new IllegalArgumentException("Provide 2–5 explicit choices");Set<String> ids=new HashSet<>();for(int i=0;i<options.length();i++){JSONObject o=options.getJSONObject(i);if(!o.getString("id").matches("[a-zA-Z0-9_-]{1,40}")||!ids.add(o.getString("id"))||o.optString("label").isBlank()||o.getString("label").length()>80)throw new IllegalArgumentException("Invalid choice");}}
  String sid=args.optString("ui_session_id");
  if(kind.equals("handoff")&&!args.optString("handoff_reason").equals("system_permission")){
   JSONObject owner=AssistantConversation.uiOwner(service,sid);if(sid.isEmpty()||!thread.equals(owner.optString("thread_id")))throw new SecurityException("Handoff must use an owned virtual screen");
  }
  if(args.optString("handoff_reason").equals("system_permission")){JSONArray permissions=args.getJSONArray("permissions");if(permissions.length()==0||permissions.length()>8)throw new IllegalArgumentException("Invalid permissions");for(int i=0;i<permissions.length();i++)if(!InterventionActionActivity.PERMISSIONS.contains(permissions.getString(i)))throw new SecurityException("Unsupported runtime permission");}
  String id=UUID.randomUUID().toString();JSONObject row=new JSONObject().put("id",id).put("thread_id",thread).put("conversation_id",current.getString("conversation_id")).put("assistant_request_id",current.getString("request_id")).put("turn_id",envelope.optString("turn_id")).put("call_id",envelope.getString("request_id")).put("snapshot",CodexToolGateway.canonical(args)).put("arguments",args).put("state","waiting_user").put("revision",1).put("created_at",System.currentTimeMillis());
  row=store.create(row,thread+":"+current.getString("request_id")+":"+args.optString("dedupe_key",envelope.getString("request_id")));
  if(service.scheduledAssistants!=null)service.scheduledAssistants.internal("intervention.wait",new JSONObject().put("thread_id",thread));
  changed(row);return new JSONObject().put("state","waiting_user").put("intervention_id",row.getString("id")).put("executed",false).put("deferred",true);
 }
 void requireCurrent(JSONObject row)throws Exception{JSONObject current=current(row.getString("thread_id"));if(!Authorization.enabled(service)||current.optBoolean("cancel_requested")||!row.getString("thread_id").equals(current.optString("thread_id"))||!row.getString("assistant_request_id").equals(current.optString("request_id")))throw new IllegalStateException("Original task changed; confirmation is invalid");}
 void requireRevision(JSONObject row,long revision)throws Exception{if(row.getLong("revision")!=revision||!Set.of("waiting_user","handoff_active").contains(row.getString("state")))throw new IllegalStateException(service.getString(R.string.assistant_request_changed));}
 synchronized void resolve(String id,long revision,String choice)throws Exception{
  if(service.getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException("Unlock before confirming");
  JSONObject row=store.get(id);requireCurrent(row);requireRevision(row,revision);JSONObject args=row.getJSONObject("arguments");boolean valid=choice.equals("cancel");
  JSONArray options=args.optJSONArray("options");if(options!=null)for(int i=0;i<options.length();i++)valid|=choice.equals(options.getJSONObject(i).getString("id"));
  if(args.getString("kind").equals("handoff"))valid=choice.equals("returned")||choice.equals("cancel");
  if(!valid)throw new IllegalArgumentException("Unknown choice");
  if(args.optString("handoff_reason").equals("third_party_permission")&&choice.equals("returned")){UiSessionController ui=service.ui.find(args.getString("ui_session_id"));if(ui==null||ui.session==null)throw new IllegalStateException("Original permission screen lost");ui.refreshPermissionState();if(ui.permissionPresent)throw new IllegalStateException("请先完成原生权限选择");ui.resumePermissionSession();}
  JSONObject result=new JSONObject().put("state","succeeded").put("intervention_id",id).put("selected_option",choice).put("executed",false).put("requires_observation",true).put("resolved_at",System.currentTimeMillis());
  if(row.has("host_screen_package"))result.put("next_step","Screen choice recorded. Retry the original open/create operation; the host will apply this request-scoped choice.");
  row=store.transition(id,revision,"resolved",result);
  if(choice.equals("cancel")&&args.optString("handoff_reason").equals("system_control")){UiSessionController ui=service.ui.find(args.optString("ui_session_id"));if(ui!=null)ui.handler.post(()->ui.release("paused",service.getString(R.string.assistant_current_operation_stopped_07441)));}
  if(row.has("host_screen_package")&&choice.equals("background")&&row.getString("host_screen_package").equals(UiScreenContext.mainPackage(service)))service.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
  changed(row);deliver(id);
 }
 synchronized void returnHandoff(String id,String session)throws Exception{
  if(service.getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException(service.getString(R.string.assistant_please_unlock_first_83552));
  JSONObject row=store.get(id);requireCurrent(row);JSONObject args=row.getJSONObject("arguments");
  if(!args.optString("kind").equals("handoff")||!session.equals(args.optString("ui_session_id")))throw new SecurityException(service.getString(R.string.assistant_request_changed));
  if(row.optString("state").equals("resolved")&&row.optJSONObject("result")!=null&&row.getJSONObject("result").optString("selected_option").equals("returned"))return;
  if(row.optString("state").equals("invalidated")&&row.optJSONObject("result")!=null&&row.getJSONObject("result").optString("state").equals("screen_unavailable")){deliver(id);return;}
  resolve(id,row.getLong("revision"),"returned");
 }
 void systemControlResult(String session,boolean granted)throws Exception{
  JSONArray rows=store.pending();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i),args=row.getJSONObject("arguments");if(!Set.of("waiting_user","handoff_active").contains(row.optString("state"))||!args.optString("handoff_reason").equals("system_control")||!session.equals(args.optString("ui_session_id")))continue;
   permissionResult(row.getString("id"),row.getLong("revision"),new JSONObject().put("computer_control",granted));
   service.startForegroundService(new Intent(service,AssistantOrbService.class).setAction("show").putExtra("intervention_id",row.getString("id")));
  }
 }
 synchronized void permissionResult(String id,long revision,JSONObject grants)throws Exception{JSONObject row=store.get(id);requireCurrent(row);JSONObject result=new JSONObject().put("state","succeeded").put("intervention_id",id).put("permissions",grants).put("executed",false).put("requires_observation",true).put("resolved_at",System.currentTimeMillis());row=store.transition(id,revision,"resolved",result);changed(row);deliver(id);}
 synchronized JSONObject begin(String id,long revision)throws Exception{JSONObject row=store.get(id);requireCurrent(row);if(row.optString("state").equals("handoff_active")&&row.getJSONObject("arguments").optString("kind").equals("handoff")&&revision>0&&revision<=row.getLong("revision"))return row;requireRevision(row,revision);JSONObject args=row.getJSONObject("arguments");String sid=args.optString("ui_session_id");if(!args.optString("handoff_reason").equals("system_permission")){UiSessionController ui=service.ui.find(sid);if(ui==null||(!args.optString("handoff_reason").equals("system_control")&&ui.session==null)||!row.getString("thread_id").equals(AssistantConversation.uiOwner(service,sid).optString("thread_id")))throw new IllegalStateException("Original screen unavailable");if(args.optString("handoff_reason").equals("system_control"))ui.openSystemConsent();else if(args.optString("handoff_reason").equals("third_party_permission"))ui.startPermissionHandoff();else ui.enterMirrorUserControl();}row=store.transition(id,revision,"handoff_active",null);changed(row);return row;}
 void deliver(String id){delivery.execute(()->{try{JSONObject row=store.get(id);if(!row.has("result"))return;requireCurrent(row);if(service.scheduledAssistants!=null&&!service.scheduledAssistants.internal("intervention.resume",new JSONObject().put("thread_id",row.getString("thread_id"))).optBoolean("ready"))return;try(CodexAccountClient client=new CodexAccountClient(service)){JSONObject result=client.call(new JSONObject().put("op","intervention/resolve").put("params",new JSONObject().put("id",id).put("thread_id",row.getString("thread_id")).put("call_id",row.getString("call_id")).put("result",row.getJSONObject("result"))));if(result.optBoolean("delivered")){if(!result.optString("turn_id").isEmpty()){JSONObject scheduled=service.scheduledAssistants==null?new JSONObject():service.scheduledAssistants.internal("thread",new JSONObject().put("thread_id",row.getString("thread_id")));if(scheduled.length()>0)service.scheduledAssistants.checkpoint(scheduled,new JSONObject().put("turn_id",result.getString("turn_id")));else AssistantConversation.resumeIntervention(service,row.getString("assistant_request_id"),result.getString("turn_id"));}store.delivered(id);}}}catch(Exception e){android.util.Log.w("AIOSIntervention","Result retained for recovery",e);}});}
 void changed(JSONObject row)throws Exception{
  service.sendBroadcast(new Intent(AssistantConversation.CHANGED).setPackage(service.getPackageName()));
  service.sendBroadcast(new Intent(os.aios.workspace.WorkspaceStore.CHANGED).setPackage(service.getPackageName()));
  String id=row.getString("id");NotificationManager nm=service.getSystemService(NotificationManager.class);
  if(!Set.of("waiting_user","handoff_active").contains(row.getString("state"))){nm.cancel(id,31);return;}
  JSONObject args=row.getJSONObject("arguments");Notification.Builder b=DeviceHost.ui().interventionNotification(service,row.getString("thread_id").equals(visibleThread)).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(os.aios.core.DisplayText.clean("小助手需要你处理")).setContentText(os.aios.core.DisplayText.clean(args.getString("title"))).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(DeviceHost.ui().interventionNotification(service,row.getString("thread_id").equals(visibleThread)).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(os.aios.core.DisplayText.clean("小助手需要你处理")).setContentText(os.aios.core.DisplayText.clean("解锁后查看")).build()).setOnlyAlertOnce(true).setContentIntent(intent(row,"open"));
  if(args.getString("kind").equals("decision")&&!args.optBoolean("sensitive",true)&&args.toString().length()<600){JSONArray choices=args.getJSONArray("options");for(int i=0;i<Math.min(3,choices.length());i++){JSONObject o=choices.getJSONObject(i);b.addAction(new Notification.Action.Builder(null,os.aios.core.DisplayText.clean(o.getString("label")),intent(row,o.getString("id"))).build());}}
  else b.addAction(new Notification.Action.Builder(null,args.optString("handoff_reason").equals("system_permission")?"授权":"查看并处理",intent(row,"open")).build());
  nm.notify(id,31,b.build());
 }
 PendingIntent intent(JSONObject row,String choice)throws Exception{Intent i=new Intent(service,InterventionActionActivity.class).setData(android.net.Uri.parse("aios://intervention/"+row.getString("id")+"/"+row.getLong("revision")+"/"+choice)).putExtra("id",row.getString("id")).putExtra("revision",row.getLong("revision")).putExtra("choice",choice);return PendingIntent.getActivity(service,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 String approvedScreen(String thread,String request,String pkg)throws Exception{JSONArray rows=store.list(thread);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(request.equals(row.optString("assistant_request_id"))&&pkg.equals(row.optString("host_screen_package"))&&row.optString("state").equals("resolved")){String mode=row.getJSONObject("result").optString("selected_option");if(Set.of("foreground","background").contains(mode))return mode;}}return "";}
 void recover(){try{JSONArray rows=store.pending();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);try{requireCurrent(row);changed(row);if(row.has("result"))deliver(row.getString("id"));}catch(Exception stale){if(Set.of("waiting_user","handoff_active").contains(row.optString("state")))changed(store.transition(row.getString("id"),row.getLong("revision"),"invalidated",null));}}}catch(Exception e){android.util.Log.w("AIOSIntervention","Recovery retained for retry",e);}}
 void mainScreenLocked(){try{JSONObject state=AssistantConversation.state(service);if(!state.optString("thread_id").equals(service.ui.foreground.threadId)||!state.optString("status").equals("running"))return;String request=state.getString("request_id");AssistantConversation.boundaryPause(service,request,"主屏锁定");delivery.execute(()->{try(CodexAccountClient client=new CodexAccountClient(service)){client.call(new JSONObject().put("op","execution/stop").put("params",new JSONObject().put("threadId",state.getString("thread_id")).put("turnId",state.optString("turn_id")).put("pauseGoal",true)));}catch(Exception e){android.util.Log.e("AIOSIntervention","Main-screen stop requires recovery",e);}});}catch(Exception e){android.util.Log.e("AIOSIntervention","Cannot preserve locked task",e);}}
 void deleteThread(String thread){try{if(thread.equals(AssistantConversation.state(service).optString("thread_id")))AssistantConversation.cancel(service);delivery.execute(()->{try(CodexAccountClient client=new CodexAccountClient(service)){client.call(new JSONObject().put("op","intervention/forget").put("params",new JSONObject().put("thread_id",thread)));}catch(Exception e){android.util.Log.w("AIOSIntervention","Native deferred request cleanup pending",e);}});JSONArray rows=store.list(thread);for(int i=0;i<rows.length();i++)service.getSystemService(NotificationManager.class).cancel(rows.getJSONObject(i).getString("id"),31);store.deleteThread(thread);}catch(Exception e){android.util.Log.e("AIOSIntervention","Conversation cleanup failed",e);}}
 public void close(){delivery.shutdown();store.close();}
}
