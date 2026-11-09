package os.aios.device.rom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;

/** ROM-only touch observing and authorization synchronization, without shared UI policy. */
public final class RomForegroundInput {
    private RomForegroundInput() {}
    public static boolean configure(AccessibilityService service) {
        AccessibilityServiceInfo info = service.getServiceInfo();
        int sources = InputDevice.SOURCE_TOUCHSCREEN | InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_STYLUS;
        boolean observing = android.view.accessibility.Flags.motionEventObserving();
        boolean allowed = observing && service.checkSelfPermission("android.permission.ACCESSIBILITY_MOTION_EVENT_OBSERVING") == PackageManager.PERMISSION_GRANTED;
        info.setMotionEventSources(allowed ? sources : 0);
        if (observing) info.setObservedMotionEventSources(allowed ? sources : 0);
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        service.setServiceInfo(info);
        AccessibilityServiceInfo applied = service.getServiceInfo();
        return allowed && applied != null && (applied.getMotionEventSources() & sources) == sources
            && (applied.getObservedMotionEventSources() & sources) == sources;
    }
    public static boolean physicalPointerDown(MotionEvent event) {
        return event.getDisplayId() == 0 && event.getDeviceId() >= 0
            && (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_SCROLL);
    }
    public static void synchronizeAuthorization(Context context, boolean enabled) {
        String key = "aios_assistant_ui_control_enabled";
        int expected = enabled ? 1 : 0;
        if (Settings.Secure.getInt(context.getContentResolver(), key, -1) != expected
            && !Settings.Secure.putInt(context.getContentResolver(), key, expected))
            throw new IllegalStateException("ui_authorization_sync_failed");
    }
}
