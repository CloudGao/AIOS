package os.aios.agent;
import org.json.*;
/** Durable thread history is authoritative; event buffers may be lost. */
final class MinutesTurnResult {
 static JSONObject find(JSONObject thread,String turnId,String marker)throws JSONException {
  JSONArray turns=thread.optJSONArray("turns");if(turns==null)return null;
  for(int i=0;i<turns.length();i++){
   JSONObject turn=turns.getJSONObject(i);
   if(!turnId.isEmpty()){if(turnId.equals(turn.optString("id")))return turn;continue;}
   JSONArray items=turn.optJSONArray("items");if(items==null)continue;
   for(int j=0;j<items.length();j++){
    JSONObject item=items.getJSONObject(j);if(!"userMessage".equals(item.optString("type")))continue;
    JSONArray content=item.optJSONArray("content");if(content==null)continue;
    for(int k=0;k<content.length();k++)if(content.getJSONObject(k).optString("text").startsWith(marker+"\n"))return turn;
   }
  }return null;
 }
 static JSONObject after(JSONObject thread,String anchor,String expectedText)throws JSONException {
  JSONArray turns=thread.optJSONArray("turns");if(turns==null)return null;
  boolean eligible=anchor.isEmpty();JSONObject found=null;
  for(int i=0;i<turns.length();i++){
   JSONObject turn=turns.getJSONObject(i);
   if(!eligible){if(anchor.equals(turn.optString("id")))eligible=true;continue;}
   JSONArray items=turn.optJSONArray("items");if(items==null)continue;
   boolean match=false;
   for(int j=0;j<items.length();j++){
    JSONObject item=items.getJSONObject(j);if(!"userMessage".equals(item.optString("type")))continue;
    JSONArray content=item.optJSONArray("content");if(content==null)continue;
    for(int k=0;k<content.length();k++)if(expectedText.equals(content.getJSONObject(k).optString("text")))match=true;
   }
   if(match){if(found!=null)throw new IllegalStateException("Ambiguous turn submission");found=turn;}
  }
  return found;
 }
 static String reply(JSONObject turn)throws JSONException {
  if(!"full".equals(turn.optString("itemsView","full")))throw new IllegalStateException("纪要历史不完整");
  JSONArray items=turn.optJSONArray("items");StringBuilder finals=new StringBuilder(),fallback=new StringBuilder();
  if(items!=null)for(int i=0;i<items.length();i++){
   JSONObject item=items.getJSONObject(i);if(!"agentMessage".equals(item.optString("type")))continue;
   String text=item.optString("text").trim(),phase=item.optString("phase");
   StringBuilder target=phase.equals("final_answer")?finals:phase.isEmpty()?fallback:null;
   if(target!=null&&!text.isEmpty()){if(target.length()>0)target.append('\n');target.append(text);}
  }return (finals.length()>0?finals:fallback).toString();
 }

 /** A live history read can briefly precede the corresponding persisted completion.
  * Confirm negative terminal snapshots before finalizing; never resubmit a turn. */
 static final class TerminalObservation {
  private String id="",status="";
  boolean observe(JSONObject turn) {
   String nextId=turn==null?"":turn.optString("id"),next=turn==null?"":turn.optString("status");
   if(nextId.isEmpty()||(!next.equals("failed")&&!next.equals("interrupted"))){reset();return false;}
   boolean stable=id.equals(nextId)&&status.equals(next);id=nextId;status=next;return stable;
  }
  void reset(){id="";status="";}
 }
 private MinutesTurnResult(){}
}
