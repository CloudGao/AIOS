package com.android.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;

/** Narrow system-owned identity checks for the AIOS assistant panel. */
public final class AiosAssistantPolicy {
    private static final String PACKAGE = "os.aios.agent";
    private AiosAssistantPolicy() {}

    public static boolean isSignedAgent(Context context, int uid, String packageName) {
        if (!PACKAGE.equals(packageName)) return false;
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getPackageUidAsUser(PACKAGE, UserHandle.getUserId(uid)) == uid
                    && pm.checkSignatures(uid, Process.SYSTEM_UID) == PackageManager.SIGNATURE_MATCH;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean mayBypassService(Context context, ComponentName component,
            android.content.pm.ServiceInfo service, int userId) {
        return service != null && service.applicationInfo != null
                && UserHandle.getUserId(service.applicationInfo.uid) == userId
                && isSignedAgent(context, service.applicationInfo.uid, service.packageName)
                && mayBypass(context, component, userId);
    }

    public static boolean mayBypass(Context context, ComponentName component, int userId) {
        if (component == null || !PACKAGE.equals(component.getPackageName())
                || !(PACKAGE + ".ForegroundUiService").equals(component.getClassName())) return false;
        try {
            int uid = context.getPackageManager().getPackageUidAsUser(PACKAGE, userId);
            return isSignedAgent(context, uid, PACKAGE)
                    && Settings.Secure.getIntForUser(context.getContentResolver(),
                            "aios_assistant_ui_control_enabled", 0, userId) == 1
                    && !context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isPanel(Context context, int uid, WindowManager.LayoutParams attrs) {
        return attrs.type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                && "AIOS Assistant Panel".contentEquals(attrs.getTitle())
                && isSignedAgent(context, uid, attrs.packageName);
    }
}
