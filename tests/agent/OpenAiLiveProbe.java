package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;import org.json.*;
/** User-authorized minimal inference and read-only phone tool acceptance. */
public final class OpenAiLiveProbe extends Instrumentation {
 Bundle args;CodexAccountClient client;public void onCreate(Bundle b){args=b;super.onCreate(b);start();}
 JSONObject call(String op,JSONObject p)throws Exception{return client.call(new JSONObject().put("op",op).put("params",p));}
 JSONObject rpc(String method,JSONObject p)throws Exception{return client.call(new JSONObject().put("op","rpc").put("method",method).put("params",p));}
 public void onStart(){Bundle out=new Bundle();client=new CodexAccountClient(getTargetContext());try{
  String action=args.getString("action","status");
  if(action.equals("select")){out.putString("result",call("accounts/select",new JSONObject().put("provider",args.getString("provider","openai"))).toString());finish(-1,out);return;}
  if(action.equals("status")){JSONObject status=call("accounts/status",new JSONObject());status.remove("email");out.putString("result",status.put("reasoning",call("accounts/reasoning",new JSONObject())).toString());finish(-1,out);return;}
  String effort=args.getString("effort","none");JSONObject status=call("accounts/status",new JSONObject());
  if(!status.getString("model").equals("gpt-6-luna"))throw new Exception("Expected Luna");
  call("accounts/reasoning/save",new JSONObject().put("provider",status.getString("provider")).put("model","gpt-6-luna").put("effort",effort));
  String id=args.getString("thread","");if(id.isEmpty())id=call("thread/start",new JSONObject()).getJSONObject("thread").getString("id");
  else rpc("thread/resume",new JSONObject().put("threadId",id));
  String prompt=args.getString("prompt","Reply with exactly OPENAI_LUNA_OK. Do not use tools.");
  JSONObject turn=rpc("turn/start",new JSONObject().put("threadId",id).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",prompt).put("text_elements",new JSONArray())))).getJSONObject("turn");
  String turnId=turn.getString("id");long deadline=android.os.SystemClock.elapsedRealtime()+150000;JSONObject completed=null;
  while(android.os.SystemClock.elapsedRealtime()<deadline){Thread.sleep(1500);JSONObject thread=rpc("thread/read",new JSONObject().put("threadId",id).put("includeTurns",true)).getJSONObject("thread");JSONArray turns=thread.getJSONArray("turns");for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i);if(t.optString("id").equals(turnId)&&!t.optString("status").equals("inProgress"))completed=t;}if(completed!=null)break;}
  JSONObject result=new JSONObject().put("thread",id).put("turn",turnId).put("requested_effort",effort).put("result",completed==null?JSONObject.NULL:completed);
  out.putString("result",result.toString());finish(completed!=null&&completed.optString("status").equals("completed")?-1:0,out);
 }catch(Throwable e){out.putString("result","FAIL "+e);finish(0,out);}finally{client.close();}}
}
