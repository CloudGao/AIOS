package os.aios.permissionanchor;
import android.app.*;import android.content.*;import android.os.*;
/** Private, signature-protected holder for an AIOS permission request's original device. */
public class MainActivity extends Activity {
 String token;boolean registered;
 final BroadcastReceiver end=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(token!=null&&token.equals(i.getStringExtra("token")))finish();}};
 public void onCreate(Bundle b){super.onCreate(b);token=getIntent().getStringExtra("token");if(token==null||getDisplay().getDisplayId()==0){finish();return;}
  registerReceiver(end,new IntentFilter("os.aios.permissionanchor.FINISH"),"os.aios.permission.CONTROL_SESSION",null,Context.RECEIVER_EXPORTED);registered=true;
  android.widget.TextView text=new android.widget.TextView(this);text.setText("AI 已暂停，等待主屏权限处理");setContentView(text);
 }
 protected void onResume(){super.onResume();if(isFinishing())return;ResultReceiver reply=getIntent().getParcelableExtra("ready",ResultReceiver.class);if(reply!=null){Bundle data=new Bundle();data.putInt("display_id",getDisplay().getDisplayId());reply.send(0,data);}}
 protected void onDestroy(){if(registered)unregisterReceiver(end);super.onDestroy();}
}
