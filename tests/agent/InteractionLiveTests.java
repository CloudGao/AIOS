package os.aios.agent;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;
/** Production enqueue, real steering, and FIFO followup through the on-device bridge. */
public final class InteractionLiveTests extends Instrumentation {
 int checks;void check(boolean v,String why){if(!v)throw new AssertionError(why);checks++;}Bundle args;
 public void onCreate(Bundle b){args=b;super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();String mode=args.getString("mode","live");
  if(mode.equals("status")){JSONObject s=AssistantConversation.state(c);out.putString("result",new JSONObject().put("status",s.optString("status")).put("thread",s.optString("thread_id")).put("request",s.optString("request_id")).put("pending",s.optJSONArray("pending_messages")).toString());finish(-1,out);return;}
  check(!java.util.Set.of("running","queued").contains(AssistantConversation.state(c).optString("status")),"prior request idle");
  if(!args.getString("reuse","false").equals("true"))AssistantConversation.fresh(c);long sent=SystemClock.elapsedRealtime();
  String prompt=mode.equals("latency")?"Reply with exactly LATENCY_OK. Do not call tools or save memories.":"This is an isolated acceptance test. Use a shell command to sleep for 18 seconds, then reply exactly ORIGINAL_END. Do not access personal data or save memories.";
  AssistantConversation.enqueue(c,prompt);c.startForegroundService(new Intent(c,AgentService.class));
  String original="",thread="",turn="";long first=-1;boolean steered=false;String queuedId="";long deadline=sent+180000;
  while(SystemClock.elapsedRealtime()<deadline){JSONObject s=AssistantConversation.state(c);String status=s.optString("status");if(original.isEmpty())original=s.optString("request_id");if(!s.optString("partial_reply").isEmpty()&&first<0)first=SystemClock.elapsedRealtime()-sent;
   if(!mode.equals("latency")&&!steered&&status.equals("running")&&!s.optString("turn_id").isEmpty()){
    thread=s.getString("thread_id");turn=s.getString("turn_id");s=AssistantConversation.enqueue(c,"Update the current task: after the sleep, reply exactly STEER_ACCEPTED instead of ORIGINAL_END. Do not save memories.");String id=s.getJSONArray("pending_messages").getJSONObject(0).getString("id");AssistantConversation.followupAction(c,s.getString("conversation_id"),id,"steer");
    s=AssistantConversation.enqueue(c,"Reply with exactly QUEUE_ACCEPTED. Do not call tools or save memories.");queuedId=s.getJSONArray("pending_messages").getJSONObject(s.getJSONArray("pending_messages").length()-1).getString("id");steered=true;
   }
   if(mode.equals("latency")&&status.equals("completed")){check(s.optString("reply").contains("LATENCY_OK"),"real API reply");out.putLong("completion_ms",SystemClock.elapsedRealtime()-sent);out.putLong("first_projection_ms",first);out.putString("thread",s.optString("thread_id"));break;}
   if(steered&&s.optString("request_id").equals(queuedId)&&status.equals("completed")){
    check(thread.equals(s.optString("thread_id")),"queue retains native thread");check(!turn.equals(s.optString("turn_id")),"queue starts next native turn");check(s.optString("reply").contains("QUEUE_ACCEPTED"),"queued response received");check(s.optJSONArray("pending_messages")==null||s.getJSONArray("pending_messages").length()==0,"queue drained");
    boolean guidance=false,reply=false;JSONArray history=s.getJSONArray("history");for(int i=0;i<history.length();i++){JSONObject item=history.getJSONObject(i);if(item.optString("role").equals("user")&&item.optString("text").startsWith("Update the current task:"))guidance=true;if(item.optString("role").equals("assistant")&&item.optString("text").contains("STEER_ACCEPTED"))reply=true;}check(guidance&&reply,"guidance acknowledged and applied to original turn");out.putString("thread",thread);out.putLong("completion_ms",SystemClock.elapsedRealtime()-sent);break;
   }
   if(status.equals("failed")||status.equals("interrupted"))throw new AssertionError("request ended "+status+" "+s.optString("error"));Thread.sleep(200);
  }
  check(out.containsKey("completion_ms"),"completed before acceptance deadline");try(CodexAccountClient client=new CodexAccountClient(c)){JSONObject events=client.call(new JSONObject().put("op","events").put("after",0));JSONArray items=events.optJSONArray("items");for(int i=0;items!=null&&i<items.length();i++){JSONObject event=items.getJSONObject(i),p=event.optJSONObject("params");if(event.optString("method").equals("thread/tokenUsage/updated")&&p!=null&&out.getString("thread","").equals(p.optString("threadId")))out.putString("usage",p.optJSONObject("tokenUsage")==null?"{}":p.getJSONObject("tokenUsage").toString());}}out.putString("result","PASS "+checks+" checks");
 }catch(Throwable t){out.putString("error",android.util.Log.getStackTraceString(t));}finish(out.containsKey("error")?0:-1,out);}
}
