package os.aios.agent;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import os.aios.device.ScreenBackend;
import org.json.JSONObject;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** Full-screen, system-composited interactive portal into one persistent virtual-screen slot. */
public class UiViewerActivity extends Activity {
    private static java.lang.ref.WeakReference<UiViewerActivity> front=new java.lang.ref.WeakReference<>(null);
    static String visibleSession(){UiViewerActivity v=front.get();return v==null||v.isFinishing()?"":v.taskId;}
    @Override protected void onStart(){super.onStart();front=new java.lang.ref.WeakReference<>(this);}
    @Override protected void onStop(){if(front.get()==this)front.clear();super.onStop();}
    final Handler handler=new Handler(Looper.getMainLooper());
    final java.util.concurrent.ExecutorService work=java.util.concurrent.Executors.newSingleThreadExecutor();
    final Runnable refresh=this::refreshMirror;
    String interventionId="";
    ScreenBackend.Preview mirror;TextView status,control;String taskId="";boolean visible,bound,mirrorAttached;
    ScreenBackend.Session platformSession;

    public void onCreate(Bundle saved){
        super.onCreate(saved);
        AiosTheme.window(this);
        taskId=getIntent().getStringExtra("session_id");
        interventionId=getIntent().getStringExtra("intervention_id");if(interventionId==null)interventionId="";
        if(!interventionId.isEmpty())try{JSONObject row=AgentService.instance.interventions.store.get(interventionId);AgentService.instance.interventions.requireCurrent(row);if(!taskId.equals(row.getJSONObject("arguments").getString("ui_session_id"))||!row.getString("state").equals("handoff_active"))throw new SecurityException("Invalid handoff");}catch(Exception e){finish();return;}
        AgentService agent=AgentService.instance;
        if(taskId==null)taskId=agent==null||agent.ui.only()==null?"":agent.ui.only().taskId;
        // Compact toolbar on surface_container_low; the mirror itself keeps the real screen colours.
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(AiosTheme.background(this));
        root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(4),dp(4),dp(8),dp(4));top.setMinimumHeight(dp(64));top.setBackgroundColor(AiosTheme.surfaceLow(this));
        top.addView(AiosUi.iconButton(this,"back",UiViewerActivity.this.getString(R.string.assistant_exit_virtual_screen_70020),this::finish));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(4),0,dp(8),0);TextView title=AiosUi.oneLine(AiosUi.body(this,UiViewerActivity.this.getString(R.string.assistant_virtual_screens_e10a1)));title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));labels.addView(title);
        status=AiosUi.oneLine(AiosUi.meta(this,""));labels.addView(status);top.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(AiosUi.iconButton(this,"keyboard",UiViewerActivity.this.getString(R.string.assistant_type_text_ae47a),this::showTextInput));
        top.addView(AiosUi.iconButton(this,"assistant",UiViewerActivity.this.getString(R.string.assistant_assistant_d78a1),this::openAssistant));
        control=AiosUi.tonalButton(this,UiViewerActivity.this.getString(R.string.assistant_take_control_fc16a),this::toggleUserControl);LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-2,dp(40));cl.setMarginStart(dp(4));top.addView(control,cl);
        root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        mirror=DeviceHost.screens(this).preview(this);mirror.setInteractive(false);mirror.view().setContentDescription(os.aios.core.DisplayText.clean(UiViewerActivity.this.getString(R.string.assistant_live_virtual_screen_341a8)));
        root.addView(mirror.view(),new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=AiosUi.meta(this,interventionId.isEmpty()?UiViewerActivity.this.getString(R.string.assistant_live_screen_keeps_running_in_the_background_when_you_leave_a6c61):UiViewerActivity.this.getString(R.string.assistant_user_intervention_stays_paused_until_you_return_control_6888e));hint.setGravity(Gravity.CENTER);hint.setPadding(dp(16),dp(10),dp(16),dp(10));hint.setBackgroundColor(AiosTheme.surfaceLow(this));root.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    void openAssistant(){try{markUserInteraction();startForegroundService(new Intent(this,AssistantOrbService.class).setAction("show").putExtra("ui_session_id",taskId));}catch(Exception e){Toast.makeText(this,os.aios.core.DisplayText.clean(UiViewerActivity.this.getString(R.string.assistant_assistant_unavailable_52594)),Toast.LENGTH_SHORT).show();}}
    void showTextInput(){
        EditText input=AiosUi.area(this,UiViewerActivity.this.getString(R.string.assistant_type_into_the_focused_field_on_the_virtual_screen_ee9d2),null,2);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(UiViewerActivity.this.getString(R.string.assistant_type_text_ae47a))).setView(AiosUi.dialogContent(this,input)).setNegativeButton(os.aios.core.DisplayText.clean(UiViewerActivity.this.getString(R.string.assistant_cancel_4d0b4)),null).setPositiveButton(os.aios.core.DisplayText.clean(UiViewerActivity.this.getString(R.string.assistant_send_1214d)),null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=input.getText().toString();if(text.isEmpty())return;try{userOperation(new JSONObject().put("op","text").put("text",text).put("replace",false));dialog.dismiss();}catch(Exception ignored){}}));
        dialog.show();input.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    void userOperation(JSONObject q){work.execute(()->{try{AgentService a=AgentService.instance;UiSessionController ui=a==null?null:a.ui.find(taskId);if(ui==null)throw new IllegalStateException();JSONObject result=ui.callUserOperation(q);if(!result.optBoolean("executed"))throw new IllegalStateException(result.optString("state"));handler.post(()->status.setText(UiViewerActivity.this.getString(R.string.assistant_you_have_control_assistant_will_read_the_screen_again_before_cont_804b2)));}catch(Exception e){handler.post(()->Toast.makeText(this,os.aios.core.DisplayText.clean(UiViewerActivity.this.getString(R.string.assistant_virtual_screen_operation_incomplete_182ec)),Toast.LENGTH_SHORT).show());}});}
    void markUserInteraction(){AgentService a=AgentService.instance;UiSessionController ui=a==null?null:a.ui.find(taskId);if(ui!=null)ui.noteMirrorInteraction();}
    void toggleUserControl(){AgentService a=AgentService.instance;UiSessionController ui=a==null?null:a.ui.find(taskId);if(ui==null)return;if(!interventionId.isEmpty()){try{a.interventions.returnHandoff(interventionId,taskId);ui.exitMirrorUserControl();finish();}catch(Exception e){Toast.makeText(this,os.aios.core.DisplayText.clean(e.getMessage()),Toast.LENGTH_LONG).show();}}else if(ui.mirrorUserControl)ui.exitMirrorUserControl();else ui.enterMirrorUserControl();handler.postDelayed(refresh,100);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&platformSession!=null){AgentService a=AgentService.instance;UiSessionController ui=a==null?null:a.ui.find(taskId);if(ui!=null&&ui.mirrorUserControl)markUserInteraction();}return super.dispatchTouchEvent(event);}
    protected void onResume(){super.onResume();visible=true;markUserInteraction();handler.post(refresh);}
    protected void onPause(){visible=false;handler.removeCallbacks(refresh);detachMirror();if(interventionId.isEmpty()&&!isChangingConfigurations())setMirrorUserControl(false);markUserInteraction();super.onPause();}
    protected void onDestroy(){detachMirror();work.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){finish();}
    void detachMirror(){if(mirrorAttached){mirror.detach();mirrorAttached=false;}platformSession=null;}
    void setMirrorUserControl(boolean entered){AgentService a=AgentService.instance;UiSessionController ui=a==null?null:a.ui.find(taskId);if(ui!=null){if(entered)ui.enterMirrorUserControl();else ui.exitMirrorUserControl();}}
    void refreshMirror(){
        if(!visible)return;AgentService a=AgentService.instance;UiSessionController ui=a==null?null:a.ui.find(taskId);
        if(ui==null){detachMirror();status.setText(UiViewerActivity.this.getString(R.string.assistant_screen_slot_closed_a72ca));return;}
        boolean ready=(interventionId.isEmpty()||!ui.mirrorUserControl||!ui.nativeActive)&&Authorization.enabled(this)&&ui.session!=null&&ui.state.equals("active")&&(ui.nativeActive||ui.mirrorUserControl)&&ui.displayId>0;
        if(!ready){detachMirror();status.setText(ui.taskName+" · "+ui.detail);handler.postDelayed(refresh,400);return;}
        if(platformSession!=ui.session||!mirrorAttached){detachMirror();platformSession=ui.session;mirror.attach(platformSession);mirrorAttached=true;}
        mirror.setInteractive(ui.mirrorUserControl);control.setText(!interventionId.isEmpty()?UiViewerActivity.this.getString(R.string.assistant_done_return_to_assistant_721e8):ui.mirrorUserControl?UiViewerActivity.this.getString(R.string.assistant_return_to_assistant_406e5):UiViewerActivity.this.getString(R.string.assistant_take_control_fc16a));
        status.setText(ui.taskName+UiViewerActivity.this.getString(R.string.assistant_screen_6f3f5)+ui.displayId+(ui.mirrorUserControl?UiViewerActivity.this.getString(R.string.assistant_interactive_4f83e):UiViewerActivity.this.getString(R.string.assistant_live_view_4bae1)));handler.postDelayed(refresh,750);
    }
}
