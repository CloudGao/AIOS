package os.aios.agent;
import org.json.*;
import java.util.*;
/** Only public summaries, tool lifecycle and assistant output; never raw reasoning. */
final class AssistantLiveTurn {
 String progressKind="";JSONArray progressArgs=new JSONArray();
 boolean terminalSeen;String terminalStatus="";long lastFinalUpdateAt;
 final String threadId;String turnId,progress="";
 final LinkedHashMap<String,String> text=new LinkedHashMap<>();
 final Map<String,String> phase=new HashMap<>();
 AssistantLiveTurn(String thread,String turn){threadId=thread;turnId=turn;}
 JSONObject checkpoint() throws JSONException {
  JSONObject items=new JSONObject(),phases=new JSONObject();
  for(Map.Entry<String,String> e:text.entrySet())items.put(e.getKey(),e.getValue());
  for(Map.Entry<String,String> e:phase.entrySet())phases.put(e.getKey(),e.getValue());
  return new JSONObject().put("thread_id",threadId).put("turn_id",turnId).put("progress",progress).put("progress_kind",progressKind).put("progress_args",progressArgs).put("text",items).put("phase",phases).put("order",new JSONArray(text.keySet())).put("terminal_seen",terminalSeen).put("terminal_status",terminalStatus).put("last_final_update_at",lastFinalUpdateAt);
 }
 void restore(JSONObject saved){
  if(saved==null||!threadId.equals(saved.optString("thread_id"))||!turnId.equals(saved.optString("turn_id")))return;
  text.clear();phase.clear();progress=saved.optString("progress");progressKind=saved.optString("progress_kind");progressArgs=saved.optJSONArray("progress_args");if(progressArgs==null)progressArgs=new JSONArray();terminalSeen=saved.optBoolean("terminal_seen");terminalStatus=saved.optString("terminal_status");lastFinalUpdateAt=saved.optLong("last_final_update_at");
  JSONObject items=saved.optJSONObject("text"),phases=saved.optJSONObject("phase");JSONArray order=saved.optJSONArray("order");
  if(items!=null&&order!=null)for(int i=0;i<order.length();i++){String id=order.optString(i);text.put(id,items.optString(id));}
  if(phases!=null){Iterator<String> keys=phases.keys();while(keys.hasNext()){String id=keys.next();phase.put(id,phases.optString(id));}}
 }
 void snapshot(JSONObject turn){
  text.clear();phase.clear();progress="";progressKind="";progressArgs=new JSONArray();JSONArray items=turn.optJSONArray("items");
  if(items==null)return;
  for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;
   if("agentMessage".equals(item.optString("type"))){String id=item.optString("id"),body=item.optString("text");phase.put(id,item.optString("phase"));text.put(id,body);if("commentary".equals(phase.get(id))){progress=body;progressKind="";}else if("final_answer".equals(phase.get(id))&&!body.isEmpty())lastFinalUpdateAt=System.currentTimeMillis();}
  }
 }
 boolean overlapsSnapshot(JSONObject batch){
  if(batch.optBoolean("reset"))return true;
  JSONArray items=batch.optJSONArray("items");if(items==null)return false;
  for(int i=0;i<items.length();i++){
   JSONObject e=items.optJSONObject(i),args=e==null?null:e.optJSONObject("params");
   if(args==null||!threadId.equals(args.optString("threadId"))||!turnId.equals(args.optString("turnId")))continue;
   String method=e.optString("method");
   if(method.equals("item/started")||method.equals("item/completed")||method.equals("item/agentMessage/delta")||method.startsWith("item/reasoning/summary"))return true;
  }
  return false;
 }
 boolean accept(JSONObject event){
  JSONObject p=event.optJSONObject("params");if(p==null||!threadId.equals(p.optString("threadId")))return false;String method=event.optString("method");
  if(method.equals("warning")){String message=p.optString("message");if(isTransport(message))progress=safeTransport(message);return true;}
  if("turn/completed".equals(event.optString("method"))){JSONObject done=p.optJSONObject("turn");if(done!=null&&turnId.equals(done.optString("id"))){terminalSeen=true;terminalStatus=done.optString("status");}}
  String turn=p.optString("turnId");if(turnId.isEmpty()||!turnId.equals(turn))return false;
  String id=p.optString("itemId");JSONObject item=p.optJSONObject("item");
  if(method.equals("error")){JSONObject error=p.optJSONObject("error");String message=error==null?p.optString("error"):error.optString("message");progress=p.optBoolean("willRetry")?safeTransport(message):hostProgress("failed","模型连接恢复失败，正在确认本轮终态…");}
  if(item!=null){id=item.optString("id");if(item.optString("type").equals("agentMessage")){
    phase.put(id,item.optString("phase",""));String body=item.optString("text");if(!body.isEmpty()){text.put(id,body);if("final_answer".equals(phase.get(id)))lastFinalUpdateAt=System.currentTimeMillis();}
   }else if(method.equals("item/started")){String kind=item.optString("type");
    if(kind.equals("dynamicToolCall")||kind.equals("mcpToolCall"))progress=hostProgress("tool","正在执行："+item.optString("tool",item.optString("name","手机操作")),item.optString("tool",item.optString("name")));
    else if(kind.equals("webSearch"))progress=hostProgress("search","正在查找资料");
    else if(kind.equals("commandExecution"))progress=hostProgress("command","正在处理任务");
   }
  }
  if(method.equals("item/agentMessage/delta")){String delta=p.optString("delta");text.put(id,text.getOrDefault(id,"")+delta);if("final_answer".equals(phase.get(id))&&!delta.isEmpty())lastFinalUpdateAt=System.currentTimeMillis();}
  if(method.equals("item/reasoning/summaryTextDelta")){if(!progressKind.isEmpty())progress="";progressKind="";progress=(progress+p.optString("delta"));}
  if(method.equals("item/reasoning/summaryPartAdded")){progress="";progressKind="";}
  if("commentary".equals(phase.get(id))){progress=text.getOrDefault(id,progress);progressKind="";}
  if(progress.length()>2000)progress=progress.substring(progress.length()-2000);
  return true;
 }
 private String hostProgress(String kind,String legacy,String... args){progressKind=kind;progressArgs=new JSONArray(Arrays.asList(args));return legacy;}
 private static boolean isTransport(String raw){String value=raw.toLowerCase(java.util.Locale.ROOT);return value.contains("reconnect")||value.contains("retry")||value.contains("stream")||value.contains("connection");}
 private String safeTransport(String raw){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:retrying|reconnecting).*?(\\d+)\\s*/\\s*(\\d+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
  return m.find()?hostProgress("retry","模型连接中断，正在重连（"+m.group(1)+"/"+m.group(2)+"）",m.group(1),m.group(2)):hostProgress("recovery","模型连接中断，正在按有限策略恢复…");
 }
 String reply(){StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:text.entrySet())if(!"commentary".equals(phase.get(e.getKey()))){if(b.length()>0)b.append("\n\n");b.append(e.getValue());}return b.toString();}
 String speakable(){StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:text.entrySet())if("final_answer".equals(phase.get(e.getKey()))){if(b.length()>0)b.append("\n\n");b.append(e.getValue());}return b.toString();}
 boolean hasFinalReply(){return !speakable().isBlank()&&phase.values().stream().anyMatch("final_answer"::equals);}
 boolean finalReplySettled(long now,long quietMs){return hasFinalReply()&&lastFinalUpdateAt>0&&now-lastFinalUpdateAt>=quietMs;}
 boolean completedWithFinalReply(){return terminalSeen&&"completed".equals(terminalStatus)&&hasFinalReply();}
}
