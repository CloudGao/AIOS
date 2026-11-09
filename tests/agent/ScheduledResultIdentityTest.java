package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;import org.json.JSONObject;
public final class ScheduledResultIdentityTest extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  JSONObject[] cases={new JSONObject(),new JSONObject().put("thread_id","thread"),new JSONObject().put("turn_id","turn"),new JSONObject().put("thread_id","thread").put("turn_id",""),new JSONObject().put("thread_id","thread").put("turn_id","  "),new JSONObject().put("thread_id","thread").put("turn_id","turn")};
  for(int i=0;i<cases.length;i++)if(ScheduledAssistantCoordinator.hasNativeResultIdentity(cases[i])!=(i==5))throw new AssertionError("case "+i);
  out.putString("result","PASS 6 native result identity cases");finish(-1,out);
 }catch(Throwable e){out.putString("error",e.toString());finish(0,out);}}
}
