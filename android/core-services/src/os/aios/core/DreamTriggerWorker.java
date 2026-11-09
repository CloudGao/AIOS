package os.aios.core;
import android.content.*;import androidx.work.*;import org.json.JSONObject;import os.aios.core.events.EventHub;
/** Event Runtime trigger: persist first, then dispatch the Harness-owned processor. */
public final class DreamTriggerWorker extends Worker{
 public DreamTriggerWorker(Context c,WorkerParameters p){super(c,p);}
 public Result doWork(){try{boolean manual=getInputData().getBoolean("manual",false);if(!manual&&!DreamScheduler.prefs(getApplicationContext()).getBoolean("enabled",true))return Result.success();String occurrence=(manual?"manual:":"periodic:")+System.currentTimeMillis();EventHub.publish(null,"dream.trigger",occurrence,"1",new JSONObject().put("occurrence",occurrence).put("manual",manual),true);Intent i=new Intent("os.aios.core.RUN_DREAM").setPackage("os.aios.agent").putExtra("occurrence",occurrence).putExtra("manual",manual);getApplicationContext().sendBroadcast(i);DreamScheduler.prefs(getApplicationContext()).edit().putLong("dispatched_at",System.currentTimeMillis()).apply();return Result.success();}catch(Exception e){android.util.Log.e("AIOSCoreDream","Trigger dispatch failed",e);return Result.retry();}}
}
