package os.aios.agent;
import org.json.*;
public final class AssistantLiveTurnTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static JSONObject event(String method,String thread,String turn,String id,String delta)throws Exception{return new JSONObject().put("method",method).put("params",new JSONObject().put("threadId",thread).put("turnId",turn).put("itemId",id).put("delta",delta));}
 static JSONObject item(String id,String phase,String text)throws Exception{return new JSONObject().put("id",id).put("type","agentMessage").put("phase",phase).put("text",text);}
 public static void main(String[] args)throws Exception{
  AssistantLiveTurn live=new AssistantLiveTurn("thread","turn");
  JSONObject start=event("item/started","thread","turn","a","");start.getJSONObject("params").put("item",item("a","final_answer",""));live.accept(start);
  check(!live.accept(event("item/agentMessage/delta","other","turn","a","wrong")));
  check(!live.accept(event("item/agentMessage/delta","thread","old","a","wrong")));
  live.accept(event("item/agentMessage/delta","thread","turn","a","第一句。"));check(live.speakable().equals("第一句。"));check(live.hasFinalReply());check(!live.finalReplySettled(System.currentTimeMillis(),1500));
  AssistantLiveTurn resumed=new AssistantLiveTurn("thread","turn");resumed.restore(new JSONObject(live.checkpoint().toString()));
  resumed.accept(event("item/agentMessage/delta","thread","turn","a","第二句。"));check(resumed.reply().equals("第一句。第二句。"));check(!resumed.finalReplySettled(System.currentTimeMillis(),1500));check(resumed.finalReplySettled(System.currentTimeMillis()+1600,1500));
  JSONObject done=event("item/completed","thread","turn","a","");done.getJSONObject("params").put("item",item("a","final_answer","第一句。第二句。"));resumed.accept(done);check(resumed.reply().equals("第一句。第二句。"));
  JSONObject terminal=event("turn/completed","thread","turn","","");terminal.getJSONObject("params").put("turn",new JSONObject().put("id","turn").put("status","completed"));resumed.accept(terminal);check(resumed.completedWithFinalReply());
  AssistantLiveTurn terminalRestored=new AssistantLiveTurn("thread","turn");terminalRestored.restore(new JSONObject(resumed.checkpoint().toString()));check(terminalRestored.completedWithFinalReply());
  resumed.accept(event("item/reasoning/textDelta","thread","turn","secret","PRIVATE"));check(!resumed.reply().contains("PRIVATE")&&!resumed.progress.contains("PRIVATE"));
  resumed.snapshot(new JSONObject().put("items",new JSONArray().put(item("c","commentary","正在读取设置")).put(item("a","final_answer","完整回复。"))));check(resumed.reply().equals("完整回复。"));check(resumed.progress.equals("正在读取设置"));
  AssistantLiveTurn unrelated=new AssistantLiveTurn("thread","another");unrelated.restore(resumed.checkpoint());check(unrelated.reply().isEmpty());
  check(live.accept(new JSONObject().put("method","warning").put("params",new JSONObject().put("threadId","thread").put("message","Reconnecting... 2/5"))));check(live.progress.contains("2/5"));
  check(live.accept(new JSONObject().put("method","error").put("params",new JSONObject().put("threadId","thread").put("turnId","turn").put("willRetry",false).put("error",new JSONObject().put("message","websocket closed")))));check(live.progress.contains("失败"));
  check(!live.accept(new JSONObject().put("method","warning").put("params",new JSONObject().put("threadId","old-thread").put("message","Reconnecting... 4/5"))));
  check(!live.overlapsSnapshot(new JSONObject().put("items",new JSONArray().put(new JSONObject().put("method","deprecationNotice")))));
  check(!live.overlapsSnapshot(new JSONObject().put("items",new JSONArray().put(event("item/agentMessage/delta","other","turn","a","wrong")))));
  check(!live.overlapsSnapshot(new JSONObject().put("items",new JSONArray().put(event("item/agentMessage/delta","thread","old","a","wrong")))));
  check(live.overlapsSnapshot(new JSONObject().put("items",new JSONArray().put(event("item/agentMessage/delta","thread","turn","a","new")))));
  check(live.overlapsSnapshot(new JSONObject().put("reset",true)));
  System.out.println("PASS live event isolation, durable terminal recovery, final deduplication, public-only progress and snapshot recovery");
 }
}
