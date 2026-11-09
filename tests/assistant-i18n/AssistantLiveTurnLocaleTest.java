package os.aios.agent;
import org.json.*;
public final class AssistantLiveTurnLocaleTest {
 static int count;static void check(boolean b){if(!b)throw new AssertionError();count++;}
 public static void main(String[] args)throws Exception{
  AssistantLiveTurn live=new AssistantLiveTurn("t","u");
  live.accept(new JSONObject().put("method","warning").put("params",new JSONObject().put("threadId","t").put("message","Reconnecting 2/5")));
  check(live.progressKind.equals("retry"));check(live.progressArgs.getString(0).equals("2"));check(live.progressArgs.getString(1).equals("5"));
  AssistantLiveTurn restored=new AssistantLiveTurn("t","u");restored.restore(live.checkpoint());check(restored.progressKind.equals("retry"));check(restored.progressArgs.toString().equals(live.progressArgs.toString()));
  restored.accept(new JSONObject().put("method","item/reasoning/summaryTextDelta").put("params",new JSONObject().put("threadId","t").put("turnId","u").put("delta","Model progress")));
  check(restored.progressKind.isEmpty());check(restored.progress.equals("Model progress"));
  restored.accept(new JSONObject().put("method","item/started").put("params",new JSONObject().put("threadId","t").put("turnId","u").put("item",new JSONObject().put("type","dynamicToolCall").put("tool","device.info"))));
  check(restored.progressKind.equals("tool"));check(restored.progressArgs.getString(0).equals("device.info"));
  restored.snapshot(new JSONObject().put("items",new JSONArray()));check(restored.progressKind.isEmpty());
  System.out.println("PASS: "+count+" live progress metadata assertions");
 }
}
