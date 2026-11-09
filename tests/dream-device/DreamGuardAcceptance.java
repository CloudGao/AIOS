package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;import androidx.work.*;import java.util.concurrent.TimeUnit;
public class DreamGuardAcceptance extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();WorkManager wm=WorkManager.getInstance(getTargetContext());OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(DreamWorker.class).addTag("acceptance-screen-guard").build();try{
 if(!getTargetContext().getSystemService(android.os.PowerManager.class).isInteractive())throw new AssertionError("Screen must be on");
 wm.enqueue(request).getResult().get(10,TimeUnit.SECONDS);WorkInfo info=null;for(int i=0;i<50;i++){info=wm.getWorkInfoById(request.getId()).get(5,TimeUnit.SECONDS);if(info.getState()==WorkInfo.State.ENQUEUED&&info.getRunAttemptCount()>0)break;Thread.sleep(200);}
 if(info==null||info.getState()!=WorkInfo.State.ENQUEUED||info.getRunAttemptCount()!=1)throw new AssertionError("Expected one attempt then retry: "+info);
 String status=DreamWorker.prefs(getTargetContext()).getString("last_status_code","");if(!status.equals("screen"))throw new AssertionError(status);
 out.putString("stream","PASS: real WorkManager ran automatic worker, screen-on guard deferred before model submission; test work cancelled\n");
 }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));}finally{try{wm.cancelWorkById(request.getId()).getResult().get(10,TimeUnit.SECONDS);}catch(Exception e){}finish(out.getString("stream").startsWith("PASS")?-1:0,out);}}
}