package os.aios.agent;

import android.content.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** Shared native rendering; cards and notifications mutate the same revisioned request. */
public final class InterventionCards {
 public static void append(Context c,LinearLayout parent,String thread){try{AgentService a=AgentService.instance;if(a==null||thread.isEmpty())return;JSONArray rows=a.interventions.store.list(thread);for(int i=rows.length()-1;i>=0;i--)add(c,parent,rows.getJSONObject(i));}catch(Exception e){android.util.Log.w("AIOSIntervention","Cannot render requests",e);}}
 public static boolean pending(String thread,String turn,String request){try{AgentService a=AgentService.instance;if(a==null)return false;JSONArray rows=a.interventions.store.forTurn(thread,turn,request);for(int i=0;i<rows.length();i++)if(java.util.Set.of("waiting_user","handoff_active").contains(rows.getJSONObject(i).optString("state")))return true;}catch(Exception e){android.util.Log.w("AIOSIntervention","Cannot read turn",e);}return false;}
 public static void appendTurn(Context c,LinearLayout parent,String thread,String turn,String request){try{AgentService a=AgentService.instance;if(a==null||thread.isEmpty())return;JSONArray rows=a.interventions.store.forTurn(thread,turn,request);for(int i=0;i<rows.length();i++)add(c,parent,rows.getJSONObject(i));}catch(Exception e){android.util.Log.w("AIOSIntervention","Cannot render turn",e);}}
 public static boolean running(Context c,String thread,String turn,String request){try{
  JSONObject state=AssistantConversation.state(c);
  if(!thread.equals(state.optString("thread_id"))||!java.util.Set.of("running","queued").contains(state.optString("status")))return false;
  return !request.isEmpty()?request.equals(state.optString("request_id")):!turn.isEmpty()&&turn.equals(state.optString("turn_id"));
 }catch(Exception ignored){return false;}}
 public static void appendExchange(Context c,LinearLayout parent,String thread,String request,String turn){
  LinearLayout cards=AiosUi.column(c);appendTurn(c,cards,thread,turn,request);if(cards.getChildCount()==0)return;
  LinearLayout details=os.aios.core.ExecutionDetails.create(c,parent,thread+":"+(request.isEmpty()?turn:request),running(c,thread,turn,request),pending(thread,turn,request));details.addView(cards);
 }
 public static void appendHistoryExchange(Context c,LinearLayout parent,JSONObject state,JSONObject item,int index)throws Exception{
  String thread=state.optString("thread_id"),request=item.optString("request_id"),turn=item.optString("turn_id");
  if(!request.isEmpty()||!turn.isEmpty()){appendExchange(c,parent,thread,request,turn);return;}
  AgentService a=AgentService.instance;if(a==null||thread.isEmpty())return;
  long end=Long.MAX_VALUE;JSONArray history=state.optJSONArray("history");for(int i=index+1;history!=null&&i<history.length();i++){JSONObject next=history.getJSONObject(i);if(next.optString("role").equals("user")){end=next.optLong("at");break;}}
  JSONArray rows=a.interventions.store.list(thread);LinearLayout cards=AiosUi.column(c);boolean pending=false;
  for(int i=rows.length()-1;i>=0;i--){JSONObject row=rows.getJSONObject(i);long at=row.optLong("created_at");if(at<item.optLong("at")||at>=end)continue;add(c,cards,row);pending|=java.util.Set.of("waiting_user","handoff_active").contains(row.optString("state"));}
  if(cards.getChildCount()>0)os.aios.core.ExecutionDetails.create(c,parent,thread+":legacy:"+item.optLong("at"),pending).addView(cards);
 }
 static int dp(Context c,int n){return AiosUi.dp(c,n);}
 /** Pending requests use tertiary_container (attention), resolved ones the plain surface. */
 static void add(Context c,LinearLayout parent,JSONObject row)throws Exception{
  JSONObject args=row.getJSONObject("arguments");String state=row.getString("state");boolean pending=state.equals("waiting_user")||state.equals("handoff_active");
  int bg=pending?AiosTheme.tertiary(c):AiosTheme.surface(c),fg=pending?AiosTheme.onTertiary(c):AiosTheme.text(c),sub=pending?AiosTheme.onTertiary(c):AiosTheme.muted(c);
  LinearLayout card=AiosUi.column(c);card.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,12));card.setBackground(AiosUi.shape(c,bg,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,4);lp.bottomMargin=dp(c,12);card.setLayoutParams(lp);
  LinearLayout head=AiosUi.row(c);LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(dp(c,20),dp(c,20));il.setMarginEnd(dp(c,10));head.addView(AiosUi.icon(c,pending?"bell":"check",fg),il);
  TextView title=AiosUi.text(c,args.getString("title"),16,fg);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));head.addView(title,new LinearLayout.LayoutParams(0,-2,1));card.addView(head);
  String detail=(args.optString("reason")+"\n"+args.optString("action_summary")).trim();if(!detail.isEmpty()){TextView body=AiosUi.text(c,detail,14,sub);body.setPaddingRelative(dp(c,30),dp(c,4),0,0);card.addView(body);}
  LinearLayout actions=AiosUi.column(c);actions.setGravity(android.view.Gravity.END);actions.setPadding(0,dp(c,8),0,0);
  if(state.equals("resolved")){TextView result=AiosUi.text(c,c.getString(R.string.assistant_handled_d745b)+row.getJSONObject("result").optString("selected_option",c.getString(R.string.assistant_system_permission_result_received_11b53))+c.getString(R.string.assistant_task_outcome_still_needs_verification_bcfb6),13,sub);result.setPaddingRelative(dp(c,30),dp(c,4),0,0);card.addView(result);if(AgentService.instance.interventions.store.undelivered(row.getString("thread_id"),row.getString("assistant_request_id")))actions.addView(button(c,c.getString(R.string.assistant_resume_original_task_df546),true,()->AgentService.instance.interventions.deliver(row.getString("id"))));}
  else if(pending){
   if(args.getString("kind").equals("decision")){JSONArray choices=args.getJSONArray("options");LinearLayout wrap=AiosUi.column(c);wrap.setPaddingRelative(0,dp(c,4),0,0);for(int i=0;i<choices.length();i++){JSONObject choice=choices.getJSONObject(i);TextView b=button(c,choice.getString("label"),i==0,()->AgentService.instance.interventions.resolve(row.getString("id"),row.getLong("revision"),choice.getString("id")));LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=dp(c,6);wrap.addView(b,bl);}card.addView(wrap);}
   else {if(args.optString("handoff_reason").equals("third_party_permission")&&state.equals("handoff_active"))actions.addView(button(c,c.getString(R.string.assistant_done_return_to_assistant_721e8),true,()->AgentService.instance.interventions.returnHandoff(row.getString("id"),args.optString("ui_session_id"))));
    actions.addView(button(c,args.optString("handoff_reason").equals("system_control")?c.getString(R.string.approval_system_open):args.optString("handoff_reason").equals("system_permission")?c.getString(R.string.assistant_allow_access_3a6e6):c.getString(R.string.assistant_open_verification_screen_a3a5b),true,()->{
    if(args.optString("handoff_reason").equals("system_permission")){c.startActivity(new Intent(c,InterventionActionActivity.class).putExtra("id",row.getString("id")).putExtra("revision",row.getLong("revision")).putExtra("choice","open").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;}
    InterventionCoordinator coordinator=AgentService.instance.interventions;JSONObject latest=coordinator.begin(row.getString("id"),row.getLong("revision"));
    if(args.optString("handoff_reason").equals("system_control"))return;
    if(args.optString("handoff_reason").equals("third_party_permission")){c.stopService(new Intent(c,AssistantOrbService.class));return;}
    c.startActivity(new Intent(c,UiViewerActivity.class).putExtra("session_id",args.getString("ui_session_id")).putExtra("intervention_id",row.getString("id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   }));}
   TextView cancel=button(c,c.getString(R.string.assistant_cancel_this_operation_79c71),false,()->AgentService.instance.interventions.resolve(row.getString("id"),row.getLong("revision"),"cancel"));actions.addView(cancel,0);
  }else{TextView status=AiosUi.text(c,c.getString(R.string.assistant_request_no_longer_valid_8f86a)+state,13,sub);status.setPaddingRelative(dp(c,30),dp(c,4),0,0);card.addView(status);}
  if(actions.getChildCount()>0)card.addView(actions);parent.addView(card);
 }
 interface Action{void run()throws Exception;}
 /** Filled for the primary choice, text button otherwise; both guard against the keyguard. */
 static TextView button(Context c,String label,boolean primary,Action action){
  TextView b=primary?AiosUi.filledButton(c,label,null):AiosUi.textButton(c,label,null);
  if(!primary)b.setTextColor(AiosTheme.onTertiary(c));
  b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);b.setEllipsize(null);b.setMinHeight(dp(c,48));b.setPaddingRelative(dp(c,16),dp(c,10),dp(c,16),dp(c,10));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,6);b.setLayoutParams(lp);
  AiosUi.clickable(b,()->{try{if(c.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked())throw new SecurityException(c.getString(R.string.assistant_please_unlock_first_83552));action.run();b.setEnabled(false);b.setAlpha(.5f);}catch(Exception e){Toast.makeText(c,os.aios.core.DisplayText.clean(e.getMessage()),Toast.LENGTH_LONG).show();}});
  return b;
 }
}
