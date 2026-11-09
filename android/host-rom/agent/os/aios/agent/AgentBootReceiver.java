package os.aios.agent;
import android.content.*;
/** Restore phone-owned queues after reboot or package replacement; never starts recording. */
public final class AgentBootReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  String action=intent.getAction();
  if((Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))&&Authorization.enabled(context))
   context.startForegroundService(new Intent().setComponent(os.aios.host.HostBindings.agentService()));
 }
}
