package os.aios.agent;
import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.json.*;
import os.aios.runtime.CoreRuntimeClient;
/** Real cross-process notification/transaction regression; owns and removes its sole task. */
public final class CleanupAcceptance extends Instrumentation {
 int count; CoreRuntimeClient core; String task; final LinkedBlockingQueue<String> events=new LinkedBlockingQueue<>();
 void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
 JSONObject ws(String op,JSONObject p)throws Exception{return core.call("workspace",op,p).getJSONObject("data");}
 void notice(String label)throws Exception{check(events.poll(5,TimeUnit.SECONDS)!=null,label);}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();boolean registered=false;Throwable failure=null;
  BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){events.offer(i.getAction());}};
  try{
   core=new CoreRuntimeClient(context);
   context.registerReceiver(receiver,new IntentFilter("os.aios.workspace.CHANGED"),"os.aios.permission.CORE_RUNTIME",null,Context.RECEIVER_EXPORTED);registered=true;
   JSONObject created=ws("task.create",new JSONObject().put("title","Cleanup regression fixture").put("status","todo"));task=created.getString("id");notice("create reaches Agent from Core");
   events.clear();ws("home.snapshot",new JSONObject());ws("task.list",new JSONObject());ws("task.get",new JSONObject().put("id",task));
   check(events.poll(700,TimeUnit.MILLISECONDS)==null,"read operations do not generate refresh loop");
   JSONObject changed=ws("task.update",new JSONObject().put("id",task).put("expected_revision",created.getLong("revision")).put("title","Cleanup updated"));notice("update reaches Agent");
   check(ws("task.get",new JSONObject().put("id",task)).getString("title").equals("Cleanup updated"),"updated state readback");
   events.clear();boolean rejected=false;try{ws("task.update",new JSONObject().put("id",task).put("expected_revision",created.getLong("revision")).put("title","must not save"));}catch(Exception stale){rejected=true;}
   check(rejected,"stale revision rejected");check(events.poll(700,TimeUnit.MILLISECONDS)==null,"failed transaction sends no success notification");
   check(ws("task.get",new JSONObject().put("id",task)).getString("title").equals("Cleanup updated"),"rollback preserves data");
   ws("task.delete",new JSONObject().put("id",task).put("expected_revision",changed.getLong("revision")));task=null;notice("delete reaches Agent");
  }catch(Throwable e){failure=e;}finally{
   if(task!=null)try{JSONObject row=ws("task.get",new JSONObject().put("id",task));ws("task.delete",new JSONObject().put("id",task).put("expected_revision",row.getLong("revision")));}catch(Throwable cleanup){if(failure==null)failure=cleanup;}
   if(registered)context.unregisterReceiver(receiver);
  }
  result.putString("stream",failure==null?"PASS: "+count+" cleanup cross-process assertions\n":"FAIL after "+count+": "+android.util.Log.getStackTraceString(failure));finish(failure==null?-1:0,result);
 }
}
