package os.aios.tests;
import android.app.*;import android.os.*;
public final class DisplayInstrumentation extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();runOnMainSync(()->{try{DisplayDevice.run(getContext());out.putString("result","PASS "+DisplayDevice.count+" installed-APK presentation assertions");}catch(Throwable t){out.putString("error",t.toString());}});finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}