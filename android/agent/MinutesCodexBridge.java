package os.aios.agent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Phone-only consumer of the signature protected recording-summary queue. */
final class MinutesCodexBridge implements FeatureConsumer {
 private static final Uri URI=Uri.parse("content://os.aios.speech.minutes");
 private final Context context;
 private final CodexAccountClient codex;
 private final NativeThreadPager history;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final AtomicBoolean closed=new AtomicBoolean();
 MinutesCodexBridge(Context c){context=c.getApplicationContext();codex=new CodexAccountClient(c);history=new NativeThreadPager(this::rpc);}
 public void start(){worker.execute(this::loop);}
 private JSONObject provider(String method,JSONObject data)throws Exception {
  Bundle args=new Bundle();if(data!=null)args.putString("json",data.toString());
  Bundle result=context.getContentResolver().call(URI,method,null,args);
  if(result==null)throw new IllegalStateException("纪要服务未返回结果");
  return new JSONObject(result.getString("json","{}"));
 }
 private JSONObject rpc(String method,JSONObject params)throws Exception {
  return codex.call(new JSONObject().put("op","rpc").put("method",method).put("params",params));
 }
 private void update(JSONObject job,String status,String reply,String error)throws Exception {
  provider("update",new JSONObject(job.toString()).put("status",status).put("reply",reply).put("error",error));
 }
 private void loop(){
  while(!closed.get())try{
   if(!Authorization.enabled(context)){Thread.sleep(10000);continue;}
   JSONObject job=provider("next",null);
   if(job.optString("request_id").isEmpty()){Thread.sleep(10000);continue;}
   try{process(job);}catch(InterruptedException e){throw e;}
   catch(Exception failure){
    // Never log transcript content or resend a turn with an uncertain outcome.
    if(!closed.get())update(job,"failed","","手机 AIOS 总结未完成，请检查账号或网络；未自动重复提交。");
   }
  }catch(InterruptedException e){Thread.currentThread().interrupt();return;}
   catch(Exception unavailable){try{Thread.sleep(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}}
 }
 private void process(JSONObject job)throws Exception {
  if(!Authorization.enabled(context))return;
  String requestId=job.getString("request_id"),marker="[AIOS-MINUTES:"+requestId+":"+job.optInt("attempt",0)+"]";
  codex.call(new JSONObject().put("op","start").put("mode","live"));
  if("queued".equals(job.getString("status"))){
   JSONObject created=codex.call(new JSONObject().put("op","minutes/thread/start"));
   job.put("thread_id",created.getJSONObject("thread").getString("id")).put("turn_id","");
   // Persist before the non-idempotent request. Recovery must inspect history.
   update(job,"running","","");job.put("status","running");
   String text=marker+"\n此前累计纪要（仅作待核对材料）：\n"+job.optString("previous_summary")+"\n"+job.getString("text");
   try{
    JSONObject started=rpc("turn/start",new JSONObject().put("threadId",job.getString("thread_id"))
      .put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",text))));
    job.put("turn_id",started.getJSONObject("turn").getString("id"));update(job,"running","","");
   }catch(InterruptedException e){throw e;}
   catch(Exception uncertain){/* Read the durable thread; never resend. */}
  }
  if(job.optString("thread_id").isEmpty())throw new IllegalStateException("缺少纪要会话关联");
  long began=job.optLong("started_ms",0);
  long deadline=(began>0?began:System.currentTimeMillis())+15*60_000L;
  MinutesTurnResult.TerminalObservation terminal=new MinutesTurnResult.TerminalObservation();
  while(!closed.get()&&System.currentTimeMillis()<deadline){
   if(!Authorization.enabled(context)){
    if(!job.optString("turn_id").isEmpty())try{rpc("turn/interrupt",new JSONObject().put("threadId",job.getString("thread_id")).put("turnId",job.getString("turn_id")));}catch(Exception ignored){}
    update(job,"interrupted","","AIOS 授权已关闭，转写已保留");return;
   }
   try{
    JSONObject thread=history.current(job.getString("thread_id"),job.optString("turn_id"));
    JSONObject turn=MinutesTurnResult.find(thread,job.optString("turn_id"),marker);
    boolean terminalConfirmed=terminal.observe(turn);
    if(turn!=null){
     if(job.optString("turn_id").isEmpty()){job.put("turn_id",turn.getString("id"));update(job,"running","","");}
     String status=turn.optString("status");
     if("completed".equals(status)){
      String reply=MinutesTurnResult.reply(turn);
      job.put("retry_safe",reply.isBlank());update(job,reply.isBlank()?"failed":"completed",reply,reply.isBlank()?"AIOS 已结束但没有返回纪要":"");return;
     }
     if(("failed".equals(status)||"interrupted".equals(status))&&terminalConfirmed){
      job.put("retry_safe",true);update(job,status,"","手机 AIOS 本轮未完成，转写已保留");return;
     }
    }
   }catch(InterruptedException e){throw e;}
    catch(Exception temporarilyUnavailable){terminal.reset();/* Retain the job and its IDs. */}
   Thread.sleep(5000);
  }
  if(!closed.get())update(job,"failed","","纪要结果超时或提交状态未知；转写已保留，未自动重复提交。");
 }
 public void close(){closed.set(true);worker.shutdownNow();codex.close();}
}
