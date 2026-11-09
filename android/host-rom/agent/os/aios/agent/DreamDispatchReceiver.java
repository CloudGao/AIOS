package os.aios.agent;
import android.content.*;
import androidx.work.*;
/** ROM wake-to-worker wiring. Scheduling authority remains in AIOSCoreServices. */
public final class DreamDispatchReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){
  String occurrence=i.getStringExtra("occurrence");if(occurrence==null||occurrence.isEmpty())return;
  boolean manual=i.getBooleanExtra("manual",false);
  Data input=new Data.Builder().putString("occurrence",occurrence).putBoolean("manual",manual).build();
  Constraints.Builder limits=new Constraints.Builder().setRequiresBatteryNotLow(true);
  if(!manual)limits.setRequiredNetworkType(NetworkType.CONNECTED).setRequiresCharging(true).setRequiresStorageNotLow(true);
  WorkManager.getInstance(c).enqueueUniqueWork("aios-dream-action:"+occurrence,ExistingWorkPolicy.KEEP,new OneTimeWorkRequest.Builder(DreamWorker.class).setConstraints(limits.build()).setInputData(input).build());
 }
}
