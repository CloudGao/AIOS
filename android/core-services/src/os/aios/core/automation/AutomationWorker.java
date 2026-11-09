package os.aios.core.automation;

import android.content.Context;
import androidx.work.*;
import java.util.concurrent.*;
import org.json.*;

public final class AutomationWorker extends Worker {
    public AutomationWorker(Context c,WorkerParameters p){super(c,p);}
    @Override public Result doWork(){
        AutomationService service=AutomationService.get();
        if(getInputData().getString("native_pipeline")!=null){try{service.runNative(getInputData().getInt("native_version",0),getInputData().getLong("native_slice",0),this::isStopped);return Result.success();}catch(Exception e){android.util.Log.e("AIOSAutomation","Native handler failed",e);return Result.failure();}}
        String id=getInputData().getString("job_id");
        long rev=getInputData().getLong("revision",0),due=getInputData().getLong("scheduled_at",0);
        boolean manual=getInputData().getBoolean("manual",false);
        String runId=getInputData().getString("token");JSONObject job;
        try{
            JSONObject current=service.call("get",new JSONObject().put("id",id));
            if(!manual&&current.getJSONObject("schedule").getString("kind").equals("interval")){
                due=current.getLong("next_at");runId=runId+":"+getId()+":"+due;
            }
            job=service.begin(id,rev,runId,due,manual,getRunAttemptCount());if(job==null)return Result.success();
        }catch(IllegalStateException busy){return getRunAttemptCount()<3?Result.retry():Result.failure();}
        catch(Exception e){return Result.failure();}
        String status="succeeded",error=null;JSONObject output=null;final String token=runId;
        try{
            output=new ScriptRuntime(getApplicationContext()).execute(job.getString("source"),new JSONObject().put("runId",runId).put("scheduledAt",due),job.getInt("timeout_ms"),new ScriptRuntime.Host(){
                public void check()throws Exception{if(isStopped())throw new CancellationException("Worker stopped");service.check(id,rev);}
                public Object invoke(String op,JSONObject args)throws Exception{return service.invoke(id,rev,token,op,args);}
            },false);
        }catch(TimeoutException e){status="timed_out";error="Execution exceeded time limit";}
        catch(CancellationException|InterruptedException|SecurityException e){status="cancelled";error=e.toString();}
        catch(Exception e){status="failed";error=e.toString();}
        try{service.finish(id,rev,runId,status,output,error,manual);}catch(Exception e){android.util.Log.e("AIOSAutomation","Could not persist result",e);return Result.retry();}
        // A script failure is a recorded occurrence, not a reason to replay business logic.
        return Result.success();
    }
}
