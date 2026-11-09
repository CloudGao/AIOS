package os.aios.agent;
import android.app.*;
import android.os.Bundle;
import android.widget.*;
/** Optional text fallback; never shown until requested from the orb menu. */
public final class AssistantInputActivity extends Activity {
 private TextView reply;private boolean registered;
 private final android.content.BroadcastReceiver changes=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,android.content.Intent i){renderReply();}};
 private void renderReply(){if(reply==null)return;try{org.json.JSONObject s=AssistantConversation.state(this);String status=s.optString("status"),text=s.optString("reply");
  if(status.equals("queued"))text=AssistantInputActivity.this.getString(R.string.assistant_sending_to_assistant_c9dcb);
  else if(status.equals("running")){text=s.optString("partial_reply");if(text.isEmpty())text=AssistantLocale.progress(this,s);}
  else if(status.equals("failed")||status.equals("interrupted"))text=AssistantLocale.notice(this,s);
  reply.setText(text.isBlank()?AssistantInputActivity.this.getString(R.string.assistant_continue_this_conversation_08a4f):text);
 }catch(Exception e){reply.setText(AssistantInputActivity.this.getString(R.string.assistant_could_not_load_the_conversation_71967));}}
 @Override protected void onStart(){super.onStart();registerReceiver(changes,new android.content.IntentFilter(AssistantConversation.CHANGED),android.content.Context.RECEIVER_NOT_EXPORTED);registered=true;renderReply();}
 @Override protected void onStop(){if(registered){unregisterReceiver(changes);registered=false;}super.onStop();}

 @Override public void onCreate(Bundle state){super.onCreate(state);int p=os.aios.core.AiosUi.dp(this,24);LinearLayout content=os.aios.core.AiosUi.column(this);content.setPadding(p,os.aios.core.AiosUi.dp(this,8),p,0);
  reply=os.aios.core.AiosUi.body(this,"");reply.setTextIsSelectable(true);reply.setPadding(os.aios.core.AiosUi.dp(this,4),0,os.aios.core.AiosUi.dp(this,4),os.aios.core.AiosUi.dp(this,16));renderReply();
  ScrollView scroll=new ScrollView(this);scroll.addView(reply,new ScrollView.LayoutParams(-1,-2));content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  EditText input=os.aios.core.AiosUi.area(this,AssistantInputActivity.this.getString(R.string.assistant_type_your_next_message_8cbbe),getPreferences(0).getString("draft",""),3);input.setMaxLines(7);content.addView(input,new LinearLayout.LayoutParams(-1,-2));
  AlertDialog d=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(AssistantInputActivity.this.getString(R.string.assistant_current_conversation_d0882))).setView(content).setNegativeButton(os.aios.core.DisplayText.clean(AssistantInputActivity.this.getString(R.string.assistant_close_6c14b)),(a,w)->finish()).setPositiveButton(os.aios.core.DisplayText.clean(AssistantInputActivity.this.getString(R.string.assistant_send_1214d)),null).create();
  d.setOnShowListener(a->{input.requestFocus();d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.getButton(-1).setOnClickListener(v->{try{AssistantConversation.enqueue(this,input.getText().toString());input.setText("");d.dismiss();finish();}catch(Exception e){input.setError(os.aios.core.DisplayText.clean(e.getMessage()));}});});d.setOnDismissListener(a->{getPreferences(0).edit().putString("draft",input.getText().toString()).apply();finish();});d.show();
 }
}
