package os.aios.host;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;

/** Trusted, build-selected deployment bindings. Never populated from tool arguments. */
public final class HostBindings {
    private HostBindings() {}
    public static final String HOST_ID = HostEnvironment.HOST_ID;
    public static final String AGENT_PACKAGE = HostEnvironment.AGENT_PACKAGE;
    public static final String CORE_PACKAGE = HostEnvironment.CORE_PACKAGE;
    public static final String RUNTIME_PACKAGE = HostEnvironment.RUNTIME_PACKAGE;
    public static final String CORE_AUTHORITY = HostEnvironment.CORE_AUTHORITY;
    public static final String CONTROL_PERMISSION = HostEnvironment.CONTROL_PERMISSION;
    public static final String CORE_PERMISSION = HostEnvironment.CORE_PERMISSION;
    public static final Uri CORE_ENDPOINT = Uri.parse("content://" + CORE_AUTHORITY);
    public static final Uri CAPABILITY_ENDPOINT = Uri.parse("content://" + HostEnvironment.CAPABILITY_AUTHORITY);

    public static ComponentName agentService() {
        return new ComponentName(AGENT_PACKAGE, "os.aios.agent.AgentService");
    }
    public static ComponentName runtimeService() {
        return new ComponentName(RUNTIME_PACKAGE, "os.aios.codex.RuntimeService");
    }
    public static ComponentName deviceSearchService() {
        return new ComponentName(AGENT_PACKAGE, "os.aios.agent.DeviceSearchService");
    }
    public static boolean ownsInitialization(Context context) {
        return context.getPackageName().equals(Application.getProcessName());
    }
    public static Context applicationContext(Context context) {
        Context app = context.getApplicationContext();
        return app == null ? context : app;
    }
    /** Existing callers also retain their signature/permission protection. */
    public static int agentUid(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        return context.getPackageManager().getApplicationInfo(AGENT_PACKAGE, 0).uid;
    }
    public static int runtimeUid(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        return context.getPackageManager().getApplicationInfo(RUNTIME_PACKAGE, 0).uid;
    }
    public static int coreUid(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        return context.getPackageManager().getApplicationInfo(CORE_PACKAGE, 0).uid;
    }
    public static File nativePayload(Context context, String name) {
        if (!name.matches("lib[A-Za-z0-9_]+\\.so")) throw new IllegalArgumentException("invalid_payload_name");
        return new File(context.getApplicationInfo().nativeLibraryDir, name);
    }
    public static File codexExecutable(Context context) throws IOException {
        File file = nativePayload(context, "libcodex_app_server.so");
        if (!file.canExecute() && HostEnvironment.CODEX_FALLBACK != null) file = new File(HostEnvironment.CODEX_FALLBACK);
        if (!file.canExecute()) throw new IOException("codex_executable_unavailable");
        return file;
    }
    public static File bundledSearchModels() {
        return new File(HostEnvironment.SEARCH_MODELS);
    }
    public static File bundledTextModel() {
        return new File(HostEnvironment.TEXT_MODEL);
    }
    public static String systemCommandPath() { return HostEnvironment.COMMAND_PATH; }
    public static String commandShell() { return "/system/bin/sh"; }
    public static String workspaceAssets() { return "aios-workspace"; }
}
