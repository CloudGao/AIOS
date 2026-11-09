package os.aios.agent;

import android.content.*;
import android.os.PowerManager;
import android.media.AudioManager;
import androidx.work.*;
import org.json.*;
import os.aios.workspace.WorkspaceStore;
import java.time.*;
import java.util.concurrent.TimeUnit;

/** Constraint-driven background work. No resident polling loop, no phone UI control. */
public final class DreamWorker extends Worker {
 static final String PREFS="aios_dreaming";
 private volatile CodexAccountClient client;
 private volatile String thread="",turn="";
 private static volatile DreamWorker active;
 private volatile boolean preempted;
 private NativeThreadPager history;
 private String dagRun="",dagOwner="";private int dagGeneration;
 static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
 public DreamWorker(Context c,WorkerParameters p){super(c,p);}
 static void ensureScheduled(Context c){register(c,ExistingWorkPolicy.KEEP);}
 static void schedule(Context c){register(c,ExistingWorkPolicy.REPLACE);}
 private static void register(Context c,ExistingWorkPolicy policy){
  WorkManager.getInstance(c).enqueueUniqueWork("aios-dream-registration",policy,new OneTimeWorkRequest.Builder(DreamScheduleWorker.class).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build());
 }
 static void manual(Context c){
  try{new os.aios.runtime.CoreRuntimeClient(c).call("processing","dream.trigger",new JSONObject());}catch(Exception e){throw new IllegalStateException("Core Event Runtime unavailable",e);}
 }
 static boolean ownsResource(String owner){DreamWorker w=active;return w!=null&&owner.equals("dream:"+w.getId());}
 static void preempt(){DreamWorker running=active;if(running!=null){running.preempted=true;new Thread(running::interrupt,"AIOS-review-interrupt").start();}}
 private void interrupt(){CodexAccountClient c=client;String t=thread,u=turn;
  if(c==null||t.isEmpty())return;
  try{
   // A response can be lost after turn/start. Inspect this dedicated thread, never resend.
   if(u.isEmpty()){
    JSONObject state=new NativeThreadPager(this::rpc).current(t,u);JSONArray turns=state.optJSONArray("turns");
    if(turns!=null&&turns.length()>0){JSONObject last=turns.getJSONObject(turns.length()-1);if(!java.util.Set.of("completed","failed","interrupted").contains(last.optString("status")))u=last.getString("id");}
   }
   if(!u.isEmpty())rpc("turn/interrupt",new JSONObject().put("threadId",t).put("turnId",u));
  }catch(Exception ignored){}
 }
 @Override public void onStopped(){preempted=true;interrupt();}
 private JSONObject rpc(String method,JSONObject p)throws Exception{return client.call(new JSONObject().put("op","rpc").put("method",method).put("params",p));}
 private String blocked(boolean manual)throws Exception{
  Context c=getApplicationContext();
  if(!Authorization.enabled(c))return "authorization";
  if(!prefs(c).getBoolean("enabled",true))return "disabled";
  if(preempted||isStopped())return "preempted";
  String state=AssistantConversation.state(c).optString("status");if(state.equals("queued")||state.equals("running"))return "foreground";
  PowerManager power=c.getSystemService(PowerManager.class);
  if(power.isPowerSaveMode())return "power_save";
  if(power.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_MODERATE)return "thermal";
  if(!manual&&power.isInteractive())return "screen";
  AudioManager audio=c.getSystemService(AudioManager.class);
  if(audio.getMode()!=AudioManager.MODE_NORMAL)return "audio";
  if(!audio.getActiveRecordingConfigurations().isEmpty())return "recording";
  return "";
 }
 private boolean available(boolean manual)throws Exception{String reason=blocked(manual);if(!reason.isEmpty())note(reason);return reason.isEmpty();}
 private void note(String code){prefs(getApplicationContext()).edit().putString("last_status_code",code).putLong("last_checked",System.currentTimeMillis()).apply();}
 static String statusText(Context c){
  android.content.SharedPreferences p=prefs(c);
  if(!p.getBoolean("enabled",true))return c.getString(R.string.dream_status_disabled);
  if(p.getBoolean("schedule_error",false))return c.getString(R.string.dream_status_schedule_error);
  String code=p.getString("last_status_code","");
  int id=c.getResources().getIdentifier("dream_status_"+code,"string",c.getPackageName());
  return id==0?p.getString("last_status",c.getString(R.string.dream_status_never)):c.getString(id);
 }
 private void beginLedger(String occurrence)throws Exception{
  os.aios.automation.AutomationService a=os.aios.automation.AutomationService.get();
  a.putAuthorization(new JSONObject().put("id","aios.dreaming.preference").put("subject","dreaming").put("task_id","").put("approved_version",1).put("capabilities",new JSONArray().put("scheduler.execute")).put("resources",new JSONArray()));
  try{a.call("graph.get",new JSONObject().put("id","aios.dreaming"));}catch(Exception missing){a.call("graph.put",new JSONObject().put("id","aios.dreaming").put("nodes",new JSONArray().put(new JSONObject().put("id","window").put("kind","trigger").put("trigger",new JSONObject().put("type","time").put("not_before",0))).put(new JSONObject().put("id","review").put("kind","action").put("side_effect","non_idempotent_write").put("queryable",true))).put("edges",new JSONArray().put(new JSONObject().put("from","window").put("to","review"))));}
  JSONObject run=a.call("run.start",new JSONObject().put("graph_id","aios.dreaming").put("occurrence",occurrence).put("authorization_id","aios.dreaming.preference"));dagRun=run.getString("id");dagOwner="dream:"+getId();
  // Refresh dependencies after the immediate time trigger, independent of node row order.
  a.call("run.ready",new JSONObject().put("id",dagRun));
  JSONObject claimed=a.call("node.claim",new JSONObject().put("run_id",dagRun).put("node_id","review").put("owner",dagOwner).put("lease_ms",300000));dagGeneration=claimed.getInt("generation");
 }
 private void finishLedger(String state,String detail){if(dagRun.isEmpty())return;try{os.aios.automation.AutomationService.get().call("node.finish",new JSONObject().put("run_id",dagRun).put("node_id","review").put("owner",dagOwner).put("generation",dagGeneration).put("state",state).put("result",new JSONObject().put("detail",detail)));}catch(Exception e){android.util.Log.e("AIOSDream","Could not finalize DAG ledger",e);}}
 @Override public Result doWork(){
  Context c=getApplicationContext();String batch="";boolean submitted=false;String phase="eligibility";long began=android.os.SystemClock.elapsedRealtime();
  try(WorkspaceStore store=new WorkspaceStore(c)){
   boolean manual=getInputData().getBoolean("manual",false);
   if(!prefs(c).getBoolean("enabled",true)){note("disabled");return Result.success();}
   if(!available(manual))return Result.retry();
   if(!new os.aios.runtime.CoreRuntimeClient(c).call("internal","assistant.external.acquire",new JSONObject().put("owner","dream:"+getId()).put("process_id",android.os.Process.myPid())).optBoolean("acquired")){note("capacity");return Result.retry();}active=this;
   ZonedDateTime now=ZonedDateTime.now();LocalDate day=now.toLocalDate();if(now.getHour()<3)day=day.minusDays(1);
   int limit=prefs(c).getBoolean("twice",false)?2:1;boolean afternoon=limit==2&&(manual||now.getHour()>=12);
   long start=day.atTime(3,0).atZone(now.getZone()).toInstant().toEpochMilli();
   boolean weekly=!manual&&day.getDayOfWeek()==DayOfWeek.SUNDAY&&!afternoon;
   String window=manual?"manual/"+getId():day+"/"+(afternoon?"noon":"night");
   phase="snapshot";JSONObject snapshot=store.dream("prepare",new JSONObject().put("window",window).put("day_start",start).put("daily_limit",limit).put("weekly",weekly).put("manual",manual));
   if(snapshot.has("skip")){String skip=snapshot.optString("skip");note(skip);return skip.equals("busy")?Result.retry():Result.success();}
   batch=snapshot.getString("batch_id");note("preparing");
   beginLedger(batch); // Each unsubmitted retry has a fresh preparation/ledger; no failed node is reclaimed.
   if(!available(manual))throw new InterruptedException();
   phase="model";client=new CodexAccountClient(c);history=new NativeThreadPager(this::rpc);client.call(new JSONObject().put("op","start").put("mode","live"));
   String model=prefs(c).getString("model","");
   JSONObject catalog=client.call(new JSONObject().put("op","accounts/models"));JSONArray models=catalog.optJSONArray("items");
   if(model.isEmpty()){
    // Prefer an available mini, otherwise the catalog default; price is not advertised by this API.
    String fallback="";
    if(models!=null)for(int i=0;i<models.length();i++){
     JSONObject m=models.getJSONObject(i);JSONArray options=m.optJSONArray("supportedReasoningEfforts");boolean low=false;
     if(options!=null)for(int j=0;j<options.length();j++)if(options.getJSONObject(j).optString("reasoningEffort").equals("low"))low=true;
     if(!low)continue;String id=m.optString("model");if(m.optBoolean("isDefault"))fallback=id;
     if(id.toLowerCase(java.util.Locale.ROOT).contains("mini")){model=id;break;}
    }
    if(model.isEmpty())model=fallback;if(model.isEmpty())throw new IllegalStateException("dream_model_unavailable");
    if(!prefs(c).edit().putString("model",model).commit())throw new IllegalStateException("dream_model_persist_failed");
   }
   boolean supported=false;
   if(models!=null)for(int i=0;i<models.length();i++){JSONObject m=models.getJSONObject(i);if(!model.equals(m.optString("model")))continue;JSONArray efforts=m.optJSONArray("supportedReasoningEfforts");if(efforts!=null)for(int j=0;j<efforts.length();j++)if("low".equals(efforts.getJSONObject(j).optString("reasoningEffort")))supported=true;}
   if(!supported)throw new IllegalStateException("dream_model_unavailable");
   JSONArray jobs=os.aios.automation.AutomationService.get().call("list",new JSONObject()).getJSONArray("items"),shortJobs=new JSONArray();
   for(int i=0;i<Math.min(20,jobs.length());i++){JSONObject job=jobs.getJSONObject(i);JSONObject shortJob=new JSONObject();for(String key:new String[]{"id","title","enabled","schedule","revision"})if(job.has(key))shortJob.put(key,job.get(key));if(shortJobs.toString().length()+shortJob.toString().length()<1200)shortJobs.put(shortJob);}
   snapshot.put("schedules",shortJobs).put("timezone",now.getZone().getId());
   String input=snapshot.toString();if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>224000)throw new IllegalStateException("dream_input_capacity");
   prefs(c).edit().putInt("last_input_characters",input.length()).apply();
   phase="thread";JSONObject created=client.call(new JSONObject().put("op","dream/thread/start").put("params",new JSONObject().put("model",model)));
   thread=created.getJSONObject("thread").getString("id");new os.aios.runtime.CoreRuntimeClient(c).call("internal","assistant.external.bind",new JSONObject().put("owner","dream:"+getId()).put("thread_id",thread));
   store.dream("checkpoint",new JSONObject().put("id",batch).put("thread_id",thread));
   if(!available(manual))throw new InterruptedException();
   // Persist the batch before this non-idempotent request; an uncertain response is never resent.
   if(android.os.SystemClock.elapsedRealtime()-began>=5*60000L)throw new IllegalStateException("dream_prepare_timeout");
   long eventCursor=client.call(new JSONObject().put("op","status")).optLong("cursor");
   store.dream("checkpoint",new JSONObject().put("id",batch).put("submitted",true));
   phase="submission";submitted=true;note("submitted");JSONObject started=rpc("turn/start",new JSONObject().put("threadId",thread).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",input))));
   turn=started.getJSONObject("turn").getString("id");store.dream("checkpoint",new JSONObject().put("id",batch).put("turn_id",turn));
   AssistantLiveTurn live=new AssistantLiveTurn(thread,turn);
   long deadline=began+5*60000L;
   MinutesTurnResult.TerminalObservation terminal=new MinutesTurnResult.TerminalObservation();
   while(android.os.SystemClock.elapsedRealtime()<deadline){
    if(!available(manual))throw new InterruptedException();
    try{JSONObject events=client.call(new JSONObject().put("op","events").put("after",eventCursor).put("wait_ms",500));
     eventCursor=events.optLong("cursor",eventCursor);JSONArray eventItems=events.optJSONArray("items");
     if(eventItems!=null)for(int i=0;i<eventItems.length();i++)live.accept(eventItems.getJSONObject(i));
    }catch(InterruptedException e){throw e;}catch(Exception temporary){android.util.Log.w("AIOSDream","Live completion observation unavailable",temporary);}
    if(live.completedWithFinalReply()){
     phase="validation";JSONObject output=parseOutput(live.speakable(),batch,snapshot);JSONObject saved=store.dream("commit",output);
     finishLedger("succeeded","Dream proposals committed from completed event");note(saved.optInt("proposed")==0?"complete_empty":"complete_changes");return Result.success();
    }
    JSONObject result;
    try{JSONObject nativeThread=history.current(thread,turn);result=MinutesTurnResult.find(nativeThread,turn,"");}
    catch(InterruptedException e){throw e;}catch(Exception temporary){terminal.reset();Thread.sleep(3000);continue;}
    boolean confirmed=terminal.observe(result);
    if(result!=null){String status=result.optString("status");
     if(status.equals("completed")){
      phase="validation";JSONObject output=parseOutput(MinutesTurnResult.reply(result),batch,snapshot);
      JSONObject saved=store.dream("commit",output);finishLedger("succeeded","Dream proposals committed");note(saved.optInt("proposed")==0?"complete_empty":"complete_changes");return Result.success();
     }
     if((status.equals("failed")||status.equals("interrupted"))&&confirmed)throw new IllegalStateException("dream_model_failed");
    }
    Thread.sleep(3000);
   }
   throw new IllegalStateException("dream_timeout");
  }catch(Exception failure){
   interrupt();
   android.util.Log.e("AIOSDream","Review failed at "+(submitted?"submitted":"preparation"),failure);
   String code="dream_model_unavailable".equals(failure.getMessage())?"model_unavailable":failure instanceof InterruptedException?"preempted":submitted?(phase.equals("validation")?"validation_failed":"result_failed"):"prepare_failed";
   // Preserve a machine-readable phase and error type without storing model/source text.
   String detail=code+":"+phase+":"+failure.getClass().getSimpleName();
   if(!(failure instanceof InterruptedException))note(code);
   finishLedger(preempted||failure instanceof InterruptedException?"paused":submitted?"outcome_unknown":"failed",detail);
   if(!batch.isEmpty())try(WorkspaceStore store=new WorkspaceStore(c)){store.dream("checkpoint",new JSONObject().put("id",batch).put("status","interrupted").put("summary",detail));}catch(Exception ignored){}
   // A submitted or uncertain turn is never automatically replayed by this work item.
   return !submitted&&!code.equals("model_unavailable")&&getRunAttemptCount()<3?Result.retry():Result.success();
  }finally{
   boolean confirmed=!submitted;
   if(submitted&&client!=null)try{JSONArray turns=history.current(thread,turn).optJSONArray("turns");if(turns!=null&&turns.length()>0)confirmed=java.util.Set.of("completed","failed","interrupted").contains(turns.getJSONObject(turns.length()-1).optString("status"));}catch(Exception unknown){}
   if(confirmed)try{new os.aios.runtime.CoreRuntimeClient(c).call("internal","assistant.external.release",new JSONObject().put("owner","dream:"+getId()));}catch(Exception ignored){}
   if(active==this)active=null;if(client!=null)client.close();
  }
 }
 private static JSONObject parseOutput(String value,String batch,JSONObject snapshot)throws Exception{
  String reply=value.trim();if(reply.length()>64000)throw new IllegalStateException("dream_output_capacity");
  if(reply.startsWith("```json")&&reply.endsWith("```"))reply=reply.substring(7,reply.length()-3).trim();
  JSONObject output=new JSONObject(reply);if(!batch.equals(output.optString("batch_id")))throw new IllegalStateException("dream_batch_mismatch");
  // Models occasionally repeat the session component in a source id, and may
  // leave evidence used by a proposal in the partial cursor. Normalize only to
  // a unique source from this immutable batch; unknown evidence still fails in
  // WorkspaceDream.commit().
  java.util.Set<String> allowed=new java.util.HashSet<>(),reviewed=new java.util.HashSet<>();
  JSONArray sources=snapshot.getJSONArray("sources");for(int i=0;i<sources.length();i++)allowed.add(sources.getJSONObject(i).getString("id"));
  JSONArray changes=output.optJSONArray("changes");if(changes==null)changes=new JSONArray();
  for(int i=0;i<changes.length();i++){JSONArray refs=changes.getJSONObject(i).optJSONArray("source_ids");if(refs==null)continue;for(int j=0;j<refs.length();j++){String id=sourceId(refs.getString(j),allowed);refs.put(j,id);reviewed.add(id);}}
  JSONArray rawRemaining=output.optJSONArray("remaining_source_ids"),normalized=new JSONArray();java.util.Set<String> seen=new java.util.HashSet<>();
  if(rawRemaining!=null)for(int i=0;i<rawRemaining.length();i++){String id=sourceId(rawRemaining.getString(i),allowed);if(!reviewed.contains(id)&&seen.add(id))normalized.put(id);}
  output.put("remaining_source_ids",normalized);
  // The cursor, not explanatory prose, decides whether this bounded batch is
  // complete. Sources omitted by prepare() were never part of this batch and
  // remain unprocessed for the next run.
  JSONArray remaining=output.optJSONArray("remaining_source_ids");
  if("partial".equals(output.optString("status"))&&remaining!=null&&remaining.length()==0)output.put("status","complete");
  return output;
 }
 private static String sourceId(String value,java.util.Set<String> allowed){
  if(allowed.contains(value))return value;String tail=value.substring(value.lastIndexOf(':')+1),match="";
  for(String candidate:allowed)if(candidate.endsWith(":"+tail)){if(!match.isEmpty())return value;match=candidate;}
  return match.isEmpty()?value:match;
 }

}
