package os.aios.core.automation;
import android.content.*;
public final class AutomationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){AutomationService.get().reconcileAsync();}
}
