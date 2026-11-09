package os.aios.speech;
import android.content.*;
public final class MeetingBootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))c.startForegroundService(new Intent(c,MeetingMonitorService.class));}
}
