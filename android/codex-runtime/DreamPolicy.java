package os.aios.codex;
import org.json.*;
import java.nio.file.Files;
import java.io.File;
/** Separate, tool-free review identity; never borrows the foreground model/effort. */
final class DreamPolicy {
 static JSONObject params(CodexProcess process,String model,JSONArray models)throws Exception{
  boolean supported=false;
  for(int i=0;i<models.length();i++){
   JSONObject m=models.getJSONObject(i);if(!m.optString("model").equals(model))continue;
   JSONArray efforts=m.optJSONArray("supportedReasoningEfforts");
   if(efforts!=null)for(int j=0;j<efforts.length();j++)if(efforts.getJSONObject(j).optString("reasoningEffort").equals("low"))supported=true;
  }
  if(!supported)throw new IllegalArgumentException("回顾模型不可用或不支持 low，请在空闲回顾设置中重新选择");
  File root=new File(process.workspace,"dreaming-module/skills/aios-dreaming");
  String instruction=os.aios.core.CompatFiles.readString(new File(root,"SKILL.md").toPath())+"\n"+os.aios.core.CompatFiles.readString(new File(root,"references/save-contract.md").toPath());
  File isolated=new File(process.home,"dreaming-isolated");if(!isolated.isDirectory()&&!isolated.mkdirs())throw new java.io.IOException("无法建立回顾隔离目录");
  JSONObject p=MinutesPolicy.params(process).put("cwd",isolated.getAbsolutePath()).put("baseInstructions",instruction+PresentationPolicy.INSTRUCTIONS).put("model",model)
    .put("dynamicTools",new JSONArray()).put("environments",new JSONArray()).put("selectedCapabilityRoots",new JSONArray());
  p.getJSONObject("config").put("model_reasoning_effort","low");return p;
 }
 private DreamPolicy(){}
}
