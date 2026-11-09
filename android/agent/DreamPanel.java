package os.aios.agent;
import android.app.*;
import android.content.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
import os.aios.workspace.WorkspaceStore;

/** Human-facing settings and suggestions. No automatic action execution. */
final class DreamPanel {
 static String preview(JSONObject c)throws Exception{
  JSONObject p=new JSONObject(c.getString("payload")),before=p.optJSONObject("before");
  JSONObject after=before==null?new JSONObject():new JSONObject(before.toString());for(String field:new String[]{"content_md","content","intent","time_text","start_date","due_date","kind","scope_type","scope_id","valid_until"})if(p.has(field))after.put(field,p.get(field));
  if(!after.has("scope_type"))after.put("scope_type","project");if(!after.has("scope_id")||(p.has("scope_type")&&!p.has("scope_id")))after.put("scope_id",after.optString("scope_type").equals("project")?"aios:phone":"");
  String result=c.getString("title")+"\n"+(before==null?"新增":"变更前：\n"+before.optString("title")+"\n"+before.optString("content_md",before.optString("content"))+"\n时间："+before.optString("time_text")+" "+before.optString("start_date")+" → "+before.optString("due_date"));
  result+="\n变更后：\n"+after.optString("content_md",after.optString("content",after.optString("intent")))+"\n时间："+after.optString("time_text")+" "+after.optString("start_date")+" → "+after.optString("due_date");
  if(c.optString("kind").equals("memory"))result+="\n记忆类型："+after.optString("kind","fact")+"；范围："+after.optString("scope_type","project")+" "+after.optString("scope_id")+"；有效期（0为长期）："+after.optLong("valid_until",0);
  if(p.has("status"))result+="\n状态："+(before==null?"":before.optString("status")+" → ")+p.optString("status");
  result+="\n原因："+p.optString("reason")+"\n变化："+p.optString("change_summary");
  if(p.has("reconsiders"))result+="\n这次重新询问的依据："+p.optString("reconsider_reason");
  return result;
 }
 static boolean direct(JSONObject c)throws Exception{String kind=c.getString("kind");JSONObject p=new JSONObject(c.getString("payload"));return kind.equals("task")||kind.equals("memory")||(kind.equals("skill")&&!p.has("target_id")&&!p.optBoolean("needs_verification",true));}
 static void confirmPending(Activity a){
  confirmPending(a,null);
 }
 static int dp(Activity a,int n){return AiosUi.dp(a,n);}
 static void confirmPending(Activity a,java.util.Set<String> shown){
  if(a.isFinishing()||a.isDestroyed())return;
  try(WorkspaceStore store=new WorkspaceStore(a)){
   JSONArray all=store.dream("status",new JSONObject()).getJSONArray("proposals");
   java.util.ArrayList<JSONObject> proposals=new java.util.ArrayList<>();java.util.ArrayList<AiosUi.Choice> choices=new java.util.ArrayList<>();
   LinearLayout panel=AiosUi.column(a);ScrollView scroll=AiosUi.dialogContent(a,panel);
   text(a,panel,"以下内容尚未写入。逐项选择；关闭或暂不决定不会记为拒绝，也不会启动任务。");
   for(int i=0;i<all.length();i++){JSONObject metadata=all.getJSONObject(i);if(!metadata.optString("status").equals("pending")||(shown!=null&&shown.contains(metadata.getString("id"))))continue;JSONObject c=store.dream("change",new JSONObject().put("id",metadata.getString("id")));if(!direct(c))continue;if(shown!=null)shown.add(c.getString("id"));
    proposals.add(c);LinearLayout card=AiosUi.column(a);card.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,12));card.setBackground(AiosUi.shape(a,AiosTheme.surfaceHigh(a),16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(a,12);panel.addView(card,lp);
    TextView body=AiosUi.body(a,preview(c));body.setTextIsSelectable(true);card.addView(body);
    card.addView(AiosUi.actions(a,AiosUi.textButton(a,"来源与详情",()->detail(a,c))));
    AiosUi.Choice choice=new AiosUi.Choice(a,new String[]{"暂不决定","同意保存","不同意，不再问"},0,null);card.addView(choice.view);choices.add(choice);
   }
   if(proposals.isEmpty())return;
   AlertDialog dialog=new AlertDialog.Builder(a).setTitle(os.aios.core.DisplayText.clean("确认记忆与事项变更 · "+proposals.size()+" 项")).setView(scroll).setNegativeButton(os.aios.core.DisplayText.clean("稍后再看"),null).setPositiveButton(os.aios.core.DisplayText.clean("提交选择"),null).create();
   dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
    int applied=0;String failure="";
    try(WorkspaceStore current=new WorkspaceStore(a)){for(int i=0;i<proposals.size();i++){int choice=choices.get(i).getSelectedItemPosition();if(choice==0)continue;
     try{current.dream("resolve",new JSONObject().put("id",proposals.get(i).getString("id")).put("action",choice==1?"accept":"ignore"));choices.get(i).setSelection(0);choices.get(i).view.setEnabled(false);choices.get(i).view.setAlpha(.5f);applied++;}catch(Exception e){failure+=proposals.get(i).optString("title")+"："+e.getMessage()+"\n";}
    }}catch(Exception e){failure+=e.getMessage();}
    if(failure.isEmpty()){dialog.dismiss();Toast.makeText(a,os.aios.core.DisplayText.clean("已记录 "+applied+" 项选择；未选择的保持待确认"),1).show();}else new AlertDialog.Builder(a).setTitle(os.aios.core.DisplayText.clean("部分变更未保存")).setMessage(os.aios.core.DisplayText.clean(failure+"已成功的选择不会重复执行。内容冲突请关闭后重新核对。")).setPositiveButton(os.aios.core.DisplayText.clean("知道了"),null).show();
   }));dialog.show();
  }catch(Exception e){error(a,e);}
 }
 /** Tonal button inside dialogs and cards. */
 static void button(Activity a,LinearLayout panel,String title,Runnable action){TextView b=AiosUi.tonalButton(a,title,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(a,8);panel.addView(b,lp);}
 static void text(Activity a,LinearLayout p,String value){TextView t=AiosUi.secondary(a,value);t.setPadding(0,dp(a,6),0,dp(a,6));t.setTextIsSelectable(true);p.addView(t);}
 static void error(Activity a,Exception e){Toast.makeText(a,os.aios.core.DisplayText.clean(e.getMessage()==null?"请稍后重试":e.getMessage()),Toast.LENGTH_LONG).show();}
 /** Settings rows appended to the host “助手” group: 夜间回顾 switch + 回顾记录 row with pending badge. */
 static void settingsRows(Activity a,LinearLayout p){
  android.content.SharedPreferences prefs=DreamWorker.prefs(a);
  Switch enabled=AiosUi.m3switch(a,prefs.getBoolean("enabled",true),value->{prefs.edit().putBoolean("enabled",value).apply();if(!value)DreamWorker.preempt();DreamWorker.schedule(a);});
  LinearLayout night=AiosUi.settingRow(a,"moon","夜间回顾",prefs.getBoolean("twice",false)?"每天两次 · 整理技能、记忆与建议":"每天一次 · 整理技能、记忆与建议",enabled,false,false,null);p.addView(night);
  int pending=0;try(WorkspaceStore store=new WorkspaceStore(a)){JSONArray changes=store.dream("status",new JSONObject()).getJSONArray("proposals");for(int i=0;i<changes.length();i++)if(changes.getJSONObject(i).optString("status").equals("pending"))pending++;}catch(Exception ignored){}
  View trailing=pending>0?AiosUi.badge(a,pending):null;
  LinearLayout records=AiosUi.settingRow(a,"history","回顾记录与建议",pending>0?pending+" 项待你决定 · "+DreamWorker.statusText(a):DreamWorker.statusText(a),trailing,false,false,()->settingsSheet(a));p.addView(records);
 }
 /** Detailed review settings (model, cadence, manual run) in a dialog. */
 static void settingsSheet(Activity a){LinearLayout body=AiosUi.column(a);settings(a,body);new AlertDialog.Builder(a).setTitle(os.aios.core.DisplayText.clean("回顾与学习")).setView(AiosUi.dialogContent(a,body)).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).show();}
 static void settings(Activity a,LinearLayout p){
  android.content.SharedPreferences prefs=DreamWorker.prefs(a);
  p.addView(AiosUi.switchRow(a,"每天两次","午间与夜间各整理一次",prefs.getBoolean("twice",false),value->{prefs.edit().putBoolean("twice",value).apply();DreamWorker.schedule(a);}));
  text(a,p,a.getString(R.string.dream_policy_explanation));
  TextView model=AiosUi.secondary(a,"回顾模型："+prefs.getString("model","首次运行优先选可用 mini，否则目录默认模型；不会自动跨提供商")+"\n模型目录未提供价格，请按自己的套餐选择。");model.setPadding(0,dp(a,6),0,dp(a,6));p.addView(model);
  LinearLayout actions=AiosUi.row(a);
  actions.addView(AiosUi.tonalButton(a,"选择回顾模型",()->{new Thread(()->{
   try(CodexAccountClient client=new CodexAccountClient(a)){
    JSONObject result=client.call(new JSONObject().put("op","accounts/models"));JSONArray items=result.optJSONArray("items");java.util.ArrayList<String> ids=new java.util.ArrayList<>();
    if(items!=null)for(int i=0;i<items.length();i++){JSONObject m=items.getJSONObject(i);JSONArray options=m.optJSONArray("supportedReasoningEfforts");if(options!=null)for(int j=0;j<options.length();j++)if(options.getJSONObject(j).optString("reasoningEffort").equals("low")){ids.add(m.getString("model"));break;}}
    a.runOnUiThread(()->{if(a.isDestroyed())return;if(ids.isEmpty()){Toast.makeText(a,os.aios.core.DisplayText.clean("当前提供商没有可验证的低推理模型目录"),1).show();return;}new AlertDialog.Builder(a).setTitle(os.aios.core.DisplayText.clean("回顾模型 · 固定低推理强度")).setItems(os.aios.core.DisplayText.clean(ids.toArray(new String[0])),(d,w)->{prefs.edit().putString("model",ids.get(w)).apply();model.setText("回顾模型："+ids.get(w)+" · 低推理强度");}).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();});
   }catch(Exception e){a.runOnUiThread(()->error(a,e));}
  },"AIOS-review-models").start();}));
  LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=dp(a,8);
  actions.addView(AiosUi.tonalButton(a,"现在整理",()->{DreamWorker.manual(a);Toast.makeText(a,os.aios.core.DisplayText.clean("已安排手动回顾；不占自动回顾预算，仍以前台请求优先"),1).show();}),gap);
  LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2);al.topMargin=dp(a,8);p.addView(actions,al);
  text(a,p,DreamWorker.statusText(a));
  diagnostics(a,p);
  p.addView(AiosUi.actions(a,AiosUi.textButton(a,"查看回顾记录和建议",()->show(a))));
 }
 /** Tertiary banner on the task list when suggestions await a decision. */
 static void taskCard(Activity a,LinearLayout p){
  try(WorkspaceStore store=new WorkspaceStore(a)){
   JSONArray changes=store.dream("status",new JSONObject()).getJSONArray("proposals");int n=0;for(int i=0;i<changes.length();i++)if(changes.getJSONObject(i).optString("status").equals("pending"))n++;
   if(n>0){LinearLayout banner=AiosUi.banner(a,"moon","夜间回顾有 "+n+" 项建议等你决定","查看",()->show(a));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,12);banner.setLayoutParams(lp);p.addView(banner);}
  }catch(Exception e){android.util.Log.w("AIOSDream","Unable to show suggestions",e);}
 }
 /** Compact entry on the skills page. */
 static void reviewBanner(Activity a,LinearLayout p){
  String status=DreamWorker.statusText(a);
  LinearLayout banner=AiosUi.banner(a,"moon","回顾与学习 · "+status,"记录",()->show(a));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,12);banner.setLayoutParams(lp);p.addView(banner);
 }
 static void show(Activity a){
  try(WorkspaceStore store=new WorkspaceStore(a)){
   JSONObject data=store.dream("status",new JSONObject());LinearLayout p=AiosUi.column(a);ScrollView scroll=AiosUi.dialogContent(a,p);
   text(a,p,"待整理来源："+data.getLong("pending")+"\n"+DreamWorker.statusText(a));
   if(data.getJSONArray("gaps").length()>0)text(a,p,"有过期或超出保留上限的证据未整理；原会话不受影响。");
   JSONArray changes=new JSONArray();java.util.Set<String> ids=new java.util.HashSet<>();for(String key:new String[]{"proposals","changes"}){JSONArray list=data.getJSONArray(key);for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);if(ids.add(item.getString("id")))changes.put(item);}}
   p.addView(AiosUi.filledButton(a,"逐项确认待保存变更",()->confirmPending(a)));
   if(changes.length()>0){TextView label=AiosUi.label(a,"变更");label.setPadding(0,dp(a,16),0,dp(a,8));p.addView(label);}
   for(int i=0;i<changes.length();i++){
    JSONObject change=changes.getJSONObject(i);String status=change.getString("status");int color=status.equals("pending")?AiosTheme.warn(a):status.equals("saved")||status.equals("accepted")?AiosTheme.accent(a):AiosTheme.outlineStrong(a);
    p.addView(AiosUi.statusRow(a,color,change.getString("title"),label(a,status),null,i==0,i==changes.length()-1,()->detail(a,change)));
   }
   JSONArray runs=data.getJSONArray("runs");if(runs.length()>0){TextView label=AiosUi.label(a,"运行记录");label.setPadding(0,dp(a,16),0,dp(a,8));p.addView(label);}
   for(int i=0;i<runs.length();i++){JSONObject run=runs.getJSONObject(i);p.addView(AiosUi.settingRow(a,"history",(run.optString("mode").equals("weekly")?"每周合并":"日常回顾")+" · "+run.optString("status"),runSummary(a,run.optString("summary")),null,i==0,i==runs.length()-1,null));}
   new AlertDialog.Builder(a).setTitle(os.aios.core.DisplayText.clean("回顾记录")).setView(scroll).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).show();
  }catch(Exception e){error(a,e);}
 }
 static String label(Activity a,String s){switch(s){case "publish_pending":return a.getString(R.string.dream_state_publish_pending);case "publish_retry":return a.getString(R.string.dream_state_publish_retry);case "unverified":return a.getString(R.string.dream_state_unverified);case "pending":return "待你决定";case "saved":return "已保存";case "deferred":return "稍后";case "rejected":return "已忽略";case "memory_candidate":return "记忆待确认";case "suppressed":return "已尊重原有决定";case "accepted":return "已接受";case "source_deleted":return "来源已删除";default:return s;}}
 static void detail(Activity a,JSONObject summary){try{
  JSONObject c;try(WorkspaceStore store=new WorkspaceStore(a)){c=store.dream("change",new JSONObject().put("id",summary.getString("id")));}
  JSONObject payload=new JSONObject(c.getString("payload"));LinearLayout p=AiosUi.column(a);ScrollView scroll=AiosUi.dialogContent(a,p);
  TextView body=AiosUi.body(a,preview(c)+"\n"+payload.optString("recurrence")+"\n"+payload.optString("verification"));body.setTextIsSelectable(true);p.addView(body);
  JSONArray refs=new JSONArray(c.getString("source_ids"));for(int i=0;i<refs.length();i++){
   String ref=refs.getString(i);int colon=ref.lastIndexOf(':');String session=colon<0?ref:ref.substring(0,colon);
   p.addView(AiosUi.settingRow(a,"chat","来源会话 "+(i+1),null,null,i==0,i==refs.length()-1,()->a.startActivity(new Intent(a,MainActivity.class).putExtra("workspace_tab","sessions").putExtra("workspace_session",session))));
  }
  if(c.optString("status").equals("memory_candidate"))p.addView(AiosUi.actions(a,AiosUi.textButton(a,"到长期记忆中确认或修改",()->a.startActivity(new Intent(a,MainActivity.class).putExtra("workspace_tab","settings").putExtra("workspace_page","memories")))));
  String status=c.optString("status");if(status.equals("pending")||status.equals("deferred")){
   View primary;
   if(direct(c)){
    primary=AiosUi.filledButton(a,"同意，保存这项变更",()->resolve(a,c,"accept",false));
   }else primary=AiosUi.filledButton(a,"交给助手核对并处理",()->{try(WorkspaceStore store=new WorkspaceStore(a)){
    JSONObject fresh=store.dream("suggestion",new JSONObject().put("id",c.getString("id")));
    AssistantConversation.enqueue(a,"请核对以下空闲回顾建议及来源，并协助我编辑保存。先检查已有记录和冲突，不要重复执行原操作。定时任务请先给出具体计划让我确认：\n"+fresh.toString());
    a.startForegroundService(new Intent(a,AgentService.class));a.startActivity(new Intent(a,AssistantInputActivity.class));
   }catch(Exception e){error(a,e);}});
   p.addView(AiosUi.actions(a,AiosUi.textButton(a,"忽略，不再建议",()->resolve(a,c,"ignore",false)),AiosUi.textButton(a,"稍后",()->resolve(a,c,"later",false)),primary));
  }
  new AlertDialog.Builder(a).setTitle(os.aios.core.DisplayText.clean(c.getString("title"))).setView(scroll).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).show();
 }catch(Exception e){error(a,e);}}
 static void resolve(Activity a,JSONObject c,String action,boolean delegate){try(WorkspaceStore store=new WorkspaceStore(a)){
  JSONObject result=store.dream("resolve",new JSONObject().put("id",c.getString("id")).put("action",action));
  if(delegate&&result.has("id")){AssistantConversation.enqueueTask(a,result.getString("id"));a.startForegroundService(new Intent(a,AgentService.class));a.startActivity(new Intent(a,AssistantInputActivity.class));}
  Toast.makeText(a,os.aios.core.DisplayText.clean("已保存你的选择"),0).show();
 }catch(Exception e){error(a,e);}}
 static String runSummary(Activity a,String summary){
  int colon=summary.indexOf(':');if(colon<0)return summary;
  String code=summary.substring(0,colon);if(!java.util.Set.of("preempted","prepare_failed","result_failed","validation_failed","model_unavailable").contains(code))return summary;
  int id=a.getResources().getIdentifier("dream_status_"+code,"string",a.getPackageName());return id==0?summary:a.getString(id);
 }
 static String time(Activity a,long value){return value<=0?a.getString(R.string.dream_time_none):android.text.format.DateFormat.getDateFormat(a).format(new java.util.Date(value))+" "+android.text.format.DateFormat.getTimeFormat(a).format(new java.util.Date(value));}
 static void diagnostics(Activity a,LinearLayout panel){
  TextView view=AiosUi.secondary(a,"");panel.addView(view);
  new Thread(()->{String text;try{
   JSONObject state=new os.aios.runtime.CoreRuntimeClient(a).call("processing","dream.status",new JSONObject());
   text=a.getString(R.string.dream_schedule_diagnostics,time(a,state.optLong("registered_at")),time(a,state.optLong("dispatched_at")),time(a,DreamWorker.prefs(a).getLong("last_checked",0)));
  }catch(Exception e){text=a.getString(R.string.dream_diagnostics_unavailable);}
  final String message=text;a.runOnUiThread(()->{if(!a.isDestroyed())view.setText(message);});
  },"AIOS-dream-diagnostics").start();
 }
 private DreamPanel(){}
}
