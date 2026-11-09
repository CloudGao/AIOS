package os.aios.agent;

import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Main-thread registry. Each input, callback and package lease belongs to one task ID. */
final class UiSessionManager {
    static final ThreadLocal<String> caller=new ThreadLocal<>();
    static final int MAX_SLOTS=os.aios.host.HostProduct.screenSlots(), MAX_ACTIVE=MAX_SLOTS;
    final AgentService service;
    final ForegroundUiSession foreground;
    final UiScreenRouter screenRouter;
    final Handler handler=new Handler(Looper.getMainLooper());
    final UiActionReadback.Cadence observationCadence=new UiActionReadback.Cadence();
    final LinkedHashMap<String,UiSessionController> sessions=new LinkedHashMap<>();
    final Map<String,Long> mainReservations=new HashMap<>();
    final SharedPreferences registry;
    long order;
    UiSessionManager(AgentService s){
        service=s;foreground=new ForegroundUiSession(s,this);screenRouter=new UiScreenRouter(this);registry=s.getSharedPreferences("ui_sessions",0);
        LinkedHashSet<String> restored=new LinkedHashSet<>();
        String ordered=registry.getString("ordered_ids","");
        if(!ordered.isEmpty())for(String id:ordered.split(","))if(!id.isEmpty())restored.add(id);
        restored.addAll(registry.getStringSet("ids",Collections.emptySet()));
        for(String id:restored){
            UiSessionController task=new UiSessionController(s,this,id);
            if(!task.state.equals("closed")&&!task.state.equals("ready"))sessions.put(id,task);
            else task.destroy();
        }
        AssistantConversation.reconcileUiSessions(s,sessions.keySet());
        // Migrate an unfinished single-session task once, preserving its paused state.
        if(!registry.getBoolean("migrated",false)){
            SharedPreferences old=s.getSharedPreferences("ui_session",0);
            String id=old.getString("task_id","");
            if(old.getBoolean("unfinished",false)&&!id.isEmpty()&&!sessions.containsKey(id)){
                UiSessionController task=new UiSessionController(s,this,id);
                task.target=old.getString("package","");task.taskName=old.getString("task_name","");
                task.set("paused","控制层已升级；手动继续后重新读取页面");sessions.put(id,task);
            }
            registry.edit().putBoolean("migrated",true).apply();persist();
        }
    }
    void persist(){registry.edit().putStringSet("ids",new HashSet<>(sessions.keySet())).putString("ordered_ids",String.join(",",sessions.keySet())).apply();}
    void removeClosed(){
        Iterator<Map.Entry<String,UiSessionController>> it=sessions.entrySet().iterator();
        while(it.hasNext()){
            UiSessionController task=it.next().getValue();
            if(task.state.equals("closed")){it.remove();task.destroy();}
        }
    }
    long nextOrder(){return ++order;}
    UiSessionController find(String id){return sessions.get(id);}
    UiSessionController only(){
        UiSessionController found=null;
        for(UiSessionController s:sessions.values())if(!s.state.equals("closed")){
            if(found!=null)return null;found=s;
        }
        return found;
    }
    boolean owns(UiSessionController s){return s.session!=null || Arrays.asList("creating","preparing","needs_user_consent","resuming").contains(s.state);}
    UiSessionController owner(String pkg){for(UiSessionController s:sessions.values())if(s.controlsPackage(pkg)&&owns(s))return s;return null;}
    String blockingOwner(UiSessionController s){if(foreground.owns(s.target))return foreground.id;UiSessionController o=owner(s.target);return o!=null&&o!=s?o.taskId:"";}
    int queuePosition(UiSessionController s){
        if(!s.state.equals("waiting_for_app")||owns(s))return 0;
        int pos=1;for(UiSessionController other:sessions.values())if(other!=s&&other.target.equals(s.target)&&other.state.equals("waiting_for_app")&&other.queueOrder<s.queueOrder)pos++;
        return pos;
    }
    void reserveMain(String pkg){mainReservations.put(pkg,SystemClock.elapsedRealtime()+10000);}
    boolean canAcquire(UiSessionController task){
        if(foreground.owns(task.target)||foreground.blocksRequest(task.target))return false;
        if(SystemClock.elapsedRealtime()<mainReservations.getOrDefault(task.target,0L))return false;
        UiSessionController owner=owner(task.target);
        if(owner!=null&&owner!=task)return false;
        if(queuePosition(task)>1)return false;
        int count=0;
        for(UiSessionController s:sessions.values())if(s!=task){
            if(owns(s))count++;
            // Serialize system consent preparation, not active sessions.
            if(Arrays.asList("creating","needs_user_consent","preparing").contains(s.state))return false;
        }
        try{
            JSONObject capacity=AgentCapabilities.client(service).module("virtual-screen");
            if(!capacity.optString("state").equals("ready"))return false;
            JSONObject limits=capacity.optJSONObject("limits");
            return count<Math.min(MAX_ACTIVE,limits==null?0:limits.optInt("max_sessions",0));
        }catch(Exception unavailable){return false;}
    }
    JSONObject call(JSONObject q)throws Exception{
        JSONObject routed=new JSONObject(q.toString());routed.remove("_caller_thread");if(caller.get()!=null)routed.put("_caller_thread",caller.get());
        if(Looper.myLooper()==handler.getLooper())return dispatch(routed);
        JSONObject result=callOnce(routed);
        for(int retry=0;retry<40&&result.optString("state").equals("waiting_resource");retry++){Thread.sleep(500);result=callOnce(routed);}
        // Creation is asynchronous. Wait off the main thread through ordinary preparation,
        // so the model receives the actual ready/consent state instead of a transient queue state.
        if(q.optString("op").equals("create")&&!result.optString("session_id").isEmpty()){
            JSONObject status=new JSONObject().put("op","status").put("session_id",result.getString("session_id"));
            if(routed.has("_caller_thread"))status.put("_caller_thread",routed.getString("_caller_thread"));
            for(int retry=0;retry<60&&Set.of("waiting_for_app","creating","preparing","resuming").contains(result.optString("state"));retry++){Thread.sleep(500);result=callOnce(status);}
        }
        if(Set.of("tree","screenshot").contains(q.optString("op")))
            return UiObservationRecovery.resolve(routed,result,this::callOnce,Thread::sleep);
        return UiActionReadback.complete(q,result,this::call);
    }
    JSONObject callOnce(JSONObject routed)throws Exception{
        if(Set.of("tree","screenshot").contains(routed.optString("op"))){
            long delay=observationCadence.reserve(routed.optString("session_id"),SystemClock.elapsedRealtime());
            if(delay>0)Thread.sleep(delay);
        }
        FutureTask<JSONObject> work=new FutureTask<>(()->dispatch(routed));handler.post(work);
        try{return work.get(15,TimeUnit.SECONDS);}
        catch(TimeoutException e){work.cancel(false);throw e;}
        catch(ExecutionException e){throw new IllegalStateException(e.getCause());}
    }
    JSONObject listing()throws Exception{
        JSONArray all=new JSONArray();int active=0;
        ArrayList<UiSessionController> ordered=new ArrayList<>(sessions.values());
        ordered.sort(Comparator.comparingInt(s->s.state.equals("active")?0:s.state.equals("paused")?2:1));
        for(UiSessionController s:ordered)if(!s.state.equals("closed")){
            s.refreshPermissionState();JSONObject row=s.status();
            try{row.put("app_label",service.getPackageManager().getApplicationLabel(service.getPackageManager().getApplicationInfo(s.target,0)));}catch(Exception ignored){row.put("app_label",s.target);}
            all.put(row);if(owns(s))active++;
        }
        if(!foreground.state.equals("closed"))all.put(foreground.status());
        return new JSONObject().put("state","succeeded").put("max_foreground_sessions",1).put("bridge_protocol",5).put("sessions",all).put("active_count",active).put("slot_count",sessions.size()).put("max_background_slots",MAX_SLOTS).put("max_concurrent_ui_tasks",MAX_ACTIVE);
    }
    JSONObject dispatch(JSONObject q)throws Exception{
        String op=q.getString("op");
        String thread=q.optString("_caller_thread");
        if(q.has("_caller_thread")&&thread.isEmpty())return new JSONObject().put("state","conversation_required").put("executed",false);
        if(!Authorization.enabled(service)&&!Set.of("list","status","close").contains(op))return Authorization.denied();
        if(!thread.isEmpty()&&service.interventions!=null&&service.interventions.waiting(thread)&&!Set.of("list","status","close").contains(op))return new JSONObject().put("state","waiting_user").put("executed",false);
        if(op.equals("list"))return listing();
        JSONObject choice=screenRouter.status(q.optString("session_id"),thread);if(choice!=null)return choice;
        if(op.equals("create")){
            String mode=screenRouter.mode(q.optString("package"),q.optString("mode","background"),thread);
            if(mode.equals("choice"))return screenRouter.choose(q.getString("package"),thread,selected->dispatch(new JSONObject(q.toString()).put("mode",selected)));
            JSONObject unavailable=AgentCapabilities.guard(service,mode.equals("foreground")?"screen.foreground.create":"screen.virtual.create");
            if(unavailable!=null)return unavailable;
            if(mode.equals("foreground"))return foreground.create(q);
            if(!mode.equals("background"))throw new IllegalArgumentException("Invalid UI mode");
            foreground.releasePreviousRequest(q.optString("package"));
            if(foreground.blocksRequest(q.optString("package")))return foreground.status().put("state","user_takeover").put("executed",false);
            removeClosed();
            String requestedPackage=q.optString("package","com.android.settings");
            if(service.scheduledAssistants!=null){service.scheduledAssistants.internal("screen.sync",listing());JSONObject me=service.scheduledAssistants.internal("thread",new JSONObject().put("thread_id",thread));if(me.length()==0)for(UiSessionController existing:sessions.values())if(existing.controlsPackage(requestedPackage)||UiSessionController.targetPackages(service,requestedPackage).contains(existing.target)){String other=AssistantConversation.uiOwner(service,existing.taskId).optString("thread_id");if(!thread.equals(other)&&service.scheduledAssistants.internal("screen.preempt",new JSONObject().put("thread_id",other)).optBoolean("requested"))return new JSONObject().put("state","waiting_resource").put("executed",false);}}

            for(UiSessionController existing:sessions.values())if(existing.controlsPackage(requestedPackage)||UiSessionController.targetPackages(service,requestedPackage).contains(existing.target)){boolean same=!thread.isEmpty()&&thread.equals(AssistantConversation.uiOwner(service,existing.taskId).optString("thread_id"));return existing.status().put("state",same?"app_in_use":"session_owned_by_other_conversation").put("executed",false).put("reason",same?"该应用已有本会话虚拟屏，请继续原槽位":"该应用已被其他会话占用，请先由用户关闭原槽位");}
            if(sessions.size()>=MAX_SLOTS&&service.scheduledAssistants==null)return listing().put("state","slot_limit").put("executed",false).put("reason","四个虚拟屏槽位已满");
            String id=UUID.randomUUID().toString();
            if(service.scheduledAssistants!=null){JSONObject allocation=service.scheduledAssistants.internal("screen.request",new JSONObject().put("thread_id",thread).put("session_id",id));if(!allocation.optString("state").equals("allocated"))return allocation;}
            UiSessionController task=new UiSessionController(service,this,id);
            try{AssistantConversation.attachUiSession(service,id,thread);JSONObject result=task.dispatch(q);sessions.put(id,task);persist();return result;}
            catch(Exception e){AssistantConversation.releaseUiSession(service,id);task.destroy();ScheduledScreenRelease.after(service,id,-1,task.creationRequest,task.creationRequest.isEmpty());throw e;}
        }
        String id=q.optString("session_id",q.optString("task_id",""));
        if(!id.isEmpty()&&id.equals(foreground.id)){if(!thread.isEmpty()&&!thread.equals(foreground.threadId))return new JSONObject().put("state","session_owned_by_other_conversation").put("executed",false);return foreground.dispatch(q);}
        if(id.isEmpty()&&!foreground.state.equals("closed")){
            if(sessions.values().stream().noneMatch(s->!s.state.equals("closed"))){if(!thread.isEmpty()&&!thread.equals(foreground.threadId))return new JSONObject().put("state","session_owned_by_other_conversation").put("executed",false);return foreground.dispatch(q);}
            return listing().put("state","session_id_required").put("executed",false);
        }
        UiSessionController task=id.isEmpty()?only():find(id);
        if(task==null){
            if(op.equals("status")&&id.isEmpty())return listing().put("state",sessions.isEmpty()?"ready":"multiple_sessions");
            return listing().put("state",id.isEmpty()?"session_id_required":"session_not_found").put("executed",false);
        }
        if(!thread.isEmpty()&&!thread.equals(AssistantConversation.uiOwner(service,task.taskId).optString("thread_id")))return new JSONObject().put("state","session_owned_by_other_conversation").put("executed",false);
        if(op.equals("handover")&&!thread.isEmpty())return screenRouter.choose(task.target,thread,selected->selected.equals("background")?task.status():task.dispatch(q));
        JSONObject result=task.dispatch(q);
        if(task.state.equals("closed")){sessions.remove(task.taskId);task.destroy();}
        persist();return result;
    }
    JSONObject open(Intent intent)throws Exception{return open(intent,"background","");}
    JSONObject open(Intent intent,String mode,String sessionId)throws Exception{return open(intent,mode,sessionId,caller.get()==null?"":caller.get());}
    JSONObject open(Intent intent,String mode,String sessionId,String thread)throws Exception{
        if(!mode.equals("foreground")&&!mode.equals("background"))throw new IllegalArgumentException("mode must be foreground or background");
        if(Looper.myLooper()!=handler.getLooper()){
            FutureTask<JSONObject> work=new FutureTask<>(()->open(intent,mode,sessionId,thread));handler.post(work);
            try{return work.get(15,TimeUnit.SECONDS);}
        catch(TimeoutException e){work.cancel(false);throw e;}
        }
        if(!Authorization.enabled(service))return Authorization.denied();
        ComponentName component=intent.resolveActivity(service.getPackageManager());
        if(component==null)return new JSONObject().put("state","not_supported").put("reason","No installed activity handles this request").put("executed",false);
        java.util.List<android.content.pm.ResolveInfo> handlers=service.getPackageManager().queryIntentActivities(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        Set<String> components=new HashSet<>();JSONArray candidates=new JSONArray();
        for(android.content.pm.ResolveInfo handler:handlers){
            if(handler.activityInfo==null)continue;
            ComponentName actual=new ComponentName(handler.activityInfo.packageName,handler.activityInfo.name);
            if(!components.add(actual.flattenToString()))continue;
            candidates.put(new JSONObject().put("package",actual.getPackageName()).put("component",actual.flattenToString()).put("label",String.valueOf(handler.loadLabel(service.getPackageManager()))));
        }
        if(!AppDiscovery.isHandler(component.flattenToString(),components))return new JSONObject().put("state","selection_required").put("candidates",candidates).put("executed",false).put("reason","Choose an installed handler matching the user's requested app; do not launch the system resolver as an application").put("next_step","For maps.open/browser.open retry with a candidate package and explicit mode. If the thread has an older dedicated tool schema, use aios_phone_action with capability and arguments. For other workflows open the requested candidate app and use its UI; do not guess a default.");
        intent.setComponent(component);
        String pkg=component.getPackageName();
        String selectedMode=screenRouter.mode(pkg,mode,thread);
        if(selectedMode.equals("choice"))return screenRouter.choose(pkg,thread,selected->open(intent,selected,sessionId,thread));
        JSONObject unavailable=AgentCapabilities.guard(service,selectedMode.equals("foreground")?"screen.foreground.launch":"screen.virtual.launch");
        if(unavailable!=null)return unavailable;
        if(selectedMode.equals("background"))foreground.releasePreviousRequest(pkg);
        if(selectedMode.equals("background")&&pkg.equals("android"))return new JSONObject().put("state","foreground_required").put("executed",false).put("reason","This system interaction requires foreground handoff");
        if(foreground.blocksRequest(pkg))return foreground.status().put("state","user_takeover").put("executed",false);
        if(selectedMode.equals("foreground")&&service.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked())return new JSONObject().put("state","device_locked").put("executed",false);
        if(selectedMode.equals("foreground")){
            UiSessionController owned=owner(pkg);
            if(owned!=null&&!thread.equals(AssistantConversation.uiOwner(service,owned.taskId).optString("thread_id")))return new JSONObject().put("state","session_owned_by_other_conversation").put("executed",false);
            reserveMain(pkg);
            if(owned!=null){
                owned.takeOver(owned.taskId,new ResultReceiver(handler){@Override protected void onReceiveResult(int code,Bundle data){
                    if(code==0&&Authorization.enabled(service))try{service.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),android.app.ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());}
                    catch(Exception e){android.util.Log.e("AIOSUi","Foreground launch after handover failed",e);}
                }});
                return owned.status().put("state","handover_requested").put("mode",selectedMode).put("completed_operation_verified",false);
            }
            service.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),android.app.ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
            return new JSONObject().put("state","handed_to_app").put("mode",selectedMode).put("completed_operation_verified",false);
        }
        if(!sessionId.isEmpty()){
            UiSessionController selected=find(sessionId);
            if(selected==null)return new JSONObject().put("state","session_not_found").put("executed",false);
            if(!thread.equals(AssistantConversation.uiOwner(service,sessionId).optString("thread_id"))||thread.isEmpty())return new JSONObject().put("state","session_owned_by_other_conversation").put("executed",false);
            return selected.open(intent).put("mode",selectedMode);
        }
        // Each workflow gets its own lease; never inject an intent into another task's screen.
        JSONObject result=dispatch(new JSONObject().put("op","create").put("package",component.getPackageName()).put("_caller_thread",thread));
        UiSessionController s=find(result.optString("session_id"));if(s!=null&&thread.equals(AssistantConversation.uiOwner(service,s.taskId).optString("thread_id"))&&!thread.isEmpty()){if("app_in_use".equals(result.optString("state")))return s.open(intent).put("mode",selectedMode);s.pendingIntent=new Intent(intent);}
        return result.put("completed_operation_verified",false);
    }
    void command(Intent intent){
        String id=intent.getStringExtra("task_id"),pkg=intent.getStringExtra("package");
        UiSessionController task=id==null||id.isEmpty()?owner(pkg):find(id);
        if(task==null && (id==null||id.isEmpty())){
            for(UiSessionController candidate:sessions.values())if(candidate.target.equals(pkg)&&!candidate.state.equals("closed")){task=candidate;break;}
        }
        if(task==null && (id==null||id.isEmpty())){
            for(UiSessionController candidate:sessions.values()){
                candidate.refreshPermissionState();
                if(candidate.permissionHandoff && candidate.permissionTaskMoved
                    && (pkg!=null && (pkg.equals(DeviceHost.tasks(service).permissionControllerPackage())||pkg.equals(service.getPackageName())))){
                    if(task==null||candidate.permissionVisible)task=candidate;
                }
            }
        }
        if(task==null){ResultReceiver reply=intent.getParcelableExtra("reply",ResultReceiver.class);if(reply!=null)reply.send(1,new Bundle());return;}
        task.command(intent);
    }
    void takeOver(String id,ResultReceiver reply){
        if(id.equals(foreground.id)){foreground.userTakeover("用户接管前台任务");if(reply!=null)reply.send(0,new Bundle());return;}
        UiSessionController s=find(id);
        if(s==null){if(reply!=null)reply.send(1,new Bundle());return;}
        // A waiting card can target an app already leased to another task.
        UiSessionController owned=owner(s.target);
        if(owned!=null&&owned!=s){sessions.remove(s.taskId);s.release("closed","重复槽位已关闭");persist();takeOverAndRelease(owned,reply);}
        else takeOverAndRelease(s,reply);
    }
    void takeOverAndRelease(UiSessionController task,ResultReceiver reply){
        task.takeOver(task.taskId,new ResultReceiver(handler){@Override protected void onReceiveResult(int code,Bundle data){
            if(code==0&&!task.permissionHandoff){
                sessions.remove(task.taskId);task.release("closed","应用已移到主屏，虚拟屏槽位已释放");persist();
            }
            if(reply!=null)reply.send(code,data==null?new Bundle():data);
        }});
    }
    void destroy(){screenRouter.dismiss();foreground.userTakeover("服务停止，前台任务已暂停");for(UiSessionController s:sessions.values())s.destroy();persist();}
}
