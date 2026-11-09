package os.aios.agent;
import android.app.*;import android.os.*;import org.json.*;
public final class GoalDeviceProbe extends Instrumentation {
 Bundle args;
 public void onCreate(Bundle b){args=b;super.onCreate(b);start();}
 JSONObject rpc(CodexAccountClient c,String m,JSONObject p)throws Exception{return c.call(new JSONObject().put("op","rpc").put("method",m).put("params",p));}
 void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public void onStart(){Bundle out=new Bundle();JSONArray results=new JSONArray();String id="";
 try(CodexAccountClient c=new CodexAccountClient(getTargetContext())){
  c.call(new JSONObject().put("op","start").put("mode","live"));
  if(args.containsKey("mirror")){
   java.util.Map<String,?> pending=getTargetContext().getSharedPreferences("assistant_pending_captures",0).getAll();int queued=0,marked=0;
   for(Object value:pending.values())if(value instanceof String)queued+=new JSONArray((String)value).length();
   for(Object value:getTargetContext().getSharedPreferences("assistant_workspace_mirrors",0).getAll().values())if(String.valueOf(value).startsWith("v7:"))marked++;
   results.put(new JSONObject().put("pending_threads",pending.size()).put("pending_turns",queued).put("v7_mirrors",marked));
  }else if(args.containsKey("aged")){
   synchronized(AssistantConversation.class){AssistantConversation.fresh(getTargetContext());AssistantConversation.enqueue(getTargetContext(),"验收用请求：测试程序将开始时间模拟为2小时前，并非真实耗时。请计算59乘61，只回复数字，不创建Goal。");
    JSONObject state=AssistantConversation.state(getTargetContext()).put("submitted_at",System.currentTimeMillis()-2*60*60*1000L);
    getTargetContext().getSharedPreferences("assistant_conversation",0).edit().putString("state",state.toString()).commit();}
   getTargetContext().startForegroundService(new android.content.Intent(getTargetContext(),AgentService.class));
   JSONObject state=null;for(int i=0;i<120;i++){Thread.sleep(1000);state=AssistantConversation.state(getTargetContext());if(java.util.Set.of("completed","failed","interrupted").contains(state.optString("status")))break;}
   check(state!=null&&state.optString("status").equals("completed"),"aged request failed: "+state);
   check(state.optString("reply").contains("3599"),"aged answer");JSONObject last=state.getJSONArray("history").getJSONObject(state.getJSONArray("history").length()-1);
   check(last.getLong("execution_duration_ms")>=2*60*60*1000L,"duration uses original request start");
   check(last.getLong("updated_at")>state.getLong("submitted_at"),"last update timestamp");
   Thread.sleep(2000);{
    Bundle request=new Bundle();request.putInt("protocol_version",1);request.putString("arguments",new JSONObject().put("id","codex:"+state.getString("thread_id")).put("limit",10).toString());
    Bundle response=getTargetContext().getContentResolver().call(android.net.Uri.parse("content://os.aios.core.runtime"),"workspace","session.get",request);
    JSONObject session=new JSONObject(response.getString("result")).getJSONObject("data");
    JSONArray messages=session.getJSONArray("messages");JSONObject timing=null;for(int i=0;i<messages.length();i++){JSONObject m=messages.getJSONObject(i);if(m.optString("role").equals("assistant")&&!m.optString("detail").isEmpty()){JSONObject meta=new JSONObject(m.getString("detail")).optJSONObject("aios_timing");if(meta!=null)timing=meta;}}
    check(timing!=null&&timing.optLong("duration_ms")>=2*60*60*1000L,"Workspace preserves request duration");results.put(new JSONObject().put("workspace_timing",timing));}
   results.put(new JSONObject().put("test","synthetic-two-hour-request-age").put("status",state.getString("status")).put("reply",state.optString("reply")).put("duration_ms",last.getLong("execution_duration_ms")).put("updated_at",last.getLong("updated_at")));
  }else if(args.containsKey("dream")){
   id=args.getString("dream");JSONObject t=rpc(c,"thread/read",new JSONObject().put("threadId",id).put("includeTurns",true)).getJSONObject("thread");
   JSONArray turns=t.optJSONArray("turns"),brief=new JSONArray();if(turns!=null)for(int i=0;i<turns.length();i++){JSONObject turn=turns.getJSONObject(i),row=new JSONObject().put("id",turn.optString("id")).put("status",turn.optString("status")).put("reply",MinutesTurnResult.reply(turn));if(turn.has("error"))row.put("error",turn.get("error"));JSONArray items=turn.optJSONArray("items"),itemBrief=new JSONArray();if(items!=null)for(int j=0;j<items.length();j++){JSONObject item=items.getJSONObject(j),shortItem=new JSONObject();for(String key:new String[]{"id","type","status","name","error","message"})if(item.has(key))shortItem.put(key,item.get(key));itemBrief.put(shortItem);}row.put("items",itemBrief);brief.put(row);}
   results.put(new JSONObject().put("turns",brief));id="";
  }else if(args.containsKey("thread")){
   id=args.getString("thread");JSONObject t=rpc(c,"thread/read",new JSONObject().put("threadId",id).put("includeTurns",true)).getJSONObject("thread");
   JSONArray turns=t.optJSONArray("turns"),brief=new JSONArray();if(turns!=null)for(int i=0;i<turns.length();i++){JSONObject turn=turns.getJSONObject(i);brief.put(new JSONObject().put("id",turn.optString("id")).put("status",turn.optString("status")).put("reply",MinutesTurnResult.reply(turn)));}
   results.put(new JSONObject().put("turns",brief).put("goal",rpc(c,"thread/goal/get",new JSONObject().put("threadId",id)).opt("goal")));id="";
  }else{
   id=c.call(new JSONObject().put("op","thread/start")).getJSONObject("thread").getString("id");JSONObject p=new JSONObject().put("threadId",id);
   check(rpc(c,"thread/goal/get",p).isNull("goal"),"ordinary new thread must not have goal");results.put("ordinary-no-goal");
   JSONObject goal=rpc(c,"thread/goal/set",new JSONObject().put("threadId",id).put("objective","验收暂停目标，无需执行任何操作。").put("status","paused").put("origin","user")).getJSONObject("goal");
   check(goal.getString("status").equals("paused"),"pause");results.put("create-paused-on-fresh-thread");
   rpc(c,"thread/goal/set",new JSONObject().put("threadId",id).put("status","active").put("origin","user"));
   c.call(new JSONObject().put("op","execution/stop").put("params",p));
   goal=rpc(c,"thread/goal/get",p).getJSONObject("goal");check(goal.getString("status").equals("active"),"execution stop must not pause goal");results.put("execution-stop-keeps-goal-active");
   rpc(c,"thread/goal/clear",new JSONObject().put("threadId",id).put("origin","user"));check(rpc(c,"thread/goal/get",p).isNull("goal"),"clear");results.put("clear");
   id="";
  }
  out.putString("stream",results.toString());finish(Activity.RESULT_OK,out);
 }catch(Throwable e){results.put(e.toString());out.putString("stream",results.toString());finish(Activity.RESULT_CANCELED,out);}
 }
}
