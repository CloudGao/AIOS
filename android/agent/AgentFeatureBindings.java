package os.aios.agent;

/** Shared contribution contracts, populated once by trusted host initialization. */
final class AgentFeatureBindings {
    interface ConsumerFactory{FeatureConsumer create(android.content.Context context);}
    interface OperationsFactory{DeviceOperationRegistry create(android.content.Context context);}
    interface Work{
        androidx.work.ListenableWorker.Result run(android.content.Context context,androidx.work.WorkerParameters parameters);
    }
    private static boolean installed;
    private static NotificationAccess source;
    private static NotificationHooks hooks;
    private static ConsumerFactory minutes;
    private static Work notificationWork;
    private static OperationsFactory operations;
    static synchronized void install(NotificationAccess selectedSource,NotificationHooks selectedHooks,ConsumerFactory selectedMinutes,Work selectedWork,OperationsFactory selectedOperations){
        if(installed)throw new IllegalStateException("agent_features_already_bound");
        source=selectedSource;hooks=selectedHooks;minutes=selectedMinutes;notificationWork=selectedWork;operations=selectedOperations;installed=true;
    }
    private static synchronized void require(){if(!installed)throw new IllegalStateException("agent_features_not_initialized");}
    static NotificationAccess notificationSource(){require();return source;}
    static NotificationHooks notifications(){require();return hooks;}
    static DeviceOperationRegistry operations(android.content.Context context){require();return operations.create(context);}
    static FeatureConsumer minutes(android.content.Context context){require();return minutes.create(context);}
    static androidx.work.ListenableWorker.Result notificationWork(android.content.Context context,androidx.work.WorkerParameters parameters){require();return notificationWork.run(context,parameters);}
}
