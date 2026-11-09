package os.aios.agent;

import android.content.Context;
import os.aios.device.*;

/** Shared binding slot. Only the trusted Application host installs an implementation. */
final class DeviceHost {
    interface Binding {
        HostUi ui();
        ScreenBackend screens(Context context);
        DeviceBackend platform(Context context,DeviceBackend.ApplicationLauncher launcher);
        DeviceTasks tasks(Context context);
        boolean configureForeground(android.accessibilityservice.AccessibilityService service);
        boolean physicalPointerDown(android.view.MotionEvent event);
        void synchronizeAuthorization(Context context,boolean enabled);
        boolean debuggableSystem();
        org.json.JSONObject probe(Context context,String module)throws Exception;
    }
    private static Binding binding;
    static synchronized void install(Binding value){
        if(value==null||binding!=null&&binding!=value)throw new IllegalStateException("device_binding_conflict");
        binding=value;
    }
    private static synchronized Binding get(){
        if(binding==null)throw new IllegalStateException("device_binding_not_initialized");
        return binding;
    }
    static HostUi ui(){return get().ui();}
    static ScreenBackend screens(Context context){return get().screens(context);}
    static DeviceBackend platform(Context context,DeviceBackend.ApplicationLauncher launcher){return get().platform(context,launcher);}
    static DeviceTasks tasks(Context context){return get().tasks(context);}
    static boolean configureForeground(android.accessibilityservice.AccessibilityService service){return get().configureForeground(service);}
    static boolean physicalPointerDown(android.view.MotionEvent event){return get().physicalPointerDown(event);}
    static void synchronizeAuthorization(Context context,boolean enabled){get().synchronizeAuthorization(context,enabled);}
    static boolean debuggableSystem(){return get().debuggableSystem();}
    static org.json.JSONObject probe(Context context,String module)throws Exception{return get().probe(context,module);}
}
