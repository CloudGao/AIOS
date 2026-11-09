package os.aios.agent;

import android.content.Context;
import androidx.work.*;

/** Stable persisted Worker entry; removing governance never removes this compatibility endpoint. */
public final class NotificationPolicyWorker extends Worker {
    private final WorkerParameters parameters;
    public NotificationPolicyWorker(Context context,WorkerParameters parameters){
        super(context,parameters);this.parameters=parameters;
    }
    static void enqueue(Context context,String kind,String key){
        if(!os.aios.host.HostProduct.includes("notification-governance"))return;
        WorkManager.getInstance(context).enqueueUniqueWork("notification:"+kind+":"+key,ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(NotificationPolicyWorker.class)
                .setInputData(new Data.Builder().putString("kind",kind).putString("key",key).build()).build());
    }
    @Override public Result doWork(){
        return AgentFeatureBindings.notificationWork(getApplicationContext(),parameters);
    }
}
