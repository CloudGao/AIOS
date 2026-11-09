package os.aios.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.work.Configuration;
import os.aios.host.HostBindings;

/** Android process wiring only; Core retains Dream and automation scheduling authority. */
final class AgentHostLifecycle {
    private static boolean initialized;
    static Configuration workConfiguration() {
        return new Configuration.Builder().setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2)).build();
    }
    static synchronized void initialize(Context context) {
        if (initialized || !HostBindings.ownsInitialization(context)) return;
        Context app = HostBindings.applicationContext(context);
        DeviceHost.synchronizeAuthorization(app, Authorization.enabled(app));
        os.aios.automation.AutomationService.initialize(app, null);
        DreamWorker.ensureScheduled(app);
        AppCapabilityCatalog.initialize(app);
        AgentCapabilities.start(app);
        app.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                // Never trust broadcast data as authority: query Core's protected owner instead.
                DeviceHost.synchronizeAuthorization(app, Authorization.enabled(app));
            }
        }, new IntentFilter("os.aios.core.POLICY_CHANGED"), HostBindings.CORE_PERMISSION, null, Context.RECEIVER_EXPORTED);
        initialized = true;
    }
}
