package os.aios.core.automation;

import android.content.*;
import androidx.work.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Durable definitions are authoritative. WorkManager is a repairable scheduling projection. */
public final class AutomationService {
    public interface PipelineFactory { PipelineRuntime create(AutomationService service,AutomationStore store,Context context); }
    private static PipelineFactory pipelineFactory;
    public static synchronized void registerPipelineFactory(PipelineFactory factory){
        if(instance!=null||pipelineFactory!=null)throw new IllegalStateException("pipeline_registration_closed");
        pipelineFactory=factory;
    }
    public interface Host {boolean authorized();Object read(String capability,JSONObject arguments)throws Exception;}
    public static final String CHANGED="os.aios.automation.CHANGED";
    public static final Set<String> CAPABILITIES=Set.of("workspace.task.list","workspace.task.get","workspace.memory.list","workspace.settings.get");
    private static AutomationService instance;
    private final AssistantScheduler assistants;
    private final PipelineRuntime nativePipelines;private final Context context;private final Host host;private final AutomationStore store;
    private final Set<String> active=new HashSet<>();
    private final ExecutorService scheduler=Executors.newSingleThreadExecutor();
    public static synchronized void initialize(Context c,Host h){if(instance==null){instance=new AutomationService(c,h);instance.reconcileAsync();}}
    public static synchronized AutomationService get(){if(instance==null)throw new IllegalStateException("Automation host not initialized");return instance;}
    private AutomationService(Context c,Host h){context=c.getApplicationContext();host=h;store=new AutomationStore(context);assistants=new AssistantScheduler(store,context,this);nativePipelines=pipelineFactory==null?null:pipelineFactory.create(this,store,context);
        IntentFilter changes=new IntentFilter();for(String action:List.of(Intent.ACTION_POWER_CONNECTED,Intent.ACTION_POWER_DISCONNECTED,Intent.ACTION_SCREEN_ON,Intent.ACTION_SCREEN_OFF,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED))changes.addAction(action);
        context.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent intent){nativeStateChanged();}},changes,Context.RECEIVER_NOT_EXPORTED);
        android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);if(power!=null)power.addThermalStatusListener(status->nativeStateChanged());
    }
    void changed(){context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));}
    void authorized(){if(!host.authorized())throw new SecurityException("AIOS authorization disabled");}
    static long now(){return System.currentTimeMillis();}
    static String text(JSONObject p,String key,int max)throws Exception{String s=p.getString(key);if(s.isBlank()||s.length()>max)throw new IllegalArgumentException(key+" must contain 1.."+max+" characters");return s;}
    JSONObject require(String id)throws Exception{JSONObject j=store.get(id);if(j==null||j.optBoolean("deleted"))throw new IllegalArgumentException("Task missing or deleted");return j;}
    void revision(JSONObject j,JSONObject p)throws Exception{if(j.getLong("revision")!=p.getLong("expected_revision"))throw new IllegalStateException("Revision conflict: reload the task");}
    public synchronized JSONObject assistantInternal(String action,JSONObject p)throws Exception { if(!Set.of("checkpoint","screen.release","release.empty","thread","tool.finish","shutdown","result.pending","result.ack","source.status","external.release","external.list").contains(action))authorized();store.database().beginTransaction();try{JSONObject result=assistants.internal(action,p);store.database().setTransactionSuccessful();return result;}finally{store.database().endTransaction();} }
    public JSONObject call(String action,JSONObject p)throws Exception{
        if(action.startsWith("rule.")||action.startsWith("execution.")||action.equals("assistant.status")){if(!Set.of("rule.list","rule.get","rule.sources","execution.list","execution.get","assistant.status").contains(action))authorized();synchronized(this){JSONObject result=assistants.call(action,p);if(!Set.of("rule.list","rule.get","rule.sources","rule.validate","execution.list","execution.get","assistant.status").contains(action))reconcileAsync();return result;}}
        if(action.equals("authorization.revoke")){authorized();synchronized(this){return new PolicyLedger(store).revoke(p.getString("id"));}}
        if(action.equals("authorization.require")){authorized();synchronized(this){new PolicyLedger(store).require(p.getString("id"),p.getString("capability"),p.optString("resource"),p.optLong("plan_version",1));return new JSONObject().put("authorized",true);}}
        if(action.equals("resource.acquire")){authorized();synchronized(this){return new PolicyLedger(store).acquire(p.getString("authorization_id"),p.getString("owner"),p.getString("resource"),p.optLong("ttl_ms",60000));}}
        if(action.equals("resource.release")){synchronized(this){new PolicyLedger(store).release(p.getString("owner"));return new JSONObject().put("released",true);}}
        if(action.startsWith("graph.")||action.startsWith("run.")||action.startsWith("node.")){authorized();synchronized(this){JSONObject result=new DagRuntime(store).call(action,p);changed();return result;}}
        if(action.equals("validate")){
            authorized();String code=text(p,"source",65536);
            return new ScriptRuntime(context).execute(code,new JSONObject(),5000,new ScriptRuntime.Host(){public void check(){authorized();}public Object invoke(String op,JSONObject args){throw new UnsupportedOperationException();}},true);
        }
        synchronized(this){
            if(action.equals("status"))return new JSONObject().put("engine","AIOS Core Event Runtime / WorkManager / JavaScriptEngine").put("owner_process","os.aios.core").put("supported",ScriptRuntime.supported()).put("authorized",host.authorized()).put("capabilities",new JSONArray(CAPABILITIES)).put("trigger_types",new JSONArray(List.of("time","topic","device"))).put("states",new JSONArray(List.of("waiting_dependency","waiting_trigger","waiting_resource","ready","submitting","running","waiting_user","paused","succeeded","failed","cancelled","outcome_unknown"))).put("min_interval_minutes",15).put("timing","not_exact").put("max_timeout_ms",30000);
            if(action.equals("list")){
                JSONArray rows=store.jobs(),items=new JSONArray();for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).optBoolean("deleted")){JSONObject j=rows.getJSONObject(i);j.remove("source");j.remove("state");items.put(j);}return new JSONObject().put("items",items);
            }
            if(action.equals("get"))return require(p.getString("id"));
            if(action.equals("runs")){require(p.getString("id"));return new JSONObject().put("items",store.runs(p.getString("id"),Math.max(1,Math.min(100,p.optInt("limit",30)))));}
            if(action.equals("versions")){require(p.getString("id"));return new JSONObject().put("items",store.versions(p.getString("id")));}
            if(action.equals("create")){
                authorized();String key=text(p,"job_key",200);JSONObject old=store.byKey(key);
                if(old!=null){if(old.optBoolean("deleted"))throw new IllegalStateException("job_key was deleted; it cannot be recreated");return old.put("existing",true);}
                JSONObject j=definition(p).put("id",UUID.randomUUID().toString()).put("job_key",key).put("revision",1).put("created_at",now()).put("updated_at",now()).put("deleted",false).put("state",new JSONObject()).put("last_status","never");
                j.put("next_at",j.getBoolean("enabled")?Schedule.next(j.getJSONObject("schedule"),now()):0);
                store.save(j,true);changed();reconcileAsync();return j;
            }
            JSONObject j=require(p.getString("id"));revision(j,p);
            if(action.equals("run")){
                authorized();String request=text(p,"request_id",100);if(!request.matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException("request_id: use letters, numbers, dot, dash or underscore");
                String token=j.getString("id")+":"+j.getLong("revision")+":manual:"+request;
                JSONObject previous=store.run(token);if(previous!=null)return new JSONObject().put("run_id",token).put("state",previous.getString("status"));
                enqueue(j,token,now(),true);return new JSONObject().put("run_id",token).put("state","queued");
            }
            if(action.equals("update")){
                authorized();JSONObject def=definition(p);for(String key:List.of("title","description","source","schedule","capabilities","timeout_ms","network_required"))j.put(key,def.get(key));
                // Editing never resumes a paused task. Resume is a separate explicit operation.
            }else if(action.equals("pause"))j.put("enabled",false);
            else if(action.equals("resume")){authorized();j.put("enabled",true);}
            else if(action.equals("delete"))j.put("enabled",false).put("deleted",true);
            else throw new IllegalArgumentException("Unknown automation action: "+action);
            j.put("revision",j.getLong("revision")+1).put("updated_at",now());
            j.put("next_at",j.getBoolean("enabled")?Schedule.next(j.getJSONObject("schedule"),now()):0);
            store.save(j,true);WorkManager.getInstance(context).cancelAllWorkByTag(tag(j.getString("id")));changed();reconcileAsync();return j;
        }
    }
    /** Host-only approval bridge; this method is not registered as a model tool. */
    public synchronized JSONObject putAuthorization(JSONObject grant)throws Exception{authorized();return new PolicyLedger(store).put(grant);}
    /** Event Runtime entry point; matching remains inside the durable scheduler ledger. */
    public synchronized JSONObject onEvent(JSONObject event)throws Exception{reconcileAsync();return new DagRuntime(store).call("trigger.event",event);}
    private JSONObject definition(JSONObject p)throws Exception{
        JSONArray caps=p.optJSONArray("capabilities");if(caps==null)caps=new JSONArray();Set<String> unique=new LinkedHashSet<>();
        for(int i=0;i<caps.length();i++){String c=caps.getString(i);if(!CAPABILITIES.contains(c))throw new IllegalArgumentException("Unsupported JS capability: "+c);unique.add(c);}
        int timeout=p.optInt("timeout_ms",30000);if(timeout<1000||timeout>30000)throw new IllegalArgumentException("timeout_ms must be 1000..30000");
        String description=p.optString("description","");if(description.length()>10000)throw new IllegalArgumentException("Description exceeds 10000 characters");
        return new JSONObject().put("title",text(p,"title",300)).put("description",description).put("source",text(p,"source",65536)).put("schedule",Schedule.validate(p.getJSONObject("schedule"))).put("capabilities",new JSONArray(unique)).put("timeout_ms",timeout).put("network_required",p.optBoolean("network_required",false)).put("enabled",p.optBoolean("enabled",true));
    }
    private static String tag(String id){return "aios-automation:"+id;}
    private Constraints constraints(JSONObject j){return new Constraints.Builder().setRequiredNetworkType(j.optBoolean("network_required")?NetworkType.CONNECTED:NetworkType.NOT_REQUIRED).build();}
    private Data input(JSONObject j,String token,long due,boolean manual)throws Exception{return new Data.Builder().putString("job_id",j.getString("id")).putLong("revision",j.getLong("revision")).putString("token",token).putLong("scheduled_at",due).putBoolean("manual",manual).build();}
    private void enqueue(JSONObject j,String token,long due,boolean manual)throws Exception{
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(AutomationWorker.class).setInputData(input(j,token,due,manual)).setInitialDelay(Math.max(0,due-now()),TimeUnit.MILLISECONDS).setConstraints(constraints(j)).addTag(tag(j.getString("id"))).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork(token,ExistingWorkPolicy.KEEP,request).getResult().get(5,TimeUnit.SECONDS);
    }
    public void reconcileAsync(){scheduler.execute(()->{try{reconcile();}catch(Exception e){android.util.Log.e("AIOSAutomation","Reconcile failed; persisted definitions retained",e);}});}
    void nativeStateChanged(){scheduler.execute(()->{try{synchronized(this){if(nativePipelines!=null)nativePipelines.onStateChanged();}reconcile();}catch(Exception e){android.util.Log.e("AIOSAutomation","State reconciliation failed",e);}});}
    public synchronized JSONObject albumStatus()throws Exception{return nativePipelines==null?new JSONObject().put("state","not_packaged").put("enabled",false).put("reason_code","photo_index_not_packaged"):nativePipelines.status();}
    public synchronized JSONObject configureAlbum(JSONObject p)throws Exception{if(nativePipelines==null)return albumStatus();JSONObject result=nativePipelines.configure(p);reconcileAsync();return result;}
    void runNative(int version,long slice,java.util.function.BooleanSupplier stopped)throws Exception{if(nativePipelines==null)throw new UnsupportedOperationException("photo_index_not_packaged");nativePipelines.run(version,slice,stopped);}
    public synchronized void reconcile()throws Exception{
        if(host.authorized())assistants.reconcile();
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork("assistant-rule-repair",androidx.work.ExistingPeriodicWorkPolicy.KEEP,new androidx.work.PeriodicWorkRequest.Builder(AssistantScheduleWorker.class,15,java.util.concurrent.TimeUnit.MINUTES).build());
        if(nativePipelines!=null)nativePipelines.reconcile();
        JSONArray jobs=store.jobs();
        for(int i=0;i<jobs.length();i++){
            JSONObject j=jobs.getJSONObject(i);String id=j.getString("id");
            if(!active.contains(id)){
                JSONArray runs=store.runs(id,100);for(int k=0;k<runs.length();k++){JSONObject r=runs.getJSONObject(k);if(r.getString("status").equals("running")){r.put("status","interrupted").put("finished_at",now()).put("error","Process ended before result was saved");store.saveRun(r);}}
            }
            if(j.optBoolean("deleted")||!j.getBoolean("enabled")||!host.authorized()){WorkManager.getInstance(context).cancelAllWorkByTag(tag(id));j.put("scheduling_state",host.authorized()?"paused":"authorization_disabled");store.save(j,false);continue;}
            long due=j.getLong("next_at");if(due==0)continue;
            String token=id+":"+j.getLong("revision");JSONObject schedule=j.getJSONObject("schedule");
            try{if(schedule.getString("kind").equals("interval")){
                PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(AutomationWorker.class,schedule.getLong("minutes"),TimeUnit.MINUTES).setInitialDelay(Math.max(0,due-now()),TimeUnit.MILLISECONDS).setInputData(input(j,token,due,false)).setConstraints(constraints(j)).addTag(tag(id)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build();
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(token,ExistingPeriodicWorkPolicy.KEEP,request).getResult().get(5,TimeUnit.SECONDS);
            }else enqueue(j,token+":"+due,due,false);
                j.put("scheduling_state","scheduled").put("scheduling_error",JSONObject.NULL);
            }catch(Exception e){j.put("scheduling_state","error").put("scheduling_error",e.toString());}
            store.save(j,false);
        }
    }
    synchronized JSONObject begin(String id,long rev,String runId,long scheduled,boolean manual,int attempt)throws Exception{
        authorized();JSONObject j=require(id);if(j.getLong("revision")!=rev||(!manual&&!j.getBoolean("enabled")))return null;
        if(active.contains(id))throw new IllegalStateException("Task already running");
        JSONObject old=store.run(runId);if(old!=null&&!Set.of("running","interrupted").contains(old.getString("status")))return null;
        JSONObject run=new JSONObject().put("id",runId).put("job_id",id).put("revision",rev).put("scheduled_at",scheduled).put("started_at",now()).put("finished_at",JSONObject.NULL).put("manual",manual).put("attempt",attempt).put("status","running").put("logs",new JSONArray());
        store.saveRun(run);active.add(id);j.put("last_status","running").put("last_run_id",runId);store.save(j,false);changed();return j;
    }
    synchronized void check(String id,long rev)throws Exception{authorized();JSONObject j=store.get(id);if(j==null||j.optBoolean("deleted")||j.getLong("revision")!=rev)throw new CancellationException("Task changed, paused or deleted");}
    synchronized Object invoke(String id,long rev,String runId,String op,JSONObject p)throws Exception{
        check(id,rev);JSONObject j=require(id);
        if(op.equals("state.get"))return j.getJSONObject("state");
        if(op.equals("state.set")){JSONObject value=p.getJSONObject("value");if(value.toString().length()>16384)throw new IllegalArgumentException("State exceeds 16 KiB");j.put("state",value);store.save(j,false);return true;}
        if(op.equals("log")){JSONObject run=store.run(runId);JSONArray logs=run.getJSONArray("logs");String value=p.getString("value");if(logs.toString().length()+value.length()>8192)throw new IllegalArgumentException("Log limit: 8 KiB");logs.put(new JSONObject().put("at",now()).put("text",value));store.saveRun(run);return true;}
        if(op.equals("call")){
            String name=p.getString("name");boolean granted=false;JSONArray caps=j.getJSONArray("capabilities");for(int i=0;i<caps.length();i++)if(caps.getString(i).equals(name))granted=true;
            if(!granted||!CAPABILITIES.contains(name))throw new SecurityException("Capability not granted: "+name);
            return host.read(name,p.getJSONObject("args"));
        }
        throw new IllegalArgumentException("Unknown host operation");
    }
    synchronized void finish(String id,long rev,String runId,String status,JSONObject output,String error,boolean manual)throws Exception{
        try{
            JSONObject run=store.run(runId);if(run==null)return;
            run.put("status",status).put("finished_at",now()).put("error",error==null?JSONObject.NULL:error.substring(0,Math.min(error.length(),8192))).put("result",output==null?JSONObject.NULL:output.opt("result"));store.saveRun(run);
            JSONObject j=store.get(id);if(j!=null&&!j.optBoolean("deleted")){
                if(runId.equals(j.optString("last_run_id")))j.put("last_status",status);
                if(j.getLong("revision")==rev&&!manual){JSONObject s=j.getJSONObject("schedule");j.put("next_at",s.getString("kind").equals("once")?0:Schedule.next(s,now()));}
                store.save(j,false);
            }
        }finally{active.remove(id);changed();reconcileAsync();}
    }
}
