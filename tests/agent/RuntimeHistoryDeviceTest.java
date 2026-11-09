package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;import org.json.*;
public final class RuntimeHistoryDeviceTest extends Instrumentation {
 Bundle arguments; public void onCreate(Bundle args){arguments=args;super.onCreate(args);start();}
 public void onStart(){Bundle out=new Bundle();JSONArray rows=new JSONArray();CodexAccountClient client=new CodexAccountClient(getTargetContext());
  try{
   client.call(new JSONObject().put("op","start").put("mode","live"));
   if("true".equals(arguments.getString("large_request"))){
    String payload="x".repeat(220000);JSONObject status=client.call(new JSONObject().put("op","status").put("transport_probe",payload));
    if(status.optString("runtimeId").isEmpty())throw new AssertionError("large Binder request did not reach runtime");
    out.putString("result",new JSONObject().put("passed",true).put("request_characters",payload.length()).put("runtime_id",status.getString("runtimeId")).toString());finish(-1,out);return;
   }
   if(arguments.getBoolean("errors",false)||"true".equals(arguments.getString("errors"))){
    JSONArray errors=new JSONArray();long cursor=0;
    for(int page=0;page<20;page++){
     JSONObject batch=client.call(new JSONObject().put("op","events").put("after",cursor));JSONArray all=batch.getJSONArray("items");
     for(int i=0;i<all.length();i++){JSONObject event=all.getJSONObject(i);if("error".equals(event.optString("method")))errors.put(event);}
     long next=batch.optLong("cursor");if(next==cursor||all.length()==0)break;cursor=next;
    }
    if("true".equals(arguments.getString("stop")))client.call(new JSONObject().put("op","execution/stop").put("params",new JSONObject().put("threadId","01a16984-4d96-7001-8425-7a6e49871acc")));
    out.putString("result",errors.toString());finish(-1,out);return;
   }
   String before=client.call(new JSONObject().put("op","status")).getString("runtimeId");
   for(int i=0;i<20;i++){
    long start=android.os.SystemClock.elapsedRealtime();
    JSONObject response=client.call(new JSONObject().put("op","rpc").put("method","thread/read").put("params",new JSONObject().put("threadId","01a16984-4d96-7001-8425-7a6e49871acc").put("includeTurns",true)));
    String raw=response.toString();if(raw.contains("data:image/"))throw new AssertionError("inline media remains");
    int turns=response.getJSONObject("thread").getJSONArray("turns").length();if(turns<2)throw new AssertionError("history lost");
    rows.put(new JSONObject().put("iteration",i+1).put("characters",raw.length()).put("turns",turns).put("milliseconds",android.os.SystemClock.elapsedRealtime()-start));
   }
   String after=client.call(new JSONObject().put("op","status")).getString("runtimeId");if(!before.equals(after))throw new AssertionError("runtime restarted");
   out.putString("result",new JSONObject().put("passed",true).put("same_runtime",true).put("reads",rows).toString());finish(-1,out);
  }catch(Throwable e){out.putString("result","FAILED "+e+" reads="+rows);finish(0,out);}finally{client.close();}
 }
}
