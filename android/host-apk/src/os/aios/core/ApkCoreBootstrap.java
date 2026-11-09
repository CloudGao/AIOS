package os.aios.core;
public final class ApkCoreBootstrap {
    private ApkCoreBootstrap() {}
    public static void initialize(android.app.Application app) { CoreHostLifecycle.initialize(app); }
}
