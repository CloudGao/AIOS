package os.aios.device.rom;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.content.Context;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.media.Image;
import android.util.Size;
import android.view.View;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.extensions.computercontrol.AiosSessionBridge;
import com.android.extensions.computercontrol.view.MirrorView;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import os.aios.device.ScreenBackend;

/** All Lineage ComputerControl and MirrorView types terminate here. */
public final class RomScreenBackend implements ScreenBackend {
    private final Context context;
    public RomScreenBackend(Context context) { this.context = context; }
    @Override public String implementationId() { return "android.companion.virtual.computercontrol.ComputerControlSession"; }
    private android.content.SharedPreferences receipts() { return context.getSharedPreferences("rom_screen_receipts",0); }
    private void receipt(String requestId, String state) {
        if(!receipts().edit().putString(requestId,state).commit())throw new IllegalStateException("screen_receipt_write_failed");
    }
    @Override public ReleaseState releaseState(String requestId,int lastKnownDisplay) {
        String proof=requestId.isEmpty()?"":receipts().getString(requestId,"");
        if(proof.equals("released"))return ReleaseState.RELEASED;
        if(proof.startsWith("display:"))try{lastKnownDisplay=Integer.parseInt(proof.substring(8));}catch(NumberFormatException invalid){return ReleaseState.UNKNOWN;}
        if(lastKnownDisplay<=0)return ReleaseState.UNKNOWN;
        android.hardware.display.DisplayManager displays=context.getSystemService(android.hardware.display.DisplayManager.class);
        if(displays==null)return ReleaseState.UNKNOWN;
        return displays.getDisplay(lastKnownDisplay)==null?ReleaseState.RELEASED:ReleaseState.PRESENT;
    }
    @Override public void create(String requestId, String name, List<String> packages, Executor executor, Creation callback) {
        if(requestId.isEmpty()||receipts().contains(requestId))throw new IllegalArgumentException("creation_request_id_reused");
        VirtualDeviceManager manager = context.getSystemService(VirtualDeviceManager.class);
        if (manager == null) { receipt(requestId,"released"); throw new IllegalStateException("virtual_device_service_unavailable"); }
        receipt(requestId,"pending"); // Persist before submission; exceptions after this point may have side effects.
        manager.requestComputerControlSession(new ComputerControlSessionParams.Builder()
            .setName(name).setTargetComputerControlVersion(4).setTargetPackageNames(packages).build(), executor,
            new ComputerControlSession.Callback() {
                public void onSessionPending(IntentSender sender) { callback.onSessionPending(sender); }
                public void onSessionCreationFailed(int code) { receipt(requestId,"released"); callback.onSessionCreationFailed(code); }
                public void onSessionCreated(ComputerControlSession value) {
                    NativeSession wrapped=new NativeSession(value,()->receipt(requestId,"released"));
                    try{int display=wrapped.displayId();if(display>0)receipt(requestId,"display:"+display);}
                    catch(Exception unavailable){android.util.Log.w("AIOSScreen","Display identity not yet available; release remains unknown",unavailable);}
                    callback.onSessionCreated(wrapped);
                }
                public void onSessionClosed() { receipt(requestId,"released"); callback.onSessionClosed(); }
            });
    }
    @Override public Preview preview(Context context) { return new NativePreview(context); }

    private static final class NativeSession implements Session {
        final ComputerControlSession nativeSession;
        final Runnable released;
        NativeSession(ComputerControlSession value,Runnable released) { nativeSession = value; this.released=released; }
        private AccessibilityDisplayProxy proxy() throws Exception {
            java.lang.reflect.Field field=ComputerControlSession.class.getDeclaredField("mAccessibilityProxy");
            field.setAccessible(true);return (AccessibilityDisplayProxy)field.get(nativeSession);
        }
        int displayId() throws Exception { return proxy().getDisplayId(); }
        @Override public Size getDisplaySize() { return nativeSession.getDisplaySize(); }
        @Override public int refreshObservationChannel() throws Exception {
            // Current ROM may lose proxy flags on early initialization/rebind. Never use display 0.
            AccessibilityDisplayProxy proxy = proxy();
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            proxy.setInstalledAndEnabledServices(Collections.singletonList(info));
            int display = proxy.getDisplayId();
            if (display <= 0) throw new SecurityException("physical_display_refused");
            return display;
        }
        @Override public List<AccessibilityWindowInfo> getAccessibilityWindows() { return nativeSession.getAccessibilityWindows(); }
        @Override public Frame capture() throws Exception {
            try (Image image = nativeSession.getScreenshot()) {
                if (image == null) return null;
                Image.Plane plane = image.getPlanes()[0];
                Bitmap full = Bitmap.createBitmap(plane.getRowStride() / plane.getPixelStride(), image.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    full.copyPixelsFromBuffer(plane.getBuffer());
                    Bitmap crop = Bitmap.createBitmap(full, 0, 0, image.getWidth(), image.getHeight());
                    if (crop != full) full.recycle();
                    return new Frame(crop, image.getTimestamp());
                } catch (RuntimeException error) { full.recycle(); throw error; }
            }
        }
        @Override public void setLifecycleCallback(Executor executor, Lifecycle callback) {
            nativeSession.setLifecycleCallback(executor, new ComputerControlSession.LifecycleCallback() {
                public void onActive() { callback.onActive(); }
                public void onBlocked(int reason, String pkg) { callback.onBlocked(reason, pkg); }
                public void onClosed(int reason) { released.run(); callback.onClosed(reason); }
            });
        }
        @Override public void launchApplication(String pkg) { nativeSession.launchApplication(pkg); }
        @Override public void tap(int x, int y) { nativeSession.tap(x, y); }
        @Override public void doubleTap(int x, int y, int interval) { nativeSession.doubleTap(x, y, interval); }
        @Override public void longPress(int x, int y) { nativeSession.longPress(x, y); }
        @Override public void performGesture(int[] ids, int[] counts, int[] xs, int[] ys, int hold, int duration) { nativeSession.performGesture(ids, counts, xs, ys, hold, duration); }
        @Override public void insertText(String text, boolean replace, boolean commit) { nativeSession.insertText(text, replace, commit); }
        @Override public void back() { nativeSession.performAction(ComputerControlSession.ACTION_GO_BACK); }
        @Override public void notifyBlocked() { nativeSession.notifyBlocked(); }
        @Override public void requestUnblock() { nativeSession.requestUnblock(); }
        @Override public void handOverApplications() { nativeSession.handOverApplications(); }
        @Override public void close() { nativeSession.close(); }
    }
    private static final class NativePreview implements Preview {
        private final MirrorView view;
        NativePreview(Context context) {
            view = new MirrorView(context);
            view.setScaleType(MirrorView.SCALE_TYPE_FIT_CENTER);
            view.setInteractive(false);
        }
        @Override public View view() { return view; }
        @Override public void attach(Session session) {
            if (!(session instanceof NativeSession)) throw new IllegalArgumentException("preview_backend_mismatch");
            view.setComputerControlSession(AiosSessionBridge.wrap(((NativeSession) session).nativeSession));
        }
        @Override public void detach() { view.setInteractive(false); view.setComputerControlSession(null); }
        @Override public void setInteractive(boolean enabled) { view.setInteractive(enabled); }
    }
}
