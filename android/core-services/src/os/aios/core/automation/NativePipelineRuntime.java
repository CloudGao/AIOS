package os.aios.core.automation;
import android.content.Context;
import android.database.Cursor;
import androidx.work.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import os.aios.core.embedding.ModelRegistry;
import os.aios.core.indexing.*;

/** Native handlers integrated with the existing scheduler/Worker and DAG, not a second scheduler. */
final class NativePipelineRuntime implements PipelineRuntime {
    static final String ALBUM="builtin.album.semantic-index";
    private final AutomationService service;private final AutomationStore store;private final Context context;
    private final Set<String> active=new HashSet<>();private boolean forceWake;
    NativePipelineRuntime(AutomationService s,AutomationStore db,Context c){service=s;store=db;context=c;
        android.os.Handler stateHandler=new android.os.Handler(android.os.Looper.getMainLooper());
        try{context.getContentResolver().registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,new android.database.ContentObserver(stateHandler){public void onChange(boolean self){service.nativeStateChanged();}});}catch(SecurityException e){android.util.Log.w("AIOSAutomation","Media observer unavailable; scheduled reconciliation retained",e);}
    }
    private JSONObject load(String id)throws Exception{try(Cursor c=store.database().rawQuery("SELECT doc FROM native_pipelines WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    private void save(JSONObject p)throws Exception{store.database().execSQL("INSERT OR REPLACE INTO native_pipelines(id,doc) VALUES(?,?)",new Object[]{p.getString("id"),p.toString()});}
    public JSONObject configure(JSONObject p)throws Exception{
        service.authorized();int start=p.optInt("start_minute",0),end=p.optInt("end_minute",360);if(start<0||start>=1440||end<0||end>=1440||start==end)throw new IllegalArgumentException("Invalid processing window");
        if(p.optBoolean("retry_failed"))try(PhotoIndexStore index=new PhotoIndexStore(context)){index.retryFailed();}
        JSONObject old=load(ALBUM);if(old!=null&&!old.optString("run_id").isBlank()){try{new DagRuntime(store).call("run.pause",new JSONObject().put("id",old.getString("run_id")));}catch(IllegalStateException ignored){}}
        if(old!=null&&!old.optString("authorization_id").isEmpty())new PolicyLedger(store).revoke(old.getString("authorization_id"));
        JSONArray nodes=new JSONArray();for(String id:List.of("discover_delta","process_photo_batch","report_progress"))nodes.put(new JSONObject().put("id",id).put("kind","action").put("resource","heavy_inference").put("side_effect","idempotent_write"));
        JSONObject graph=new DagRuntime(store).call("graph.put",new JSONObject().put("id",ALBUM).put("nodes",nodes).put("edges",new JSONArray().put(new JSONObject().put("from","discover_delta").put("to","process_photo_batch")).put(new JSONObject().put("from","process_photo_batch").put("to","report_progress"))).put("policy",new JSONObject().put("start_minute",start).put("end_minute",end)));
        String grant=p.getBoolean("enabled")?UUID.randomUUID().toString():"";if(p.getBoolean("enabled"))service.putAuthorization(new JSONObject().put("id",grant).put("subject","host:album-settings:graph-version:"+graph.getInt("version")).put("approved_version",1).put("graph_id",ALBUM).put("graph_version",graph.getInt("version")).put("capabilities",new JSONArray().put("scheduler.execute")).put("resources",new JSONArray().put("heavy_inference")));
        JSONObject def=new JSONObject().put("id",ALBUM).put("version",graph.getInt("version")).put("enabled",p.getBoolean("enabled")).put("start_minute",start).put("end_minute",end).put("authorization_id",grant).put("slice",0).put("next_eligible_at",0).put("wait_reason",p.getBoolean("enabled")?"pending":"user_paused");save(def);return status();
    }
    public JSONObject status()throws Exception{JSONObject p=load(ALBUM);if(p==null)p=new JSONObject().put("id",ALBUM).put("enabled",false).put("start_minute",0).put("end_minute",360).put("wait_reason","not_enabled");try(PhotoIndexStore index=new PhotoIndexStore(context)){return new JSONObject(p.toString()).put("progress",index.progress()).put("models",new ModelRegistry(context).status());}}
    public void onStateChanged()throws Exception{JSONObject p=load(ALBUM);if(p!=null&&p.optBoolean("enabled")&&!active.contains(ALBUM)){p.put("next_eligible_at",0);save(p);forceWake=true;}}
    public void reconcile()throws Exception{
        JSONObject p=load(ALBUM);if(p==null||!p.optBoolean("enabled")||active.contains(ALBUM))return;
        long due=Math.max(System.currentTimeMillis(),p.optLong("next_eligible_at"));String token=ALBUM+":"+p.getInt("version")+":"+p.getLong("slice");
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(AutomationWorker.class).setInputData(new Data.Builder().putString("native_pipeline",ALBUM).putInt("native_version",p.getInt("version")).putLong("native_slice",p.getLong("slice")).build()).setInitialDelay(Math.max(0,due-System.currentTimeMillis()),TimeUnit.MILLISECONDS).setConstraints(new Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()).build();
        WorkManager.getInstance(context).enqueueUniqueWork(token,forceWake?ExistingWorkPolicy.REPLACE:ExistingWorkPolicy.KEEP,work);forceWake=false;
    }
    private String admission(JSONObject p){try{service.authorized();if(!p.optBoolean("enabled"))return "user_paused";String reason=ContinuousPolicy.reason(context,p);if(!reason.isEmpty())return reason;String models=new ModelRegistry(context).status().optString("state");return models.equals("ready")?"":models;}catch(SecurityException e){return "authorization_disabled";}}
    public void run(int version,long slice,java.util.function.BooleanSupplier stopped)throws Exception{
        JSONObject definition;String runId;
        synchronized(service){definition=load(ALBUM);if(definition==null||definition.getInt("version")!=version||definition.getLong("slice")!=slice||!definition.optBoolean("enabled")||!active.add(ALBUM))return;
            String reason=admission(definition);if(!reason.isEmpty()){waitFor(definition,reason);active.remove(ALBUM);service.reconcileAsync();return;}
            definition.put("wait_reason","");save(definition);
            String existing=definition.optString("run_id");if(existing.isEmpty()){
                JSONObject run=new DagRuntime(store).call("run.start",new JSONObject().put("graph_id",ALBUM).put("graph_version",version).put("occurrence",version+":"+slice).put("authorization_id",definition.getString("authorization_id")));runId=run.getString("id");definition.put("run_id",runId);save(definition);
            }else runId=existing;
        }
        String owner="native:"+runId+":"+UUID.randomUUID();JSONObject claim=null;
        try{
            for(int n=0;n<3;n++){
                synchronized(service){if(new DagRuntime(store).call("run.get",new JSONObject().put("id",runId)).optString("state").equals("succeeded"))break;}
                check(version,runId,stopped);synchronized(service){JSONArray ready=new DagRuntime(store).call("run.ready",new JSONObject().put("id",runId)).getJSONArray("items");if(ready.length()==0)break;claim=new DagRuntime(store).call("node.claim",new JSONObject().put("run_id",runId).put("node_id",ready.getJSONObject(0).getString("node_id")).put("owner",owner).put("lease_ms",300000));if(!claim.optString("state").equals("running"))throw new IllegalStateException("resource_busy");}
                JSONObject held=claim;JSONObject result=new PhotoIndexExecutor(context).execute(claim.getString("node_id"),()->{try{check(version,runId,stopped);checkLease(runId,owner,held);}catch(Exception e){throw new CancellationException(e.getMessage());}});
                synchronized(service){check(version,runId,stopped);finish(runId,owner,claim,"succeeded",result);claim=null;}
            }
            synchronized(service){JSONObject p=load(ALBUM);if(p.getInt("version")==version){JSONObject run=new DagRuntime(store).call("run.get",new JSONObject().put("id",runId));if(run.optString("state").equals("succeeded")){p.remove("run_id");p.put("slice",slice+1);try(PhotoIndexStore index=new PhotoIndexStore(context)){JSONObject progress=index.progress();p.put("next_eligible_at",progress.optInt("pending")>0||progress.optInt("cleanup_pending")>0||!progress.optBoolean("inventory_complete")?0:ContinuousPolicy.nextWindow(p));}p.put("wait_reason","");save(p);}else waitFor(p,"lease_wait");}}
        }catch(Exception e){synchronized(service){if(claim!=null)try{finish(runId,owner,claim,"waiting_resource",new JSONObject().put("wait_reason",e.getMessage()));}catch(Exception stale){}JSONObject p=load(ALBUM);if(p.getInt("version")==version)waitFor(p,String.valueOf(e.getMessage()));}}
        finally{synchronized(service){new PolicyLedger(store).release(owner);active.remove(ALBUM);}service.reconcileAsync();}
    }
    private void check(int version,String run,java.util.function.BooleanSupplier stopped)throws Exception{synchronized(service){if(stopped.getAsBoolean())throw new CancellationException("worker_stopped");JSONObject p=load(ALBUM);if(p.getInt("version")!=version)throw new CancellationException("policy_changed");new PolicyLedger(store).require(p.getString("authorization_id"),"scheduler.execute","heavy_inference",1);if(os.aios.core.embedding.EmbeddingClient.interactivePending())throw new CancellationException("interactive_query");String reason=admission(p);if(!reason.isEmpty())throw new IllegalStateException(reason);JSONObject state=new DagRuntime(store).call("run.get",new JSONObject().put("id",run));if(!state.getString("state").equals("running"))throw new CancellationException("run_paused");}}
    private void checkLease(String run,String owner,JSONObject claim)throws Exception{synchronized(service){JSONArray nodes=new DagRuntime(store).call("run.get",new JSONObject().put("id",run)).getJSONArray("nodes");for(int i=0;i<nodes.length();i++){JSONObject node=nodes.getJSONObject(i);if(node.getString("node_id").equals(claim.getString("node_id"))){if(!node.getString("state").equals("running")||node.getInt("generation")!=claim.getInt("generation")||!node.getString("lease_owner").equals(owner)||node.getLong("lease_until")<=System.currentTimeMillis())throw new CancellationException("stale_node_lease");return;}}throw new CancellationException("node_missing");}}
    private void finish(String run,String owner,JSONObject claim,String state,JSONObject result)throws Exception{new DagRuntime(store).call("node.finish",new JSONObject().put("run_id",run).put("node_id",claim.getString("node_id")).put("owner",owner).put("generation",claim.getInt("generation")).put("state",state).put("result",result));}
    private void waitFor(JSONObject p,String reason)throws Exception{p.put("wait_reason",reason).put("next_eligible_at",reason.equals("outside_night_window")?ContinuousPolicy.nextWindow(p):System.currentTimeMillis()+(reason.equals("interactive_query")?1000:900000)).put("slice",p.getLong("slice")+1);save(p);}
}
