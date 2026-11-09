package os.aios.codex;

import android.os.SystemClock;
import org.json.*;

/** Stateless text jobs in isolated tool-free threads; never resume the user's assistant. */
final class SystemTextInference {
    static JSONObject generate(CodexProcess process,JSONObject request)throws Exception{
        String instruction=request.getString("instruction"),input=request.getString("input");
        if(instruction.length()>8000||input.length()>16000)throw new IllegalArgumentException("input_capacity");
        JSONObject params=MinutesPolicy.params(process).put("baseInstructions",instruction+"\nReturn only the requested answer, without tools or commentary. Keep output under "+Math.max(1,Math.min(512,request.optInt("max_tokens",128)))+" tokens.")
            .put("model",request.getString("model")).put("dynamicTools",new JSONArray())
            .put("environments",new JSONArray()).put("selectedCapabilityRoots",new JSONArray()).put("historyMode","paginated");
        JSONObject created=process.call("thread/start",params,20);
        String thread=created.getJSONObject("thread").getString("id"),turn="";
        long began=SystemClock.elapsedRealtime();
        try{
            JSONObject started=process.call("turn/start",new JSONObject().put("threadId",thread).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",input))),20);
            turn=started.getJSONObject("turn").getString("id");
            while(SystemClock.elapsedRealtime()-began<30000){
                JSONObject page=process.call("thread/turns/list",new JSONObject().put("threadId",thread).put("limit",1).put("sortDirection","desc").put("itemsView","summary"),10);
                JSONArray turns=page.optJSONArray("data");
                if(turns!=null&&turns.length()>0){
                    JSONObject current=turns.getJSONObject(0);String status=current.optString("status");
                    if(!turn.equals(current.optString("id")))throw new IllegalStateException("inference_turn_identity");
                    if(status.equals("completed")){
                        StringBuilder text=new StringBuilder();
                        JSONObject itemPage=process.call("thread/items/list",new JSONObject().put("threadId",thread).put("turnId",turn).put("limit",16).put("sortDirection","desc"),10);
                        JSONArray wrapped=itemPage.getJSONArray("data"),items=new JSONArray();
                        for(int index=wrapped.length()-1;index>=0;index--)items.put(wrapped.getJSONObject(index).getJSONObject("item"));
                        if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.optString("type").equals("agentMessage")&&item.optString("phase","final_answer").equals("final_answer"))text.append(item.optString("text"));}
                        if(text.length()==0||text.length()>16000)throw new IllegalStateException("inference_output_capacity");
                        return new JSONObject().put("text",text.toString()).put("model",request.getString("model")).put("backend","cloud");
                    }
                    if(status.equals("failed")||status.equals("interrupted"))throw new IllegalStateException("inference_failed");
                }
                Thread.sleep(500);
            }
            throw new java.io.IOException("inference_timeout");
        }finally{
            if(!turn.isEmpty())try{process.call("turn/interrupt",new JSONObject().put("threadId",thread).put("turnId",turn),5);}catch(Exception ignored){}
            try{process.call("thread/archive",new JSONObject().put("threadId",thread),5);}catch(Exception ignored){}
        }
    }
}
