package os.aios.agent;
import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
public final class InterventionTests extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();String name="intervention-instrumentation-test.db";getTargetContext().deleteDatabase(name);try(InterventionStore s=new InterventionStore(getTargetContext(),name)){
 JSONObject a=new JSONObject().put("id","test-a").put("thread_id","test-thread").put("assistant_request_id","test-request").put("snapshot","one").put("state","waiting_user").put("revision",1);
 s.create(a,"dedupe");if(!s.create(a,"dedupe").getString("id").equals("test-a"))throw new AssertionError("dedupe");
 JSONObject result=new JSONObject().put("selected_option","yes");s.transition("test-a",1,"resolved",result);boolean rejected=false;try{s.transition("test-a",1,"resolved",result);}catch(IllegalStateException expected){rejected=true;}if(!rejected)throw new AssertionError("double decision");
 if(!s.undelivered("test-thread","test-request")||s.undelivered("other-thread","test-request"))throw new AssertionError("outbox scope");s.close();try(InterventionStore reopened=new InterventionStore(getTargetContext(),name)){if(!reopened.undelivered("test-thread","test-request")||!reopened.get("test-a").getJSONObject("result").getString("selected_option").equals("yes"))throw new AssertionError("durable result");reopened.delivered("test-a");if(reopened.undelivered("test-thread","test-request"))throw new AssertionError("ack");reopened.deleteThread("test-thread");if(reopened.list("test-thread").length()!=0||reopened.pending().length()!=0)throw new AssertionError("delete privacy");}
 out.putString("stream","PASS: 6 intervention persistence assertions\n");finish(-1,out);
 }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,out);}finally{getTargetContext().deleteDatabase(name);}}
}
