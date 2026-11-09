package os.aios.agent;
import org.json.*;
/** Native goal adapter; no second autonomous execution loop. */
final class AssistantGoal {
 static String command(String text){
  String t=text.trim();if(!t.equals("/goal")&&!t.startsWith("/goal ")&&!t.startsWith("/goal\n"))return "";
  String rest=t.substring(5).trim();return rest.isEmpty()?"get":java.util.Set.of("pause","resume","clear").contains(rest)?rest:"set";
 }
 static String objective(String text){return text.trim().substring(5).trim();}
 static boolean active(JSONObject goal){return goal!=null&&"active".equals(goal.optString("status"));}
 static JSONObject latestAfter(JSONObject thread,String anchor)throws JSONException{
  JSONArray turns=thread.optJSONArray("turns");if(turns==null)return null;
  boolean seen=anchor.isEmpty();JSONObject latest=null;
  for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);if(!seen){if(anchor.equals(t.optString("id")))seen=true;continue;}latest=t;}return latest;
 }
 static String duration(long millis){long seconds=Math.max(0,millis)/1000;long hours=seconds/3600,minutes=seconds/60%60;return (hours>0?hours+"小时":"")+(minutes>0?minutes+"分":"")+(seconds%60)+"秒";}
 static boolean messageChanged(JSONObject current,JSONObject patch){
  for(String key:new String[]{"status","turn_id","partial_reply","progress","error"})
   if(patch.has(key)&&!patch.optString(key).equals(current.optString(key)))return true;
  if(patch.has("goal")){JSONObject before=current.optJSONObject("goal"),after=patch.optJSONObject("goal");
   if(!(before==null?"":before.optString("status")).equals(after==null?"":after.optString("status")))return true;}
  return false;
 }
 static String label(JSONObject goal){if(goal==null)return "没有持续目标";return switch(goal.optString("status")){
  case "active"->"持续执行中";case "complete"->"目标已完成";case "paused"->"目标已暂停";case "blocked"->"目标遇到阻塞";
  case "budgetLimited"->"目标预算已用完";case "usageLimited"->"用量受限";default->"目标状态待确认";};}
 static String summary(JSONObject goal){return label(goal)+(goal==null?"":"\n"+goal.optString("objective"));}
}
