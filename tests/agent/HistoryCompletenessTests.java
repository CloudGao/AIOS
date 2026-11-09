package os.aios.agent;
import android.app.Instrumentation;import android.os.*;import org.json.*;import java.util.*;import os.aios.core.*;
public final class HistoryCompletenessTests extends Instrumentation {
 String liveThread="01a1926a-a71f-7502-b5c8-ad8c237985bf";boolean originalFixture=true;
 int checks;void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
 public void onCreate(Bundle args){super.onCreate(args);if(args!=null&&args.containsKey("live_thread")){liveThread=args.getString("live_thread");originalFixture=false;}start();}
 JSONObject item(String id,String type,String text)throws Exception{return new JSONObject().put("id",id).put("type",type).put("text",text).put("phase",type.equals("agentMessage")?"final_answer":"");}
 JSONObject turn(String id,JSONArray items,String cursor)throws Exception{return new JSONObject().put("id",id).put("items",items).put("items_has_older",cursor!=null).put("items_next_cursor",cursor==null?JSONObject.NULL:cursor);}
 JSONObject page(JSONArray turns,String cursor)throws Exception{return new JSONObject().put("turns",turns).put("next_cursor",cursor==null?JSONObject.NULL:cursor);}
 List<String> ids(JSONArray values)throws Exception{ArrayList<String> result=new ArrayList<>();for(int i=0;i<values.length();i++)result.add(values.getJSONObject(i).getString("id"));return result;}
 List<JSONObject> displayed(JSONObject history)throws Exception{List<JSONObject> rows=new ArrayList<>();for(TranscriptText.Turn t:TranscriptText.turns(ConversationHistory.messages(history),true)){rows.addAll(t.users);rows.addAll(t.execution);rows.addAll(t.answers);}return rows;}
 void verifyReal(JSONObject history)throws Exception{
  JSONArray turns=history.getJSONArray("turns");check(turns.length()==2,"both native turns, including automatic continuation, available");
  int count=0;List<String> expected=new ArrayList<>();List<String> replies=new ArrayList<>();
  for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);JSONArray items=t.getJSONArray("items");count+=items.length();for(int j=0;j<items.length();j++){JSONObject m=items.getJSONObject(j);if(TranscriptText.internalItem(m)||TranscriptText.instructionRead(m))continue;if(m.optString("type").equals("userMessage")&&TranscriptText.userItem(m).isBlank())continue;expected.add(t.getString("id")+":"+m.getString("id"));if(m.optString("type").equals("agentMessage"))replies.add(m.getString("text"));}}
  check(count==59,"all 59 original records read");check(replies.size()==3,"three real assistant replies");
  List<String> actual=new ArrayList<>(),visibleReplies=new ArrayList<>();for(JSONObject m:displayed(history)){actual.add(m.getString("id"));if(m.getString("role").equals("assistant"))visibleReplies.add(m.getString("content_md"));}
  check(actual.equals(expected),"every non-instruction native record remains in source order");check(visibleReplies.equals(replies),"no intermediate/final assistant answer omitted or changed");
  check(visibleReplies.get(2).contains("都装好了"),"continuation completion answer restored");
 }
 public void onStart(){Bundle out=new Bundle();try{
  JSONObject real;try(java.io.InputStream stream=getContext().getAssets().open("app-store-native.json")){real=new JSONObject(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
  String untouched=real.toString();verifyReal(real);check(real.toString().equals(untouched),"raw history unchanged");
  JSONObject second=real.getJSONArray("turns").getJSONObject(1);JSONArray firstHalf=new JSONArray(),lastHalf=new JSONArray();JSONArray all=second.getJSONArray("items");for(int i=0;i<all.length();i++)(i<10?firstHalf:lastHalf).put(all.get(i));
  JSONObject split=turn(second.getString("id"),lastHalf,"earlier");String keyBefore=TranscriptText.turns(ConversationHistory.messages(page(new JSONArray().put(split),null)),true).get(0).key;
  ConversationHistory.prependItems(split,new JSONObject().put("items",firstHalf).put("next_cursor",JSONObject.NULL));
  check(ids(split.getJSONArray("items")).equals(ids(all)),"within-turn paging restores every item in original order");
  check(TranscriptText.turns(ConversationHistory.messages(page(new JSONArray().put(split),null)),true).get(0).key.equals(keyBefore),"expansion key survives loading earlier items");
  JSONObject joined=ConversationHistory.merge(page(new JSONArray().put(real.getJSONArray("turns").getJSONObject(1)),"earlier"),page(new JSONArray().put(real.getJSONArray("turns").getJSONObject(0)),null),true);verifyReal(joined);
  JSONObject refreshed=ConversationHistory.merge(joined,page(real.getJSONArray("turns"),null),false);verifyReal(refreshed);
  JSONArray before=new JSONArray().put(item("a","agentMessage","first reply")).put(item("tool","dynamicToolCall","work")).put(item("b","agentMessage","second reply"));
  List<JSONObject> boundaries=displayed(page(new JSONArray().put(turn("boundary",before,null)),null));check(boundaries.size()==3&&boundaries.get(0).getString("content_md").equals("first reply")&&boundaries.get(1).getString("kind").equals("dynamicToolCall")&&boundaries.get(2).getString("content_md").equals("second reply"),"execution between two final answers stays between them");
  // 130 records, three item pages; overlapping pages must not duplicate any item.
  JSONArray hundred=new JSONArray(),tail=new JSONArray(),middle=new JSONArray(),head=new JSONArray();for(int i=0;i<130;i++){JSONObject m=item("step"+i,"dynamicToolCall","step");hundred.put(m);if(i>=66)tail.put(m);if(i>=2&&i<=66)middle.put(m);if(i<=2)head.put(m);}
  JSONObject longTurn=turn("long",tail,"middle");ConversationHistory.prependItems(longTurn,new JSONObject().put("items",middle).put("next_cursor","head"));ConversationHistory.prependItems(longTurn,new JSONObject().put("items",head).put("next_cursor",JSONObject.NULL));check(ids(longTurn.getJSONArray("items")).equals(ids(hundred)),"130 item turn merges without missing/duplicate steps");check(!longTurn.getBoolean("items_has_older"),"complete item cursor terminates");
  JSONArray earlierTurns=new JSONArray(),laterTurns=new JSONArray();for(int i=0;i<25;i++){JSONObject t=turn("turn"+i,new JSONArray().put(item("reply"+i,"agentMessage","reply")),null);if(i<14)earlierTurns.put(t);if(i>=13)laterTurns.put(t);}JSONObject many=ConversationHistory.merge(page(laterTurns,"old"),page(earlierTurns,null),true);check(many.getJSONArray("turns").length()==25,"25 turns merged across 12-turn page boundary");check(displayed(many).size()==25,"all paginated replies remain visible");
  final int[] turnCalls={0};NativeThreadPager pager=new NativeThreadPager((method,p)->{if(method.equals("thread/turns/list")){turnCalls[0]++;check(!p.has("cursor"),"null cursor starts newest page");return new JSONObject().put("data",new JSONArray().put(new JSONObject().put("id","native"))).put("nextCursor",JSONObject.NULL);}return new JSONObject().put("data",new JSONArray().put(new JSONObject().put("item",item("final","agentMessage","kept")))).put("nextCursor",JSONObject.NULL);});check(pager.olderTurns("thread",null).getJSONArray("turns").length()==1&&turnCalls[0]==1,"null cursor no longer skips latest twelve turns");
  // Read through the production FD provider, not a fixture substitute.
  Bundle args=new Bundle();args.putString("thread_id",liveThread);Bundle reply=getTargetContext().getContentResolver().call(android.net.Uri.parse("content://os.aios.agent.history"),"latest",null,args);check(reply!=null&&!reply.containsKey("error"),"native history provider succeeds: "+(reply==null?"null":reply.getString("error")));ParcelFileDescriptor fd=reply.getParcelable("result_fd",ParcelFileDescriptor.class);check(fd!=null,"history uses FD transfer");try(java.io.InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(fd)){JSONObject live=new JSONObject(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));if(originalFixture)verifyReal(live);else check(!displayed(live).isEmpty(),"current provider thread retains messages");}
  for(Object value:getTargetContext().getSharedPreferences("assistant_conversation_archive",0).getAll().values())if(value instanceof String){JSONObject archived=new JSONObject((String)value);if(archived.optString("thread_id").equals("01a1926a-a71f-7502-b5c8-ad8c237985bf")){JSONArray h=archived.optJSONArray("history");out.putString("archive_history_count",Integer.toString(h==null?0:h.length()));}}
  NativeThreadPagerTest.main(new String[0]);
  out.putString("stream","PASS: "+checks+" native history completeness assertions\n");finish(-1,out);
 }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,out);}}
}
