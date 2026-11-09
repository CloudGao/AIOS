package os.aios.agent;
import android.content.*;import android.content.pm.PackageManager;import android.accessibilityservice.*;import android.view.*;import org.json.*;import os.aios.device.*;
final class ApkDeviceBindings implements DeviceHost.Binding {
    static final ApkDeviceBindings INSTANCE=new ApkDeviceBindings();
    private static final HostUi UI=new ApkUiHost();
    public HostUi ui(){return UI;}
    public ScreenBackend screens(Context c){return new ApkScreenBackend(c);}
    public DeviceBackend platform(Context c,DeviceBackend.ApplicationLauncher launcher){return new ApkPlatformServices(c,launcher);}
    public DeviceTasks tasks(Context c){return new ApkDeviceTasks(c);}
    public boolean configureForeground(AccessibilityService service){
        AccessibilityServiceInfo info=service.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        service.setServiceInfo(info);return AdbBridge.monitorsInput();
    }
    public boolean physicalPointerDown(MotionEvent e){return false;/* Dedicated shell monitor reports physical input without interception. */}
    public void synchronizeAuthorization(Context c,boolean enabled){if(enabled)LocalAdbActivation.startRecovery(c);}
    public boolean debuggableSystem(){return true;/* External gateway still requires shell UID and enabled ADB. */}
    public JSONObject probe(Context c,String module)throws Exception{
        boolean supported=true,permission=true,connected=true;String reason="ready";JSONObject limits=new JSONObject();
        switch(module){
            case "virtual-screen":permission=ForegroundUiService.instance!=null;connected=AdbBridge.connected();limits.put("max_sessions",4).put("scheduler_reserved",1);break;
            case "foreground-control":permission=ForegroundUiService.instance!=null;connected=AdbBridge.monitorsInput();reason="safe_takeover_unavailable";break;
            case "notification-events":permission=c.getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(c,PhoneNotifications.class));connected=NotificationSources.connected();break;
            case "photos":permission=c.checkSelfPermission("android.permission.READ_MEDIA_IMAGES")==PackageManager.PERMISSION_GRANTED||c.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==PackageManager.PERMISSION_GRANTED;break;
            case "speech":supported=!SpeechModelSelection.usesBuiltin(c,ApkSpeech.local(c))||ApkSpeech.supports(AssistantLocale.systemLocale(c));permission=c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;break;
            case "miniapps":supported=true;break;
            default:break;
        }
        if(!supported)reason="unsupported_host";else if(!permission)reason=(module.equals("virtual-screen")||module.equals("foreground-control"))?"accessibility_required":"permission_required";else if(!connected&&!module.equals("foreground-control"))reason="adb_activation_required";else if(connected)reason="ready";
        JSONObject result=new JSONObject().put("id",module).put("supported",supported).put("permission_ready",permission).put("connected",connected).put("limits",limits).put("reason_code",reason);
        if(reason.equals("accessibility_required"))result.put("required_access","accessibility").put("settings_action",android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).put("recovery_hint",c.getString(R.string.apk_permission_accessibility_help));
        return result;
    }
}
