package os.aios.codex;
import org.json.*;
public final class StatelessTextInferenceTest {
 static int count;static void check(boolean ok){count++;if(!ok)throw new AssertionError("stateless check "+count);}
 public static void main(String[] args)throws Exception{
  JSONObject request=new JSONObject().put("instruction","Classify one notification").put("input","current notification").put("model","example").put("max_tokens",24).put("json",true).put("schema",new JSONObject().put("type","object"));
  for(String protocol:new String[]{"responses","chat","messages"}){
   String baseline=StatelessTextInference.body(protocol,request).toString();
   for(int i=0;i<64;i++){JSONObject body=StatelessTextInference.body(protocol,request);check(body.toString().equals(baseline));check(!body.has("conversation")&&!body.has("previous_response_id")&&!body.has("tools"));if(!protocol.equals("messages"))check(body.has("store")&&!body.getBoolean("store"));}
  }
  JSONObject complete=new JSONObject("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}]}");
  check(StatelessTextInference.text("responses",complete).equals("ok"));
  for(String status:new String[]{"failed","incomplete","in_progress"}){complete.put("status",status);boolean failed=false;try{StatelessTextInference.text("responses",complete);}catch(Exception e){failed=true;}check(failed);}
  boolean bounded=false;try{StatelessTextInference.body("responses",new JSONObject(request.toString()).put("input","x".repeat(16001)));}catch(IllegalArgumentException e){bounded=true;}check(bounded);
  System.out.println("PASS "+count+" stateless request and response assertions");
 }
}
