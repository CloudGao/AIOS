package os.aios.agent;
import android.app.Instrumentation;import android.os.*;import android.content.*;import android.database.ContentObserver;import android.net.Uri;
public final class OptionalGservicesDeviceTest extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  Context ordinary=getTargetContext(),euicc=ordinary.createPackageContext("com.google.euiccpixel",0);
  check(euicc,"com.google.android.gsf.gservices",false);
  check(ordinary,"com.google.android.gsf.gservices",true);
  check(euicc,"os.aios.acceptance.missing.provider",true);
  out.putString("result","PASS 3 optional-provider boundary cases");finish(-1,out);
 }catch(Throwable e){out.putString("error",e.toString());finish(0,out);}}
 private void check(Context c,String authority,boolean mustReject){ContentObserver observer=new ContentObserver(new Handler(Looper.getMainLooper())){};boolean rejected=false;try{c.getContentResolver().registerContentObserver(Uri.parse("content://"+authority),true,observer);}catch(SecurityException expected){rejected=true;}finally{c.getContentResolver().unregisterContentObserver(observer);}if(rejected!=mustReject)throw new AssertionError(c.getPackageName()+" "+authority+" rejected="+rejected);}
}
