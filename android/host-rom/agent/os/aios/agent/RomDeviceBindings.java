package os.aios.agent;

import android.content.Context;
import os.aios.device.ScreenBackend;

/** Product-selected factories. Shared callers do not import any ROM implementation type. */
final class RomDeviceBindings implements DeviceHost.Binding {
    private static final RomDeviceBindings INSTANCE=new RomDeviceBindings();
    static void install(){DeviceHost.install(INSTANCE);}
    private static final HostUi UI=new RomUiHost();
    public HostUi ui(){return UI;}
    public ScreenBackend screens(Context context) { return new os.aios.device.rom.RomScreenBackend(context); }
    public os.aios.device.DeviceBackend platform(Context context,os.aios.device.DeviceBackend.ApplicationLauncher launcher) { return new RomPlatformServices(context,launcher); }
    public os.aios.device.DeviceTasks tasks(Context context) { return new os.aios.device.rom.RomDeviceTasks(context); }
    public boolean configureForeground(android.accessibilityservice.AccessibilityService service) { return os.aios.device.rom.RomForegroundInput.configure(service); }
    public boolean physicalPointerDown(android.view.MotionEvent event) { return os.aios.device.rom.RomForegroundInput.physicalPointerDown(event); }
    public void synchronizeAuthorization(Context context, boolean enabled) { os.aios.device.rom.RomForegroundInput.synchronizeAuthorization(context, enabled); }
    public boolean debuggableSystem() { return android.os.Build.IS_DEBUGGABLE; }
    public org.json.JSONObject probe(Context context, String module) throws Exception {
        return os.aios.device.rom.RomCapabilityProbe.module(context,module,NotificationSources.connected(),
            ForegroundUiService.instance!=null,ForegroundUiService.instance!=null&&ForegroundUiService.instance.observesPhysicalInput);
    }
}
