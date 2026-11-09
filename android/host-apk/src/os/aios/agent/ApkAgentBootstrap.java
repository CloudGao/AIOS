package os.aios.agent;
public final class ApkAgentBootstrap {
    private ApkAgentBootstrap() {}
    public static void install(){DeviceHost.install(ApkDeviceBindings.INSTANCE);AgentFeatureInstaller.install();}
    public static void initialize(android.content.Context context){AgentHostLifecycle.initialize(context);}
}
