package os.aios.agent;
import os.aios.workspace.ExecutionState;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import org.json.*;
/** Background virtual-screen list: grouped rows with tri-state dots; open to view live, × to close. */
public final class UiSessionsActivity extends Activity {
 final Handler handler=new Handler(Looper.getMainLooper());
 LinearLayout panel,list;String last="";
 final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
 int dp(int n){return AiosUi.dp(this,n);}
 public void onCreate(Bundle state){super.onCreate(state);ScrollView scroll=new ScrollView(this);panel=AiosUi.column(this);panel.addView(AiosUi.appBar(this,UiSessionsActivity.this.getString(R.string.assistant_background_screens_f2063),this::finish));TextView hint=AiosUi.secondary(this,UiSessionsActivity.this.getString(R.string.assistant_tap_for_live_view_taking_control_pauses_the_task_9922f));hint.setPadding(dp(16),0,dp(16),dp(12));panel.addView(hint);list=AiosUi.group(this);panel.addView(list);scroll.addView(panel);AiosUi.page(this,scroll);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});}
 protected void onResume(){super.onResume();handler.post(refresh);}
 protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
 void render(){try{
  AgentService a=AgentService.instance;JSONObject data=a==null?new JSONObject():a.ui.listing();String current=data.toString();if(current.equals(last))return;last=current;list.removeAllViews();
  JSONArray rows=data.optJSONArray("sessions");java.util.List<JSONObject> visible=new java.util.ArrayList<>();
  if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!"background".equals(row.optString("mode")))continue;String filter=getIntent().getStringExtra("conversation_id");if(filter!=null&&!filter.equals(row.optString("conversation_id")))continue;visible.add(row);}
  if(visible.isEmpty()){list.addView(AiosUi.empty(this,"screen",UiSessionsActivity.this.getString(R.string.assistant_no_background_screen_tasks_106b9),UiSessionsActivity.this.getString(R.string.assistant_screens_appear_here_when_the_assistant_works_in_an_app_d1eb4)));return;}
  for(int i=0;i<visible.size();i++){JSONObject row=visible.get(i);String id=row.getString("session_id");String userState=row.optString("user_state",ExecutionState.RUNNING);String label=AssistantLocale.label(this,userState);
   int color="paused".equals(userState)?AiosTheme.warn(this):ExecutionState.RUNNING.equals(userState)?AiosTheme.accent(this):AiosTheme.outlineStrong(this);
   ImageView close=AiosUi.iconButton(this,"close",UiSessionsActivity.this.getString(R.string.assistant_close_this_virtual_screen_1fa50),()->startService(new Intent(this,AgentService.class).putExtra("ui_card_action","close").putExtra("session_id",id)));
   String meta=label+" · "+row.optString("app_label")+(row.optString("detail").isEmpty()?"":" · "+row.optString("detail"));
   LinearLayout item=AiosUi.statusRow(this,color,row.optString("task_name",UiSessionsActivity.this.getString(R.string.assistant_this_conversation_7df96)),meta,close,i==0,i==visible.size()-1,()->startActivity(new Intent(this,UiViewerActivity.class).putExtra("session_id",id)));
   item.setContentDescription(os.aios.core.DisplayText.clean(UiSessionsActivity.this.getString(R.string.assistant_open_virtual_screen_ce3d9)+row.optString("app_label")+"，"+row.optString("task_name")));list.addView(item);}
 }catch(Exception ignored){}}
}
