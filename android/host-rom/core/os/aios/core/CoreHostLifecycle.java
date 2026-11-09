package os.aios.core;

import android.app.Application;
import androidx.work.Configuration;
import os.aios.host.HostBindings;

/** Process ownership and Android worker wiring belong to the host. */
final class CoreHostLifecycle {
    private CoreHostLifecycle() {}
    static Configuration workConfiguration() {
        return new Configuration.Builder()
            .setExecutor(java.util.concurrent.Executors.newFixedThreadPool(3)).build();
    }
    static void initialize(Application application) {
        RomModelIdentity.install();
        if (HostBindings.ownsInitialization(application)) initializeProvider(application);
    }
    static void initializeProvider(android.content.Context context){
        RomModelIdentity.install();
        CoreState.prepare(context);
        CoreFeatureBindings.register(context);
        CoreState.initialize(context);
    }
}
