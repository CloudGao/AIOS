package os.aios.agent;
import org.json.*;
public class MinutesTurnResultTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 static JSONObject message(String phase,String text)throws Exception{return new JSONObject().put("type","agentMessage").put("phase",phase).put("text",text);}
 public static void main(String[] args)throws Exception {
  String marker="[AIOS-MINUTES:s:1]";
  JSONObject unrelated=new JSONObject().put("id","old").put("items",new JSONArray().put(message("final_answer","old")));
  JSONObject turn=new JSONObject().put("id","new").put("status","completed").put("items",new JSONArray()
    .put(new JSONObject().put("type","userMessage").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",marker+"\nrecording"))))
    .put(message("commentary","do not include" )).put(message("","fallback")).put(message("final_answer","minutes")));
  JSONObject thread=new JSONObject().put("turns",new JSONArray().put(unrelated).put(turn));
  check(MinutesTurnResult.find(thread,"",marker)==turn);
  check(MinutesTurnResult.find(thread,"old",marker)==unrelated);
  check(MinutesTurnResult.find(thread,"missing",marker)==null);
  check(MinutesTurnResult.find(thread,"","another-request")==null);
  check(MinutesTurnResult.reply(turn).equals("minutes"));
  check(MinutesTurnResult.reply(new JSONObject().put("items",new JSONArray().put(message("","fallback")))).equals("fallback"));
  check(MinutesTurnResult.reply(new JSONObject()).isEmpty());
  check(MinutesTurnResult.after(thread,"old",marker+"\nrecording")==turn);
  check(MinutesTurnResult.after(thread,"new",marker+"\nrecording")==null);
  check(MinutesTurnResult.after(thread,"missing",marker+"\nrecording")==null);
  JSONObject duplicate=new JSONObject(turn.toString()).put("id","duplicate");
  JSONObject ambiguous=new JSONObject().put("turns",new JSONArray().put(unrelated).put(turn).put(duplicate));
  boolean ambiguityRejected=false;try{MinutesTurnResult.after(ambiguous,"old",marker+"\nrecording");}catch(IllegalStateException expected){ambiguityRejected=true;}check(ambiguityRejected);
  turn.put("itemsView","summary");boolean rejected=false;try{MinutesTurnResult.reply(turn);}catch(IllegalStateException e){rejected=true;}check(rejected);
  MinutesTurnResult.TerminalObservation observed=new MinutesTurnResult.TerminalObservation();
  JSONObject negative=new JSONObject().put("id","turn-a").put("status","interrupted");
  check(!observed.observe(negative));
  check(!observed.observe(new JSONObject().put("id","turn-a").put("status","completed")));
  check(!observed.observe(negative));check(observed.observe(negative));
  check(!observed.observe(new JSONObject().put("id","turn-b").put("status","interrupted")));
  check(!observed.observe(null));
  check(!observed.observe(negative));observed.reset();check(!observed.observe(negative));
  check(!observed.observe(new JSONObject().put("id","turn-a").put("status","failed")));
  check(observed.observe(new JSONObject().put("id","turn-a").put("status","failed")));
  check(!observed.observe(new JSONObject().put("id","turn-a").put("status","inProgress")));
  check(!observed.observe(negative));
  System.out.println("PASS: durable turn recovery plus 12 terminal-observation checks: transient interruption, real failure, changing IDs/status, missing reads and reset");
 }
}
