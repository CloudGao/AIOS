package os.aios.workspace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import os.aios.core.AiosIcon;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Workspace application shell. Host app can add its controls without coupling storage to it. */
public class WorkspaceActivity extends Activity {
    private int INK, MUTED, BLUE;
    private static final String[] STATUSES={"note","todo","doing","done"};
    private String[] statusLabels(){return new String[]{getString(R.string.workspace_note),getString(R.string.workspace_todo),getString(R.string.workspace_doing),getString(R.string.workspace_done)};}
    private WorkspaceStore store;
    private SearchPanel searchPanel;
    private final ExecutorService mediaWorker=Executors.newSingleThreadExecutor();
    private final Set<String> collapsed=new HashSet<>();
    private LinearLayout shell,content,navigation;
    private String taskFilter="all";
    private String memoryFilter="active",memoryQuery="";
    private ScrollView scroll;
    private final os.aios.core.ChatScrollFollow chatFollow=new os.aios.core.ChatScrollFollow();
    private String renderedSession;
    private final Runnable deferredConversationRender=()->{if(!isDestroyed()&&!isFinishing())safe(this::render);};
    protected boolean conversationTouching(){return chatFollow.touching()||os.aios.core.TextSelection.active(getWindow().getDecorView());}
    protected void followConversation(){if(scroll!=null)chatFollow.changed(scroll);}
    protected void refreshConversation(){nativeHistoryRevision=-1;nativeHistoryFailed=false;safe(this::render);}
    protected String liveConversationTurn(String thread,JSONObject history){return "";}
    protected void renderPendingConversation(LinearLayout parent,JSONObject session)throws Exception{}
    protected String sessionSubtitle(JSONObject session){return session.optString("source").equals("codex")?getString(os.aios.runtime.R.string.history_native_caption):getString(R.string.live_session_local);}
    protected void renderConversationComposer(LinearLayout parent,LinearLayout messages,JSONObject session,JSONObject nativePage)throws Exception{}

    private String tab="tasks",taskId,sessionId,page="",pickerTask,pickerRelink;
    private boolean editing=false,loadingMedia=false,loadingHistory=false;
    private JSONObject currentTask;
    private EditText titleEditor,bodyEditor,composer;
    private AiosUi.Choice statusEditor;
    private String messageDraft="";
    private String composerSession;
    private final android.os.Handler refreshHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final android.content.BroadcastReceiver changes=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent i){refreshHandler.removeCallbacks(refreshContent);refreshHandler.postDelayed(refreshContent,200);}};
    private final Runnable refreshContent=()->{
        if(editing||loadingMedia||loadingHistory||isDestroyed()||"automation".equals(tab)||"search".equals(tab))return;
        int y=scroll==null?0:scroll.getScrollY();
        safe(()->{if(taskId!=null){try{call("task.get","id",taskId);}catch(IllegalArgumentException e){taskId=null;}}
            if(sessionId!=null){try{call("session.get","id",sessionId);}catch(IllegalArgumentException e){sessionId=null;}}
            render();if(sessionId==null)scroll.post(()->scroll.scrollTo(0,y));});
    };
    private long messageBefore=Long.MAX_VALUE;
    private JSONObject nativeHistory;
    private String nativeHistoryCursor;
    private boolean nativeHistoryFailed;
    private long nativeHistoryRevision=-1;
    private int renderGeneration,historyLoadGeneration;
    private boolean pickerCopy;
    @FunctionalInterface interface Work {void run() throws Exception;}
    private void safe(Work work){try{work.run();}catch(Exception e){toast(e.getMessage()==null?e.toString():e.getMessage());}}
    private void toast(String text){Toast.makeText(this,os.aios.core.DisplayText.clean(text),Toast.LENGTH_LONG).show();}
    private JSONObject args(Object... values) throws Exception {JSONObject p=new JSONObject();for(int i=0;i<values.length;i+=2)p.put((String)values[i],values[i+1]==null?JSONObject.NULL:values[i+1]);return p;}
    private JSONObject call(String op,Object... values) throws Exception{return store.call(op,args(values)).getJSONObject("data");}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color){TextView t=AiosUi.text(this,value,size,color);t.setPadding(0,dp(4),0,dp(4));return t;}
    /** Tonal (secondary_container) button; the single most important action on a page uses primary(). */
    private TextView button(String label,Work action){TextView b=AiosUi.tonalButton(this,label,()->safe(action));b.setContentDescription(os.aios.core.DisplayText.clean(label));return b;}
    private TextView primary(String label,Work action){TextView b=AiosUi.filledButton(this,label,()->safe(action));b.setContentDescription(os.aios.core.DisplayText.clean(label));return b;}
    private TextView textButton(String label,Work action){TextView b=AiosUi.textButton(this,label,()->safe(action));b.setContentDescription(os.aios.core.DisplayText.clean(label));return b;}
    private void space(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    /** One label → full-width filled button; several → equal-width tonal buttons with 8dp gaps. */
    private void actions(LinearLayout parent,String[] labels,Work... work){LinearLayout r=row();for(int i=0;i<labels.length;i++){TextView b=labels.length==1?primary(labels[i],work[i]):button(labels[i],work[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)lp.leftMargin=dp(8);r.addView(b,lp);}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(4);rp.bottomMargin=dp(12);parent.addView(r,rp);}
    private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(20),dp(16),dp(20),dp(16));c.setBackground(background(AiosTheme.surface(this),28));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(c,lp);return c;}
    private EditText editor(String hint,String value,boolean multiline){EditText e=multiline?AiosUi.area(this,hint,value,6):AiosUi.field(this,hint,value);if(!multiline)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);os.aios.core.DisplayText.editable(e);e.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));return e;}
    /** Grouped row builder used for all lists; status colour follows AiosUi.statusColor. */
    private int taskColor(String status){return status.equals("doing")?BLUE:status.equals("done")?AiosTheme.outlineStrong(this):AiosTheme.warn(this);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new WorkspaceStore(this);
        if(state!=null){taskFilter=state.getString("taskFilter","all");tab=state.getString("tab","tasks");taskId=state.getString("task");sessionId=state.getString("session");page=state.getString("page","");editing=state.getBoolean("editing");messageDraft=state.getString("messageDraft","");pickerTask=state.getString("pickerTask");pickerRelink=state.getString("pickerRelink");pickerCopy=state.getBoolean("pickerCopy");messageBefore=state.getLong("messageBefore",Long.MAX_VALUE);nativeHistoryCursor=state.getString("nativeHistoryCursor");ArrayList<String> ids=state.getStringArrayList("collapsed");if(ids!=null)collapsed.addAll(ids);}
        if(state==null)applyWorkspaceIntent(getIntent());
        safe(this::render);
    }
    private void applyWorkspaceIntent(Intent intent){
        String target=intent.getStringExtra("workspace_tab");
        if(target==null||!Set.of("tasks","sessions","automation","skills","settings","search").contains(target))return;
        stashMessage();saveDraft();tab=target;taskId=null;sessionId=null;page="";editing=false;composer=null;messageDraft="";messageBefore=Long.MAX_VALUE;
        ++historyLoadGeneration;loadingHistory=false;nativeHistory=null;nativeHistoryCursor=null;nativeHistoryFailed=false;nativeHistoryRevision=-1;
        if("settings".equals(tab)&&"memories".equals(intent.getStringExtra("workspace_page")))page="memories";
        String requestedTask=intent.getStringExtra("workspace_task");
        if("tasks".equals(tab)&&requestedTask!=null){try{call("task.get","id",requestedTask);taskId=requestedTask;}catch(Exception missing){toast("任务不存在或已删除");}}
        String requested=intent.getStringExtra("workspace_session");
        if("sessions".equals(tab)&&requested!=null){try{call("session.get","id",requested);sessionId=requested;}catch(Exception missing){toast("此会话已不存在");}}
        intent.removeExtra("workspace_tab");intent.removeExtra("workspace_session");
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);applyWorkspaceIntent(intent);safe(this::render);}
    @Override protected void onSaveInstanceState(Bundle out){
        saveDraft();out.putString("taskFilter",taskFilter);out.putString("tab",tab);out.putString("task",taskId);out.putString("session",sessionId);out.putString("page",page);out.putBoolean("editing",editing);out.putString("messageDraft",composer==null?messageDraft:composer.getText().toString());out.putString("pickerTask",pickerTask);out.putString("pickerRelink",pickerRelink);out.putBoolean("pickerCopy",pickerCopy);out.putLong("messageBefore",messageBefore);out.putString("nativeHistoryCursor",nativeHistoryCursor);out.putStringArrayList("collapsed",new ArrayList<>(collapsed));super.onSaveInstanceState(out);
    }
    @Override protected void onPause(){saveDraft();stashMessage();unregisterReceiver(changes);refreshHandler.removeCallbacks(refreshContent);refreshHandler.removeCallbacks(deferredConversationRender);super.onPause();}
    @Override protected void onResume(){super.onResume();registerReceiver(changes,new android.content.IntentFilter(WorkspaceStore.CHANGED),"os.aios.permission.CORE_RUNTIME",null,android.content.Context.RECEIVER_EXPORTED);if(sessionId!=null){nativeHistoryRevision=-1;nativeHistoryFailed=false;}if(store!=null&&!editing&&!loadingMedia)safe(this::render);}
    @Override protected void onDestroy(){if(searchPanel!=null)searchPanel.close();closeAutomation();mediaWorker.shutdown();store.close();super.onDestroy();}

    protected void addHostConfiguration(LinearLayout parent) {}
    /** Standalone navigation row (all corners 28dp). Hosts building groups use the positional overload. */
    protected void settingRow(LinearLayout parent,String title,String summary,Runnable action){settingRow(parent,null,title,summary,null,true,true,action);}
    /** Settings-style row: leading icon, title + one-line summary, trailing widget or chevron; 28/4dp grouped corners. */
    protected LinearLayout settingRow(LinearLayout parent,String icon,String title,String summary,View trailing,boolean first,boolean last,Runnable action){
        LinearLayout line=AiosUi.settingRow(this,icon,title,summary,trailing,first,last,action);line.setContentDescription(os.aios.core.DisplayText.clean(title));parent.addView(line);return line;
    }
    /** Section header above a group of rows. */
    protected void section(LinearLayout parent,String label){TextView t=AiosUi.label(this,label);t.setPadding(dp(4),dp(12),0,dp(8));parent.addView(t);}
    protected void renderAutomation(LinearLayout parent) {}
    protected void closeAutomation() {}
    protected boolean handleAutomationBack(){return false;}
    protected void onMessageSaved(JSONObject message) throws Exception {}
    protected void startAssistantTask(JSONObject task) throws Exception {throw new IllegalStateException("手机助手不可用");}
    protected void newAssistantSession() throws Exception {throw new IllegalStateException("手机助手不可用");}
    protected void continueCodexSession(JSONObject session) throws Exception {throw new IllegalStateException("手机助手不可用");}
    private void stashMessage(){if(composer!=null&&composerSession!=null)getPreferences(0).edit().putString("message:"+composerSession,composer.getText().toString()).apply();}

    private void saveDraft(){
        if(editing&&titleEditor!=null&&bodyEditor!=null&&taskId!=null&&currentTask!=null)safe(()->getPreferences(0).edit().putString("task:"+taskId,args("title",titleEditor.getText().toString(),"content_md",bodyEditor.getText().toString(),"status",STATUSES[statusEditor.getSelectedItemPosition()],"expected_revision",currentTask.getLong("revision")).toString()).commit());
    }
    private void leave(Work next){
        if(!editing){safe(next);return;}
        new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("保存修改？")).setMessage(os.aios.core.DisplayText.clean("保存后离开，或保留草稿稍后继续。")).setPositiveButton(os.aios.core.DisplayText.clean("保存"),(d,w)->safe(()->{saveTask();next.run();})).setNeutralButton(os.aios.core.DisplayText.clean("保留草稿"),(d,w)->{saveDraft();editing=false;safe(next);}).setNegativeButton(os.aios.core.DisplayText.clean("继续编辑"),null).show();
    }
    protected void onWorkspaceRender(){}
    private void render() throws Exception {
        if(sessionId!=null&&java.util.Objects.equals(renderedSession,sessionId)&&conversationTouching()){refreshHandler.removeCallbacks(deferredConversationRender);refreshHandler.postDelayed(deferredConversationRender,180);return;}
        int previousY=scroll==null?0:scroll.getScrollY();
        INK=AiosTheme.text(this);MUTED=AiosTheme.muted(this);BLUE=AiosTheme.accent(this);
        if(searchPanel!=null){searchPanel.close();searchPanel=null;}
        onWorkspaceRender();
        closeAutomation();renderGeneration++;if(composer!=null){stashMessage();messageDraft=java.util.Objects.equals(composerSession,sessionId)?composer.getText().toString():sessionId==null?"":getPreferences(0).getString("message:"+sessionId,"");composer=null;}titleEditor=null;bodyEditor=null;
        shell=column();shell.setBackgroundColor(AiosTheme.background(this));
        shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        if(tab.equals("search"))shell.addView(AiosUi.toolbar(this,getString(R.string.workspace_search),()->safe(()->{tab="tasks";page="";render();})));
        scroll=os.aios.core.ChatScrollFollow.create(this);scroll.setFillViewport(true);scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);scroll.setVerticalScrollBarEnabled(false);content=column();content.setPadding(dp(16),0,dp(16),dp(24));scroll.addView(content);if(sessionId!=null)chatFollow.attach(scroll);FrameLayout body=new FrameLayout(this);body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        if(tab.equals("tasks")&&taskId==null){LinearLayout add=AiosUi.fab(this,"add",getString(R.string.workspace_new_task),()->safe(()->newTask(null)));FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,dp(56),Gravity.BOTTOM|Gravity.END);position.setMargins(dp(16),dp(16),dp(16),dp(16));body.addView(add,position);content.setPadding(dp(16),0,dp(16),dp(96));}
        String[] tabs={"tasks","sessions","automation","skills","settings"},labels={getString(R.string.workspace_tasks),getString(R.string.workspace_sessions),getString(R.string.workspace_automation),getString(R.string.workspace_skills),getString(R.string.workspace_settings)},icons={"tasks","chat","automation","skills","settings"};
        int selected=java.util.Arrays.asList(tabs).indexOf(tab);
        navigation=AiosUi.navigation(this,icons,labels,selected,at->leave(()->{tab=tabs[at];taskId=null;sessionId=null;messageDraft="";messageBefore=Long.MAX_VALUE;page="";render();}));
        if(taskId==null&&sessionId==null&&page.isEmpty())shell.addView(navigation);
        setContentView(shell);
        AiosTheme.window(this);
        if(tab.equals("tasks")){if(taskId==null)taskList();else taskDetail();}
        else if(tab.equals("sessions")){if(sessionId==null)sessionList();else sessionDetail();}
        else if(tab.equals("automation"))renderAutomation(content);
        else if(tab.equals("skills"))skillList();
        else if(tab.equals("search"))searchPanel=new SearchPanel(this,content,store,(source,id,sid)->{if(source.equals("tasks")){taskId=id;tab="tasks";}else if(source.equals("sessions")){sessionId=sid;tab="sessions";}else{tab="settings";page="memories";memoryQuery="";}render();});
        else if(page.equals("memories"))memories();else settings();
        if(sessionId!=null){if(!java.util.Objects.equals(renderedSession,sessionId))chatFollow.reset();
            if(chatFollow.following())chatFollow.changed(scroll);else {ScrollView target=scroll;target.post(()->target.scrollTo(0,previousY));}}
        renderedSession=sessionId;
    }
    private String skillQuery="";
    private int skillOffset=0;
    protected void renderSkillReview(LinearLayout parent){}
    private String skillKind="",skillApplication="",skillDomain="",skillStateFilter="";
    private void skillList()throws Exception{
        listToolbar(getString(R.string.workspace_skills),()->skillEditor(null));
        renderSkillReview(content);
        EditText query=editor("搜索名称、说明或应用",skillQuery,false);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);query.setSingleLine(true);os.aios.core.DisplayText.editable(query);
        AiosIcon glass=new AiosIcon(this,"search",MUTED);query.setCompoundDrawablesWithIntrinsicBounds(glass,null,null,null);query.setCompoundDrawablePadding(dp(12));
        query.setOnEditorActionListener((v,a,e)->{safe(()->{skillQuery=query.getText().toString();skillOffset=0;render();});return true;});content.addView(query);space(content,12);
        String[] kinds={"","app","domain","workflow"},kindNames={getString(R.string.workspace_all),"应用知识","领域方法","组合流程"};LinearLayout chips=row();
        for(int i=0;i<kinds.length;i++){final String kind=kinds[i];LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(dp(8));chips.addView(AiosUi.chip(this,kindNames[i],kind.equals(skillKind),()->safe(()->{skillKind=kind;skillOffset=0;render();})),cp);}
        boolean filtered=!skillApplication.isEmpty()||!skillDomain.isEmpty()||!skillStateFilter.isEmpty();
        chips.addView(AiosUi.assistChip(this,"tune",filtered?"筛选中":"筛选",()->{LinearLayout options=column();EditText app=editor("应用名称",skillApplication,false),domain=editor("领域",skillDomain,false);options.addView(app);space(options,12);options.addView(domain);space(options,12);options.addView(AiosUi.fieldLabel(this,"状态"));String[] values={"","available","needs_revalidation","auto_paused","user_disabled","unverified"};int at=Math.max(0,java.util.Arrays.asList(values).indexOf(skillStateFilter));AiosUi.Choice states=new AiosUi.Choice(this,new String[]{"全部状态","可用","待重新验证","自动暂停","用户停用","未验证"},at,null);options.addView(states.view);new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("筛选技能")).setView(AiosUi.dialogContent(this,options)).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setNeutralButton(os.aios.core.DisplayText.clean("清除"),(dialog,which)->safe(()->{skillApplication="";skillDomain="";skillStateFilter="";skillOffset=0;render();})).setPositiveButton(os.aios.core.DisplayText.clean("应用"),(dialog,which)->safe(()->{skillApplication=app.getText().toString().trim();skillDomain=domain.getText().toString().trim();skillStateFilter=values[states.getSelectedItemPosition()];skillOffset=0;render();})).show();}));
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);strip.addView(chips);content.addView(strip);space(content,12);
        JSONObject result=call("skill.list","query",skillQuery,"knowledge_kind",skillKind,"application",skillApplication,"domain",skillDomain,"state",skillStateFilter,"offset",skillOffset,"limit",20);
        JSONArray items=result.getJSONArray("items");
        if(items.length()==0)empty("还没有匹配的技能","成功操作后，小助手可以总结方法；你也可以手动添加。");
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);String skillId=item.optString("id");String state=item.optString("effective_state");
            int color=!item.optBoolean("enabled",true)?AiosTheme.outlineStrong(this):state.equals("available")?BLUE:state.equals("auto_paused")||state.equals("needs_revalidation")?AiosTheme.warn(this):AiosTheme.outlineStrong(this);
            String meta=(item.optString("app_label").isEmpty()?"":item.optString("app_label")+" · ")+(item.optBoolean("enabled",true)?skillState(state):"已停用")+" · "+(item.optString("source").equals("learned")?"自动总结":"手动添加");
            content.addView(AiosUi.statusRow(this,color,item.optString("title","未命名技能"),meta,AiosUi.icon(this,"chevron",MUTED),i==0,i==items.length()-1,()->safe(()->skillDetail(skillId))));
        }
        if(skillOffset>0||!result.isNull("next_offset")){space(content,12);LinearLayout pager=row();pager.setGravity(Gravity.CENTER);if(skillOffset>0)pager.addView(textButton("上一页",()->{skillOffset=Math.max(0,skillOffset-20);render();}));if(!result.isNull("next_offset"))pager.addView(textButton("下一页",()->{skillOffset=result.getInt("next_offset");render();}));content.addView(pager);}
    }
    private String skillState(String state){switch(state){case "available":return "可用";case "auto_paused":return "方法异常，已自动暂停";case "needs_revalidation":return "待重新验证";case "archived":return "已合并归档";case "user_disabled":return "用户已停用";default:return "尚未验证";}}
    private void skillDetail(String id)throws Exception{
        JSONObject item=call("skill.get","id",id);
        LinearLayout panel=column();
        String state=item.optBoolean("enabled",true)?item.optBoolean("requires_revalidation")?"应用已更新，需重新核对":skillState(item.optString("effective_state",item.optString("lifecycle"))):"用户已停用";
        int stateColor=!item.optBoolean("enabled",true)?AiosTheme.outlineStrong(this):state.equals("可用")?BLUE:AiosTheme.warn(this);
        LinearLayout stateRow=row();stateRow.addView(AiosUi.tag(this,state,stateColor));panel.addView(stateRow);space(panel,12);
        if(!item.optString("description").isEmpty()){panel.addView(text(item.optString("description"),14,MUTED));space(panel,8);}
        TextView body=text(item.optString("content_md"),16,INK);body.setTextIsSelectable(true);panel.addView(body);space(panel,12);divider(panel);space(panel,8);
        int independent=0,demonstration=0,failures=0;JSONArray samples=item.optJSONArray("samples");if(samples!=null)for(int n=0;n<samples.length();n++){JSONObject sample=samples.getJSONObject(n);String kind=sample.optString("kind");if(kind.equals("independent"))independent+=sample.optInt("count");else if(kind.equals("failure"))failures+=sample.optInt("count");else demonstration+=sample.optInt("count");}
        panel.addView(text("独立验证 "+independent+" 次 · 示范或纠正验证 "+demonstration+" 次 · 未完成 "+failures+" 次"+(item.optBoolean("user_edited")?"\n手动内容受保护":""),13,MUTED));
        JSONArray health=item.optJSONArray("health");if(health!=null)for(int n=0;n<health.length();n++){JSONObject h=health.getJSONObject(n);panel.addView(text((h.optString("capability_id").isEmpty()?"此方法":h.optString("capability_id"))+"："+skillState(h.optString("state")),13,MUTED));}
        if(item.optString("source").equals("learned")&&!item.optBoolean("evidence_traceable"))panel.addView(text("历史验证来源已移除或到期，当前无法追溯原证据。",13,MUTED));
        if(item.has("parameters"))panel.addView(text("参数："+item.optString("parameters"),13,MUTED));if(item.has("success_conditions"))panel.addView(text("成功判断："+item.optString("success_conditions"),13,MUTED));
        String evidence=item.optString("evidence","{}");
        if(!evidence.isBlank()&&!evidence.equals("{}")){try{JSONObject proof=new JSONObject(evidence);panel.addView(text("结果核对："+proof.optString("verification")+"\n适用应用："+proof.optString("package")+" · "+proof.optString("app_version")+"\n应用更新后需重新核对页面。",13,MUTED));}catch(JSONException malformed){panel.addView(text("该技能来自旧版本，验证信息暂不可读；操作方法仍可正常查看和编辑。",13,MUTED));}}
        ScrollView view=AiosUi.dialogContent(this,panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(item.optString("title","未命名技能"))).setView(view).setPositiveButton(os.aios.core.DisplayText.clean("编辑"),(d,w)->safe(()->skillEditor(item))).setNegativeButton(os.aios.core.DisplayText.clean("关闭"),null).setNeutralButton(os.aios.core.DisplayText.clean("管理"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setItems(os.aios.core.DisplayText.clean(new String[]{item.optBoolean("enabled",true)?"停用":"启用","删除","版本与回滚"}),(dd,index)->safe(()->{
            if(index==0){call("skill.update","id",id,"expected_revision",item.getLong("revision"),"enabled",!item.optBoolean("enabled",true));dialog.dismiss();render();}
            else if(index==2){JSONArray versions=call("skill.versions","id",id).getJSONArray("items");String[] labels=new String[versions.length()];for(int i=0;i<labels.length;i++)labels[i]="版本 "+versions.getJSONObject(i).getLong("revision");new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("回滚方法（保留当前启停决定）")).setItems(os.aios.core.DisplayText.clean(labels),(d2,n)->safe(()->{call("skill.rollback","id",id,"expected_revision",item.getLong("revision"),"target_revision",versions.getJSONObject(n).getLong("revision"));dialog.dismiss();render();})).show();}
            else new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("删除技能？")).setMessage(os.aios.core.DisplayText.clean("删除后不会因重复总结自动恢复。")).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("删除"),(ddd,which)->safe(()->{call("skill.delete","id",id,"expected_revision",item.getLong("revision"));dialog.dismiss();render();})).show();
        })).show()));dialog.show();
    }
    private void skillEditor(JSONObject old)throws Exception{
        LinearLayout panel=column();
        EditText title=editor("技能名称",old==null?"":old.getString("title"),false),description=editor("什么时候使用",old==null?"":old.getString("description"),false),body=editor("操作方法、可替换参数和成功判断",old==null?"":old.getString("content_md"),true);
        panel.addView(title);space(panel,12);panel.addView(description);space(panel,12);panel.addView(body);ScrollView view=AiosUi.dialogContent(this,panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(old==null?"添加技能":"编辑技能")).setView(view).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("保存"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->safe(()->{
            JSONObject p=args("title",title.getText().toString(),"description",description.getText().toString(),"content_md",body.getText().toString());
            if(old!=null)p.put("id",old.getString("id")).put("expected_revision",old.getLong("revision"));
            store.call(old==null?"skill.create":"skill.update",p);dialog.dismiss();render();
        })));dialog.show();
    }
    private void heading(String title,String caption){TextView h=AiosUi.heading(this,title);h.setPadding(0,dp(8),0,dp(4));content.addView(h);if(!caption.isEmpty())content.addView(text(caption,14,MUTED));space(content,12);}
    private String preview(String value){return value.replaceAll("(?m)^#{1,6}\\s+", "").replace("**", "").replace("`", "").replaceAll("\\s+", " ").trim();}
    private void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(AiosTheme.outline(this));parent.addView(line,new LinearLayout.LayoutParams(-1,Math.max(1,dp(1))));}
    private LinearLayout listRow(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(4),dp(4),dp(4),dp(4));parent.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;}
    private void limited(TextView view,int lines){view.setMaxLines(lines);view.setEllipsize(android.text.TextUtils.TruncateAt.END);}
    private void detailOptions(){if(editing){toast("请先保存或取消编辑");return;}new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("任务选项")).setItems(os.aios.core.DisplayText.clean(new String[]{"＋ 子任务","关联会话","＋ 图片 / 视频","＋ 链接","移动、排序与删除"}),(d,w)->safe(()->{switch(w){case 0:newTask(taskId);break;case 1:chooseRelations(true);break;case 2:pickMedia(null);break;case 3:insertLink();break;default:taskMenu(currentTask);}})).show();}
    private void empty(String title,String caption){content.addView(AiosUi.empty(this,tab.equals("sessions")?"chat":tab.equals("skills")?"skills":page.equals("memories")?"memory":"tasks",title,caption));}
    private String taskStateLabel(String status){String user=getString("doing".equals(status)?R.string.workspace_running:"done".equals(status)?R.string.workspace_stopped:R.string.workspace_paused);String reason="doing".equals(status)?"":statusLabel(status);return reason.isEmpty()?user:getString(R.string.workspace_state_reason,user,reason);}
    private String statusLabel(String status){for(int i=0;i<STATUSES.length;i++)if(STATUSES[i].equals(status))return statusLabels()[i];return status;}
    protected void renderReviewSuggestions(LinearLayout parent){}
    protected void renderInterventions(LinearLayout parent,String thread,String turn){}
    protected boolean pendingInterventions(String thread,String turn){return false;}
    protected boolean runningExecution(String thread,String turn){return false;}
    protected void onSessionDeleted(String thread){}
    private void taskList() throws Exception {
        listToolbar(getString(R.string.workspace_tasks),null);
        renderReviewSuggestions(content);
        JSONArray all=call("task.list").getJSONArray("items");
        LinearLayout filters=row();String[] keys={"all","doing","todo","done"},names={getString(R.string.workspace_all),getString(R.string.workspace_doing),getString(R.string.workspace_todo),getString(R.string.workspace_done)};
        for(int i=0;i<keys.length;i++){String key=keys[i];LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(dp(8));filters.addView(AiosUi.chip(this,names[i],key.equals(taskFilter),()->safe(()->{taskFilter=key;render();})),cp);}
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);strip.addView(filters);content.addView(strip);space(content,12);
        if(all.length()==0){empty(getString(R.string.workspace_empty_goal),getString(R.string.workspace_empty_goal_body));return;}
        if(taskFilter.equals("all")){
            for(String group:new String[]{"doing","pending","done"}){
                JSONArray grouped=new JSONArray();boolean hasRoot=false;
                for(int i=0;i<all.length();i++){JSONObject item=all.getJSONObject(i);boolean root=WorkspaceStore.nullable(item,"parent_id")==null;String status=item.optString("status");boolean match=group.equals("pending")?!status.equals("doing")&&!status.equals("done"):group.equals(status);if(!root||match){grouped.put(item);if(root)hasRoot=true;}}
                if(hasRoot){TextView section=AiosUi.label(this,group.equals("pending")?getString(R.string.workspace_attention):group.equals("doing")?getString(R.string.workspace_doing):getString(R.string.workspace_done));section.setPadding(dp(4),dp(12),0,dp(8));content.addView(section);tree(grouped,null,0,new HashSet<>());}
            }
        }else{JSONArray filtered=new JSONArray();for(int i=0;i<all.length();i++){JSONObject item=all.getJSONObject(i);if(taskFilter.equals(item.optString("status"))){JSONObject copy=new JSONObject(item.toString());copy.put("parent_id",JSONObject.NULL);filtered.put(copy);}}if(filtered.length()==0)empty(getString(R.string.workspace_empty_tasks),getString(R.string.workspace_empty_tasks_body));else tree(filtered,null,0,new HashSet<>());}
    }
    private ImageView iconAction(String icon,String label,Work action){return AiosUi.iconButton(this,icon,label,()->safe(action));}
    /** Large-title app bar for list pages: title 28sp, search and optional add on the trailing side. */
    private void listToolbar(String name,Work add){LinearLayout bar=row();bar.setPadding(0,dp(8),0,dp(8));TextView title=AiosUi.title(this,name);title.setPadding(0,0,0,0);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));bar.addView(iconAction("search",getString(R.string.workspace_search),()->{tab="search";render();}));if(add!=null)bar.addView(iconAction("add","新建"+name,add));content.addView(bar);}
    /** Compact app bar for detail pages. */
    private void detailToolbar(String title,Work back,View... actions){content.addView(AiosUi.toolbar(this,title,()->safe(back),actions));}
    private void tree(JSONArray all,String parent,int depth,Set<String> visited) throws Exception {
        int first=-1,last=-1;for(int n=0;n<all.length();n++){String owner=WorkspaceStore.nullable(all.getJSONObject(n),"parent_id");if(java.util.Objects.equals(parent,owner)){if(first<0)first=n;last=n;}}
        for(int i=0;i<all.length();i++){
            JSONObject item=all.getJSONObject(i);String p=WorkspaceStore.nullable(item,"parent_id");if(!(parent==null?p==null:parent.equals(p)))continue;
            String id=item.getString("id");if(!visited.add(id))continue;
            boolean children=false;for(int j=0;j<all.length();j++)if(id.equals(all.getJSONObject(j).optString("parent_id")))children=true;
            LinearLayout outer=column();outer.setPaddingRelative(dp(Math.min(depth,5)*16),0,0,0);content.addView(outer);
            String status=item.optString("status");String meta=taskStateLabel(status);if(!item.optString("time_text").isEmpty())meta+=" · "+item.optString("time_text");else if(!item.optString("due_date").isEmpty())meta+=" · "+getString(R.string.workspace_due,item.optString("due_date"));
            View fold=null;if(children){fold=AiosUi.iconButton(this,collapsed.contains(id)?"chevron":"collapse",getString(collapsed.contains(id)?R.string.workspace_expand:R.string.workspace_collapse,item.getString("title")),()->safe(()->{if(!collapsed.add(id))collapsed.remove(id);render();}));}
            LinearLayout line=AiosUi.statusRow(this,taskColor(status),item.getString("title"),meta,fold,i==first,i==last,()->safe(()->{taskId=id;render();}));
            line.setOnLongClickListener(v->{taskMenu(item);return true;});line.setContentDescription(os.aios.core.DisplayText.clean(getString(R.string.workspace_task_description,item.getString("title"),taskStateLabel(status))));outer.addView(line);
            if(children&&!collapsed.contains(id))tree(all,id,depth+1,visited);
        }
    }
    private GradientDrawable groupShape(boolean first,boolean last){return AiosUi.groupShape(this,AiosTheme.surface(this),first,last);}
    private void askText(String title,String value,boolean multiline,java.util.function.Consumer<String> accept){
        EditText e=editor(title,value,multiline);LinearLayout container=column();container.setPadding(dp(24),dp(8),dp(24),0);container.addView(e);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(title)).setView(container).setPositiveButton(os.aios.core.DisplayText.clean("确定"),null).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(e.getText().toString().trim().isEmpty()){e.setError(os.aios.core.DisplayText.clean("内容不能为空"));return;}accept.accept(e.getText().toString());dialog.dismiss();}));dialog.show();
    }
    private void newTask(String parent){askText(parent==null?getString(R.string.workspace_new_task):"新建子任务","",false,value->safe(()->{JSONObject t=call("task.create","title",value,"parent_id",parent);taskId=t.getString("id");collapsed.remove(parent);editing=true;render();}));}
    private void taskDetail() throws Exception {
        currentTask=call("task.get","id",taskId);
        View editAction=editing?AiosUi.iconButton(this,"close","取消编辑",()->new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("放弃这次编辑？")).setPositiveButton(os.aios.core.DisplayText.clean("放弃"),(d,w)->safe(()->{getPreferences(0).edit().remove("task:"+taskId).apply();editing=false;render();})).setNegativeButton(os.aios.core.DisplayText.clean("继续编辑"),null).show()):AiosUi.iconButton(this,"edit","编辑",()->safe(()->{editing=true;render();}));
        detailToolbar(editing?"编辑任务":getString(R.string.workspace_tasks),()->leave(()->{taskId=null;render();}),editAction,AiosUi.iconButton(this,"more","更多",this::detailOptions));
        if(editing){taskEditor();return;}
        String status=currentTask.getString("status");
        TextView title=AiosUi.heading(this,currentTask.getString("title"));title.setPadding(0,dp(4),0,dp(8));content.addView(title);
        LinearLayout state=row();state.addView(AiosUi.tag(this,taskStateLabel(status),taskColor(status)));
        String when=(currentTask.optString("time_text")+" "+currentTask.optString("start_date")+(currentTask.optString("due_date").isEmpty()?"":" → "+currentTask.optString("due_date"))).trim();
        TextView time=text(when.isEmpty()?"设置时间":when,14,MUTED);time.setPadding(dp(12),dp(4),dp(12),dp(4));time.setBackground(AiosUi.ripple(this,null,8));AiosUi.clickable(time,()->safe(()->{JSONObject task=call("task.get","id",taskId);LinearLayout fields=column();EditText rough=editor("粗略时间，如 2026年中—长期",task.optString("time_text"),false),start=editor("开始日期 YYYY-MM-DD，可留空",task.optString("start_date"),false),due=editor("截止日期 YYYY-MM-DD，可留空",task.optString("due_date"),false);fields.addView(rough);space(fields,12);fields.addView(start);space(fields,12);fields.addView(due);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("事项时间 · 不确定就留空")).setView(AiosUi.dialogContent(this,fields)).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("保存"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->safe(()->{call("task.update","id",taskId,"expected_revision",task.getLong("revision"),"time_text",rough.getText().toString(),"start_date",start.getText().toString(),"due_date",due.getText().toString(),"change_summary","调整事项时间");dialog.dismiss();render();})));dialog.show();}));
        time.setContentDescription(os.aios.core.DisplayText.clean("编辑时间"));state.addView(time);content.addView(state);space(content,12);
        LinearLayout doneRow=AiosUi.switchRow(this,"标记完成",status.equals("done")?"任务已完成":"完成后会移入已完成分组",status.equals("done"),value->safe(()->{call("task.update","id",taskId,"expected_revision",currentTask.getLong("revision"),"status",value?"done":"todo");render();}));content.addView(doneRow);space(content,12);
        if(!currentTask.isNull("ai_key")){
            LinearLayout ai=card(content);TextView summaryLabel=AiosUi.label(this,"工作摘要");summaryLabel.setPadding(0,0,0,dp(4));ai.addView(summaryLabel);ai.addView(markdown(currentTask.getString("ai_summary"),null,false));space(ai,8);LinearLayout progressHead=row();progressHead.addView(AiosUi.dot(this,taskColor(currentTask.getString("ai_status"))));TextView progressLabel=text("最新进度 · "+ExecutionState.taskLabel(currentTask.getString("ai_status")),14,MUTED);progressLabel.setPadding(dp(10),0,0,0);progressHead.addView(progressLabel);ai.addView(progressHead);ai.addView(markdown(currentTask.getString("ai_progress"),null,false));
            if(currentTask.getInt("user_status")==1){space(ai,8);ai.addView(text("完成状态由你决定，AI 更新不会覆盖。",13,MUTED));ai.addView(AiosUi.actions(this,textButton("交还 AI 管理状态",()->{call("task.ai.release","id",taskId,"expected_revision",currentTask.getLong("revision"));render();})));}
        }
        if(getPreferences(0).contains("task:"+taskId))content.addView(AiosUi.banner(this,"edit","有一份未保存的草稿","继续编辑",()->safe(()->{editing=true;render();})));

        LinearLayout note=column();note.setPadding(dp(4),dp(4),dp(4),dp(16));content.addView(note);String body=currentTask.getString("content_md");if(body.isEmpty()){if(currentTask.isNull("ai_key"))note.addView(text("编辑目标、完成标准与约束，再让小助手推进。",16,MUTED));}else if(currentTask.isNull("ai_key")||!body.equals(currentTask.optString("ai_summary")))note.addView(markdown(body,currentTask,true));
        JSONArray attachments=currentTask.getJSONArray("attachments");Set<String> rendered=new HashSet<>();java.util.regex.Matcher match=java.util.regex.Pattern.compile("aios-attachment:([a-zA-Z0-9-]+)").matcher(body);while(match.find())rendered.add(match.group(1));
        for(int i=0;i<attachments.length();i++)if(!rendered.contains(attachments.getJSONObject(i).getString("id")))note.addView(attachment(attachments.getJSONObject(i).getString("id")));
        actions(content,new String[]{"让小助手继续推进"},()->startAssistantTask(call("task.get","id",taskId)));
        JSONArray sessions=currentTask.getJSONArray("sessions");
        sectionLabel("关联会话 · "+sessions.length());
        for(int i=0;i<sessions.length();i++){JSONObject s=sessions.getJSONObject(i);content.addView(AiosUi.settingRow(this,"chat",sessionTitle(s),null,null,i==0,false,()->safe(()->openSession(s.getString("id")))));}
        content.addView(AiosUi.settingRow(this,"history","历史变化","查看每次修改前后的内容",null,sessions.length()==0,true,()->safe(()->taskHistory(0))));
        JSONArray all=call("task.list").getJSONArray("items");boolean hasChildren=false;for(int i=0;i<all.length();i++)if(taskId.equals(all.getJSONObject(i).optString("parent_id")))hasChildren=true;if(hasChildren){sectionLabel("子任务");tree(all,taskId,0,new HashSet<>());}
    }
    /** Section label inside the 16dp-padded content column. */
    private void sectionLabel(String label){TextView t=AiosUi.label(this,label);t.setPadding(dp(4),dp(12),0,dp(8));content.addView(t);}
    private void taskHistory(int offset) throws Exception {
        String owner=taskId;JSONObject page=call("task.history.list","id",owner,"offset",offset);LinearLayout panel=column();ScrollView scroll=AiosUi.dialogContent(this,panel);
        JSONArray entries=page.getJSONArray("items");
        for(int i=0;i<entries.length();i++){JSONObject summary=entries.getJSONObject(i);
            panel.addView(AiosUi.settingRow(this,"history",summary.getString("summary"),new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(summary.getLong("created_at"))),null,i==0,i==entries.length()-1,()->safe(()->{
                JSONObject entry=call("task.history.get","id",owner,"history_id",summary.getString("id")),before=new JSONObject(entry.getString("before_json")),after=new JSONObject(entry.getString("after_json"));
                String value="变更前\n"+taskPicture(before)+"\n\n变更后\n"+taskPicture(after);TextView details=text(value,15,INK);details.setTextIsSelectable(true);
                new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(entry.getString("summary"))).setView(AiosUi.dialogContent(this,details)).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).show();
            })));
        }
        if(entries.length()==0)panel.addView(text("尚无记录；启用此版本前的变化不补造。",14,MUTED));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("历史变化")).setView(scroll).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).create();
        if(offset>0||page.optBoolean("has_more")){space(panel,8);LinearLayout pager=row();pager.setGravity(Gravity.CENTER);if(offset>0)pager.addView(textButton("较新记录",()->{dialog.dismiss();taskHistory(Math.max(0,offset-50));}));if(page.optBoolean("has_more"))pager.addView(textButton("更早记录",()->{dialog.dismiss();taskHistory(offset+50);}));panel.addView(pager);}
        dialog.show();
    }
    private String taskPicture(JSONObject p){return p.optString("title")+"\n"+p.optString("content_md")+"\n状态："+p.optString("status")+"\n时间："+p.optString("time_text")+" "+p.optString("start_date")+" → "+p.optString("due_date")+"\n助手进度："+p.optString("ai_progress");}
    private void taskEditor() throws Exception {
        JSONObject draft=new JSONObject(getPreferences(0).getString("task:"+taskId,"{}"));
        if(draft.has("expected_revision")){
            if(draft.getLong("expected_revision")!=currentTask.getLong("revision"))content.addView(text("草稿对应的正文已更新。请复制需要的草稿内容，重新打开最新版本后编辑。",14,os.aios.core.AiosTheme.error(this)));
            currentTask.put("revision",draft.getLong("expected_revision"));
        }
        titleEditor=editor("标题",draft.optString("title",currentTask.getString("title")),false);content.addView(titleEditor);space(content,12);
        content.addView(AiosUi.fieldLabel(this,"状态"));String status=draft.optString("status",currentTask.getString("status"));int at=Math.max(0,java.util.Arrays.asList(STATUSES).indexOf(status));statusEditor=new AiosUi.Choice(this,statusLabels(),at,null);content.addView(statusEditor.view);
        content.addView(AiosUi.fieldLabel(this,"Markdown 正文 · - [ ] 清单 · [标题](链接)"));bodyEditor=editor("任务目标、完成标准、约束和需要调整的计划…",draft.optString("content_md",currentTask.getString("content_md")),true);bodyEditor.setMinLines(12);content.addView(bodyEditor);space(content,12);
        LinearLayout bar=AiosUi.actions(this,textButton("插入勾选项",()->{int pos=Math.max(0,bodyEditor.getSelectionStart());bodyEditor.getText().insert(pos,"\n- [ ] ");}),primary("保存",()->{saveTask();render();}));content.addView(bar);
    }
    private void saveTask() throws Exception {
        JSONObject changes=args("id",taskId,"expected_revision",currentTask.getLong("revision"),"title",titleEditor.getText().toString(),"content_md",bodyEditor.getText().toString());String status=STATUSES[statusEditor.getSelectedItemPosition()];if(!status.equals(currentTask.getString("status")))changes.put("status",status);store.call("task.update",changes);
        getPreferences(0).edit().remove("task:"+taskId).apply();editing=false;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(bodyEditor.getWindowToken(),0);
    }
    private void taskMenu(JSONObject item){safe(()->{String id=item.getString("id");new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(item.getString("title"))).setItems(os.aios.core.DisplayText.clean(new String[]{"移动到…","上移","下移","删除任务及子任务"}),(d,which)->safe(()->{
        if(which==0){chooseParent(item);return;}
        if(which==3){new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("删除任务及全部子任务？")).setMessage(os.aios.core.DisplayText.clean("关联会话和引用的原始媒体会保留。")).setPositiveButton(os.aios.core.DisplayText.clean("删除"),(x,w)->safe(()->{call("task.delete","id",id,"expected_revision",item.getLong("revision"));MediaReferences.cleanup(this,store);taskId=null;editing=false;render();})).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();return;}
        JSONArray all=call("task.list").getJSONArray("items");List<String> siblings=new ArrayList<>();String parent=WorkspaceStore.nullable(item,"parent_id");for(int j=0;j<all.length();j++){JSONObject t=all.getJSONObject(j);if(java.util.Objects.equals(parent,WorkspaceStore.nullable(t,"parent_id")))siblings.add(t.getString("id"));}int target=siblings.indexOf(id)+(which==1?-1:1);if(target<0||target>=siblings.size())return;call("task.move","id",id,"expected_revision",item.getLong("revision"),"parent_id",parent,"position",target);render();
    })).show();});}
    private void chooseParent(JSONObject item) throws Exception {
        JSONArray all=call("task.list").getJSONArray("items");List<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add(null);names.add("顶层");Set<String> excluded=new HashSet<>();excluded.add(item.getString("id"));boolean changed=true;while(changed){changed=false;for(int i=0;i<all.length();i++){JSONObject t=all.getJSONObject(i);if(excluded.contains(t.optString("parent_id"))&&excluded.add(t.getString("id")))changed=true;}}
        for(int i=0;i<all.length();i++){JSONObject t=all.getJSONObject(i);if(!excluded.contains(t.getString("id"))){ids.add(t.getString("id"));names.add(taskPath(t,all));}}
        new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("移动到")).setItems(os.aios.core.DisplayText.clean(names.toArray(new String[0])),(d,w)->safe(()->{call("task.move","id",item.getString("id"),"expected_revision",item.getLong("revision"),"parent_id",ids.get(w),"position",0);render();})).show();
    }
    private String taskPath(JSONObject task,JSONArray all) throws Exception {
        String path=task.getString("title"),parent=WorkspaceStore.nullable(task,"parent_id");Set<String> seen=new HashSet<>();
        while(parent!=null&&seen.add(parent)){JSONObject found=null;for(int i=0;i<all.length();i++)if(parent.equals(all.getJSONObject(i).getString("id"))){found=all.getJSONObject(i);break;}if(found==null)break;path=found.getString("title")+" / "+path;parent=WorkspaceStore.nullable(found,"parent_id");}return path;
    }
    private void chooseRelations(boolean fromTask) throws Exception {
        JSONArray options=call(fromTask?"session.list":"task.list").getJSONArray("items");JSONArray linked=call(fromTask?"task.get":"session.get","id",fromTask?taskId:sessionId).getJSONArray(fromTask?"sessions":"tasks");Set<String> existing=new HashSet<>();for(int i=0;i<linked.length();i++)existing.add(linked.getJSONObject(i).getString("id"));String[] labels=new String[options.length()];boolean[] checked=new boolean[options.length()];for(int i=0;i<options.length();i++){JSONObject o=options.getJSONObject(i);labels[i]=fromTask?sessionTitle(o):taskPath(o,options);checked[i]=existing.contains(o.getString("id"));}
        if(options.length()==0){toast(fromTask?"请先新建会话":"请先新建任务");return;}
        new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(fromTask?"关联会话（可多选）":"关联任务（可多选）")).setMultiChoiceItems(os.aios.core.DisplayText.clean(labels),checked,(d,w,value)->checked[w]=value).setPositiveButton(os.aios.core.DisplayText.clean("保存"),(d,w)->safe(()->{for(int i=0;i<options.length();i++){String id=options.getJSONObject(i).getString("id");if(checked[i]!=existing.contains(id))call(checked[i]?"relation.link":"relation.unlink","task_id",fromTask?taskId:id,"session_id",fromTask?id:sessionId);}render();})).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();
    }
    private MarkdownView markdown(String body,JSONObject task,boolean editable){return new MarkdownView(this,body,new MarkdownView.Actions(){public void toggle(int line){safe(()->{call("task.update","id",task.getString("id"),"expected_revision",task.getLong("revision"),"content_md",MarkdownView.toggleLine(body,line));render();});}public void open(String uri){safe(()->openLink(uri));}public View attachment(String id){return WorkspaceActivity.this.attachment(id);}},editable);}
    private void openLink(String uri) throws Exception {
        if(uri.startsWith("aios-attachment:")){openMedia(call("attachment.get","id",uri.substring("aios-attachment:".length())));return;}
        Uri parsed=Uri.parse(uri);String scheme=parsed.getScheme();if(scheme==null||Set.of("javascript","data","file","content","intent").contains(scheme.toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("此链接类型不能直接打开");
        try{startActivity(new Intent(Intent.ACTION_VIEW,parsed).addCategory(Intent.CATEGORY_BROWSABLE));}catch(android.content.ActivityNotFoundException e){toast("未安装能打开此链接的应用");}
    }
    private void insertLink(){
        LinearLayout fields=column();EditText label=editor("链接标题","",false),url=editor("https:// 或 App 跳转地址","",false);url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);os.aios.core.DisplayText.editable(url);fields.addView(label);space(fields,12);fields.addView(url);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("插入链接")).setView(AiosUi.dialogContent(this,fields)).setPositiveButton(os.aios.core.DisplayText.clean("插入"),null).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->safe(()->{String address=url.getText().toString().trim();if(Uri.parse(address).getScheme()==null||address.contains("\n")||address.contains(" "))throw new IllegalArgumentException("请输入完整链接");String name=label.getText().toString().replace("[","").replace("]","");JSONObject task=call("task.get","id",taskId);call("task.update","id",taskId,"expected_revision",task.getLong("revision"),"content_md",task.getString("content_md")+"\n["+(name.isEmpty()?address:name)+"]("+address.replace(")","%29")+")\n");dialog.dismiss();render();})));dialog.show();
    }
    private void pickMedia(String relink) throws Exception {
        if(loadingMedia)return;pickerTask=taskId;pickerRelink=relink;pickerCopy=relink==null&&mediaMode().equals("copy");
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,410);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=410||result!=RESULT_OK||data==null||data.getData()==null||pickerTask==null)return;
        final String target=pickerTask,relink=pickerRelink;final boolean copy=pickerCopy;final Uri uri=data.getData();final int flags=data.getFlags();loadingMedia=true;safe(this::render);
        // Worker owns a separate connection, so rotation/destruction cannot close it mid-import.
        mediaWorker.execute(()->{try(WorkspaceStore connection=new WorkspaceStore(getApplicationContext())){
            JSONObject media=MediaReferences.importUri(getApplicationContext(),uri,flags,copy);
            if(relink!=null){media.put("id",relink);connection.call("attachment.relink",media);}
            else {media.put("task_id",target);connection.call("attachment.add",media);}
            runOnUiThread(()->{loadingMedia=false;if(!isDestroyed())safe(this::render);});
        }catch(Exception e){runOnUiThread(()->{loadingMedia=false;if(!isDestroyed()){toast("导入失败："+e.getMessage());safe(this::render);}});}});
    }
    private View attachment(String id){
        LinearLayout panel=column();panel.setPadding(0,dp(8),0,dp(12));
        try {
            JSONObject a=call("attachment.get","id",id);String mode=a.getString("storage_mode");panel.addView(text(a.getString("name")+" · "+(mode.equals("copy")?"已保存副本":"引用原件"),13,MUTED));
            ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.setBackground(background(AiosTheme.surfaceHigh(this),16));preview.setClipToOutline(true);preview.setContentDescription(os.aios.core.DisplayText.clean("打开 "+a.getString("name")));panel.addView(preview,new LinearLayout.LayoutParams(-1,dp(170)));TextView state=text("正在加载预览…",12,MUTED);panel.addView(state);
            final int generation=renderGeneration;
            boolean visual=a.optString("mime").startsWith("image/")||a.optString("mime").startsWith("video/");
            if(!visual){preview.getLayoutParams().height=dp(56);preview.setImageDrawable(new os.aios.core.AiosIcon(this,"file",AiosTheme.muted(this)));state.setText(getString(os.aios.runtime.R.string.shared_attachment_open));}
            else mediaWorker.execute(()->{Bitmap bitmap=null;String problem=null;try{bitmap=loadThumbnail(a);if(bitmap==null)problem="无法生成预览，可尝试打开或重新关联";}catch(Exception e){problem="预览不可用，原件可能已删除或访问权限已失效";}final Bitmap ready=bitmap;final String error=problem;runOnUiThread(()->{if(isDestroyed()||generation!=renderGeneration){if(ready!=null)ready.recycle();return;}if(ready!=null){preview.setImageBitmap(ready);state.setText(a.optString("mime").startsWith("video/")?"▶ 点击播放视频":"点击查看图片");}else state.setText(error);});});
            preview.setOnClickListener(v->safe(()->openMedia(a)));panel.addView(AiosUi.actions(this,textButton("重新关联",()->pickMedia(id)),textButton("移除附件",()->new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("移除此附件？")).setMessage(os.aios.core.DisplayText.clean("原始媒体不会删除。")).setPositiveButton(os.aios.core.DisplayText.clean("移除"),(d,w)->safe(()->{call("attachment.delete","id",id);MediaReferences.cleanup(this,store);render();})).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show())));
        } catch(Exception e){panel.addView(text("附件不可用，可在编辑中移除此引用。",14,MUTED));}
        return panel;
    }
    private Bitmap loadThumbnail(JSONObject attachment) throws Exception {
        Uri uri=Uri.parse(attachment.getString("uri"));
        if("content".equals(uri.getScheme())) {
            // A provider may retain a cached thumbnail after the original disappears.
            try(android.content.res.AssetFileDescriptor original=getContentResolver().openAssetFileDescriptor(uri,"r")) {
                if(original==null)throw new java.io.FileNotFoundException("Original media is unavailable");
            }
            try {return getContentResolver().loadThumbnail(uri,new android.util.Size(720,400),null);}
            catch(Exception noProviderThumbnail) { /* Documents providers need not implement thumbnails. */ }
        }
        if(attachment.getString("mime").startsWith("video/")) {
            try(android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever()) {
                retriever.setDataSource(this,uri);
                return retriever.getScaledFrameAtTime(-1,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,720,400);
            }
        }
        android.graphics.ImageDecoder.Source source="content".equals(uri.getScheme())?android.graphics.ImageDecoder.createSource(getContentResolver(),uri):android.graphics.ImageDecoder.createSource(new java.io.File(uri.getPath()));
        return android.graphics.ImageDecoder.decodeBitmap(source,(decoder,info,s)->{
            int sample=Math.max(1,Math.max(info.getSize().getWidth()/720,info.getSize().getHeight()/400));
            decoder.setTargetSampleSize(sample);
        });
    }
    private void openMedia(JSONObject a) throws Exception {
        Uri uri=Uri.parse(a.getString("uri"));String mime=a.getString("mime");
        if(!mime.startsWith("image/")&&!mime.startsWith("video/")){
            try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
            catch(android.content.ActivityNotFoundException missing){toast(getString(os.aios.runtime.R.string.shared_attachment_no_viewer));}
        }else if(mime.startsWith("video/")){
            VideoView video=new VideoView(this);video.setVideoURI(uri);MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setOnPreparedListener(p->video.start());video.setOnErrorListener((p,w,e)->{toast("视频无法播放，请检查原件或重新关联");return true;});AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(a.getString("name"))).setView(video).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),(d,w)->video.stopPlayback()).create();dialog.setOnDismissListener(d->video.stopPlayback());dialog.show();video.getLayoutParams().height=dp(340);video.requestLayout();
        } else {
            ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(a.getString("name"))).setView(image).setPositiveButton(os.aios.core.DisplayText.clean("关闭"),null).show();
            mediaWorker.execute(()->{try{android.graphics.ImageDecoder.Source source="content".equals(uri.getScheme())?android.graphics.ImageDecoder.createSource(getContentResolver(),uri):android.graphics.ImageDecoder.createSource(new java.io.File(uri.getPath()));Bitmap bitmap=android.graphics.ImageDecoder.decodeBitmap(source,(decoder,info,s)->{int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());if(max>2048)decoder.setTargetSampleSize((max+2047)/2048);});runOnUiThread(()->{if(dialog.isShowing()&&!isDestroyed())image.setImageBitmap(bitmap);else bitmap.recycle();});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){dialog.dismiss();toast("图片无法读取，请重新关联");}});}});
        }
    }
    private String sessionTitle(JSONObject session){return session.optString("source").equals("codex")?os.aios.core.TranscriptText.preview(session.optString("title")):session.optString("title");}
    private void sessionList() throws Exception {
        JSONArray sessions=call("session.list").getJSONArray("items");listToolbar(getString(R.string.workspace_sessions),()->newSession(null));
        if(sessions.length()==0)empty("还没有会话","点击右上角 ＋ 与小助手开始对话。");
        for(int i=0;i<sessions.length();i++){JSONObject item=sessions.getJSONObject(i);boolean mirrored=item.optString("source").equals("codex");
            TextView date=AiosUi.meta(this,android.text.format.DateFormat.format("MM-dd HH:mm",item.getLong("updated_at")).toString());
            LinearLayout line=AiosUi.settingRow(this,mirrored?"assistant":"chat",sessionTitle(item),sessionSubtitle(item),date,i==0,i==sessions.length()-1,()->safe(()->openSession(item.getString("id"))));
            line.setContentDescription(os.aios.core.DisplayText.clean("会话："+sessionTitle(item)));content.addView(line);}
    }
    private void newSession(String task){safe(()->{if(task==null)newAssistantSession();else startAssistantTask(call("task.get","id",task));});}
    private void openSession(String id) throws Exception {++historyLoadGeneration;loadingHistory=false;stashMessage();tab="sessions";sessionId=id;taskId=null;editing=false;composer=null;messageDraft=getPreferences(0).getString("message:"+id,"");messageBefore=Long.MAX_VALUE;nativeHistory=null;nativeHistoryCursor=null;nativeHistoryFailed=false;nativeHistoryRevision=-1;render();}
    private void sessionDetail() throws Exception {
        JSONObject session=call("session.get","id",sessionId,"before_row",messageBefore,"limit",30);
        // Resolve an entry opened before native thread creation to its canonical session.
        sessionId=session.getString("id");
        if(session.optString("source").equals("assistant_pending")){
            detailToolbar(sessionTitle(session),()->{sessionId=null;render();},AiosUi.iconButton(this,"more",getString(R.string.live_session_more),()->sessionMenu(session)));
            renderPendingConversation(content,session);return;
        }
        boolean mirrored=session.optString("source").equals("codex");
        detailToolbar(sessionTitle(session),()->{if(composer!=null)getPreferences(0).edit().putString("message:"+sessionId,composer.getText().toString()).apply();composer=null;sessionId=null;messageDraft="";render();},AiosUi.iconButton(this,"tasks","关联任务",()->safe(()->chooseRelations(false))),AiosUi.iconButton(this,"more","更多",()->sessionMenu(session)));
        TextView caption=text(mirrored?getString(os.aios.runtime.R.string.history_native_caption):"本地会话 · 长按消息保存为记忆",13,MUTED);caption.setPadding(dp(4),0,dp(4),dp(8));content.addView(caption);
        JSONArray tasks=session.getJSONArray("tasks");if(tasks.length()>0){LinearLayout chips=row();for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(dp(8));chips.addView(AiosUi.assistChip(this,"tasks",t.getString("title"),()->safe(()->{tab="tasks";taskId=t.getString("id");sessionId=null;render();})),cp);}HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);strip.addView(chips);content.addView(strip);space(content,12);}
        LinearLayout history=row();history.setGravity(Gravity.CENTER);
        if(mirrored){
            final String expectedSession=sessionId;
            if(!loadingHistory&&!nativeHistoryFailed&&(nativeHistory==null||nativeHistoryRevision!=session.optLong("revision")))refreshHandler.post(()->{
                if(expectedSession.equals(sessionId)&&!loadingHistory&&!nativeHistoryFailed)safe(()->loadNativePage(session,"latest",null,null));
            });
            if(loadingHistory)history.addView(text(getString(os.aios.runtime.R.string.history_loading),14,MUTED));
            else {
                if(nativeHistory!=null&&!nativeHistory.isNull("next_cursor")){String cursor=nativeHistory.getString("next_cursor");history.addView(textButton(getString(os.aios.runtime.R.string.history_load_older),()->loadNativeHistory(session,cursor)));}
                history.addView(textButton(getString(os.aios.runtime.R.string.history_refresh),()->loadNativePage(session,"latest",null,null)));
            }
            if(nativeHistoryFailed)content.addView(text(getString(os.aios.runtime.R.string.history_saved_fallback),13,MUTED));
        }else{
            if(session.getBoolean("has_more"))history.addView(textButton("查看更早的消息",()->{messageBefore=session.getLong("next_before_row");render();}));
            if(messageBefore!=Long.MAX_VALUE)history.addView(textButton("返回最新消息",()->{messageBefore=Long.MAX_VALUE;render();}));
        }
        if(history.getChildCount()>0)content.addView(history);
        JSONArray messages=mirrored?(nativeHistory!=null?nativeMessages(nativeHistory):nativeHistoryFailed?session.getJSONArray("messages"):new JSONArray()):session.getJSONArray("messages");
        if(messages.length()==0&&(!mirrored||nativeHistory!=null))empty("这里还没有消息","可以记录问题与上下文。连接执行端后，AI 回复也会保存到同一会话。");
        Set<String> renderedInterventions=new HashSet<>();
        for(os.aios.core.TranscriptText.Turn turn:os.aios.core.TranscriptText.turns(messages,mirrored)){
            if(mirrored&&!turn.id.isEmpty()&&turn.id.equals(liveConversationTurn(session.optString("source_id"),nativeHistory)))continue;
            for(JSONObject m:turn.users)messageBubble(m,content);
            LinearLayout execution=column();
            JSONObject sourceTurn=os.aios.core.ConversationHistory.turn(nativeHistory,turn.id);
            boolean first=renderedInterventions.add(turn.id);
            boolean hasEarlier=first&&sourceTurn!=null&&sourceTurn.optBoolean("items_has_older");
            if(hasEarlier)execution.addView(textButton(getString(os.aios.runtime.R.string.history_load_steps),()->loadNativeItems(session,sourceTurn)));
            for(JSONObject m:turn.execution){
                if(m.optString("role").equals("assistant"))messageBubble(m,execution);else toolEvent(m,execution);
            }
            if(first&&!turn.id.isEmpty())renderInterventions(execution,session.optString("source_id"),turn.id);
            if(execution.getChildCount()>0){
                boolean pending=first&&pendingInterventions(session.optString("source_id"),turn.id);
                boolean running=runningExecution(session.optString("source_id"),turn.id);
                Runnable expand=hasEarlier?()->{if(!loadingHistory&&!nativeHistoryFailed)safe(()->loadNativeItems(session,sourceTurn));}:null;
                os.aios.core.ExecutionDetails.create(this,content,session.optString("source_id")+":"+turn.key,running,pending,expand).addView(execution);
            }
            for(JSONObject m:turn.answers)messageBubble(m,content);
        }
        if(mirrored){renderConversationComposer(shell,content,session,nativeHistory);return;}
        LinearLayout compose=row();compose.setPadding(0,dp(8),0,0);composerSession=sessionId;composer=editor("记录一条消息…",messageDraft,true);composer.setMinLines(1);composer.setMaxLines(6);composer.setBackground(AiosUi.shape(this,AiosTheme.surfaceHigh(this),28));compose.addView(composer,new LinearLayout.LayoutParams(0,-2,1));
        ImageView send=AiosUi.filledIconButton(this,"send","保存消息",AiosTheme.accent(this),AiosTheme.onAccent(this),()->safe(()->{String body=composer.getText().toString();if(body.trim().isEmpty())return;JSONObject message=call("message.append","session_id",sessionId,"role","user","content_md",body);composer.setText("");messageDraft="";getPreferences(0).edit().remove("message:"+sessionId).apply();onMessageSaved(message);messageBefore=Long.MAX_VALUE;render();scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(48),dp(48));sp.leftMargin=dp(8);sp.gravity=Gravity.BOTTOM;compose.addView(send,sp);content.addView(compose);
    }
    private void messageBubble(JSONObject m,LinearLayout parent)throws Exception {
        final JSONObject message=m;String role=m.getString("role");
            boolean user=role.equals("user");LinearLayout bubble=column();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.bottomMargin=dp(12);bp.gravity=user?Gravity.END:Gravity.START;if(user){bp.setMarginStart(dp(48));bubble.setPadding(dp(16),dp(10),dp(16),dp(12));bubble.setBackground(AiosUi.shape(this,AiosTheme.selected(this),20,4,20,20));}else{bp.setMarginEnd(dp(16));bubble.setPadding(dp(4),dp(4),dp(4),dp(4));}
            parent.addView(bubble,bp);
            if(!m.getString("content_md").isEmpty()){if(user||m.getString("content_md").contains("aios-attachment:"))bubble.addView(markdown(m.getString("content_md"),null,false));else bubble.addView(AiosUi.assistantText(this,m.getString("content_md")));}
            TextView timing=text(messageTiming(m),11,MUTED);timing.setPadding(0,dp(2),0,0);timing.setGravity(user?Gravity.END:Gravity.START);if(user)timing.setTextColor((AiosTheme.onSelected(this)&0x00ffffff)|0xb3000000);bubble.addView(timing);

            if(!m.getString("content_md").isEmpty()){bubble.setOnLongClickListener(v->{new AlertDialog.Builder(this).setItems(os.aios.core.DisplayText.clean(new String[]{"保存为记忆"}),(d,w)->safe(()->{call("memory.create","content",m.getString("content_md"),"source_session_id",sessionId,"source_message_id",m.optBoolean("native_history")?"":m.getString("id"));toast("已保存，可在设置中管理记忆");})).show();return true;});bubble.setContentDescription(os.aios.core.DisplayText.clean((user?"你：":"小助手：")+m.getString("content_md")));bubble.setFocusable(true);}
    }
    private void loadNativeHistory(JSONObject session,String cursor)throws Exception{loadNativePage(session,"older_turns",null,cursor);}
    private void loadNativeItems(JSONObject session,JSONObject turn)throws Exception{if(!turn.optBoolean("items_has_older")||turn.isNull("items_next_cursor")||os.aios.core.ConversationHistory.turn(nativeHistory,turn.optString("id"))!=turn)return;loadNativePage(session,"older_items",turn,turn.getString("items_next_cursor"));}
    private void loadNativePage(JSONObject session,String method,JSONObject turn,String cursor)throws Exception{
        if(loadingHistory||!session.getString("id").equals(sessionId))return;
        Bundle extras=new Bundle();extras.putString("thread_id",session.getString("source_id"));
        if(cursor!=null)extras.putString("cursor",cursor);if(turn!=null)extras.putString("turn_id",turn.getString("id"));
        final String expectedSession=sessionId;final JSONObject expectedHistory=nativeHistory;final int generation=++historyLoadGeneration;
        loadingHistory=true;nativeHistoryFailed=false;render();
        mediaWorker.execute(()->{
            JSONObject page=null;Exception failure=null;
            try{Bundle reply=getContentResolver().call(Uri.parse("content://os.aios.agent.history"),method,null,extras);if(reply==null||reply.containsKey("error"))throw new IllegalStateException(reply==null?"History service unavailable":reply.getString("error"));android.os.ParcelFileDescriptor descriptor=reply.getParcelable("result_fd",android.os.ParcelFileDescriptor.class);
                if(descriptor!=null)try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){byte[] bytes=input.readNBytes(64*1024*1024+1);if(bytes.length>64*1024*1024)throw new java.io.IOException("History page exceeds transfer limit");page=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));}
                else page=new JSONObject(reply.getString("result"));}catch(Exception error){failure=error;}
            final JSONObject loaded=page;final Exception error=failure;
            runOnUiThread(()->{
                if(isDestroyed()||generation!=historyLoadGeneration)return;loadingHistory=false;
                if(!"sessions".equals(tab)||!expectedSession.equals(sessionId))return;
                if(error!=null){nativeHistoryFailed=true;android.util.Log.w("AIOSHistory","Cannot load native history",error);toast(getString(os.aios.runtime.R.string.history_load_failed));safe(this::render);return;}
                safe(()->{
                    if(turn==null){nativeHistory=os.aios.core.ConversationHistory.merge(nativeHistory,loaded,method.equals("older_turns"));nativeHistoryCursor=cursor;if(method.equals("latest"))nativeHistoryRevision=session.optLong("revision");}
                    else{if(nativeHistory!=expectedHistory)return;os.aios.core.ConversationHistory.prependItems(turn,loaded);}
                    render();
                });
            });
        });
    }
    private JSONArray nativeMessages(JSONObject page)throws Exception{return os.aios.core.ConversationHistory.messages(page);}
    private JSONObject timingMetadata(JSONObject m){try{return new JSONObject(m.optString("detail","{}")).optJSONObject("aios_timing");}catch(Exception ignored){return null;}}
    private String elapsedText(long millis){long seconds=Math.max(0,millis)/1000;return (seconds>=3600?seconds/3600+"小时":"")+(seconds/60%60>0?seconds/60%60+"分":"")+(seconds%60)+"秒";}
    private String messageTiming(JSONObject m){JSONObject timing=timingMetadata(m);long updated=timing==null?m.optLong("created_at"):timing.optLong("updated_at");String result=updated>0?(timing==null?"记录时间 ":"最后更新 ")+android.text.format.DateFormat.format("MM-dd HH:mm:ss",updated):"";
        if(timing!=null){long duration=timing.optLong("duration_ms",-1),elapsed=timing.optLong("elapsed_ms",-1);if(duration>=0)result="执行了 "+elapsedText(duration)+" · "+result;else if(elapsed>=0)result="已执行 "+elapsedText(elapsed)+" · "+result;}return result;}
    private void toolEvent(JSONObject message,LinearLayout parent) throws Exception {
        LinearLayout item=listRow(parent),details=column();String body=message.getString("content_md");final String visibleDetail=os.aios.core.TranscriptText.toolDetail(message.optString("detail"));
        String summary=preview(body);if(summary.isEmpty())summary="工具执行记录";try{JSONObject detail=new JSONObject(message.optString("detail","{}"));if(!detail.isNull("durationMs")&&detail.optLong("durationMs",-1)>=0)summary+=" · 执行了 "+elapsedText(detail.getLong("durationMs"));}catch(Exception ignored){}
        final String label=summary;String kind=message.optString("kind");String icon=kind.contains("command")||kind.contains("exec")?"bolt":kind.contains("file")||kind.contains("patch")?"folder":kind.contains("web")||kind.contains("search")?"web":kind.contains("plan")?"tasks":"check";
        LinearLayout toggle=AiosUi.receipt(this,icon,label,false);ImageView chevron=AiosUi.icon(this,"chevron",MUTED);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(18),dp(18));cp.leftMargin=dp(8);toggle.addView(chevron,cp);toggle.setBackground(AiosUi.ripple(this,null,8));
        AiosUi.clickable(toggle,()->{boolean open=details.getVisibility()==View.GONE;if(open&&details.getChildCount()==0){if(!body.isEmpty())details.addView(markdown(body,null,false));if(!visibleDetail.isEmpty()){TextView raw=text(visibleDetail,12,MUTED);raw.setTextIsSelectable(true);details.addView(raw);}}details.setVisibility(open?View.VISIBLE:View.GONE);chevron.setImageDrawable(new AiosIcon(this,open?"collapse":"chevron",MUTED));});
        toggle.setContentDescription(os.aios.core.DisplayText.clean("展开 / 收起详细记录："+label));item.addView(toggle);
        details.setPadding(dp(30),dp(4),dp(4),dp(8));details.setVisibility(View.GONE);item.addView(details);
    }
    private void sessionMenu(JSONObject session){new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("会话选项")).setItems(os.aios.core.DisplayText.clean(new String[]{"重命名","删除会话"}),(d,w)->{
        if(w==0)askText("会话名称",sessionTitle(session),false,value->safe(()->{call("session.update","id",sessionId,"expected_revision",session.getLong("revision"),"title",value);render();}));
        else {LinearLayout deletionOptions=column();deletionOptions.setPadding(dp(24),dp(8),dp(24),0);LinearLayout deleteMemory=AiosUi.switchRow(this,"同时删除保存的记忆","从此会话保存的记忆",false,null),deleteSkills=AiosUi.switchRow(this,"同时删除学习的技能","从此会话总结的技能",false,null);deletionOptions.addView(deleteMemory);space(deletionOptions,2);deletionOptions.addView(deleteSkills);new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("删除此会话？")).setMessage(os.aios.core.DisplayText.clean("本机记录和关联将被删除，任务会保留。小助手来源会话会停止同步；手机上的原始会话不会删除。")).setView(deletionOptions).setPositiveButton(os.aios.core.DisplayText.clean("删除"),(x,n)->safe(()->{String deleted=sessionId;call("session.delete","id",sessionId,"expected_revision",session.getLong("revision"),"delete_memories",AiosUi.isChecked(deleteMemory),"delete_skills",AiosUi.isChecked(deleteSkills));onSessionDeleted(session.optString("source_id"));getPreferences(0).edit().remove("message:"+deleted).apply();sessionId=null;composer=null;messageDraft="";render();})).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();}
    }).show();}
    private String mediaMode() throws Exception {JSONArray values=call("settings.get").getJSONArray("items");for(int i=0;i<values.length();i++)if(values.getJSONObject(i).getString("key").equals("media_mode"))return values.getJSONObject(i).getString("value");return "reference";}
    private void settings() throws Exception {
        LinearLayout bar=row();bar.setPadding(0,dp(8),0,dp(8));TextView title=AiosUi.title(this,getString(R.string.workspace_settings));title.setPadding(0,0,0,0);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));content.addView(bar);
        addHostConfiguration(content);
        sectionLabel("记忆与存储");
        boolean autoSave=false;JSONArray preferences=call("settings.get").getJSONArray("items");for(int i=0;i<preferences.length();i++){JSONObject pref=preferences.getJSONObject(i);if(pref.getString("key").equals("memory_auto_save"))autoSave=pref.getString("value").equals("true");}
        settingRow(content,"memory","管理记忆","查看来源、修正内容或忘记",null,true,false,()->{page="memories";safe(this::render);});
        settingRow(content,"book","自动保存明确事实","关闭后 AI 提炼的内容需先确认",AiosUi.m3switch(this,autoSave,value->safe(()->call("settings.set","key","memory_auto_save","value",Boolean.toString(value)))),false,false,null);
        settingRow(content,"image","媒体默认保存副本",mediaMode().equals("copy")?"附件复制到本机，占用更多空间":"引用原件，删除或失权后需重新关联",AiosUi.m3switch(this,mediaMode().equals("copy"),value->safe(()->call("settings.set","key","media_mode","value",value?"copy":"reference"))),false,true,null);
        space(content,8);content.addView(text("本地资料不跨设备同步。",12,MUTED));
    }
    private void memories() throws Exception {
        detailToolbar("记忆",()->{page="";render();},AiosUi.iconButton(this,"add","新增记忆",()->safe(()->editMemory(null))));
        EditText search=editor("搜索记忆或历史消息",memoryQuery,false);search.setSingleLine(true);os.aios.core.DisplayText.editable(search);search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setCompoundDrawablesWithIntrinsicBounds(new AiosIcon(this,"search",MUTED),null,null,null);search.setCompoundDrawablePadding(dp(12));search.setOnEditorActionListener((v,a,e)->{safe(()->{memoryQuery=search.getText().toString().trim();render();});return true;});content.addView(search);space(content,12);
        LinearLayout chips=row();String[] filters={"active","candidate","history"},filterNames={"已记住","待确认","历史搜索"};for(int i=0;i<filters.length;i++){final String f=filters[i];LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(dp(8));chips.addView(AiosUi.chip(this,filterNames[i],f.equals(memoryFilter),()->safe(()->{memoryFilter=f;render();})),cp);}content.addView(chips);space(content,12);
        if(memoryFilter.equals("history")){
            if(memoryQuery.isEmpty()){empty("查找过去的讨论","输入关键词，搜索本地会话和已同步的小助手消息。");return;}
            JSONObject result=call("session.search","query",memoryQuery,"limit",30);JSONArray hits=result.getJSONArray("items");
            if(hits.length()==0)empty("没有找到相关历史","试试更具体的关键词。");
            for(int i=0;i<hits.length();i++){JSONObject hit=hits.getJSONObject(i);content.addView(AiosUi.settingRow(this,"chat",hit.getString("snippet"),"查看来源会话",null,i==0,i==hits.length()-1,()->safe(()->openSession(hit.getString("session_id")))));}if(result.getBoolean("has_more")){space(content,8);content.addView(text("还有更多结果，请细化关键词。",12,MUTED));}return;
        }
        JSONArray items=call("memory.list","state",memoryFilter).getJSONArray("items");Set<String> matches=new HashSet<>();
        if(!memoryQuery.isEmpty()&&memoryFilter.equals("active")){JSONArray hits=call("memory.search","query",memoryQuery,"limit",50).getJSONArray("items");for(int i=0;i<hits.length();i++)matches.add(hits.getJSONObject(i).getString("id"));}
        int visible=0;
        for(int i=0;i<items.length();i++){JSONObject m=items.getJSONObject(i);if(!memoryQuery.isEmpty()&&!matches.contains(m.getString("id"))&&!m.getString("content").contains(memoryQuery))continue;visible++;
            LinearLayout c=card(content);TextView body=text(m.getString("content"),16,INK);body.setTextIsSelectable(true);c.addView(body);long until=m.optLong("valid_until");String scope=m.optString("scope_type");String label=scope.equals("user")?"用户偏好 / 全局":scope.equals("task")?"关联任务":"legacy-computer-project".equals(m.optString("scope_id"))?"历史电脑项目（不用于本机记忆召回）":"本机 AIOS";
            TextView metaLine=text((m.optInt("pinned")==1?"置顶 · ":"")+label+" · "+android.text.format.DateFormat.format("M月d日 HH:mm",m.getLong("updated_at"))+(until==0?"":until<=System.currentTimeMillis()?" · 已过期":" · 有效至 "+android.text.format.DateFormat.format("M月d日",until)),12,MUTED);c.addView(metaLine);
            String source=WorkspaceStore.nullable(m,"source_session_id");
            if(memoryFilter.equals("candidate")){c.addView(AiosUi.actions(this,textButton("拒绝",()->{call("memory.resolve","id",m.getString("id"),"expected_revision",m.getLong("revision"),"decision","reject");render();}),button("替代旧记忆",()->replaceMemory(m)),primary("接受",()->{call("memory.resolve","id",m.getString("id"),"expected_revision",m.getLong("revision"),"decision","accept");render();})));}
            c.addView(AiosUi.actions(this,source==null?null:textButton("来源会话",()->openSession(source)),textButton("编辑",()->editMemory(m)),textButton(m.optInt("pinned")==1?"取消置顶":"置顶",()->{call("memory.update","id",m.getString("id"),"expected_revision",m.getLong("revision"),"pinned",m.optInt("pinned")!=1);render();}),textButton("删除",()->new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("忘记这条记忆？")).setMessage(os.aios.core.DisplayText.clean("将移除记忆及检索索引，原始会话仍保留。下一轮会用新会话避免沿用旧上下文。")).setPositiveButton(os.aios.core.DisplayText.clean("删除"),(d,w)->safe(()->{call("memory.delete","id",m.getString("id"),"expected_revision",m.getLong("revision"));render();})).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show())));
        }
        if(visible==0)empty(memoryFilter.equals("candidate")?"没有待确认的记忆":"没有匹配的记忆","可以手动添加，也可以从会话消息保存。");
    }
    private void editMemory(JSONObject memory)throws Exception{
        LinearLayout form=column();EditText body=editor("希望记住什么？",memory==null?"":memory.getString("content"),true);body.setMinLines(3);form.addView(body);space(form,12);
        String[] kinds={"fact","preference","convention","experience"};form.addView(AiosUi.fieldLabel(this,"类型"));AiosUi.Choice kind=new AiosUi.Choice(this,new String[]{"事实","偏好","约定","经验"},memory==null?0:Math.max(0,java.util.Arrays.asList(kinds).indexOf(memory.getString("kind"))),null);form.addView(kind.view);
        JSONArray tasks=call("task.list").getJSONArray("items");List<String> labels=new ArrayList<>(),types=new ArrayList<>(),ids=new ArrayList<>();labels.add("本机 AIOS");types.add("project");ids.add("aios-phone");labels.add("所有对话");types.add("user");ids.add("");for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);labels.add("任务："+t.getString("title"));types.add("task");ids.add(t.getString("id"));}
        if(memory!=null&&"legacy-computer-project".equals(memory.optString("scope_id"))){labels.add("历史电脑项目（保持原范围）");types.add("project");ids.add("legacy-computer-project");}
        form.addView(AiosUi.fieldLabel(this,"适用范围"));Spinner scope=new Spinner(this);scope.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));if(memory!=null)for(int i=0;i<ids.size();i++)if(types.get(i).equals(memory.getString("scope_type"))&&ids.get(i).equals(memory.getString("scope_id")))scope.setSelection(i);scope.setBackground(AiosUi.shape(this,AiosTheme.surfaceHigh(this),16));scope.setPadding(dp(16),dp(14),dp(16),dp(14));scope.setMinimumHeight(dp(56));form.addView(scope);space(form,12);
        form.addView(AiosUi.fieldLabel(this,"有效期"));AiosUi.Choice expiry=new AiosUi.Choice(this,new String[]{"保留当前有效期","长期有效","一天后过期","七天后过期","三十天后过期"},0,null);form.addView(expiry.view);
        ScrollView view=AiosUi.dialogContent(this,form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(memory==null?"新增记忆":"编辑记忆")).setView(view).setPositiveButton(os.aios.core.DisplayText.clean("保存"),null).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->safe(()->{
            int s=scope.getSelectedItemPosition(),e=expiry.getSelectedItemPosition();long until=e==0?(memory==null?0:memory.optLong("valid_until")):e==1?0:System.currentTimeMillis()+(e==2?1:e==3?7:30)*86400000L;
            JSONObject p=args("content",body.getText().toString(),"kind",kinds[kind.getSelectedItemPosition()],"scope_type",types.get(s),"scope_id",ids.get(s),"valid_until",until);if(memory!=null){p.put("id",memory.getString("id"));p.put("expected_revision",memory.getLong("revision"));}
            store.call(memory==null?"memory.create":"memory.update",p);dialog.dismiss();render();
        })));dialog.show();
    }
    private void replaceMemory(JSONObject candidate)throws Exception{
        JSONArray all=call("memory.list").getJSONArray("items");List<JSONObject> targets=new ArrayList<>();List<String> titles=new ArrayList<>();for(int i=0;i<all.length();i++){JSONObject m=all.getJSONObject(i);if(m.getString("scope_type").equals(candidate.getString("scope_type"))&&m.getString("scope_id").equals(candidate.getString("scope_id"))){targets.add(m);titles.add(m.getString("content"));}}
        if(targets.isEmpty()){toast("没有相同范围的旧记忆，可以直接接受。");return;}
        new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("选择要替代的旧记忆")).setItems(os.aios.core.DisplayText.clean(titles.toArray(new String[0])),(d,w)->safe(()->{JSONObject target=targets.get(w);call("memory.resolve","id",candidate.getString("id"),"expected_revision",candidate.getLong("revision"),"decision","replace","target_id",target.getString("id"),"target_revision",target.getLong("revision"));render();})).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();
    }
    @Override public void onBackPressed(){if(tab.equals("automation")&&handleAutomationBack())return;stashMessage();leave(()->{if(taskId!=null){taskId=null;render();}else if(sessionId!=null){sessionId=null;composer=null;messageDraft="";render();}else if(!page.isEmpty()){page="";render();}else if(!tab.equals("tasks")){tab="tasks";render();}else super.onBackPressed();});}
}
