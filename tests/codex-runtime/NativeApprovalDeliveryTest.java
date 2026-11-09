package os.aios.codex;
import android.app.Instrumentation;import android.content.*;import android.os.Bundle;import org.json.*;import java.io.*;import java.lang.reflect.Field;
/** Isolated native child and isolated receipts; never approves a real command. */
public final class NativeApprovalDeliveryTest extends Instrumentation {
 int checks;void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();File home=new File(getTargetContext().getCacheDir(),"approval-delivery-"+java.util.UUID.randomUUID());String prefix=home.getName();Context isolated=new ContextWrapper(getTargetContext()){@Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences(prefix+"-"+name,mode);}};
 try(CodexProcess process=new CodexProcess(isolated,home,event->{},"http://127.0.0.1:1/v1",new File(getTargetContext().getFilesDir(),"android-ca.pem"))){
  Field input=CodexProcess.class.getDeclaredField("input");input.setAccessible(true);BufferedWriter original=(BufferedWriter)input.get(process);StringWriter sent=new StringWriter();input.set(process,new BufferedWriter(sent));
  try{
   for(String method:new String[]{"item/commandExecution/requestApproval","item/fileChange/requestApproval"})for(String decision:new String[]{"accept","decline","cancel"}){
    String id="fixture-"+checks;JSONObject params=new JSONObject().put("threadId","thread").put("callId",id);JSONObject pending=new JSONObject().put("id",id).put("method",method).put("params",params);process.deferredTools.put(id,pending);
    JSONObject value=new JSONObject().put("id",id).put("thread_id","wrong").put("call_id",id).put("result",new JSONObject().put("selected_option",decision));boolean denied=false;try{process.resolveIntervention(value);}catch(SecurityException expected){denied=true;}check(denied,"wrong thread rejected");
    value.put("thread_id","thread");int before=sent.getBuffer().length();check(process.resolveIntervention(value).getBoolean("delivered"),"delivered");JSONObject response=new JSONObject(sent.toString().substring(before).trim());check(response.getJSONObject("result").getString("decision").equals(decision),"exact native response");check(response.getString("id").equals(id),"native callback id retained");int after=sent.getBuffer().length();check(process.resolveIntervention(value).getBoolean("delivered")&&sent.getBuffer().length()==after,"duplicate does not replay");
   }
   String id="fixture-lost";isolated.getSharedPreferences("deferred_tools",0).edit().putString(id,new JSONObject().put("method","item/commandExecution/requestApproval").toString()).commit();JSONObject lost=process.resolveIntervention(new JSONObject().put("id",id).put("thread_id","thread").put("call_id",id).put("result",new JSONObject().put("selected_option","accept")));check(!lost.getBoolean("delivered")&&lost.getString("state").equals("outcome_unknown"),"lost native callback never approves by replay");
  }finally{input.set(process,original);}
  result.putString("stream","PASS: "+checks+" native approval response and identity assertions\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,result);}finally{for(String name:new String[]{"intervention_results","deferred_tools","minutes_threads","dream_threads"})getTargetContext().deleteSharedPreferences(prefix+"-"+name);remove(home);}}
 static void remove(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);}file.delete();}
}
