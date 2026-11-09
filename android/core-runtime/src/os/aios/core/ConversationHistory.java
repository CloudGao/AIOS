package os.aios.core;
import org.json.*;
import java.util.*;
/** Native history pages retain identity, chronology and cursors; folding never filters replies. */
public final class ConversationHistory {
 private static JSONArray unique(JSONArray earlier,JSONArray later)throws JSONException {
  LinkedHashMap<String,JSONObject> items=new LinkedHashMap<>();
  for(JSONArray source:new JSONArray[]{earlier,later})if(source!=null)for(int i=0;i<source.length();i++){JSONObject item=source.getJSONObject(i);items.put(item.getString("id"),item);}
  return new JSONArray(items.values());
 }
 public static JSONObject merge(JSONObject current,JSONObject page,boolean older)throws JSONException {
  if(current==null)return new JSONObject(page.toString());
  JSONObject out=new JSONObject(current.toString());JSONArray left=older?page.getJSONArray("turns"):current.getJSONArray("turns"),right=older?current.getJSONArray("turns"):page.getJSONArray("turns");
  LinkedHashMap<String,JSONObject> turns=new LinkedHashMap<>();
  for(JSONArray source:new JSONArray[]{left,right})for(int i=0;i<source.length();i++){
   JSONObject turn=new JSONObject(source.getJSONObject(i).toString()),prior=turns.get(turn.getString("id"));
   if(prior!=null){turn.put("items",unique(prior.optJSONArray("items"),turn.optJSONArray("items")));
    // The earlier loaded boundary remains valid when a refresh only returns the recent tail.
    turn.put("items_has_older",prior.optBoolean("items_has_older")).put("items_next_cursor",prior.opt("items_next_cursor"));
   }turns.put(turn.getString("id"),turn);
  }
  out.put("turns",new JSONArray(turns.values()));
  if(older)out.put("next_cursor",page.opt("next_cursor"));
  return out;
 }
 public static void prependItems(JSONObject turn,JSONObject page)throws JSONException {
  turn.put("items",unique(page.getJSONArray("items"),turn.getJSONArray("items"))).put("items_next_cursor",page.opt("next_cursor")).put("items_has_older",!page.isNull("next_cursor"));
 }
 public static JSONObject turn(JSONObject history,String id)throws JSONException {
  if(history==null)return null;JSONArray turns=history.getJSONArray("turns");for(int i=0;i<turns.length();i++)if(id.equals(turns.getJSONObject(i).optString("id")))return turns.getJSONObject(i);return null;
 }
 public static JSONArray messages(JSONObject page)throws JSONException {
  JSONArray out=new JSONArray(),turns=page.getJSONArray("turns");
  for(int i=0;i<turns.length();i++){JSONObject turn=turns.getJSONObject(i);JSONArray items=turn.optJSONArray("items");if(items==null)continue;
   JSONObject leading=turn.optJSONObject("leading_user_item");if(leading!=null){boolean found=false;for(int k=0;k<items.length();k++)found|=leading.getString("id").equals(items.getJSONObject(k).getString("id"));if(!found)items=unique(new JSONArray().put(leading),items);}

   for(int j=0;j<items.length();j++){
    JSONObject item=items.getJSONObject(j);String type=item.optString("type"),role="system",body="";
    if(TranscriptText.internalItem(item))continue;
    if(type.equals("userMessage")){role="user";body=TranscriptText.userItem(item);}
    else if(type.equals("agentMessage")||type.equals("plan")){role="assistant";body=item.optString("text");}
    else body=type+(item.optString("status").isEmpty()?"":" · "+item.optString("status"));
    if(body.isEmpty())continue;
    JSONObject meta=new JSONObject().put("aios_phase",item.optString("phase"));
    long start=Math.round(1000*turn.optDouble("startedAt",0)),end=Math.round(1000*turn.optDouble("completedAt",0));
    if(role.equals("user"))meta.put("aios_timing",new JSONObject().put("updated_at",start));
    else if(role.equals("assistant")&&item.optString("phase").equals("final_answer")&&end>0)meta.put("aios_timing",new JSONObject().put("updated_at",end).put("duration_ms",turn.optLong("durationMs",-1)));
    out.put(new JSONObject().put("native_history",true).put("id",turn.getString("id")+":"+item.getString("id")).put("turn_id",turn.getString("id")).put("role",role).put("kind",type).put("phase",item.optString("phase")).put("content_md",body).put("detail",role.equals("system")?item.toString():meta.toString()).put("created_at",start));
   }
  }return out;
 }
}
