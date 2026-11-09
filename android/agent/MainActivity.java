package os.aios.agent;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
import os.aios.workspace.WorkspaceActivity;

/** Host integration: workspace is an independent Android library module. */
public class MainActivity extends WorkspaceActivity {
    private String liveThread="",liveTurn="",lastLiveStatus="",liveRenderKey="";
    private boolean liveNativeComplete,historyFocused;private int historySelection;
    private LinearLayout liveMessages;private android.widget.EditText historyInput;
    private final android.os.Handler liveHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateLive=()->{try{updateLiveMessages();}catch(Exception e){android.util.Log.w("AIOSHistory","Live update",e);}};
    private final android.content.BroadcastReceiver liveReceiver=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent i){liveHandler.removeCallbacks(updateLive);liveHandler.postDelayed(updateLive,50);}};
    @Override protected void onStart(){super.onStart();registerReceiver(liveReceiver,new android.content.IntentFilter(AssistantConversation.CHANGED),android.content.Context.RECEIVER_NOT_EXPORTED);}
    @Override protected void onStop(){unregisterReceiver(liveReceiver);liveHandler.removeCallbacks(updateLive);super.onStop();}
    @Override protected String sessionSubtitle(JSONObject session){
        if(!java.util.Set.of("codex","assistant_pending").contains(session.optString("source")))return super.sessionSubtitle(session);
        return sessionStatus(session.optString("source_status"));
    }
    protected String sessionStatus(String status){
        int label=switch(status){case "queued"->R.string.session_status_queued;case "running","inProgress"->R.string.session_status_running;case "paused"->R.string.session_status_paused;case "completed"->R.string.session_status_completed;case "failed"->R.string.session_status_failed;case "interrupted"->R.string.session_status_interrupted;default->R.string.session_status_assistant;};
        return getString(label);
    }
    @Override protected void renderPendingConversation(LinearLayout parent,JSONObject session)throws Exception{
        JSONObject saved=new JSONObject(getSharedPreferences("assistant_conversation_archive",0).getString(session.optString("source_id"),"{}"));
        parent.addView(AiosUi.selectable(this,saved.optString("text",session.optString("title")),16,AiosTheme.text(this)));
        parent.addView(AiosUi.meta(this,sessionSubtitle(session)));
        if(!saved.optString("error").isEmpty())parent.addView(AiosUi.assistantText(this,saved.optString("error")));
        if(session.optString("source_id").equals(AssistantConversation.state(this).optString("conversation_id")))
            parent.addView(AiosUi.textButton(this,getString(R.string.session_open_assistant),()->startActivity(new Intent(this,AssistantActivity.class))));
    }
    @Override protected String liveConversationTurn(String thread,JSONObject history){try{
        JSONObject state=AssistantConversation.state(this);if(!thread.equals(state.optString("thread_id")))return "";
        String turn=state.optString("turn_id"),status=state.optString("status");
        if(status.equals("queued")||status.equals("running"))return turn;
        JSONObject saved=os.aios.core.ConversationHistory.turn(history,turn);
        if(saved!=null&&!saved.optString("status").equals("inProgress"))return "";
        return turn;
    }catch(Exception e){return "";}}
    @Override protected void renderConversationComposer(LinearLayout parent,LinearLayout messages,JSONObject session,JSONObject nativePage)throws Exception{
        liveThread=session.getString("source_id");
        JSONObject current=AssistantConversation.state(this),saved=os.aios.core.ConversationHistory.turn(nativePage,current.optString("turn_id"));
        liveNativeComplete=liveThread.equals(current.optString("thread_id"))&&saved!=null&&!saved.optString("status").equals("inProgress");
        liveMessages=AiosUi.column(this);messages.addView(liveMessages);
        // The draft is keyed by native thread, never copied across conversations.
        LinearLayout bar=new LinearLayout(this);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),dp(8),dp(16),dp(12));
        historyInput=AssistantTextInput.create(this,getSharedPreferences("history_drafts",0).getString(liveThread,""));
        historyInput.setSingleLine(false);historyInput.setMinLines(1);historyInput.setMaxLines(5);
        final String thread=liveThread;historyInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){getSharedPreferences("history_drafts",0).edit().putString(thread,s.toString()).apply();}public void afterTextChanged(android.text.Editable e){}});
        bar.setBackground(AiosUi.shape(this,AiosTheme.surfaceHigh(this),28));
        bar.addView(AiosUi.iconButton(this,"camera",getString(R.string.assistant_camera_6042a),()->historyAttachment("camera")));
        bar.addView(historyInput,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(AiosUi.iconButton(this,"mic",getString(R.string.assistant_switch_to_voice_70d90),()->{try{selectHistoryThread();startActivity(new Intent(this,AssistantActivity.class).putExtra("mode","listen"));}catch(Exception e){historyInput.setError(e.getMessage());}}));
        bar.addView(AiosUi.iconButton(this,"add",getString(R.string.assistant_show_attachments_e3606),()->historyAttachment("file")));
        if(historyFocused){historyInput.requestFocus();historyInput.setSelection(Math.min(historySelection,historyInput.length()));}
        Runnable sendHistory=()->{try{
            String text=historyInput.getText().toString();if(text.isBlank())return;
            JSONObject state=AssistantConversation.state(this);if(!thread.equals(state.optString("thread_id")))AssistantConversation.selectThread(this,thread);
            AssistantConversation.enqueue(this,text);historyInput.setText("");startForegroundService(new Intent(this,AgentService.class));updateLiveMessages();
        }catch(Exception e){historyInput.setError(e.getMessage());}};
        bar.addView(AiosUi.iconButton(this,"send",getString(R.string.assistant_send_1214d),sendHistory));
        historyInput.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendHistory.run();return true;}return false;});parent.addView(bar);
        liveHandler.post(updateLive);
    }
    private void selectHistoryThread()throws Exception{if(!liveThread.equals(AssistantConversation.state(this).optString("thread_id")))AssistantConversation.selectThread(this,liveThread);}
    private void historyAttachment(String picker){try{selectHistoryThread();startActivity(new Intent(this,AssistantAttachmentActivity.class).putExtra("conversation_id",AssistantConversation.state(this).getString("conversation_id")).putExtra("picker",picker).putExtra("draft",historyInput.getText().toString()));}catch(Exception e){historyInput.setError(e.getMessage());}}
    private void updateLiveMessages()throws Exception{
        if(liveMessages==null||!liveMessages.isAttachedToWindow()||liveThread.isEmpty())return;
        if(conversationTouching()){liveHandler.removeCallbacks(updateLive);liveHandler.postDelayed(updateLive,180);return;}
        JSONObject state=AssistantConversation.state(this);if(!liveThread.equals(state.optString("thread_id"))){liveMessages.removeAllViews();return;}
        String status=state.optString("status"),turn=state.optString("turn_id");
        boolean active=status.equals("queued")||status.equals("running");
        boolean ended=!active&&(lastLiveStatus.equals("queued")||lastLiveStatus.equals("running"));lastLiveStatus=status;
        String renderKey=state.toString();if(renderKey.equals(liveRenderKey))return;liveRenderKey=renderKey;
        liveMessages.removeAllViews();
        if(active||!liveNativeComplete&&(!state.optString("text").isEmpty()||!state.optString("reply").isEmpty())){
            TextView mine=AiosUi.selectable(this,state.optString("text"),16,AiosTheme.text(this));mine.setPadding(dp(14),dp(10),dp(14),dp(10));mine.setBackground(AiosUi.shape(this,AiosTheme.surfaceHigh(this),20));LinearLayout.LayoutParams message=new LinearLayout.LayoutParams(-2,-2);message.gravity=android.view.Gravity.END;liveMessages.addView(mine,message);
            String partial=active?state.optString("partial_reply"):state.optString("reply");if(!active&&partial.isEmpty())partial=AssistantLocale.notice(this,state);if(!active&&partial.isEmpty())partial=getString(R.string.assistant_stopped_75ddd);if(partial.isEmpty())partial=AssistantLocale.progress(this,state);
            if(partial.isEmpty())partial=getString(status.equals("queued")?R.string.assistant_sending_a822c:R.string.assistant_thinking_4cc57);
            liveMessages.addView(AiosUi.assistantText(this,partial));
            if(!turn.isEmpty())InterventionCards.appendTurn(this,liveMessages,liveThread,turn,"");
            if(active)liveMessages.addView(AiosUi.textButton(this,getString(R.string.assistant_stop_current_task_6e072),()->{try{AssistantConversation.cancel(this);}catch(Exception e){android.util.Log.w("AIOSHistory","Cancel",e);}}));
        }
        AssistantFollowups.append(this,liveMessages,state);followConversation();boolean turnChanged=active&&!turn.isEmpty()&&!turn.equals(liveTurn);liveTurn=turn;if(ended||turnChanged)refreshConversation();
    }
    private final java.util.Set<String> shownReviewProposals=new java.util.HashSet<>();
    @Override protected void onPostResume(){super.onPostResume();if(DeviceHost.ui().showSetupIfNeeded(this))return;if(!getIntent().hasExtra("workspace_session")&&!getIntent().hasExtra("workspace_page"))DreamPanel.confirmPending(this,shownReviewProposals);}
    @Override protected void onWorkspaceRender(){InterventionCoordinator.visibleThread="";historyFocused=historyInput!=null&&historyInput.hasFocus();historySelection=historyInput==null?0:Math.max(0,historyInput.getSelectionStart());liveMessages=null;liveThread="";liveRenderKey="";}
    @Override protected void onPause(){InterventionCoordinator.visibleThread="";super.onPause();}
    private os.aios.automation.AutomationPanel automationPanel;
    private android.os.Bundle automationState;
    @Override protected void renderSkillReview(LinearLayout parent){DreamPanel.reviewBanner(this,parent);}
    @Override protected void renderInterventions(LinearLayout parent,String thread,String turn){InterventionCoordinator.visibleThread=thread;InterventionCards.appendTurn(this,parent,thread,turn,"");}
    @Override protected boolean runningExecution(String thread,String turn){return InterventionCards.running(this,thread,turn,"");}
    @Override protected boolean pendingInterventions(String thread,String turn){return InterventionCards.pending(thread,turn,"");}
    @Override protected void onSessionDeleted(String thread){if(AgentService.instance!=null)AgentService.instance.interventions.deleteThread(thread);}
    @Override protected void renderReviewSuggestions(LinearLayout parent){DreamPanel.taskCard(this,parent);}
    @Override protected void renderAutomation(LinearLayout parent){automationPanel=new os.aios.automation.AutomationPanel(this,parent,automationState);automationState=null;}
    @Override protected boolean handleAutomationBack(){return automationPanel!=null&&automationPanel.goBack();}
    @Override protected void closeAutomation(){if(automationPanel!=null){automationState=automationPanel.saveState();automationPanel.close();automationPanel=null;}}
    @Override public void onCreate(android.os.Bundle state) {
        automationState=state==null?null:state.getBundle("automation_state");
        super.onCreate(state);
        startForegroundService(new Intent(this,AgentService.class));
    }
    @Override protected void onSaveInstanceState(android.os.Bundle state){if(automationPanel!=null)state.putBundle("automation_state",automationPanel.saveState());super.onSaveInstanceState(state);}
    private java.util.concurrent.ExecutorService accountWorker;
    private CodexAccountClient accountClient;
    private TextView accountTitle,accountSummary,modelSummary,effortSummary;
    private int dp(int n){return AiosUi.dp(this,n);}
    /** Account card at the top of Settings: avatar, provider / account line, chevron to account management. */
    private void addAccountCard(LinearLayout panel) {
        LinearLayout card=AiosUi.row(this);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(AiosUi.ripple(this,AiosUi.shape(this,AiosTheme.surface(this),28)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);card.setLayoutParams(lp);
        android.widget.ImageView avatar=AiosUi.icon(this,"account",AiosTheme.onSelected(this));avatar.setBackground(AiosUi.shape(this,AiosTheme.selected(this),24));avatar.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(dp(48),dp(48));al.setMarginEnd(dp(16));card.addView(avatar,al);
        LinearLayout texts=AiosUi.column(this);accountTitle=AiosUi.oneLine(AiosUi.body(this,getString(R.string.account_card_title)));accountSummary=AiosUi.lines(AiosUi.secondary(this,getString(R.string.account_card_loading)),2);accountSummary.setPadding(0,dp(2),0,0);texts.addView(accountTitle);texts.addView(accountSummary);card.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(AiosUi.icon(this,"chevron",AiosTheme.muted(this)),new LinearLayout.LayoutParams(dp(24),dp(24)));
        card.setContentDescription(os.aios.core.DisplayText.clean(getString(R.string.account_manage)));AiosUi.clickable(card,()->startActivity(new Intent(this,CodexAccountsActivity.class)));panel.addView(card);
        if(accountWorker==null)accountWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
        if(accountClient==null)accountClient=new CodexAccountClient(this);
        final TextView title=accountTitle,summary=accountSummary;
        accountWorker.execute(()->{
            String name,detail,currentModel="",currentEffort="";
            try{JSONObject s=accountClient.call(new JSONObject().put("op","accounts/status"));String provider=s.optString("provider"),state=s.optString("state");
                if("chatgpt".equals(provider)){name="ChatGPT / GPT";
                    if("authenticated".equals(state))detail=s.optString("email","").replaceAll("^$",getString(R.string.account_card_saved))+(s.optString("plan").isEmpty()?"":" · "+s.optString("plan"))+getString(R.string.account_card_verify);
                    else detail="waiting".equals(state)?getString(R.string.account_card_waiting):getString(R.string.account_card_signed_out);
                }else{JSONObject profiles=s.optJSONObject("profiles"),cfg=profiles==null?null:profiles.optJSONObject(provider);name=getString("openai".equals(provider)?R.string.gpt_api_title:"deepseek".equals(provider)?R.string.account_deepseek:"glm".equals(provider)?R.string.account_glm:"qwen".equals(provider)?R.string.account_qwen:R.string.account_custom);
                    detail=getString(R.string.account_card_api,cfg==null?getString(R.string.account_card_unknown):cfg.optString("model"))+getString(R.string.account_card_verify);
                }
                currentModel=s.optString("model","").replaceAll("^$",getString(R.string.account_model_auto));currentEffort=effortName(s.optString("effort"));
            }catch(Exception e){name=getString(R.string.account_card_title);detail=accountClient.isRuntimeInstalled()?getString(R.string.account_card_failed):getString(R.string.account_runtime_missing);}
            final String n=name,d=detail,m=currentModel,ef=currentEffort;runOnUiThread(()->{if(isDestroyed()||accountTitle!=title)return;title.setText(n);summary.setText(d);if(modelSummary!=null)modelSummary.setText(m.isEmpty()?getString(R.string.account_read_unavailable):m);if(effortSummary!=null)effortSummary.setText(ef.isEmpty()?getString(R.string.account_read_unavailable):ef);});
        });
    }
    private String effortName(String effort){switch(effort){case "":return getString(R.string.account_effort_auto);case "none":return getString(R.string.account_effort_none);case "minimal":return getString(R.string.account_effort_minimal);case "low":return getString(R.string.account_effort_low);case "medium":return getString(R.string.account_effort_medium);case "high":return getString(R.string.account_effort_high);case "xhigh":return getString(R.string.account_effort_xhigh);case "max":return getString(R.string.account_effort_max);case "ultra":return getString(R.string.account_effort_ultra);default:return effort;}}
    private void selectReasoning(android.view.View row){row.setEnabled(false);accountWorker.execute(()->{try{
        JSONObject s=accountClient.call(new JSONObject().put("op","accounts/reasoning"));
        runOnUiThread(()->{if(isDestroyed())return;row.setEnabled(true);org.json.JSONArray options=s.optJSONArray("options");int n=options==null?0:options.length();String[] ids=new String[n+1],labels=new String[n+1];ids[0]="";labels[0]=getString(R.string.account_effort_auto)+(s.optString("defaultEffort").isEmpty()?"":" · "+effortName(s.optString("defaultEffort")));int checked=0;
            for(int i=0;i<n;i++){ids[i+1]=options.optJSONObject(i).optString("reasoningEffort");labels[i+1]=effortName(ids[i+1])+" · "+ids[i+1];if(ids[i+1].equals(s.optString("effort")))checked=i+1;}
            if(n==0){new android.app.AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(getString(R.string.account_effort_title))).setMessage(os.aios.core.DisplayText.clean(getString(R.string.account_effort_empty))).setPositiveButton(os.aios.core.DisplayText.clean(getString(R.string.account_ok)),null).show();return;}
            new android.app.AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(getString(R.string.account_effort_next))).setSingleChoiceItems(os.aios.core.DisplayText.clean(labels),checked,(d,w)->{d.dismiss();row.setEnabled(false);accountWorker.execute(()->{try{
                JSONObject result=accountClient.call(new JSONObject().put("op","accounts/reasoning/save").put("params",new JSONObject().put("provider",s.optString("provider")).put("model",s.optString("model")).put("effort",ids[w])));
                runOnUiThread(()->{if(!isDestroyed()){row.setEnabled(true);updateModelSummary(result);android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean(getString(R.string.account_effort_saved)),0).show();}});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){row.setEnabled(true);android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean(getString(R.string.account_retry_save)),1).show();}});}});}).setNegativeButton(os.aios.core.DisplayText.clean(getString(R.string.account_cancel)),null).show();
        });
    }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){row.setEnabled(true);android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean(getString(R.string.account_effort_read_failed)),1).show();}});}});}
    private void updateModelSummary(JSONObject result){if(modelSummary!=null)modelSummary.setText(result.optString("model","").replaceAll("^$",getString(R.string.account_model_auto)));if(effortSummary!=null)effortSummary.setText(effortName(result.optString("effort")));}
    private void selectModel(android.view.View row){
        row.setEnabled(false);if(modelSummary!=null)modelSummary.setText("正在读取模型…");accountWorker.execute(()->{try{
            JSONObject s=accountClient.call(new JSONObject().put("op","accounts/models"));
            runOnUiThread(()->{if(isDestroyed())return;row.setEnabled(true);if(modelSummary!=null)modelSummary.setText(s.optString("model","").replaceAll("^$","自动（服务默认）"));
                if(s.optBoolean("manual")){
                    android.widget.EditText field=AiosUi.field(this,"模型 ID",s.optString("model"));field.setSingleLine(true);os.aios.core.DisplayText.editable(field);
                    new android.app.AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("设置模型 ID")).setMessage(os.aios.core.DisplayText.clean("填写当前服务支持的模型 ID，下次请求生效。")).setView(AiosUi.dialogContent(this,field)).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("保存"),(d,w)->saveModel(s.optString("provider"),field.getText().toString(),row)).show();
                }else{
                    org.json.JSONArray items=s.optJSONArray("items");int n=items==null?0:items.length();String[] labels=new String[n+1],ids=new String[n+1];labels[0]="自动（服务默认）";ids[0]="";int checked=0;
                    for(int i=0;i<n;i++){JSONObject m=items.optJSONObject(i);ids[i+1]=m.optString("model");labels[i+1]=m.optString("displayName",ids[i+1])+" · "+ids[i+1];if(ids[i+1].equals(s.optString("model")))checked=i+1;}
                    new android.app.AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("选择模型 · 下次发送生效")).setSingleChoiceItems(os.aios.core.DisplayText.clean(labels),checked,(d,w)->{d.dismiss();saveModel(s.optString("provider"),ids[w],row);}).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();
                }
            });
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){row.setEnabled(true);if(modelSummary!=null)modelSummary.setText("无法读取模型");android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean("无法读取模型，请稍后重试"),1).show();}});}});
    }
    private void saveModel(String provider,String model,android.view.View row){row.setEnabled(false);accountWorker.execute(()->{try{
        JSONObject result=accountClient.call(new JSONObject().put("op","accounts/model").put("params",new JSONObject().put("provider",provider).put("model",model)));
        runOnUiThread(()->{if(!isDestroyed()){row.setEnabled(true);updateModelSummary(result);android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean("模型已保存，下次发送生效"),0).show();}});
    }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){row.setEnabled(true);android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean("保存失败："+e.getMessage()),1).show();}});}});}
    @Override protected void onDestroy(){if(accountWorker!=null)accountWorker.shutdownNow();if(accountClient!=null)accountClient.close();super.onDestroy();}
    /** Summary TextView of a settings row built by AiosUi.settingRow (second line of the texts column). */
    private static TextView summaryOf(LinearLayout row){for(int i=0;i<row.getChildCount();i++){if(row.getChildAt(i) instanceof LinearLayout){LinearLayout texts=(LinearLayout)row.getChildAt(i);if(texts.getChildCount()>1&&texts.getChildAt(1) instanceof TextView)return (TextView)texts.getChildAt(1);}}return null;}
    @Override protected void addHostConfiguration(LinearLayout panel) {
        DeviceHost.ui().addSetupSettings(this,panel);
        addAccountCard(panel);
        settingRow(panel,getString(R.string.share_target),ShareNotes.label(this),()->ShareNotes.choose(this,this::refreshConversation));
        section(panel,"助手");
        final LinearLayout[] model=new LinearLayout[1],effort=new LinearLayout[1];
        model[0]=settingRow(panel,"model",getString(R.string.account_model_label),getString(R.string.account_reading),null,true,false,()->selectModel(model[0]));modelSummary=summaryOf(model[0]);
        effort[0]=settingRow(panel,"tune",getString(R.string.account_effort_title),getString(R.string.account_reading),null,false,false,()->selectReasoning(effort[0]));effortSummary=summaryOf(effort[0]);
        NotificationHooks notificationSettings=AgentFeatureBindings.notifications();if(notificationSettings!=null)notificationSettings.settings(this,panel);
        DeviceHost.ui().addOptionalAppsSettings(this,panel);
        settingRow(panel,"model",getString(R.string.models_title),getString(R.string.models_description),null,false,false,()->startActivity(new Intent(this,ModelManagementActivity.class)));
        DreamPanel.settingsRows(this,panel);
        settingRow(panel,"assistant","小助手","打开半屏操作助手",null,false,true,()->startActivity(new Intent(this,AssistantActivity.class)));
        section(panel,"设备");
        settingRow(panel,"shield","设备控制与授权","总授权与后台虚拟屏",null,true,false,()->startActivity(new Intent(this,ControlActivity.class)));
        settingRow(panel,"touch","前台操作授权","在系统无障碍设置中管理",null,false,true,()->{
            Intent settings=new Intent(DeviceHost.ui().accessibilitySettingsAction());
            settings.putExtra(Intent.EXTRA_COMPONENT_NAME,new android.content.ComponentName(this,ForegroundUiService.class).flattenToString());
            try{startActivity(settings);}catch(android.content.ActivityNotFoundException|SecurityException e){
                try{startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));}
                catch(android.content.ActivityNotFoundException|SecurityException unavailable){android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean("请在系统设置 → 无障碍中开启 AIOS 前台控制"),android.widget.Toast.LENGTH_LONG).show();}
            }
        });
    }
    @Override protected void newAssistantSession() throws Exception {
        AssistantConversation.fresh(this);
        startActivity(new Intent(this,AssistantInputActivity.class));
    }
    @Override protected void startAssistantTask(JSONObject task) throws Exception {
        AssistantConversation.enqueueTask(this,task.getString("id"));
        startForegroundService(new Intent(this,AgentService.class));
        startActivity(new Intent(this,AssistantInputActivity.class));
    }
    @Override protected void continueCodexSession(JSONObject session) throws Exception {
        AssistantConversation.selectThread(this,session.getString("source_id"));
        startForegroundService(new Intent(this,AgentService.class));
        startActivity(new Intent(this,AssistantInputActivity.class));
    }
    @Override protected void onMessageSaved(JSONObject message) throws Exception {
        EventStore.add("workspace.message",new JSONObject().put("session_id",message.getString("session_id")).put("message_id",message.getString("id")));
    }
}
