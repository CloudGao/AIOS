package os.aios.agent;
import android.app.*;import android.os.*;import org.json.*;
public final class MiniAppHarnessProbe extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try(CodexAccountClient c=new CodexAccountClient(getTargetContext())){
  c.call(new JSONObject().put("op","start").put("mode","live"));
  JSONObject data=c.call(new JSONObject().put("op","rpc").put("method","thread/read").put("params",new JSONObject().put("threadId","01a15f2f-2835-7022-b1fc-d933e6550963").put("includeTurns",true)));
  JSONObject thread=data.getJSONObject("thread");JSONArray turns=thread.optJSONArray("turns"),summary=new JSONArray();
  if(turns!=null)for(int i=0;i<turns.length();i++){JSONObject t=turns.getJSONObject(i),s=new JSONObject().put("id",t.optString("id")).put("status",t.optString("status")).put("error",t.opt("error"));JSONArray items=t.optJSONArray("items"),list=new JSONArray();if(items!=null)for(int j=0;j<items.length();j++){JSONObject item=items.getJSONObject(j);String type=item.optString("type");JSONObject brief=new JSONObject().put("type",type).put("status",item.opt("status"));if(!type.equals("reasoning"))for(String key:new String[]{"error","text","command","exitCode","aggregatedOutput","tool"})if(item.has(key))brief.put(key,item.get(key));list.put(brief);}summary.put(s.put("items",list));}
  out.putString("stream",summary.toString());finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
