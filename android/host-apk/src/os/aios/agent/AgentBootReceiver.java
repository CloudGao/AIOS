package os.aios.agent;
import android.content.*;
/** Restore only through Android-approved startup; a rejected wake is never recorded as execution. */
public final class AgentBootReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  String action=intent.getAction();
  if(!(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))||!Authorization.enabled(context))return;
  try{context.startForegroundService(new Intent().setComponent(os.aios.host.HostBindings.agentService()));context.getSharedPreferences("apk_host",0).edit().remove("wake_error").apply();}
  catch(RuntimeException denied){context.getSharedPreferences("apk_host",0).edit().putString("wake_error",denied.getClass().getSimpleName()).apply();android.util.Log.w("AIOSApk","Background startup requires user action",denied);}
 }
}
