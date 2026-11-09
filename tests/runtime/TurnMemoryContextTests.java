package os.aios.codex;
import org.json.*;
public final class TurnMemoryContextTests {
 static int checks;
 static void check(boolean yes){checks++;if(!yes)throw new AssertionError("check "+checks);}
 static JSONObject snapshot(String text,long epoch)throws Exception{return new JSONObject().put("text",text).put("memory_ids",new JSONArray().put("m1")).put("entries",new JSONArray()).put("epoch",epoch).put("reset_epoch",epoch);}
 public static void main(String[] args)throws Exception{
  JSONArray input=new JSONArray().put(new JSONObject().put("type","text").put("text","install WeChat")).put(new JSONObject().put("type","image").put("text","private image"));
  int[] calls={0};
  JSONObject result=TurnMemoryContext.load((action,p)->{
   calls[0]++;check(p.getString("session_id").equals("codex:thread"));
   if(action.equals("memory.context")){check(p.getString("query").contains("install WeChat"));check(!p.getString("query").contains("private image"));check(p.getInt("max_chars")==6000);return new JSONObject().put("data",snapshot("store first",1));}
   check(p.getLong("reset_epoch")==1);check(p.getJSONArray("memory_ids").getString(0).equals("m1"));return new JSONObject();
  },"thread",input);
  check(calls[0]==2);check(result.getString("value").contains("store first"));
  int[] attempts={0};
  result=TurnMemoryContext.load((action,p)->{if(action.equals("memory.context"))return snapshot(++attempts[0]==1?"old":"corrected",attempts[0]);if(attempts[0]==1)throw new Exception("changed");return new JSONObject();},"thread",input);
  check(attempts[0]==2);check(result.getString("value").contains("corrected"));check(!result.getString("value").contains("\nold\n"));
  result=TurnMemoryContext.load((action,p)->{throw new Exception("secret detail");},"thread",input);
  check(result.getString("value").contains("unavailable"));check(!result.toString().contains("secret detail"));check(!result.toString().contains("corrected"));
  result=TurnMemoryContext.load((action,p)->{if(action.equals("memory.context"))return snapshot("",3);return new JSONObject();},"other",new JSONArray());
  check(result.getString("value").contains("epoch=3"));check(!result.toString().contains("store first"));
  System.out.println("PASS: "+checks+" turn-memory assertions");
 }
}
