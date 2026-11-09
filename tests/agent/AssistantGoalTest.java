package os.aios.agent;
import org.json.*;
public class AssistantGoalTest {
 static int checks; static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  check(AssistantGoal.command("/goal").equals("get"));check(AssistantGoal.command("/goal pause").equals("pause"));check(AssistantGoal.command("/goal resume").equals("resume"));check(AssistantGoal.command("/goal clear").equals("clear"));
  check(AssistantGoal.command("/goal 搜索商品").equals("set"));check(AssistantGoal.command("/goals hi").isEmpty());check(AssistantGoal.command("请搜索").isEmpty());check(AssistantGoal.objective("/goal 搜索").equals("搜索"));
  check(!AssistantGoal.active(null));check(AssistantGoal.active(new JSONObject().put("status","active")));check(!AssistantGoal.active(new JSONObject().put("status","paused")));
  JSONArray turns=new JSONArray().put(new JSONObject().put("id","old")).put(new JSONObject().put("id","one")).put(new JSONObject().put("id","auto-two"));JSONObject thread=new JSONObject().put("turns",turns);
  check(AssistantGoal.latestAfter(thread,"old").getString("id").equals("auto-two"));check(AssistantGoal.latestAfter(thread,"auto-two")==null);check(AssistantGoal.latestAfter(thread,"missing")==null);check(AssistantGoal.latestAfter(thread,"").getString("id").equals("auto-two"));
  check(AssistantGoal.label(new JSONObject().put("status","blocked")).contains("阻塞"));check(AssistantGoal.label(new JSONObject().put("status","complete")).contains("完成"));
  JSONObject old=new JSONObject().put("status","running").put("turn_id","a").put("partial_reply","文本").put("progress","读取页面").put("goal",new JSONObject().put("status","active").put("tokensUsed",1));
  check(!AssistantGoal.messageChanged(old,new JSONObject(old.toString()).put("event_cursor",100)));
  check(!AssistantGoal.messageChanged(old,new JSONObject().put("goal",new JSONObject().put("status","active").put("tokensUsed",50))));
  check(AssistantGoal.messageChanged(old,new JSONObject().put("partial_reply","文本更新")));
  check(AssistantGoal.messageChanged(old,new JSONObject().put("turn_id","b")));
  check(AssistantGoal.messageChanged(old,new JSONObject().put("goal",new JSONObject().put("status","paused"))));
  check(AssistantGoal.duration(0).equals("0秒"));check(AssistantGoal.duration(61000).equals("1分1秒"));check(AssistantGoal.duration(3661000).equals("1小时1分1秒"));
  System.out.println(checks+" goal adapter checks passed");
 }
}
