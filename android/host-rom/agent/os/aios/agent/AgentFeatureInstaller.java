package os.aios.agent;

/** Build-selected contributions; staging removes references to excluded implementations. */
final class AgentFeatureInstaller {
    private static boolean installed;
    static synchronized void install(){
        if(installed)return;
        AgentFeatureBindings.install(SOURCE,NOTIFICATIONS,AgentFeatureInstaller::minutes,AgentFeatureInstaller::notificationWork,AgentOperationContributions::create);
        installed=true;
    }
    private static final NotificationAccess SOURCE=os.aios.host.HostProduct.includes("notification-events")?new NotificationListenerAccess():null;
    static NotificationAccess notificationSource(){return SOURCE;}
    static FeatureConsumer minutes(android.content.Context context){
        return os.aios.host.HostProduct.includes("minutes")?new MinutesCodexBridge(context):null;
    }
    private static final NotificationHooks NOTIFICATIONS=os.aios.host.HostProduct.includes("notification-governance")
        ? new NotificationGovernanceHooks():null;
    static NotificationHooks notifications(){return NOTIFICATIONS;}
    static androidx.work.ListenableWorker.Result notificationWork(android.content.Context context,androidx.work.WorkerParameters parameters){
        if(!os.aios.host.HostProduct.includes("notification-governance"))
            return androidx.work.ListenableWorker.Result.failure(new androidx.work.Data.Builder().putString("reason_code","not_packaged").build());
        return new NotificationPolicyImplementation(context,parameters).doWork();
    }
}
