package os.aios.agent;

import android.content.Context;
import androidx.work.*;
import org.json.JSONObject;

/** Durable registration; never blocks Application startup on the Core provider. */
public final class DreamScheduleWorker extends Worker {
 public DreamScheduleWorker(Context c,WorkerParameters p){super(c,p);}
 @Override public Result doWork(){
  Context c=getApplicationContext();
  try{
   new os.aios.runtime.CoreRuntimeClient(c).call("processing","dream.schedule",new JSONObject().put("enabled",DreamWorker.prefs(c).getBoolean("enabled",true)).put("twice",DreamWorker.prefs(c).getBoolean("twice",false)));
   DreamWorker.prefs(c).edit().remove("schedule_error").apply();return Result.success();
  }catch(Exception e){
   DreamWorker.prefs(c).edit().putBoolean("schedule_error",true).apply();
   android.util.Log.e("AIOSDream","Schedule registration failed; retry retained",e);return Result.retry();
  }
 }
}
