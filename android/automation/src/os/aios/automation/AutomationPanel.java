package os.aios.automation;

import android.app.*;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import org.json.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** Native management UI; no HTML, JS, or implementation logs in the list itself. */
public final class AutomationPanel {
    private final Activity activity;private final LinearLayout parent;
    private final AutomationService service=AutomationService.get();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private String selected;
    private boolean showingRun;
    private boolean closed;private boolean assistantCategory;private AssistantRulePanel assistantDialog;private String assistantRuleId;private android.os.Bundle restoringState;private AlertDialog assistantWindow;
    private int generation;
    public AutomationPanel(Activity a,LinearLayout p){this(a,p,null);}
    public AutomationPanel(Activity a,LinearLayout p,android.os.Bundle state){activity=a;parent=p;assistantCategory=state!=null&&state.getBoolean("assistant_category");show(null);if(state!=null&&state.getBoolean("assistant_dialog")){restoringState=state;parent.post(()->{if(closed)return;assistantDialog(state.getString("assistant_rule_id"),state.getBundle("assistant_editor"));restoringState=null;});};}
    public android.os.Bundle saveState(){android.os.Bundle b=restoringState==null?new android.os.Bundle():new android.os.Bundle(restoringState);b.putBoolean("assistant_category",assistantCategory);if(assistantDialog!=null){b.putBoolean("assistant_dialog",true);b.putString("assistant_rule_id",assistantRuleId);b.putBundle("assistant_editor",assistantDialog.saveEditor());}return b;}
    private String localized(String key){return activity.getString(activity.getResources().getIdentifier("automation_category_"+key,"string",activity.getPackageName()));}
    private void assistantDialog(String id){assistantDialog(id,null);}
    private void assistantDialog(String id,android.os.Bundle draft){assistantRuleId=id;LinearLayout body=AiosUi.column(activity);AssistantRulePanel panel=new AssistantRulePanel(activity,body,id);assistantDialog=panel;AlertDialog dialog=new AlertDialog.Builder(activity).setView(AiosUi.dialogContent(activity,body)).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).create();assistantWindow=dialog;dialog.setOnDismissListener(v->{panel.close();if(assistantDialog==panel)assistantDialog=null;if(!closed&&!activity.isDestroyed())show(null);});dialog.show();if(draft!=null)panel.restoreEditor(draft);}
    private void categories(){LinearLayout row=AiosUi.row(activity);String[] labels={localized("scheduled"),localized("assistant")};for(int i=0;i<labels.length;i++){final boolean chooseAssistant=i==1;TextView tab=AiosUi.label(activity,labels[i]);boolean selected=assistantCategory==chooseAssistant;tab.setTextColor(selected?AiosTheme.accent(activity):AiosTheme.muted(activity));tab.setSelected(selected);tab.setContentDescription(labels[i]);tab.setOnClickListener(v->{if(assistantCategory!=chooseAssistant){assistantCategory=chooseAssistant;show(null);}});tab.setFocusable(true);row.addView(tab,new LinearLayout.LayoutParams(-2,-2));}parent.addView(row);}
    private void assistantList(JSONObject result){try{JSONArray rows=result.getJSONArray("items");java.util.List<JSONObject> live=new java.util.ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject rule=rows.getJSONObject(i);if(!rule.optBoolean("deleted"))live.add(rule);}if(live.isEmpty()){parent.addView(AiosUi.empty(activity,"automation",localized("empty"),localized("hint")));return;}LinearLayout list=group();for(int i=0;i<live.size();i++){JSONObject rule=live.get(i);boolean enabled=rule.optBoolean("enabled");list.addView(AiosUi.statusRow(activity,enabled?AiosTheme.accent(activity):AiosTheme.warn(activity),rule.optString("title"),localized(enabled?"enabled":"paused"),null,i==0,i==live.size()-1,()->assistantDialog(rule.optString("id"))));}}catch(Exception e){parent.addView(AiosUi.errorBanner(activity,e.getMessage()));}}

    public void close(){closed=true;generation++;if(assistantDialog!=null){assistantDialog.close();assistantDialog=null;}if(assistantWindow!=null){assistantWindow.dismiss();assistantWindow=null;}executor.shutdownNow();}
    private int dp(int v){return AiosUi.dp(activity,v);}
    private TextView text(LinearLayout to,String value,int size,int color){TextView t=AiosUi.selectable(activity,value,size,color);t.setPadding(0,dp(3),0,dp(3));to.addView(t);return t;}
    private LinearLayout card(){LinearLayout c=AiosUi.card(activity);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(c,p);return c;}
    private LinearLayout group(){LinearLayout g=AiosUi.group(activity);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(g,p);return g;}
    private void button(LinearLayout to,String name,Runnable action){TextView b=AiosUi.tonalButton(activity,name,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);lp.bottomMargin=dp(6);to.addView(b,lp);}
    private void label(String value){parent.addView(AiosUi.label(activity,value));}
    private void toolbar(String title,String iconKind,String description,Runnable click){
        if(description.startsWith("返回"))parent.addView(AiosUi.toolbar(activity,title,click));
        else parent.addView(AiosUi.toolbar(activity,title,null,AiosUi.iconButton(activity,iconKind,description,click)));
    }
    public boolean goBack(){if(showingRun){show(selected);return true;}if(selected!=null){show(null);return true;}return false;}
    private void options(JSONObject j){new AlertDialog.Builder(activity).setTitle(os.aios.core.DisplayText.clean("任务选项")).setItems(os.aios.core.DisplayText.clean(new String[]{j.optBoolean("enabled")?"暂停":"恢复","立即运行一次","编辑程序与计划","刷新结果","删除"}),(d,w)->{switch(w){case 0:mutate(j.optBoolean("enabled")?"pause":"resume",j);break;case 1:work(()->service.call("run",p("id",j.getString("id"),"expected_revision",j.getLong("revision"),"request_id",java.util.UUID.randomUUID().toString())),result->{Toast.makeText(activity,os.aios.core.DisplayText.clean("已提交运行，可刷新查看结果"),Toast.LENGTH_LONG).show();show(selected);});break;case 2:edit(j);break;case 3:show(selected);break;default:new AlertDialog.Builder(activity).setTitle(os.aios.core.DisplayText.clean("删除定时任务？")).setMessage(os.aios.core.DisplayText.clean("停止后续执行，该任务的旧提交不会将它恢复。")).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("删除"),(x,y)->mutate("delete",j)).show();}}).show();}
    private interface Work {JSONObject run()throws Exception;}
    private void work(Work action,java.util.function.Consumer<JSONObject> success){int gen=generation;executor.execute(()->{try{JSONObject r=action.run();activity.runOnUiThread(()->{if(gen==generation&&!activity.isDestroyed())success.accept(r);});}catch(Exception e){activity.runOnUiThread(()->{if(gen==generation&&!activity.isDestroyed())new AlertDialog.Builder(activity).setTitle(os.aios.core.DisplayText.clean("操作未完成")).setMessage(os.aios.core.DisplayText.clean(e.getMessage())).setPositiveButton(os.aios.core.DisplayText.clean("知道了"),null).show();});}});}
    private JSONObject p(Object... values)throws Exception{JSONObject p=new JSONObject();for(int i=0;i<values.length;i+=2)p.put((String)values[i],values[i+1]);return p;}
    private static String time(long at){return at<=0?"—":Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"));}
    private static String status(String s){switch(s){case "never":return "尚未运行";case "running":return "正在运行";case "succeeded":return "成功";case "failed":return "失败";case "timed_out":return "超时";case "cancelled":return "已取消";case "interrupted":return "执行中断";default:return s;}}
    private int runColor(String s){switch(s){case "running":return AiosTheme.accent(activity);case "failed":case "timed_out":case "interrupted":return AiosTheme.error(activity);case "succeeded":return AiosTheme.tertiary(activity);default:return AiosTheme.outlineStrong(activity);}}
    private static String schedule(JSONObject s)throws Exception{switch(s.getString("kind")){case "once":return "一次性 · "+time(s.getLong("at"));case "interval":return "每 "+s.getLong("minutes")+" 分钟";case "daily":return "每天 "+s.getString("time")+" · "+s.getString("zone");default:return "每周 "+s.getInt("day")+" · "+s.getString("time")+" · "+s.getString("zone");}}
    private static String value(Object v){try{if(v==null||v==JSONObject.NULL)return "未返回结果";if(v instanceof JSONObject)return ((JSONObject)v).toString(2);if(v instanceof JSONArray)return ((JSONArray)v).toString(2);return String.valueOf(v);}catch(Exception e){return String.valueOf(v);}}
    private static String preview(JSONObject row){String v=!row.isNull("error")?"错误："+row.optString("error"):row.optString("status").equals("running")?"正在执行，结果尚未生成":"结果："+value(row.opt("result"));v=v.replaceAll("\\s+"," ");return v.length()>120?v.substring(0,120)+"…":v;}
    private void top(){parent.post(()->{android.view.ViewParent p=parent.getParent();while(p!=null){if(p instanceof ScrollView){((ScrollView)p).scrollTo(0,0);break;}p=p.getParent();}});}
    private void runDetail(JSONObject row,String jobId,String title){generation++;showingRun=true;parent.removeAllViews();top();
        toolbar("执行结果","back","返回任务",()->show(jobId));
        LinearLayout info=card();text(info,title,22,AiosTheme.text(activity));
        LinearLayout stateRow=AiosUi.row(activity);stateRow.addView(AiosUi.dot(activity,runColor(row.optString("status"))));TextView st=AiosUi.body(activity,status(row.optString("status")));st.setPadding(dp(10),dp(6),0,dp(6));stateRow.addView(st);info.addView(stateRow);
        text(info,"触发方式："+(row.optBoolean("manual")?"手动运行":"定时运行")+" · 程序版本 "+row.optLong("revision"),14,AiosTheme.muted(activity));
        text(info,"计划："+time(row.optLong("scheduled_at"))+"\n开始："+time(row.optLong("started_at"))+"\n结束："+time(row.optLong("finished_at")),14,AiosTheme.muted(activity));
        if(row.optLong("finished_at")>0)text(info,"耗时："+Math.max(0,row.optLong("finished_at")-row.optLong("started_at"))+" 毫秒",14,AiosTheme.muted(activity));
        LinearLayout result=card();text(result,"返回结果",16,AiosTheme.text(activity)).setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));TextView body=text(result,row.optString("status").equals("running")?"正在执行，返回任务后刷新可查看最新结果。":value(row.opt("result")),13,AiosTheme.text(activity));body.setTypeface(Typeface.MONOSPACE);body.setPadding(dp(12),dp(10),dp(12),dp(10));body.setBackground(AiosUi.shape(activity,AiosTheme.surfaceHigh(activity),12));
        if(!row.isNull("error")){LinearLayout error=AiosUi.errorBanner(activity,row.optString("error"));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.bottomMargin=dp(12);parent.addView(error,ep);}
        LinearLayout logs=card();text(logs,"执行日志",16,AiosTheme.text(activity)).setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));JSONArray items=row.optJSONArray("logs");
        if(items==null||items.length()==0)text(logs,"没有日志",14,AiosTheme.muted(activity));else for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null){text(logs,time(item.optLong("at")),12,AiosTheme.muted(activity));text(logs,item.optString("text"),14,AiosTheme.text(activity));}}
    }
    public void show(String id){selected=id;showingRun=false;generation++;parent.removeAllViews();top();
        if(id==null){
            toolbar("自动化","refresh","刷新",()->show(null));
            LinearLayout entry=group();entry.addView(AiosUi.settingRow(activity,"bolt","事件与助手自动化","按通知、电源或时间触发小助手",null,true,true,()->assistantDialog(null)));
            categories();if(assistantCategory)work(()->service.call("rule.list",new JSONObject()),this::assistantList);else work(()->service.call("list",new JSONObject()),this::list);
        }else{toolbar("任务详情","back","返回列表",()->show(null));work(()->service.call("get",p("id",id)),this::detail);}
    }
    private void list(JSONObject result){try{
        JSONObject state=service.call("status",new JSONObject());if(!state.getBoolean("supported"))parent.addView(AiosUi.errorBanner(activity,"当前设备的脚本运行环境不可用。"));if(!state.getBoolean("authorized"))parent.addView(AiosUi.errorBanner(activity,"设备授权已关闭，定时程序不会执行。"));
        JSONArray items=result.getJSONArray("items");if(items.length()==0){parent.addView(AiosUi.empty(activity,"clock","还没有定时任务","告诉 AI 何时做什么，创建后会显示在这里。"));return;}
        LinearLayout list=group();
        for(int i=0;i<items.length();i++){JSONObject j=items.getJSONObject(i);boolean enabled=j.getBoolean("enabled");String meta=schedule(j.getJSONObject("schedule"))+" · "+(enabled?status(j.getString("last_status")):"已暂停")+(j.isNull("scheduling_error")?"":"\n调度未就绪："+j.optString("scheduling_error"));int color=!enabled?AiosTheme.warn(activity):!j.isNull("scheduling_error")?AiosTheme.error(activity):runColor(j.getString("last_status"));LinearLayout row=AiosUi.statusRow(activity,color,j.getString("title"),meta,null,i==0,i==items.length()-1,()->show(j.optString("id")));row.setContentDescription(os.aios.core.DisplayText.clean("查看 · "+j.getString("title")));list.addView(row);}
    }catch(Exception e){parent.addView(AiosUi.errorBanner(activity,e.toString()));}}
    private void mutate(String action,JSONObject j){work(()->service.call(action,p("id",j.getString("id"),"expected_revision",j.getLong("revision"))),r->show(action.equals("delete")?null:selected));}
    private void detail(JSONObject j){try{
        LinearLayout heading=AiosUi.row(activity);TextView title=AiosUi.heading(activity,j.getString("title"));title.setMaxLines(2);title.setPadding(dp(4),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));heading.addView(AiosUi.iconButton(activity,"more","任务选项",()->options(j)));parent.addView(heading);
        if(!j.optString("description").isEmpty()){TextView d=text(parent,j.getString("description"),14,AiosTheme.muted(activity));d.setPadding(dp(4),dp(4),dp(4),dp(8));}
        boolean enabled=j.getBoolean("enabled");
        LinearLayout plan=group();
        plan.addView(AiosUi.settingRow(activity,"clock","计划",schedule(j.getJSONObject("schedule")),null,true,false,null));
        plan.addView(AiosUi.settingRow(activity,enabled?"play":"pause",enabled?"已启用":"已暂停","下次："+time(j.getLong("next_at")),AiosUi.m3switch(activity,enabled,on->mutate(on?"resume":"pause",j)),false,j.isNull("scheduling_error"),null));
        if(!j.isNull("scheduling_error"))plan.addView(AiosUi.settingRow(activity,"warning","调度未就绪",j.optString("scheduling_error"),null,false,true,null));
        LinearLayout tools=group();
        tools.addView(AiosUi.settingRow(activity,"book","查看程序与能力","JavaScript 源码、允许的能力与超时",null,true,false,()->{try{TextView code=AiosUi.selectable(activity,"允许："+j.getJSONArray("capabilities")+"\n超时："+j.getInt("timeout_ms")/1000+" 秒\n\n"+j.getString("source"),13,AiosTheme.text(activity));code.setTypeface(Typeface.MONOSPACE);new AlertDialog.Builder(activity).setTitle(os.aios.core.DisplayText.clean("程序与能力")).setView(AiosUi.dialogContent(activity,code)).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).show();}catch(Exception e){Toast.makeText(activity,os.aios.core.DisplayText.clean(e.toString()),Toast.LENGTH_SHORT).show();}}));
        tools.addView(AiosUi.settingRow(activity,"play","立即运行一次","手动触发并记录结果",null,false,true,()->work(()->service.call("run",p("id",j.getString("id"),"expected_revision",j.getLong("revision"),"request_id",java.util.UUID.randomUUID().toString())),result->{Toast.makeText(activity,os.aios.core.DisplayText.clean("已提交运行，可刷新查看结果"),Toast.LENGTH_LONG).show();show(selected);})));
        label("运行记录");
        work(()->service.call("runs",p("id",j.getString("id"),"limit",100)),result->{try{JSONArray rows=result.getJSONArray("items");if(rows.length()==0){parent.addView(AiosUi.empty(activity,"history","尚无运行记录",null));return;}LinearLayout runs=group();for(int i=0;i<rows.length();i++){JSONObject run=rows.getJSONObject(i);LinearLayout line=AiosUi.statusRow(activity,runColor(run.getString("status")),status(run.getString("status")),time(run.getLong("started_at")),null,i==0,i==rows.length()-1,()->runDetail(run,j.optString("id"),j.optString("title")));line.setContentDescription(os.aios.core.DisplayText.clean("查看执行结果 · "+status(run.getString("status"))+" · "+time(run.getLong("started_at"))));runs.addView(line);}}catch(Exception e){parent.addView(AiosUi.errorBanner(activity,e.toString()));}});
    }catch(Exception e){parent.addView(AiosUi.errorBanner(activity,e.toString()));}}
    private void edit(JSONObject j){try{
        LinearLayout form=AiosUi.column(activity);
        form.addView(AiosUi.fieldLabel(activity,"标题"));EditText title=AiosUi.field(activity,"标题",j.getString("title"));form.addView(title);
        form.addView(AiosUi.fieldLabel(activity,"执行频率"));String[] kinds={"once","interval","daily","weekly"};
        JSONObject old=j.getJSONObject("schedule");int initial=0;for(int i=0;i<kinds.length;i++)if(kinds[i].equals(old.getString("kind")))initial=i;
        TextView hint=AiosUi.fieldLabel(activity,"");EditText timing=AiosUi.field(activity,"",null);
        TextView zoneLabel=AiosUi.fieldLabel(activity,"时区（例如 Asia/Hong_Kong）");EditText zone=AiosUi.field(activity,"时区",old.optString("zone",ZoneId.systemDefault().getId()));
        TextView dayLabel=AiosUi.fieldLabel(activity,"星期（周一 = 1，周日 = 7）");EditText day=AiosUi.field(activity,"星期",Integer.toString(old.optInt("day",1)));day.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);os.aios.core.DisplayText.editable(day);
        java.util.function.IntConsumer apply=pos->{hint.setText(pos==0?"执行日期与时间（yyyy-MM-dd HH:mm）":pos==1?"间隔分钟数（至少 15）":"执行时间（HH:mm）");boolean same=kinds[pos].equals(old.optString("kind"));timing.setText(pos==0?Instant.ofEpochMilli(same?old.optLong("at"):System.currentTimeMillis()+3600000).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")):pos==1?Long.toString(same?old.optLong("minutes"):30):same?old.optString("time"):"09:00");int weekly=pos==3?View.VISIBLE:View.GONE,zoned=pos==1?View.GONE:View.VISIBLE;day.setVisibility(weekly);dayLabel.setVisibility(weekly);zone.setVisibility(zoned);zoneLabel.setVisibility(zoned);};
        AiosUi.Choice kind=new AiosUi.Choice(activity,new String[]{"仅一次","固定间隔","每天","每周"},initial,apply);form.addView(kind.view);
        form.addView(hint);form.addView(timing);form.addView(zoneLabel);form.addView(zone);form.addView(dayLabel);form.addView(day);apply.accept(initial);
        form.addView(AiosUi.fieldLabel(activity,"JavaScript"));EditText source=AiosUi.area(activity,"JavaScript",j.getString("source"),8);source.setTypeface(Typeface.MONOSPACE);source.setTextSize(13);form.addView(source);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(os.aios.core.DisplayText.clean("编辑定时任务")).setView(AiosUi.dialogContent(activity,form)).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("保存"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String t=title.getText().toString(),code=source.getText().toString(),selectedKind=kinds[kind.getSelectedItemPosition()],when=timing.getText().toString(),tz=zone.getText().toString(),weekday=day.getText().toString();work(()->{JSONObject s=p("kind",selectedKind);if(selectedKind.equals("once"))s.put("at",LocalDateTime.parse(when,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.of(tz)).toInstant().toEpochMilli());else if(selectedKind.equals("interval"))s.put("minutes",Long.parseLong(when));else{s.put("time",when).put("zone",tz);if(selectedKind.equals("weekly"))s.put("day",Integer.parseInt(weekday));}service.call("validate",p("source",code));JSONObject edit=new JSONObject(j.toString()).put("expected_revision",j.getLong("revision")).put("title",t).put("source",code).put("schedule",s);return service.call("update",edit);},r->{dialog.dismiss();show(selected);});}));dialog.show();
    }catch(Exception e){Toast.makeText(activity,os.aios.core.DisplayText.clean(e.toString()),Toast.LENGTH_LONG).show();}}
}
