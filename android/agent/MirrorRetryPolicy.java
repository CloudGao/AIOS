package os.aios.agent;

import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Worker-confined retry budget. Restart permits recovery; no timers wake a sleeping device. */
final class MirrorRetryPolicy {
    private static final class Failure { int attempts; long next; }
    private final HashMap<String,Failure> failures=new HashMap<>();
    boolean ready(String key,long now){Failure f=failures.get(key);return f==null||(f.attempts<8&&now>=f.next);}
    void succeeded(String key){failures.remove(key);}
    void failed(String key,long now,boolean permanent){
        Failure f=failures.computeIfAbsent(key,k->new Failure());
        f.attempts=permanent?8:Math.min(8,f.attempts+1);
        f.next=now+Math.min(30*60_000L,30_000L << (f.attempts-1));
    }
    /** Bound the actual encoded payload, including JSON escapes and non-ASCII text. */
    static JSONObject projectionItem(JSONObject item)throws Exception{
        JSONObject copy=new JSONObject(item.toString());
        while(bytes(copy)>160*1024){
            String key=copy.optString("detail").length()>=copy.optString("content_md").length()?"detail":"content_md";
            String value=copy.optString(key);if(value.length()<256)throw new IllegalArgumentException("Oversized mirror metadata");
            int end=value.length()/2;if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;
            String preview=value.substring(0,end);
            copy.put(key,key.equals("detail")?new JSONObject().put("preview",preview).put("truncated",true)
                .put("source","Full content remains in the original phone assistant conversation").toString():preview+"\n[完整内容保留在手机原始会话中]");
        }
        return copy;
    }
    static int bytes(Object value){return value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    static JSONArray projectionBatches(JSONArray items)throws Exception{
        JSONArray batches=new JSONArray(),batch=new JSONArray();int size=2;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);int next=bytes(item)+1;
            if(next>160*1024+1)throw new IllegalArgumentException("Mirror item must be bounded first");
            if(batch.length()>0&&(batch.length()>=25||size+next>384*1024)){batches.put(batch);batch=new JSONArray();size=2;}
            batch.put(item);size+=next;
        }
        if(batch.length()>0)batches.put(batch);return batches;
    }
    static JSONArray metadata(JSONArray turns)throws Exception{
        JSONArray result=new JSONArray();
        if(turns==null)return result;
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.getJSONObject(i);String status=turn.optString("status");
            if(!status.equals("completed")&&!status.equals("failed")&&!status.equals("interrupted"))continue;
            String id=turn.getString("id");
            if(id.isEmpty()||id.length()>256)throw new IllegalArgumentException("Invalid turn ID");
            result.put(new JSONObject().put("id",id).put("status",status).put("startedAt",turn.optDouble("startedAt",0)));
        }
        return result;
    }
}
