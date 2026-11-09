package os.aios.agent;
import org.json.*;
public class UiActionReadbackTest {
 static int checks,reads;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static JSONObject request()throws Exception{return new JSONObject().put("op","tap").put("session_id","s");}
 static JSONObject receipt(String state,boolean executed)throws Exception{return new JSONObject().put("state",state).put("executed",executed).put("session_id","s").put("requires_observation",true);}
 static JSONObject tree(JSONObject q)throws Exception{check(q.getString("op").equals("tree"));check(q.getString("session_id").equals("s"));reads++;return new JSONObject().put("state","active").put("session_id","s").put("observation_id","new").put("windows",new JSONArray().put(new JSONObject().put("text","full page"))).put("truncated",true);}
 public static void main(String[] args)throws Exception{
  JSONObject a=UiActionReadback.complete(request(),receipt("active",true),UiActionReadbackTest::tree);
  check(a.getBoolean("executed"));check(reads==2);check(!a.getBoolean("requires_observation"));check(a.getJSONObject("observation").getBoolean("truncated"));check(a.getJSONObject("observation").getJSONArray("windows").length()==1);
  a=UiActionReadback.complete(request(),receipt("observation_required",false),UiActionReadbackTest::tree);
  check(!a.getBoolean("executed"));check(a.getString("state").equals("observation_required"));check(reads==3);check(a.getJSONObject("observation").getString("observation_id").equals("new"));
  for(String state:new String[]{"outcome_unknown","paused","user_takeover","permission_required","action_rejected","session_unavailable"}){
   a=UiActionReadback.complete(request(),receipt(state,false),q->{throw new AssertionError("Must not read "+state);});check(!a.has("observation"));
  }
  a=UiActionReadback.complete(request(),receipt("active",true),q->{throw new IllegalStateException("read failed");});check(a.getBoolean("executed"));check(a.getBoolean("observation_read_failed"));check(a.getString("state").equals("active"));
  final int[] attempts={0};a=UiActionReadback.complete(request(),receipt("active",true),q->{attempts[0]++;return receipt("observation_unavailable",false);});check(attempts[0]==2);check(a.getBoolean("observation_read_unavailable"));
  a=UiActionReadback.complete(request(),receipt("active",true),q->receipt("paused",false));check(a.getBoolean("executed"));check(a.getJSONObject("observation").getString("state").equals("paused"));check(a.getBoolean("observation_read_unavailable"));
  a=UiActionReadback.complete(request().put("session_id","other"),receipt("active",true),q->{throw new AssertionError("Wrong session");});check(!a.has("observation"));
  a=UiActionReadback.complete(request().put("op","tree"),receipt("active",true),q->{throw new AssertionError("Recursive read");});check(!a.has("observation"));
  a=UiActionReadback.complete(request(),receipt("action_submitted",true),UiActionReadbackTest::tree);check(a.getString("state").equals("action_submitted"));
  Thread.currentThread().interrupt();a=UiActionReadback.complete(request(),receipt("active",true),q->{throw new AssertionError("Interrupted");});check(a.getBoolean("executed"));check(a.getBoolean("observation_read_failed"));check(Thread.interrupted());
  UiActionReadback.Cadence cadence=new UiActionReadback.Cadence();check(cadence.reserve("a",0)==0);check(cadence.reserve("a",100)==400);check(cadence.reserve("b",100)==0);check(cadence.reserve("a",900)==100);check(cadence.reserve("a",62000)==0);
  JSONObject button=new JSONObject().put("id","search").put("class","Button").put("text","搜索").put("clickable",true).put("enabled",true).put("bounds",new JSONArray(new int[]{0,0,300,100}));
  JSONObject banner=new JSONObject().put("text","ad1").put("bounds",new JSONArray(new int[]{0,100,300,300}));
  JSONObject before=new JSONObject().put("bounds",new JSONArray(new int[]{0,0,300,600})).put("children",new JSONArray().put(button).put(banner));
  JSONObject after=new JSONObject(before.toString());after.getJSONArray("children").getJSONObject(1).put("text","ad2");check(UiActionReadback.sameTarget(before,after,100,50));
  after.getJSONArray("children").getJSONObject(0).put("text","购买");check(!UiActionReadback.sameTarget(before,after,100,50));
  after=new JSONObject(before.toString());after.getJSONArray("children").getJSONObject(0).put("bounds",new JSONArray(new int[]{0,10,300,110}));check(!UiActionReadback.sameTarget(before,after,100,50));
  after=new JSONObject(before.toString());after.getJSONArray("children").getJSONObject(0).put("enabled",false);check(!UiActionReadback.sameTarget(before,after,100,50));check(!UiActionReadback.sameTarget(before,before,100,400));
  System.out.println(checks+" UI readback checks passed");
 }
}
