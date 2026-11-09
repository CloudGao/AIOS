package os.aios.device;

import android.content.Context;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.util.Size;
import android.view.View;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import java.util.concurrent.Executor;

/** Mechanism only. Ownership, observation tokens, leases and retries belong to shared policy. */
public interface ScreenBackend {
    int BLOCK_REASON_UNKNOWN = 0;
    int BLOCK_REASON_SECURE_CONTENT = 1;
    int BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH = 2;
    int BLOCK_REASON_CALLER_INITIATED = 3;
    int BLOCK_REASON_AUTHENTICATION_PROMPT_REQUESTED = 4;
    /** requestId uniquely identifies one creation attempt, including delayed callbacks. */
    void create(String requestId, String name, List<String> packages, Executor executor, Creation callback);
    /** Read-only reconciliation. Missing records or disconnected owners are UNKNOWN, never RELEASED. */
    ReleaseState releaseState(String requestId, int lastKnownDisplay);
    enum ReleaseState { RELEASED, PRESENT, UNKNOWN }
    String implementationId();
    Preview preview(Context context);

    interface Creation {
        void onSessionPending(IntentSender sender);
        void onSessionCreationFailed(int code);
        void onSessionCreated(Session session);
        void onSessionClosed();
    }
    interface Lifecycle {
        void onActive();
        void onBlocked(int reason, String packageName);
        void onClosed(int reason);
    }
    interface Session extends AutoCloseable {
        Size getDisplaySize();
        int refreshObservationChannel() throws Exception;
        List<AccessibilityWindowInfo> getAccessibilityWindows();
        Frame capture() throws Exception;
        void setLifecycleCallback(Executor executor, Lifecycle callback);
        void launchApplication(String packageName);
        void tap(int x, int y);
        void doubleTap(int x, int y, int interval);
        void longPress(int x, int y);
        void performGesture(int[] ids, int[] counts, int[] xs, int[] ys, int hold, int duration);
        void insertText(String text, boolean replace, boolean commit);
        void back();
        void notifyBlocked();
        void requestUnblock();
        void handOverApplications();
        @Override void close();
    }
    interface Preview {
        View view();
        void attach(Session session);
        void detach();
        void setInteractive(boolean enabled);
    }
    final class Frame implements AutoCloseable {
        public final Bitmap bitmap;
        public final long timestampNs;
        public Frame(Bitmap bitmap, long timestampNs) { this.bitmap = bitmap; this.timestampNs = timestampNs; }
        @Override public void close() { bitmap.recycle(); }
    }
}
