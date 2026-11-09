package os.aios.agent;
import android.content.*;
/** Only platform-signature peers may initiate the system takeover handshake. */
public class SessionCommandReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i) {
        c.startForegroundService(new Intent(c,AgentService.class).putExtra("session_command",true)
                .putExtra("command",i.getStringExtra("command"))
                .putExtra("package",i.getStringExtra("package"))
                .putExtra("prompt_id",i.getStringExtra("prompt_id"))
                .putExtra("task_id",i.getStringExtra("task_id"))
                .putExtra("reply",i.getParcelableExtra("reply",android.os.ResultReceiver.class)));
    }
}
