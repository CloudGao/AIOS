package os.aios.agent;
import org.json.*;
import java.util.*;

/** Bounded adapter for app-server's native turn and item pagination. */
final class NativeThreadPager {
 interface Rpc { JSONObject call(String method,JSONObject params)throws Exception; }
 static final int TURN_PAGE=12,ITEM_PAGE=64;
 private final Rpc rpc;
 NativeThreadPager(Rpc value){rpc=value;}

 JSONObject recent(String threadId,int limit)throws Exception{
  JSONObject thread=metadata(threadId),page=turnPage(threadId,null,Math.max(1,Math.min(limit,TURN_PAGE)));JSONArray desc=page.getJSONArray("data");
  JSONArray turns=new JSONArray();for(int i=desc.length()-1;i>=0;i--)turns.put(hydratePage(threadId,desc.getJSONObject(i)));
  return thread.put("turns",turns).put("history_page",new JSONObject().put("page_size",desc.length()).put("has_older",!page.isNull("nextCursor")).put("next_cursor",page.opt("nextCursor")));
 }
 JSONObject current(String threadId,String expectedTurn)throws Exception{JSONObject thread=new JSONObject().put("id",threadId),page=turnPage(threadId,null,1);JSONArray data=page.getJSONArray("data"),turns=new JSONArray();if(data.length()>0){JSONObject latest=data.getJSONObject(0);if(expectedTurn.isEmpty()||expectedTurn.equals(latest.optString("id")))turns.put(hydratePage(threadId,latest));}return thread.put("turns",turns).put("history_page",new JSONObject().put("current_only",true));}

 JSONObject find(String threadId,String turnId)throws Exception{
  String cursor=null;do{JSONObject page=turnPage(threadId,cursor,TURN_PAGE);JSONArray data=page.getJSONArray("data");for(int i=0;i<data.length();i++){JSONObject turn=data.getJSONObject(i);if(turnId.equals(turn.optString("id")))return hydratePage(threadId,turn);}cursor=nullable(page,"nextCursor");}while(cursor!=null);return null;
 }

 JSONObject olderTurns(String threadId,String cursor)throws Exception{

  JSONObject page=turnPage(threadId,cursor,TURN_PAGE),out=new JSONObject().put("turns",new JSONArray());JSONArray data=page.getJSONArray("data"),turns=out.getJSONArray("turns");for(int i=data.length()-1;i>=0;i--)turns.put(hydratePage(threadId,data.getJSONObject(i)));return out.put("next_cursor",page.opt("nextCursor"));
 }
 JSONObject olderItems(String threadId,String turnId,String cursor)throws Exception{
  if(cursor==null||cursor.isEmpty())throw new IllegalArgumentException("Item cursor required");JSONObject p=new JSONObject().put("threadId",threadId).put("turnId",turnId).put("cursor",cursor).put("limit",ITEM_PAGE).put("sortDirection","desc");JSONObject page=rpc.call("thread/items/list",p);JSONArray data=page.getJSONArray("data"),items=new JSONArray();for(int i=data.length()-1;i>=0;i--)items.put(data.getJSONObject(i).getJSONObject("item"));return new JSONObject().put("turn_id",turnId).put("items",items).put("next_cursor",page.opt("nextCursor"));
 }
 JSONObject leadingUser(String threadId,String turnId)throws Exception{
  JSONObject page=rpc.call("thread/items/list",new JSONObject().put("threadId",threadId).put("turnId",turnId).put("limit",1).put("sortDirection","asc"));
  JSONArray data=page.getJSONArray("data");if(data.length()==0)return null;JSONObject item=data.getJSONObject(0).getJSONObject("item");return item.optString("type").equals("userMessage")?item:null;
 }
 private JSONObject metadata(String id)throws Exception{return rpc.call("thread/read",new JSONObject().put("threadId",id).put("includeTurns",false)).getJSONObject("thread");}
 private JSONObject turnPage(String id,String cursor,int limit)throws Exception{JSONObject p=new JSONObject().put("threadId",id).put("limit",limit).put("sortDirection","desc").put("itemsView","summary");if(cursor!=null)p.put("cursor",cursor);return rpc.call("thread/turns/list",p);}
 private JSONObject hydratePage(String threadId,JSONObject turn)throws Exception{
  JSONObject copy=new JSONObject(turn.toString());JSONObject p=new JSONObject().put("threadId",threadId).put("turnId",turn.getString("id")).put("limit",ITEM_PAGE).put("sortDirection","desc");JSONObject page=rpc.call("thread/items/list",p);JSONArray data=page.getJSONArray("data"),items=new JSONArray();for(int i=data.length()-1;i>=0;i--)items.put(data.getJSONObject(i).getJSONObject("item"));
  boolean hasOlder=!page.isNull("nextCursor");
  return copy.put("items",items).put("itemsView",hasOlder?"paged":"full").put("items_has_older",hasOlder).put("items_next_cursor",page.opt("nextCursor"));
 }
 private static String nullable(JSONObject o,String key){return o.isNull(key)?null:o.optString(key,null);}
}
