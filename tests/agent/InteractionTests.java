package os.aios.agent;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import os.aios.core.*;
/** Isolated conversation state exercises actual queue transitions without model execution. */
public final class InteractionTests extends Instrumentation {
 int checks;void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();Context target=getTargetContext();String prefix="interaction-test-"+java.util.UUID.randomUUID()+"-";
 java.util.Set<String> names=new java.util.HashSet<>();Context c=new ContextWrapper(target){public SharedPreferences getSharedPreferences(String n,int m){names.add(prefix+n);return super.getSharedPreferences(prefix+n,m);}public void sendBroadcast(Intent i){}};
 try{
  check(Authorization.enabled(target),"existing authorization enabled; never changed by test");
  JSONObject base=new JSONObject().put("conversation_id","fixture-conversation").put("request_id","fixture-request").put("thread_id","fixture-thread").put("turn_id","fixture-turn").put("status","running").put("text","original").put("history",new JSONArray());
  c.getSharedPreferences("assistant_conversation",0).edit().putString("state",base.toString()).commit();
  AssistantConversation.enqueue(c,"first queued");JSONObject s=AssistantConversation.enqueue(c,"second queued");
  check(s.getString("request_id").equals("fixture-request")&&s.getString("text").equals("original"),"busy submit retains original identity");
  check(s.getJSONArray("pending_messages").length()==2,"both messages persisted");
  String first=s.getJSONArray("pending_messages").getJSONObject(0).getString("id");
  boolean rejected=false;try{AssistantConversation.followupAction(c,"other",first,"remove");}catch(Exception expected){rejected=true;}check(rejected,"cross-conversation action rejected");
  AssistantConversation.followupAction(c,"fixture-conversation",first,"steer");JSONObject claimed=AssistantConversation.claimSteer(c,"fixture-request","fixture-turn");check(claimed.getString("text").equals("first queued"),"explicit steering claimed");
  AssistantConversation.finishSteer(c,"fixture-request",first,true);s=AssistantConversation.state(c);check(s.getJSONArray("pending_messages").length()==1,"accepted steer removed once");check(s.getJSONArray("history").getJSONObject(0).getString("text").equals("first queued"),"accepted guidance shown as user text");
  AssistantConversation.cancel(c);check(!AssistantConversation.promoteFollowup(c,false),"cancel never drains queue");
  s=AssistantConversation.state(c);s.put("status","interrupted");c.getSharedPreferences("assistant_conversation",0).edit().putString("state",s.toString()).commit();
  check(!AssistantConversation.promoteFollowup(c,false),"interrupted remains paused");check(AssistantConversation.promoteFollowup(c,true),"explicit queue continue");s=AssistantConversation.state(c);check(s.getString("text").equals("second queued")&&!s.optBoolean("cancel_requested"),"FIFO next request");
  check(s.getJSONArray("pending_messages").length()==0&&!s.has("turn_id"),"new request never reuses old turn");
  s.put("status","running").put("turn_id","new-turn");c.getSharedPreferences("assistant_conversation",0).edit().putString("state",s.toString()).commit();
  s=AssistantConversation.enqueue(c,"uncertain guidance");String uncertain=s.getJSONArray("pending_messages").getJSONObject(0).getString("id");String request=s.getString("request_id");
  AssistantConversation.followupAction(c,"fixture-conversation",uncertain,"steer");check(AssistantConversation.claimSteer(c,request,"new-turn")!=null,"claim persisted before RPC");
  check(AssistantConversation.claimSteer(c,request,"new-turn")==null,"restart never replays in-flight submission");
  check(AssistantConversation.state(c).getJSONArray("pending_messages").getJSONObject(0).getString("state").equals("unknown"),"uncertainty retained");
  AssistantConversation.followupAction(c,"fixture-conversation",uncertain,"remove");
  for(int i=0;i<20;i++)AssistantConversation.enqueue(c,"bounded "+i);rejected=false;try{AssistantConversation.enqueue(c,"overflow");}catch(Exception expected){rejected=true;}check(rejected,"queue bounded");
  for(String tag:new String[]{"en","zh-CN","zh-TW","fr","ar"}){android.content.res.Configuration config=new android.content.res.Configuration(target.getResources().getConfiguration());config.setLocale(java.util.Locale.forLanguageTag(tag));Context local=target.createConfigurationContext(config);for(String key:new String[]{"followup_count","followup_queued","followup_steer","followup_remove","followup_continue","followup_sending","followup_unknown","followup_limit"}){int id=local.getResources().getIdentifier(key,"string",target.getPackageName());check(id!=0&&!local.getString(id).isBlank(),"resource "+tag+" "+key);}check(local.getString(local.getResources().getIdentifier("followup_count","string",target.getPackageName()),2).contains("2"),"placeholder "+tag);}
  runOnMainSync(()->{TextView markdown=new TextView(target);MarkdownText.bind(markdown,"**copy me** [link](https://example.com)");check(markdown.isTextSelectable(),"markdown selectable");check(markdown.getMovementMethod() instanceof android.text.method.ArrowKeyMovementMethod,"link renderer preserves native selection");EditText field=AssistantTextInput.create(target,"original");check(field.isLongClickable()&&field.getKeyListener()!=null,"input retains editing and long press");});
  out.putString("result","PASS "+checks+" checks");
 }catch(Throwable t){out.putString("error",android.util.Log.getStackTraceString(t));}finally{for(String name:names)target.deleteSharedPreferences(name);finish(out.containsKey("error")?0:-1,out);}}
}
