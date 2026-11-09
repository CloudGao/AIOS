package os.aios.core;
import android.content.*;
/** ROM broadcast wiring; durable reconciliation remains owned by the fixed core. */
public final class CoreBootReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(i.getAction()))return;CoreHostLifecycle.initializeProvider(c);CoreState.automation().reconcileAsync();PendingResult pending=goAsync();new Thread(()->{try{DreamScheduler.restore(c.getApplicationContext());}catch(Exception e){android.util.Log.e("AIOSCoreDream","Schedule restore failed",e);}finally{pending.finish();}},"AIOS-dream-restore").start();}}
