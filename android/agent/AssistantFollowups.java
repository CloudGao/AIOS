package os.aios.agent;
import android.content.Context;import android.widget.*;import org.json.*;import os.aios.core.*;
final class AssistantFollowups {
 static void append(Context c,LinearLayout parent,JSONObject state)throws Exception{
  JSONArray pending=state.optJSONArray("pending_messages");if(pending==null||pending.length()==0)return;
  String conversation=state.getString("conversation_id");boolean running=state.optString("status").equals("running"),busy=running||state.optString("status").equals("queued");
  parent.addView(AiosUi.meta(c,c.getString(R.string.followup_count,pending.length())));
  for(int i=0;i<pending.length();i++){JSONObject item=pending.getJSONObject(i);String id=item.getString("id"),phase=item.optString("state");
   LinearLayout card=AiosUi.column(c);TextView body=AiosUi.selectable(c,item.getString("text"),16,AiosTheme.text(c));card.addView(body);
   card.addView(AiosUi.meta(c,c.getString(phase.equals("unknown")?R.string.followup_unknown:phase.equals("sending")||phase.equals("steer")?R.string.followup_sending:R.string.followup_queued)));
   if(running&&phase.equals("queued")&&!state.optBoolean("cancel_requested"))card.addView(AiosUi.textButton(c,c.getString(R.string.followup_steer),()->act(c,conversation,id,"steer")));
   if(!phase.equals("sending"))card.addView(AiosUi.textButton(c,c.getString(R.string.followup_remove),()->act(c,conversation,id,"remove")));
   parent.addView(card);
  }
  if(!busy)parent.addView(AiosUi.textButton(c,c.getString(R.string.followup_continue),()->{try{if(conversation.equals(AssistantConversation.state(c).optString("conversation_id")))AssistantConversation.promoteFollowup(c,true);}catch(Exception e){Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show();}}));
 }
 static void act(Context c,String conversation,String id,String action){try{AssistantConversation.followupAction(c,conversation,id,action);}catch(Exception e){Toast.makeText(c,e.getMessage(),Toast.LENGTH_LONG).show();}}
}
