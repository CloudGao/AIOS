package os.aios.agent;
import android.content.*;
import org.json.*;
import java.util.UUID;
/** Durable voice request state; native bridge already restricts callers by UID. */
final class AssistantConversation {
 static final String CHANGED="os.aios.agent.ASSISTANT_CHANGED";
 private static final int HISTORY_LIMIT=24;
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("assistant_conversation",0);}
 private static android.content.SharedPreferences binding(Context c){return c.getSharedPreferences("assistant_ui_binding",0);}
 static synchronized JSONObject state(Context c) throws JSONException{return new JSONObject(prefs(c).getString("state","{}"));}
 private static android.content.SharedPreferences archive(Context c){return c.getSharedPreferences("assistant_conversation_archive",0);}
 private static android.content.SharedPreferences owners(Context c){return c.getSharedPreferences("ui_conversation_owners",0);}
 private static void save(Context c,JSONObject s){
  String id=s.optString("conversation_id");
  if(!id.isEmpty()&&!archive(c).edit().putString(id,s.toString()).commit())throw new IllegalStateException("Unable to archive conversation");
  if(!prefs(c).edit().putString("state",s.toString()).commit())throw new IllegalStateException("Unable to save conversation");
  AssistantSessionPublisher.publish(c,s);
  c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
 }
 private static void requireUnbound(Context c){if(!activeUiSession(c).isEmpty())throw new IllegalStateException(c.getString(R.string.assistant_this_screen_is_bound_to_one_conversation_close_the_assistant_and__0ce0b));}
 static synchronized void voiceState(Context c,String value)throws Exception {JSONObject s=state(c);s.put("voice_state",value);if(!value.isEmpty())s.put("notice","").put("notice_resource","");save(c,s);}
 static synchronized JSONObject fresh(Context c) throws Exception {requireUnbound(c);requireIdle(c);JSONObject s=new JSONObject().put("conversation_id",UUID.randomUUID().toString()).put("status","idle").put("history",new JSONArray());save(c,s);return s;}
 private static void requireIdle(Context c)throws Exception {String status=state(c).optString("status");if(status.equals("queued")||status.equals("running"))throw new IllegalStateException(c.getString(R.string.assistant_stop_or_wait_for_the_current_request_before_switching_conversatio_c5d8d));}
 static synchronized void selectThread(Context c,String threadId)throws Exception {requireUnbound(c);requireIdle(c);if(threadId.isBlank()||threadId.length()>100)throw new IllegalArgumentException(c.getString(R.string.assistant_invalid_phone_conversation_f0667));android.content.SharedPreferences threads=c.getSharedPreferences("assistant_codex_threads",0);String conversationId=null;for(java.util.Map.Entry<String,?> entry:threads.getAll().entrySet())if(entry.getValue() instanceof String&&threadId.equals(entry.getValue())){conversationId=entry.getKey();break;}if(conversationId==null)throw new IllegalStateException(c.getString(R.string.assistant_this_record_is_not_linked_to_a_local_assistant_conversation_b8398));JSONObject restored=new JSONObject(archive(c).getString(conversationId,"{}"));if(!restored.has("conversation_id"))restored.put("conversation_id",conversationId).put("thread_id",threadId).put("status","idle").put("history",new JSONArray());save(c,restored);}
 static synchronized JSONObject enqueueTask(Context c,String taskId)throws Exception {requireIdle(c);try(os.aios.workspace.WorkspaceStore store=new os.aios.workspace.WorkspaceStore(c)){JSONObject task=store.call("task.get",new JSONObject().put("id",taskId)).getJSONObject("data");if("done".equals(task.optString("status")))throw new IllegalStateException(c.getString(R.string.assistant_task_is_complete_edit_its_status_before_continuing_80669));JSONArray sessions=task.optJSONArray("sessions");String thread="";if(sessions!=null)for(int i=0;i<sessions.length();i++){JSONObject session=sessions.getJSONObject(i);if("codex".equals(session.optString("source"))){thread=session.optString("source_id");break;}}if(thread.isEmpty())fresh(c);else selectThread(c,thread);JSONObject queued=enqueue(c,c.getString(R.string.assistant_continue_task_9128c)+task.getString("title"));queued.put("task_id",taskId);save(c,queued);return queued;}}
 static synchronized void registerScheduled(Context c,String rule,String thread,String title)throws Exception {
  try(os.aios.workspace.WorkspaceStore w=new os.aios.workspace.WorkspaceStore(c)){w.call("sync.automation",new JSONObject().put("thread_id",thread));}
  String id="schedule:"+rule+":"+thread;c.getSharedPreferences("assistant_codex_threads",0).edit().putString(id,thread).commit();
  if(!archive(c).contains(id))archive(c).edit().putString(id,new JSONObject().put("conversation_id",id).put("thread_id",thread).put("status","scheduled").put("history",new JSONArray()).put("title",title).put("scheduled",true).toString()).commit();
 }
 static synchronized void scheduledResult(Context c,String rule,String thread,String executionId,String status,String reply)throws Exception {
  String id="schedule:"+rule+":"+thread;JSONObject s=new JSONObject(archive(c).getString(id,"{}"));s.put("conversation_id",id).put("thread_id",thread).put("status",status.equals("succeeded")?"completed":status);JSONObject ids=s.optJSONObject("schedule_result_ids");if(ids==null)ids=new JSONObject();if(!ids.optBoolean(executionId)){append(s,"assistant",reply);ids.put(executionId,true);s.put("schedule_result_ids",ids);}archive(c).edit().putString(id,s.toString()).commit();c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
 }
 static synchronized String conversationForThread(Context c,String thread)throws Exception {
  if(thread.isEmpty())throw new IllegalStateException(c.getString(R.string.assistant_screen_operation_has_no_conversation_identity_ddad9));
  for(java.util.Map.Entry<String,?> e:c.getSharedPreferences("assistant_codex_threads",0).getAll().entrySet())
   if(thread.equals(e.getValue()))return e.getKey();
  throw new IllegalStateException(c.getString(R.string.assistant_screen_operation_is_not_linked_to_a_local_conversation_951f4));
 }
 static synchronized void attachUiSession(Context c,String sessionId,String thread)throws Exception {
  String conversation=conversationForThread(c,thread);
  JSONObject previous=uiOwner(c,sessionId);if(previous.length()>0&&!thread.equals(previous.optString("thread_id")))throw new IllegalStateException(c.getString(R.string.assistant_this_screen_belongs_to_another_conversation_and_cannot_be_reassig_81c15));
  JSONObject current=state(c);if(conversation.equals(current.optString("conversation_id")))save(c,current);
  if(!owners(c).edit().putString(sessionId,new JSONObject().put("conversation_id",conversation).put("thread_id",thread).toString()).commit())throw new IllegalStateException("Unable to bind screen");
 }
 static synchronized void reconcileUiSessions(Context c,java.util.Set<String> live){
  android.content.SharedPreferences.Editor e=owners(c).edit();for(String id:owners(c).getAll().keySet())if(!live.contains(id))e.remove(id);e.commit();
  binding(c).edit().remove("active_session").remove("general_state").commit();
 }
 static synchronized JSONObject uiOwner(Context c,String sessionId)throws JSONException{return new JSONObject(owners(c).getString(sessionId,"{}"));}
 static synchronized void releaseUiSession(Context c,String sessionId){
  owners(c).edit().remove(sessionId).commit();
  if(sessionId.equals(activeUiSession(c)))binding(c).edit().remove("active_session").commit();
  c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
 }
 static synchronized boolean stoppedUiOwner(Context c,String session)throws Exception {
  String id=uiOwner(c,session).optString("conversation_id");if(id.isEmpty()||id.startsWith("schedule:"))return false;
  JSONObject current=state(c);JSONObject owner=id.equals(current.optString("conversation_id"))?current:new JSONObject(archive(c).getString(id,"{}"));
  return java.util.Set.of("completed","failed","interrupted","cancelled","stopped","paused","idle").contains(owner.optString("status"));
 }
 static synchronized JSONArray screens(Context c,String conversation)throws Exception {
  JSONArray out=new JSONArray();for(java.util.Map.Entry<String,?> e:owners(c).getAll().entrySet()){
   JSONObject owner=new JSONObject((String)e.getValue());if(conversation.equals(owner.optString("conversation_id")))out.put(e.getKey());
  }return out;
 }
 static synchronized JSONObject bindUiSession(Context c,String sessionId)throws Exception {
  JSONObject owner=uiOwner(c,sessionId);String id=owner.optString("conversation_id");
  if(id.isEmpty())throw new IllegalStateException(c.getString(R.string.assistant_cannot_verify_this_screen_s_conversation_close_it_and_reopen_from_3b522));
  JSONObject current=state(c);
  if(!id.equals(current.optString("conversation_id"))){
   requireIdle(c);String raw=archive(c).getString(id,"");
   if(raw.isEmpty())throw new IllegalStateException(c.getString(R.string.assistant_original_conversation_unavailable_no_new_conversation_was_created_62d3f));
   save(c,new JSONObject(raw));
  }
  binding(c).edit().putString("active_session",sessionId).remove("general_state").commit();return state(c);
 }
 static synchronized void unbindUiSession(Context c,String sessionId){if(sessionId.equals(activeUiSession(c)))binding(c).edit().remove("active_session").commit();}
 static synchronized String activeUiSession(Context c){return binding(c).getString("active_session","");}
 private static void append(JSONObject s,String role,String text) throws JSONException {
  JSONArray old=s.optJSONArray("history"),next=new JSONArray();if(old!=null)for(int i=Math.max(0,old.length()-(HISTORY_LIMIT-1));i<old.length();i++)next.put(old.get(i));
  long now=System.currentTimeMillis();JSONObject entry=new JSONObject().put("request_id",s.optString("request_id")).put("turn_id",s.optString("turn_id")).put("role",role).put("text",text).put("at",now).put("updated_at",now).put("internal_state",role.equals("user")?"submitted":"completed");if(role.equals("assistant")&&s.optLong("submitted_at")>0)entry.put("execution_duration_ms",Math.max(0,now-s.optLong("submitted_at")));next.put(entry);s.put("history",next);
 }
 static synchronized JSONObject enqueueAttachment(Context c,String conversation,String text,JSONObject attachment) throws Exception {
  if(!conversation.equals(state(c).optString("conversation_id")))throw new IllegalStateException(c.getString(R.string.assistant_conversation_changed_return_to_the_original_conversation_to_send_12214));
  requireIdle(c);
  return enqueueInternal(c,text,attachment);
 }
 static synchronized JSONObject enqueueShared(Context c,String text,JSONArray attachments,String job)throws Exception{
  JSONObject active=state(c);if(job.equals(active.optString("share_job_id")))return active;
  for(Object raw:archive(c).getAll().values()){JSONObject previous=new JSONObject(String.valueOf(raw));if(job.equals(previous.optString("share_job_id")))return previous;}
  fresh(c);return enqueueInternal(c,text,null,attachments,job);
 }
 static synchronized JSONObject enqueue(Context c,String text) throws Exception {return enqueueInternal(c,text,null);}
 private static JSONObject enqueueInternal(Context c,String text,JSONObject attachment) throws Exception {return enqueueInternal(c,text,attachment,null,null);}
 private static JSONObject enqueueInternal(Context c,String text,JSONObject attachment,JSONArray attachments,String shareJob) throws Exception {
  text=text.trim();if(text.isEmpty()||text.length()>12000)throw new IllegalArgumentException(c.getString(R.string.assistant_enter_between_1_and_12_000_characters_39617));
  if(!Authorization.enabled(c))throw new IllegalStateException(c.getString(R.string.assistant_aios_service_is_disabled_ec493));
  JSONObject s=state(c);if(s.optBoolean("scheduled")){JSONObject detached=new os.aios.runtime.CoreRuntimeClient(c).call("internal","assistant.thread.detach",new JSONObject().put("thread_id",s.getString("thread_id")));if(detached.has("rule_id"))c.getSharedPreferences("schedule_threads",0).edit().remove("rule:"+detached.getString("rule_id")).commit();s.put("scheduled",false);}
  String status=s.optString("status");
  if(status.equals("queued")||status.equals("running")){
   String control=AssistantGoal.command(text);if(control.equals("pause")||control.equals("clear")){s.put("goal_clear_requested",control.equals("clear"));save(c,s);cancel(c);return state(c);}
   if(attachment!=null||attachments!=null)throw new IllegalStateException(c.getString(R.string.assistant_the_assistant_is_working_on_your_request_stop_it_first_d7308));
   return addFollowup(c,s,text);

  }
  JSONArray waiting=s.optJSONArray("pending_messages");
  if(waiting!=null&&waiting.length()>0&&attachment==null&&attachments==null&&AssistantGoal.command(text).isEmpty()){addFollowup(c,s,text);promoteFollowup(c,true);return state(c);}
  if(AssistantGoal.command(text).isEmpty()&&c.getSharedPreferences("assistant_goal_ui",0).getBoolean("next",false))text="/goal "+text;
  c.getSharedPreferences("assistant_goal_ui",0).edit().putBoolean("next",false).apply();
  if(!s.has("conversation_id"))s=fresh(c);
  for(String key:new String[]{"share_job_id","attachments","attachment","boundary_pause","reply","last_sentence","reply_request_id","notice_at","partial_reply","speech_reply","progress","progress_kind","progress_args","live_checkpoint","live_recover","event_epoch","event_cursor","submission_anchor","execution_text","goal","goal_tracking","goal_clear_requested","execution_finished_at","turn_id","transport_state","transport_error"})s.remove(key);
  s.put("ui_origin",UiScreenContext.capture(c));
  if(attachments!=null)s.put("attachments",attachments);if(shareJob!=null)s.put("share_job_id",shareJob);
  if(attachment!=null)s.put("attachment",new JSONObject(attachment.toString()));
  s.put("request_id",UUID.randomUUID().toString());append(s,"user",text);s.put("text",text).put("status","queued").put("submitted_at",System.currentTimeMillis()).put("message_updated_at",System.currentTimeMillis()).put("cancel_requested",false).put("error","").put("notice","").put("notice_resource","").put("voice_state","");save(c,s);return s;
 }
 static JSONObject addFollowup(Context c,JSONObject s,String text)throws Exception{
  JSONArray pending=s.optJSONArray("pending_messages");if(pending==null)pending=new JSONArray();
  if(pending.length()>=20)throw new IllegalStateException(c.getString(R.string.followup_limit));
  pending.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("text",text).put("state","queued").put("created_at",System.currentTimeMillis()));
  s.put("pending_messages",pending);save(c,s);return s;
 }
 static synchronized JSONObject followupAction(Context c,String conversation,String id,String action)throws Exception{
  JSONObject s=state(c);if(!conversation.equals(s.optString("conversation_id")))throw new IllegalStateException(c.getString(R.string.assistant_request_changed));
  JSONArray pending=s.optJSONArray("pending_messages");if(pending==null)return s;
  for(int i=0;i<pending.length();i++){JSONObject item=pending.getJSONObject(i);if(!id.equals(item.optString("id")))continue;
   if(item.optString("state").equals("sending"))throw new IllegalStateException(c.getString(R.string.followup_sending));
   if(action.equals("remove"))pending.remove(i);
   else if(action.equals("steer")&&item.optString("state").equals("queued")&&s.optString("status").equals("running")&&!s.optString("turn_id").isEmpty()&&!s.optBoolean("cancel_requested"))item.put("state","steer").put("request_id",s.getString("request_id")).put("turn_id",s.getString("turn_id"));
   else throw new IllegalStateException(c.getString(R.string.assistant_request_changed));
   save(c,s);return s;
  }return s;
 }
 static synchronized JSONObject claimSteer(Context c,String request,String turn)throws Exception{
  JSONObject s=state(c);if(!request.equals(s.optString("request_id"))||s.optBoolean("cancel_requested"))return null;
  JSONArray pending=s.optJSONArray("pending_messages");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject item=pending.getJSONObject(i);
   if(item.optString("state").equals("sending")){item.put("state","unknown");save(c,s);continue;}
   if(!item.optString("state").equals("steer"))continue;
   if(!request.equals(item.optString("request_id"))||!turn.equals(item.optString("turn_id"))){item.put("state","queued");save(c,s);continue;}
   item.put("state","sending");save(c,s);return new JSONObject(item.toString());
  }return null;
 }
 static synchronized void finishSteer(Context c,String request,String id,boolean accepted)throws Exception{
  JSONObject s=state(c);if(!request.equals(s.optString("request_id")))return;JSONArray pending=s.optJSONArray("pending_messages");
  for(int i=0;pending!=null&&i<pending.length();i++){JSONObject item=pending.getJSONObject(i);if(!id.equals(item.optString("id")))continue;
   if(accepted){append(s,"user",item.getString("text"));pending.remove(i);}else item.put("state","unknown");save(c,s);return;
  }
 }
 static synchronized boolean promoteFollowup(Context c,boolean explicit)throws Exception{
  JSONObject s=state(c);String status=s.optString("status");if(status.equals("running")||status.equals("queued")||!Authorization.enabled(c))return false;
  if(!explicit&&(!status.equals("completed")||s.optBoolean("cancel_requested")))return false;
  JSONArray pending=s.optJSONArray("pending_messages");if(pending==null||pending.length()==0)return false;
  JSONObject item=pending.getJSONObject(0);if(item.optString("state").equals("sending")){item.put("state","unknown");save(c,s);return false;}if(item.optString("state").equals("steer")){item.put("state","queued");save(c,s);}if(!item.optString("state").equals("queued"))return false;
  // One durable write activates the next request and removes it from the queue.
  pending.remove(0);s.put("request_id",item.getString("id"));
  for(String key:new String[]{"attachment","attachments","share_job_id","boundary_pause","reply","last_sentence","reply_request_id","partial_reply","speech_reply","progress","progress_kind","progress_args","live_checkpoint","live_recover","event_epoch","event_cursor","submission_anchor","execution_text","goal","goal_tracking","goal_clear_requested","execution_finished_at","turn_id","transport_state","transport_error"})s.remove(key);
  append(s,"user",item.getString("text"));s.put("text",item.getString("text")).put("status","queued").put("submitted_at",System.currentTimeMillis()).put("message_updated_at",System.currentTimeMillis()).put("cancel_requested",false).put("error","").put("notice","").put("notice_resource","").put("ui_origin",UiScreenContext.capture(c));save(c,s);return true;
 }
 static synchronized void resumeIntervention(Context c,String request,String turn)throws Exception{JSONObject s=state(c);if(!request.equals(s.optString("request_id"))||s.optBoolean("cancel_requested"))return;s.put("turn_id",turn).put("status","running").put("live_recover",true);s.remove("live_checkpoint");save(c,s);}
 static synchronized void boundaryPause(Context c,String request,String reason)throws Exception{JSONObject s=state(c);if(!request.equals(s.optString("request_id")))return;s.put("status","paused").put("boundary_pause",reason).put("message_updated_at",System.currentTimeMillis());save(c,s);}
 static synchronized void noticeResource(Context c,int resource){try{JSONObject s=state(c);s.put("notice_resource",c.getResources().getResourceEntryName(resource)).put("notice",c.getString(resource)).put("notice_at",System.currentTimeMillis());save(c,s);}catch(Exception e){android.util.Log.e("AIOSAssistant","notice",e);}}
 static synchronized void notice(Context c,String message){try{JSONObject s=state(c);s.remove("notice_resource");s.put("notice",message).put("notice_at",Math.max(System.currentTimeMillis(),s.optLong("notice_at")+1));save(c,s);}catch(Exception e){android.util.Log.e("AIOSAssistant","notice",e);}}
 static synchronized void cancel(Context c) throws Exception {JSONObject s=state(c);JSONArray pending=s.optJSONArray("pending_messages");for(int i=0;pending!=null&&i<pending.length();i++){JSONObject item=pending.getJSONObject(i);if(item.optString("state").equals("steer"))item.put("state","queued");}s.put("cancel_requested",true).put("message_updated_at",System.currentTimeMillis());if(s.optString("status").equals("queued"))s.put("status","interrupted").put("execution_finished_at",System.currentTimeMillis());save(c,s);}
 static synchronized JSONObject dispatch(Context c,String action,JSONObject p) throws Exception {
  JSONObject s=state(c);
  switch(action){
   case "status":break;
   case "next":prefs(c).edit().putLong("bridge_seen",System.currentTimeMillis()).apply();break;
   case "new":s=fresh(c);break;
   case "enqueue":s=enqueue(c,p.getString("text"));break;
   case "cancel":cancel(c);s=state(c);break;
   case "update":
    if(!s.optString("request_id").equals(p.getString("request_id"))||!s.optString("conversation_id").equals(p.getString("conversation_id")))return new JSONObject().put("ignored",true);
    String status=p.getString("status");if(!java.util.Set.of("running","completed","failed","interrupted").contains(status))throw new IllegalArgumentException("Invalid status");
    if(java.util.Set.of("completed","failed","interrupted").contains(s.optString("status")))return new JSONObject().put("ignored",true);
    if(AssistantGoal.messageChanged(s,p))s.put("message_updated_at",System.currentTimeMillis());
    for(String key:new String[]{"thread_id","turn_id","error","partial_reply","speech_reply","progress","progress_kind","progress_args","live_checkpoint","live_recover","event_epoch","event_cursor","submission_anchor","execution_text","goal","goal_tracking","goal_clear_requested","transport_state","transport_error"})if(p.has(key))s.put(key,p.get(key));s.put("status",status);
    JSONObject checkpoint=s.optJSONObject("live_checkpoint");if(checkpoint!=null&&!s.optString("turn_id").equals(checkpoint.optString("turn_id")))s.remove("live_checkpoint");
    if(java.util.Set.of("completed","failed","interrupted").contains(status))s.put("execution_finished_at",System.currentTimeMillis());
    if(status.equals("completed")){String reply=p.getString("reply");if(reply.isBlank())throw new IllegalArgumentException("Empty final reply");append(s,"assistant",reply);s.put("reply",reply).put("last_sentence",AssistantText.lastSentence(reply)).put("reply_request_id",s.getString("request_id")).put("notice","").put("notice_resource","");for(String key:new String[]{"partial_reply","speech_reply","progress","progress_kind","progress_args","live_checkpoint","live_recover"})s.remove(key);}
    if(status.equals("failed"))s.put("notice",s.optString("error",c.getString(R.string.assistant_request_incomplete_please_try_again_2dcf5))).put("notice_at",System.currentTimeMillis());
    if(status.equals("interrupted"))s.put("notice",c.getString(R.string.assistant_current_operation_stopped_07441)).put("notice_at",System.currentTimeMillis());
    if(java.util.Set.of("completed","failed","interrupted").contains(status)){s.remove("live_checkpoint");s.remove("live_recover");}save(c,s);break;
   default:throw new IllegalArgumentException("Unknown assistant action");
  }
  s.put("ui_sessions",screens(c,s.optString("conversation_id")));s.put("bridge_seen",prefs(c).getLong("bridge_seen",0));return s;
 }
}
