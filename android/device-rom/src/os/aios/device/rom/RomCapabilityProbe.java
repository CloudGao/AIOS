package os.aios.device.rom;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import org.json.JSONObject;

/** Read-only support/permission probe. Does not launch activities, request consent or load models. */
public final class RomCapabilityProbe {
    private RomCapabilityProbe() {}
    public static JSONObject module(Context context, String id, boolean notificationsConnected, boolean foregroundConnected, boolean observesInput) throws Exception {
        boolean supported = true, permission = true, connected = true;
        String reason = "ready";
        JSONObject limits = new JSONObject();
        switch (id) {
            case "virtual-screen":
                supported = context.getSystemService(android.companion.virtual.VirtualDeviceManager.class) != null;
                permission = granted(context, "android.permission.ACCESS_COMPUTER_CONTROL");
                limits.put("max_sessions", 4).put("scheduler_reserved", 1);
                break;
            case "foreground-control":
                supported = android.view.accessibility.Flags.motionEventObserving();
                permission = foregroundConnected;
                connected = foregroundConnected && observesInput;
                if (!connected) reason = "safe_takeover_unavailable";
                break;
            case "notification-events":
                permission = context.getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(
                    new ComponentName(context.getPackageName(), "os.aios.agent.PhoneNotifications"));
                connected = notificationsConnected;
                break;
            case "notification-governance":
                // This backend is built only with the paired Framework integration.
                supported = context.getPackageManager().checkSignatures("android", context.getPackageName()) == PackageManager.SIGNATURE_MATCH;
                break;
            case "photos":
                boolean allPhotos=granted(context,"android.permission.READ_MEDIA_IMAGES");
                permission=allPhotos||granted(context,"android.permission.READ_MEDIA_VISUAL_USER_SELECTED");
                limits.put("photo_scope",allPhotos?"authorized_primary_volume":"user_selected");
                break;
            case "recording": permission = granted(context, "android.permission.RECORD_AUDIO"); break;
            case "speech": supported = android.speech.SpeechRecognizer.isRecognitionAvailable(context); break;
            case "minutes": supported = context.getPackageManager().resolveContentProvider("os.aios.speech.minutes", 0) != null; break;
            case "miniapps": supported = installed(context, "os.aios.miniapp"); break;
            case "browser":
                supported = context.getPackageManager().resolveService(new android.content.Intent().setComponent(
                    new ComponentName("com.android.shell", "com.android.shell.AiosBrowserBridgeService")), 0) != null;
                break;
            default: break;
        }
        if (!supported) reason = "unsupported_host";
        else if (!permission) reason = "permission_required";
        else if (!connected && reason.equals("ready")) reason = "provider_disconnected";
        return new JSONObject().put("id", id).put("supported", supported).put("permission_ready", permission)
            .put("connected", connected).put("limits", limits).put("reason_code", reason);
    }
    private static boolean granted(Context context, String permission) { return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    private static boolean installed(Context context, String pkg) {
        try { context.getPackageManager().getApplicationInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException error) { return false; }
    }
}
