package os.aios.agent;

import android.content.Context;
import org.json.*;

/** Lightweight lifecycle projection, independent of native history paging and model execution. */
final class AssistantSessionPublisher {
    private static final java.util.concurrent.ScheduledExecutorService worker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private static final java.util.Map<String,String> pending=new java.util.LinkedHashMap<>(),sent=new java.util.HashMap<>();
    private static boolean scheduled;
    static void publish(Context context,JSONObject state){
        if(state.optBoolean("scheduled")||state.optString("request_id").isEmpty()||state.optString("text").isBlank())return;
        try{
            JSONObject p=new JSONObject();
            for(String key:new String[]{"conversation_id","request_id","thread_id","status","submitted_at","execution_finished_at","text","task_id"})if(state.has(key))p.put(key,state.get(key));
            String id=p.getString("conversation_id"),payload=p.toString();
            synchronized(pending){if(payload.equals(sent.get(id))||payload.equals(pending.get(id)))return;pending.put(id,payload);if(scheduled)return;scheduled=true;}
            worker.execute(()->drain(context.getApplicationContext()));
        }catch(Exception e){android.util.Log.w("AIOSAssistant","Cannot prepare session lifecycle",e);}
    }
    private static void drain(Context context){
        String id,payload;
        synchronized(pending){if(pending.isEmpty()){scheduled=false;return;}id=pending.keySet().iterator().next();payload=pending.get(id);}
        boolean success=false;
        try(os.aios.workspace.WorkspaceStore store=new os.aios.workspace.WorkspaceStore(context)){store.call("sync.live",new JSONObject(payload));success=true;}
        catch(Exception e){android.util.Log.w("AIOSAssistant","Session lifecycle deferred",e);}
        synchronized(pending){if(success){sent.put(id,payload);if(payload.equals(pending.get(id)))pending.remove(id);}else if(payload.equals(pending.get(id))){pending.remove(id);pending.put(id,payload);}}
        worker.schedule(()->drain(context),success?0:1000,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    static void restore(Context context){
        for(Object raw:context.getSharedPreferences("assistant_conversation_archive",0).getAll().values())try{publish(context,new JSONObject(String.valueOf(raw)));}catch(Exception e){android.util.Log.w("AIOSAssistant","Invalid archived lifecycle",e);}
    }
}
