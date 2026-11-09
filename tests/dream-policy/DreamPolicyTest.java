package os.aios.codex;
import org.json.*;import java.nio.file.*;
public class DreamPolicyTest{
 public static void main(String[] args)throws Exception{
  CodexProcess p=new CodexProcess();p.home=Path.of(args[0]).toFile();p.workspace=Path.of(args[1]).toFile();
  Path original=p.home.toPath().resolve("catalog.json");String text="{\"models\":[{\"slug\":\"test\",\"shell_type\":\"unified_exec\"}]}";Files.writeString(original,text);p.catalog=original.toString();
  p.effective.put("mcp_servers",new JSONObject().put("test",new JSONObject()));
  JSONArray models=new JSONArray().put(new JSONObject().put("model","economy").put("supportedReasoningEfforts",new JSONArray().put(new JSONObject().put("reasoningEffort","low"))));
  JSONObject result=DreamPolicy.params(p,"economy",models),config=result.getJSONObject("config");
  if(result.getString("cwd").equals(p.workspace.getAbsolutePath()))throw new AssertionError("assistant workspace leaked");
  if(!result.getString("model").equals("economy")||!config.getString("model_reasoning_effort").equals("low"))throw new AssertionError("selection");
  if(result.getJSONArray("dynamicTools").length()!=0||result.getJSONArray("environments").length()!=0||!result.getString("sandbox").equals("read-only"))throw new AssertionError("isolation");
  for(String key:MinutesPolicy.FEATURES.split(","))if(config.getBoolean("features."+key))throw new AssertionError("feature "+key);
  if(config.getJSONObject("mcp_servers").getJSONObject("test").getBoolean("enabled"))throw new AssertionError("MCP");
  if(!Files.readString(original).equals(text))throw new AssertionError("normal catalog modified");
  boolean denied=false;try{DreamPolicy.params(p,"unavailable",models);}catch(IllegalArgumentException e){denied=true;}if(!denied)throw new AssertionError("silent fallback");
  if(!result.getString("baseInstructions").contains("remaining_source_ids"))throw new AssertionError("missing save skill");
  System.out.println("PASS: real DreamPolicy model/low selection, unavailable model rejection, tool/feature/MCP denial, catalog isolation, installed Skill contract");
 }
}
