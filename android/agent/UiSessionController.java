package os.aios.agent;
import os.aios.workspace.ExecutionState;

import android.app.*;
import os.aios.device.ScreenBackend;
import android.content.*;
import android.os.*;
import android.view.accessibility.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** The single arbiter for every AIOS UI entry point. Native callbacks and input share one queue. */
final class UiSessionController {
    final AgentService service;
    final UiSessionManager manager;
    long queueOrder;
    final Handler handler = new Handler(Looper.getMainLooper());
    final SharedPreferences saved;
    String state="ready", detail="没有界面任务", taskId="", target="", taskName="", observation="";
    String promptId="";
    int epoch, displayId=-1;
    boolean nativeActive, initialBlockSeen, destroyed, permissionHandoff, permissionTaskMoved, mirrorUserControl;
    long backgroundSince, promptSince, permissionSince, observedAt, pausedSince, ownerStoppedSince, lastUserInteraction, ownerCheckedAt;boolean ownerStopped;
    int blockReason=-1;String blockingPackage="",packagesTarget="";List<String> controlPackages=Collections.emptyList();
    UiObservationFrame observationFrame;
    boolean permissionSeen, permissionPresent, permissionVisible;
    String permissionPhase="none";
    boolean anchorReady;
    String anchorToken="";
    ScreenBackend.Session session;
    String creationRequest="";
    final ScreenBackend screenBackend;
    final os.aios.device.DeviceTasks deviceTasks;
    Intent pendingIntent;
    IntentSender systemConsent;
    ResultReceiver promptReply, handoverReply;
    final Runnable ticker=this::tick;

    UiSessionController(AgentService service, UiSessionManager manager, String id) {
        this.service=service; this.manager=manager; screenBackend=DeviceHost.screens(service);deviceTasks=DeviceHost.tasks(service);saved=service.getSharedPreferences("ui_session_"+id,0);
        taskId=id;
        taskId=saved.getString("task_id",id); target=saved.getString("package","");
        taskName=saved.getString("task_name","");
        creationRequest=saved.getString("creation_request","");
        permissionHandoff=false; // Live task observation, never a persisted promise that a dialog exists.
        if(saved.getBoolean("unfinished",false)) set("paused",permissionHandoff?"请在手机主屏处理应用权限；处理后返回 AIOS Control 继续任务":"服务已重启；任务暂停，继续后需重新读取页面");
        handler.postDelayed(ticker,400);
    }
    JSONObject call(JSONObject q) throws Exception {
        if(Looper.myLooper()==handler.getLooper()) return dispatch(q);
        FutureTask<JSONObject> job=new FutureTask<>(()->dispatch(q)); handler.post(job);
        try{return job.get(15,TimeUnit.SECONDS);}catch(TimeoutException e){job.cancel(false);throw e;}
        catch(ExecutionException e){throw new IllegalStateException(e.getCause());}
    }
    void set(String next,String message) {
        if(next.equals("paused")){if(!state.equals("paused")||pausedSince==0)pausedSince=SystemClock.elapsedRealtime();}else pausedSince=0;
        state=next;detail=message;
        saved.edit().putString("task_id",taskId).putString("package",target).putString("task_name",taskName).putString("creation_request",creationRequest)
                .putBoolean("permission_handoff",permissionHandoff).putBoolean("unfinished",!next.equals("ready")&&!next.equals("closed")).apply();
    }
    JSONObject status() throws Exception {
        JSONObject r=new JSONObject().put("backend",screenBackend.implementationId())
                .put("mode","background").put("coordinate_space","display_pixels").put("bridge_protocol",4).put("session_id",taskId).put("state",state).put("detail",detail).put("task_id",taskId)
                .put("user_state",userState()).put("block_reason",blockReason).put("blocking_package",blockingPackage).put("next_step",blockReason>=0?"User takeover is required for protected pages; resume rechecks system policy. The screen is released after five stopped minutes.":"")
                .put("package",target).put("task_name",taskName).put("display_id",displayId)
                .put("requires_observation",observation.isEmpty()).put("generation",epoch)
                .put("foreground",foreground()).put("max_concurrent_ui_tasks",UiSessionManager.MAX_ACTIVE).put("queue_position",manager.queuePosition(this)).put("waiting_for_session",manager.blockingOwner(this)).put("waiting_for_user_permission",permissionPresent).put("permission_dialog_visible",permissionVisible)
                .put("permission_handoff_state",permissionPhase).put("permission_context_retained",permissionHandoff && session!=null);
        JSONObject bound=AssistantConversation.uiOwner(service,taskId);r.put("conversation_id",bound.optString("conversation_id")).put("thread_id",bound.optString("thread_id"));
        if(session==null&&!creationRequest.isEmpty())r.put("release_state",screenBackend.releaseState(creationRequest,displayId).name().toLowerCase(java.util.Locale.ROOT));
        if(session!=null)r.put("width",session.getDisplaySize().getWidth()).put("height",session.getDisplaySize().getHeight());
        return r;
    }
    JSONObject callUserOperation(JSONObject q)throws Exception {
        if(Looper.myLooper()==handler.getLooper())return userOperation(q);
        FutureTask<JSONObject> job=new FutureTask<>(()->userOperation(q));handler.post(job);
        try{return job.get(15,TimeUnit.SECONDS);}catch(TimeoutException e){job.cancel(false);throw e;}
        catch(ExecutionException e){throw new IllegalStateException(e.getCause());}
    }
    void noteMirrorInteraction(){handler.post(()->{lastUserInteraction=SystemClock.elapsedRealtime();ownerStoppedSince=0;if(state.equals("active")){invalidate();detail="用户正在实时操作；助手继续前需重新读取页面";}});}
    void enterMirrorUserControl(){handler.post(()->{
        if(session==null||!state.equals("active"))return;
        try{if(service.scheduledAssistants!=null){JSONObject grant=service.scheduledAssistants.internal("screen.takeover",new JSONObject().put("session_id",taskId));if(!grant.optBoolean("allowed")){android.widget.Toast.makeText(service,os.aios.core.DisplayText.clean(grant.optString("reason","等待共享屏幕资源")),android.widget.Toast.LENGTH_LONG).show();return;}}}catch(Exception e){android.util.Log.e("AIOSSchedule","Takeover refused",e);return;}
        mirrorUserControl=true;invalidate();set("active","用户正在实时操作；助手继续前需重新读取页面");
        try{session.notifyBlocked();}catch(Exception e){mirrorUserControl=false;fail(e);}
    });}
    void exitMirrorUserControl(){handler.post(()->{
        try{if(service.interventions!=null&&service.interventions.waiting(AssistantConversation.uiOwner(service,taskId).optString("thread_id")))return;}catch(Exception unavailable){return;}
        if(!mirrorUserControl)return;
        mirrorUserControl=false;invalidate();
        if(session!=null&&!state.equals("closed")){
            set("active","正在把虚拟屏控制权交回助手");
            try{session.requestUnblock();}catch(Exception e){fail(e);}
        }
    });}
    String userState(){
        return ExecutionState.fromInternal(state);
    }
    JSONObject blocked(String reason) throws Exception {
        return status().put("state",reason).put("session_state",state).put("executed",false)
                .put("next_step",UiScreenPolicy.recovery(reason));
    }
    List<String> controlPackages(){if(!target.equals(packagesTarget)){controlPackages=targetPackages(service,target);packagesTarget=target;}return controlPackages;}
    boolean controlsPackage(String pkg){return target.equals(pkg)||(!pkg.equals("os.aios.permissionanchor")&&controlPackages().contains(pkg));}
    boolean foreground() {
        if(target.isEmpty())return false;
        try {
            for(os.aios.device.DeviceTasks.Task t:deviceTasks.tasks())
                if(t.displayId==0 && t.visible && controlsPackage(t.topPackage)) return true;
            return false;
        }catch(RuntimeException e){return true;}
    }
    void invalidate(){observation="";observationFrame=null;}
    void release(String next,String message) {
        int releasedDisplay=displayId;
        ++epoch;systemConsent=null;invalidate();nativeActive=false;displayId=-1;pendingIntent=null;permissionHandoff=false;permissionTaskMoved=false;
        permissionPhase="none";permissionSeen=false;permissionPresent=false;permissionVisible=false;
        finishPermissionAnchor();
        ScreenBackend.Session old=session;session=null;
        set(next,message);if(next.equals("closed")){ScheduledScreenRelease.after(service,taskId,releasedDisplay,creationRequest,creationRequest.isEmpty());AssistantConversation.releaseUiSession(service,taskId);}if(old!=null)old.close();
        if(next.equals("closed")&&!releaseConfirmed())detail=service.getString(R.string.screen_release_pending);
        reply(promptReply,false);promptReply=null;reply(handoverReply,false);handoverReply=null;promptId="";
    }
    JSONObject dispatch(JSONObject q) throws Exception {
        String op=q.getString("op");
        if(op.equals("status")){refreshPermissionState();return status();}
        if(!java.util.Set.of("close","pause","handover").contains(op)){
            JSONObject unavailable=AgentCapabilities.guard(service,"screen.virtual."+op);
            if(unavailable!=null){invalidate();return unavailable.put("session_id",taskId);}
        }
        if(!Authorization.enabled(service)&&!op.equals("close"))return Authorization.denied();
        if(op.equals("create")) {
            if(!state.equals("ready")&&!state.equals("closed"))return blocked("session_busy");
            String pkg=q.optString("package","com.android.settings");
            if(!pkg.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))throw new IllegalArgumentException("Invalid package");
            service.getPackageManager().getApplicationInfo(pkg,0);
            target=pkg;queueOrder=manager.nextOrder();
            taskName=q.optString("task_name","AIOS 界面任务");
            backgroundSince=0;invalidate();set("waiting_for_app","等待目标应用进入后台，再申请控制权");
            return status().put("executed",false);
        }
        if(op.equals("close")){
            refreshPermissionState();
            if(permissionHandoff && (permissionPresent||permissionPhase.equals("opening")))return blocked("permission_pending");
            release("closed","界面任务已结束");return status();
        }
        if(op.equals("pause")){
            if(permissionHandoff){set("paused","任务已暂停，权限设备上下文保留；请先处理主屏权限页");refreshPermissionState();return status();}
            release("paused","任务已暂停，已完成的操作保留");return status();
        }
        if(op.equals("resume")) {
            refreshPermissionState();
            if(!state.equals("paused")||target.isEmpty()||(session!=null&&!permissionHandoff&&blockReason<0))return blocked("invalid_transition");
            if(permissionPresent || (permissionHandoff&&!permissionTaskMoved))return blocked("permission_pending");
            if(session!=null&&!permissionHandoff&&blockReason>=0){set("resuming",service.getString(R.string.background_resuming));session.requestUnblock();return status();}
            queueOrder=manager.nextOrder();backgroundSince=0;invalidate();set("waiting_for_app","等待应用进入后台；恢复后重新读取当前页面");
            return status();
        }
        if(op.equals("handover")) {
            if(session==null || !(state.equals("active")||state.equals("paused")))return blocked("session_unavailable");
            manager.reserveMain(target);invalidate();set("paused","已请求交还应用，任务保持暂停");session.handOverApplications();return status();
        }
        if(displayId<=0)return blocked("session_unavailable");
        if(!state.equals("active")||session==null||!nativeActive)return blocked("session_unavailable");
        if(foreground()){release("paused","用户正在前台使用应用，任务已暂停");return blocked("foreground_owned");}
        boolean input=Arrays.asList("tap","double_tap","long_press","swipe","gesture","text","back").contains(op);
        if(input && (observation.isEmpty() || !observation.equals(UiObservation.supplied(q.optString("observation_id"),q.optString("observation_token"),q.optString("observation")))))return blocked("observation_required");
        if(input){
            if(observationFrame==null||SystemClock.elapsedRealtime()-observedAt>60000)return blocked("observation_required");
            q=observationFrame.input(q);validateInput(q);invalidate();
        }
        if(op.equals("launch"))invalidate();
        JSONObject result=service.nativeOperation(this,q);
        if(op.equals("tree")||op.equals("screenshot")) {
            if(result.optString("state").equals("observation_unavailable"))return result;
            if(op.equals("tree") && result.getJSONArray("windows").length()==0)return blocked("observation_unavailable");
            observationFrame=new UiObservationFrame(result);observedAt=SystemClock.elapsedRealtime();
            observation=UUID.randomUUID().toString();result.put("observation_id",observation).put("observation_token",observation).put("requires_observation",false);
        }
        return result.put("executed",true);
    }
    void validateInput(JSONObject q) throws Exception {
        String op=q.getString("op");
        if(op.equals("text")&&q.getString("text").length()>10000)throw new IllegalArgumentException("Text too long");
        if(op.equals("tap")||op.equals("long_press")||op.equals("double_tap")){point(q.getInt("x"),q.getInt("y"));if(op.equals("double_tap")){strict(q,Set.of("op","session_id","observation_id","observation_token","observation","x","y","interval_ms"));int interval=q.optInt("interval_ms",120);if(interval<40||interval>300)throw new IllegalArgumentException("Double tap interval must be 40-300ms");}}
        if(op.equals("swipe")){strict(q,Set.of("op","session_id","observation_id","observation_token","observation","x1","y1","x2","y2","duration_ms"));point(q.getInt("x1"),q.getInt("y1"));point(q.getInt("x2"),q.getInt("y2"));int duration=q.optInt("duration_ms",400);if(duration<100||duration>5000)throw new IllegalArgumentException("Swipe duration must be 100-5000ms");}
        if(op.equals("gesture"))UiGestureSpec.parse(q,session.getDisplaySize().getWidth(),session.getDisplaySize().getHeight());
    }
    JSONObject userOperation(JSONObject q)throws Exception {
        if(displayId<=0)return blocked("session_unavailable");
        if(!state.equals("active")||session==null||!nativeActive)return blocked("session_unavailable");
        if(foreground()){release("paused","应用已离开虚拟屏，任务暂停");return blocked("foreground_owned");}
        String op=q.getString("op");
        if(!Arrays.asList("tap","double_tap","long_press","swipe","gesture","text","back").contains(op))throw new IllegalArgumentException("Unsupported user operation");
        validateInput(q);invalidate();ownerStoppedSince=0;detail="用户正在操作；助手继续前需重新读取页面";
        return service.nativeOperation(this,q).put("executed",true).put("user_input",true);
    }
    void point(int x,int y){if(x<0||y<0||x>=session.getDisplaySize().getWidth()||y>=session.getDisplaySize().getHeight())throw new IllegalArgumentException("Coordinates outside virtual display");}
    static void strict(JSONObject q,Set<String> allowed)throws Exception{for(Iterator<String> it=q.keys();it.hasNext();){String key=it.next();if(!allowed.contains(key))throw new IllegalArgumentException("Unknown input field: "+key);}}

    JSONObject open(Intent intent) throws Exception {
        if(Looper.myLooper()!=handler.getLooper()) {
            FutureTask<JSONObject> job=new FutureTask<>(()->open(intent));handler.post(job);
            try{return job.get(15,TimeUnit.SECONDS);}catch(TimeoutException e){job.cancel(false);throw e;}
        }
        if(!Authorization.enabled(service))return Authorization.denied();
        ComponentName component=intent.resolveActivity(service.getPackageManager());
        if(component==null)return new JSONObject().put("state","not_supported");
        String pkg=component.getPackageName();
        if(state.equals("ready")||state.equals("closed")){
            dispatch(new JSONObject().put("op","create").put("package",pkg));
            pendingIntent=new Intent(intent);return status().put("completed_operation_verified",false);
        }
        if(!target.equals(pkg))return blocked("session_busy");
        if(!state.equals("active")||foreground())return blocked("session_unavailable");
        launchIntent(intent);return status().put("state","handed_to_app").put("completed_operation_verified",false);
    }
    void launchIntent(Intent intent) {
        if(displayId<=0||session==null||!nativeActive||foreground())throw new IllegalStateException("No exclusive virtual display");
        invalidate();deviceTasks.launch(intent,displayId);
    }
    void createNative() {
        if(!manager.canAcquire(this))return;
        // Never replace an unconfirmed creation/close attempt, even if its callback was invalidated.
        if(!creationRequest.isEmpty()&&screenBackend.releaseState(creationRequest,-1)!=ScreenBackend.ReleaseState.RELEASED)return;
        creationRequest=UUID.randomUUID().toString();
        if(!saved.edit().putString("creation_request",creationRequest).commit())throw new IllegalStateException("screen_creation_checkpoint_failed");
        set("creating","正在创建应用专属虚拟屏");int lease=++epoch;
        screenBackend.create(creationRequest,taskName+" · "+taskId.substring(0,8),controlPackages(),service.getMainExecutor(),new ScreenBackend.Creation(){
            public void onSessionPending(IntentSender sender){
                if(lease!=epoch||destroyed)return;
                systemConsent=sender;set("needs_user_consent",service.getString(R.string.approval_system_reason));
                try{if(AssistantConversation.uiOwner(service,taskId).optString("thread_id").isEmpty())openSystemConsent();}catch(Exception e){fail(e);}
            }
            public void onSessionCreationFailed(int code){if(lease==epoch){systemConsent=null;completeSystemConsent(false);release("paused","创建虚拟屏失败："+code);}}
            public void onSessionCreated(ScreenBackend.Session value){
                if(lease!=epoch||destroyed){value.close();return;}
                systemConsent=null;session=value;completeSystemConsent(true);initialBlockSeen=false;nativeActive=false;
                set("preparing","连接虚拟屏，准备重新读取页面");
                value.setLifecycleCallback(service.getMainExecutor(),new ScreenBackend.Lifecycle(){
                    public void onActive(){
                        if(lease!=epoch||session!=value)return;nativeActive=true;blockReason=-1;blockingPackage="";
                        handler.postDelayed(()->{
                            if(lease!=epoch||session!=value||!nativeActive)return;
                            try{
                                restoreProxy(value);
                                if((state.equals("preparing")&&initialBlockSeen)||state.equals("resuming")){
                                    if(foreground()){release("paused","应用已回到前台，任务暂停");return;}
                                    if(state.equals("resuming") && permissionHandoff && !restorePermissionTask())return;
                                    if(state.equals("preparing")){
                                        if(pendingIntent!=null){Intent i=pendingIntent;pendingIntent=null;launchIntent(i);}
                                        else value.launchApplication(target);
                                    }
                                    invalidate();set("active","后台控制中；执行下一步前请读取当前页面");
                                }
                            }catch(Exception e){fail(e);}
                        },800);
                    }
                    public void onBlocked(int reason,String pkg){
                        if(lease!=epoch||session!=value)return;nativeActive=false;invalidate();blockReason=reason;blockingPackage=pkg==null?"":pkg;
                        android.util.Log.w("AIOS_UI_SESSION","Native blocked session="+taskId+" reason="+reason+" package="+blockingPackage+" state="+state);
                        if(reason==ScreenBackend.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH
                                && pkg!=null && pkg.equals(deviceTasks.permissionControllerPackage())) {
                            if(permissionHandoff)return;
                            permissionHandoff=true;permissionTaskMoved=false;permissionSeen=false;
                            permissionPresent=false;permissionVisible=false;permissionPhase="opening";
                            permissionSince=SystemClock.elapsedRealtime();pendingIntent=null;
                            set("paused","检测到权限请求，正在交回手机主屏；尚未确认弹窗显示");
                            permissionPhase="waiting_user";
                            set("paused","应用需要系统权限，等待用户点击授权入口");
                        }
                        else if(mirrorUserControl && reason==ScreenBackend.BLOCK_REASON_CALLER_INITIATED){
                            set("active","用户正在实时操作；助手继续前需重新读取页面");
                        }
                        else if(state.equals("preparing") && reason==ScreenBackend.BLOCK_REASON_CALLER_INITIATED){initialBlockSeen=true;value.requestUnblock();}
                        else if(state.equals("confirming")){reply(promptReply,true);promptReply=null;}
                        else {int message=switch(reason){case ScreenBackend.BLOCK_REASON_SECURE_CONTENT->R.string.background_block_secure;case ScreenBackend.BLOCK_REASON_AUTHENTICATION_PROMPT_REQUESTED->R.string.background_block_auth;case ScreenBackend.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH->R.string.background_block_launch;default->R.string.background_block_other;};set("paused",service.getString(message,blockingPackage));}
                    }
                    public void onClosed(int reason){ended(lease,value);}
                });
                value.notifyBlocked();
            }
            public void onSessionClosed(){if(lease==epoch)ended(lease,session);}
        });
    }
    void openSystemConsent()throws Exception{
        if(systemConsent==null||!state.equals("needs_user_consent"))throw new IllegalStateException(service.getString(R.string.assistant_request_changed));
        service.startService(new Intent(service,AssistantOrbService.class).setAction("suspend"));
        service.startIntentSender(systemConsent,new Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),0,0,0);
    }
    void completeSystemConsent(boolean granted){
        try{if(service.interventions!=null)service.interventions.systemControlResult(taskId,granted);}catch(Exception e){android.util.Log.w("AIOSIntervention","System consent result retained",e);}
    }
    void startPermissionHandoff(){
        if(!permissionHandoff||session==null||!permissionPhase.equals("waiting_user"))return;
        final ScreenBackend.Session value=session;final int lease=epoch;
        permissionPhase="opening";permissionSince=SystemClock.elapsedRealtime();
        try {
                                value.notifyBlocked();
                                anchorToken=UUID.randomUUID().toString();anchorReady=false;
                                final String expectedAnchor=anchorToken;
                                ResultReceiver ready=new ResultReceiver(handler){protected void onReceiveResult(int code,Bundle data){
                                    if(lease==epoch && session==value && expectedAnchor.equals(anchorToken) && code==0)
                                        permissionAnchorReady(data.getInt("display_id",-1));
                                }};
                                Parcel readyParcel=Parcel.obtain();ready.writeToParcel(readyParcel,0);readyParcel.setDataPosition(0);
                                ResultReceiver remoteReady=ResultReceiver.CREATOR.createFromParcel(readyParcel);readyParcel.recycle();
                                deviceTasks.launch(new Intent().setComponent(new ComponentName("os.aios.permissionanchor","os.aios.permissionanchor.MainActivity"))
                                    .putExtra("token",anchorToken).putExtra("ready",remoteReady).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK),displayId);
        } catch(Exception e){fail(e);}
    }
    void movePermissionTaskToMain(ScreenBackend.Session value) throws Exception {
        // This image queues handOverApplications' transition before immediately closing its
        // display. Move the owned task synchronously so the permission result chain survives.
        if(displayId<=0 || session!=value)throw new IllegalStateException("No owned virtual display");
        manager.reserveMain(target);
        boolean moved=false;
        for(os.aios.device.DeviceTasks.Task task:deviceTasks.tasks()) {
            if(task.displayId==displayId && target.equals(task.basePackage)) {
                deviceTasks.move(task.id,0);moved=true;
            }
        }
        if(!moved)throw new IllegalStateException("Target task unavailable for permission handoff");
        permissionTaskMoved=true;permissionSince=SystemClock.elapsedRealtime();
        // Do not close: the permission intent refers to this device ID, and grants belong to it.
        refreshPermissionState();
    }
    void ended(int lease,ScreenBackend.Session value){
        if(lease!=epoch||(session!=null&&session!=value))return;
        int endedDisplay=displayId;++epoch;session=null;nativeActive=false;displayId=-1;invalidate();
        if(!state.equals("closed"))set("paused",permissionHandoff?"权限页面已交回手机主屏；请处理后返回 AIOS Control 继续任务":"控制权已释放；任务暂停，需手动继续");
        reply(handoverReply,true);handoverReply=null;promptId="";
        if(permissionHandoff) {
            permissionHandoff=false;permissionPresent=false;permissionVisible=false;
            permissionPhase="context_lost";
            set("paused","权限请求的设备上下文已结束；未确认权限完成，请重新打开应用检查");
            finishPermissionAnchor();
        }
        set("closed","虚拟屏已结束，原会话保留");AssistantConversation.releaseUiSession(service,taskId);ScheduledScreenRelease.after(service,taskId,endedDisplay,creationRequest,false);
        handler.post(()->{manager.removeClosed();manager.persist();});
    }
    void finishPermissionAnchor() {
        if(!anchorToken.isEmpty())service.sendBroadcast(new Intent("os.aios.permissionanchor.FINISH")
            .setPackage("os.aios.permissionanchor").putExtra("token",anchorToken),"os.aios.permission.CONTROL_SESSION");
        anchorToken="";anchorReady=false;
    }
    void permissionAnchorReady(int actualDisplay) {
        if(!permissionHandoff || session==null || actualDisplay!=displayId)return;
        anchorReady=true;
        if(!permissionTaskMoved)try{movePermissionTaskToMain(session);}catch(Exception e){fail(e);}
    }
    void refreshPermissionState() {
        if(!permissionHandoff||permissionPhase.equals("waiting_user"))return;
        try {
            boolean present=false,visible=false;
            String controller=deviceTasks.permissionControllerPackage();
            for(os.aios.device.DeviceTasks.Task task:deviceTasks.tasks()) {
                if(task.displayId==0 && target.equals(task.basePackage) && controller.equals(task.topPackage)) {
                    present=true;visible|=task.visible;
                }
            }
            permissionPresent=present;permissionVisible=visible;
            String phase=present?"awaiting_user":permissionSeen?"awaiting_resume":
                SystemClock.elapsedRealtime()-permissionSince>5000?"dialog_unavailable":"opening";
            if(present)permissionSeen=true;
            if(!phase.equals(permissionPhase)){
                permissionPhase=phase;
                if(state.equals("paused"))set("paused",present?"请在手机主屏处理权限；AI 已暂停":
                    permissionSeen?"权限页面已关闭；未推断允许或拒绝，返回 AIOS Control 后可继续任务":
                    "未检测到可处理的权限弹窗；AI 已暂停，请打开应用检查");
            }
        } catch(Exception e) {
            permissionPresent=false;permissionVisible=false;permissionPhase="observation_failed";
            android.util.Log.w("AIOS_UI_SESSION","Cannot observe permission dialog",e);
        }
    }
    void resumePermissionSession() throws Exception {
        refreshPermissionState();
        if(permissionPresent){set("paused","权限页面尚未关闭，请先处理权限");return;}
        if(session==null || !anchorReady || displayId<=0)throw new IllegalStateException("Permission context lost");
        // Wake the retained display with its holder first. Moving a task onto a sleeping
        // display can cache a missing top task and permanently block native screenshots.
        invalidate();set("resuming","唤醒原会话，准备恢复应用页面");session.requestUnblock();
    }
    boolean restorePermissionTask() throws Exception {
        refreshPermissionState();
        if(permissionPresent){set("paused","权限页面尚未关闭，请先处理权限");return false;}
        if(session==null || !anchorReady || displayId<=0)throw new IllegalStateException("Permission context lost");
        boolean moved=false;
        for(os.aios.device.DeviceTasks.Task task:deviceTasks.tasks()) {
            if(task.displayId==0 && target.equals(task.basePackage)) {
                deviceTasks.move(task.id,displayId);moved=true;
            }
        }
        if(!moved){set("paused","应用任务已结束；请结束当前任务后重新开始");return false;}
        permissionHandoff=false;permissionTaskMoved=false;permissionPresent=false;permissionVisible=false;permissionPhase="none";
        invalidate();set("resuming","恢复原会话，下一步操作前重新读取页面");
        finishPermissionAnchor();return true;
    }
    void takeOver(String expectedTask, ResultReceiver receiver) {
        if(expectedTask==null || !expectedTask.equals(taskId) || target.isEmpty()) { reply(receiver,false);return; }
        try {
            manager.reserveMain(target);invalidate();pendingIntent=null;
            if(permissionHandoff && permissionTaskMoved) {reply(receiver,true);return;}
            if(session==null) {
                release("paused","用户已接管；任务暂停，已完成的操作保留");
                reply(receiver,releaseConfirmed());return;
            }
            if(handoverReply!=null){reply(receiver,false);return;}
            handoverReply=receiver;
            set("paused","正在交还应用控制权，任务保持暂停");
            session.handOverApplications();
        } catch(Exception e) { fail(e); }
    }
    void command(Intent intent) {
        ResultReceiver reply=intent.getParcelableExtra("reply",ResultReceiver.class);
        String action=intent.getStringExtra("command"),pkg=intent.getStringExtra("package"),id=intent.getStringExtra("prompt_id");
        try {
            boolean permissionPage=permissionHandoff && pkg!=null
                    && (pkg.equals(deviceTasks.permissionControllerPackage())||pkg.equals(service.getPackageName()));
            if((!target.equals(pkg)&&!permissionPage)||id==null){reply(reply,false);return;}
            if("prompt".equals(action)){
                if(permissionHandoff && permissionTaskMoved){promptId=id;reply(reply,true);return;}
                if(session==null||!state.equals("active")){
                    if(Arrays.asList("creating","preparing","resuming","needs_user_consent").contains(state))release("paused","用户在控制会话准备期间打开应用，任务已暂停");
                    if(session!=null){reply(reply,false);return;}
                    promptId=id;reply(reply,releaseConfirmed());return;
                }
                promptId=id;promptSince=SystemClock.elapsedRealtime();promptReply=reply;invalidate();
                set("confirming","用户请求打开应用；已停止新操作，等待是否接管");session.notifyBlocked();return;
            }
            if("takeover".equals(action)) {
                String expected=intent.getStringExtra("task_id");
                if(!id.equals(promptId) && !(session==null && taskId.equals(expected))){reply(reply,false);return;}
                takeOver(expected,reply);return;
            }
            if(!id.equals(promptId)||!state.equals("confirming")){reply(reply,false);return;}
            if("cancel_prompt".equals(action)){
                promptReply=null;promptId="";set("resuming","用户保留后台控制；重新读取页面后继续");session.requestUnblock();
            } else if("abandon_prompt".equals(action))release("paused","接管确认已关闭，任务保持暂停");
            else {reply(reply,false);return;}
            reply(reply,true);
        }catch(Exception e){fail(e);reply(reply,false);}
    }
    void reply(ResultReceiver receiver,boolean ok){
        if(receiver==null)return;Bundle b=new Bundle();b.putString("task_id",taskId);b.putString("state",state);b.putString("task_name",taskName);b.putBoolean("permission_handoff",permissionHandoff);b.putBoolean("control_released",releaseConfirmed()||(permissionHandoff&&permissionTaskMoved&&!state.equals("active")));receiver.send(ok?0:1,b);
    }
    boolean releaseConfirmed(){return session==null&&!nativeActive&&(creationRequest.isEmpty()||screenBackend.releaseState(creationRequest,displayId)==ScreenBackend.ReleaseState.RELEASED);}
    void restoreProxy(ScreenBackend.Session value)throws Exception {
        displayId=value.refreshObservationChannel();if(displayId<=0)throw new IllegalStateException("Refusing physical display");
    }
    void tick(){
        if(destroyed)return;
        try{
            refreshPermissionState();
            if(SystemClock.elapsedRealtime()-ownerCheckedAt>=3000){ownerCheckedAt=SystemClock.elapsedRealtime();ownerStopped=AssistantConversation.stoppedUiOwner(service,taskId);}
            boolean stoppedNow=state.equals("active")&&!mirrorUserControl&&ownerStopped;
            if(stoppedNow){if(ownerStoppedSince==0)ownerStoppedSince=SystemClock.elapsedRealtime();}else ownerStoppedSince=0;
            if(BackgroundExpiry.due(SystemClock.elapsedRealtime(),ownerStoppedSince)&&AssistantConversation.stoppedUiOwner(service,taskId)){release("closed",service.getString(R.string.background_idle_released));manager.removeClosed();manager.persist();return;}
            if(state.equals("paused")&&BackgroundExpiry.due(SystemClock.elapsedRealtime(),pausedSince)){release("closed",service.getString(R.string.background_idle_released));manager.removeClosed();manager.persist();return;}
            if(!Authorization.enabled(service)&&!state.equals("ready")&&!state.equals("closed")&&!state.equals("paused"))release("paused","AIOS 权限已关闭");
            else if(state.equals("waiting_for_app")){
                long now=SystemClock.elapsedRealtime();
                if(foreground() || !manager.canAcquire(this)){
                    backgroundSince=0;
                    detail=foreground()?"主屏正在使用此 APP，等待进入后台":!manager.blockingOwner(this).isEmpty()?"此 APP 正由其他虚拟屏使用，排队等待":"等待虚拟屏名额或前序任务";
                }
                else if(backgroundSince==0)backgroundSince=now;
                else if(now-backgroundSince>=2000){if(permissionHandoff&&session!=null)resumePermissionSession();else createNative();}
            }else if(state.equals("active")&&foreground())release("paused","用户正在使用应用，任务暂停");
            else if(state.equals("confirming")&&SystemClock.elapsedRealtime()-promptSince>120000)release("paused","接管确认超时，任务保持暂停");
        }catch(Exception e){fail(e);}finally{if(!destroyed)handler.postDelayed(ticker,400);}
    }
    void fail(Exception e){android.util.Log.e("AIOS_UI_SESSION","UI task paused",e);release("paused","界面控制异常，已暂停："+e.getClass().getSimpleName());}
    void destroy(){destroyed=true;handler.removeCallbacks(ticker);release(state.equals("ready")||state.equals("closed")?"closed":"paused","控制服务已停止");}
 static List<String> targetPackages(Context context,String target){
  return DeviceHost.tasks(context).controlPackages(target);
 }
}
