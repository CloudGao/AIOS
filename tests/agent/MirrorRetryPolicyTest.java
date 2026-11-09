package os.aios.agent;
import org.json.*;
/** Prepared for the unified host/Android test batch; not run during source-only repair. */
public final class MirrorRetryPolicyTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        MirrorRetryPolicy p=new MirrorRetryPolicy();
        check(p.ready("a",0));p.failed("a",0,false);
        check(!p.ready("a",400));check(!p.ready("a",29999));check(p.ready("a",30000));
        p.failed("a",30000,false);check(!p.ready("a",89999));check(p.ready("a",90000));
        check(p.ready("new-turn",0));
        for(int i=2;i<8;i++)p.failed("a",90000,false);
        check(!p.ready("a",Long.MAX_VALUE));
        p.succeeded("a");check(p.ready("a",0));
        p.failed("a",0,true);check(!p.ready("a",Long.MAX_VALUE));
        check(new MirrorRetryPolicy().ready("a",0));
        JSONArray bulky=new JSONArray();
        for(int i=0;i<33;i++)bulky.put(MirrorRetryPolicy.projectionItem(new JSONObject().put("item_id","i"+i).put("content_md","中文🙂\\\"".repeat(30000)).put("detail","x".repeat(200000))));
        JSONArray pages=MirrorRetryPolicy.projectionBatches(bulky);int seen=0;
        check(pages.length()>1);
        for(int i=0;i<pages.length();i++){JSONArray page=pages.getJSONArray(i);check(MirrorRetryPolicy.bytes(new JSONObject().put("generation","g".repeat(64)).put("items",page))<400*1024);
            for(int k=0;k<page.length();k++){JSONObject item=page.getJSONObject(k);check(item.getString("item_id").equals("i"+seen++));check(MirrorRetryPolicy.bytes(item)<=160*1024);}}
        check(seen==33);check(MirrorRetryPolicy.projectionBatches(new JSONArray()).length()==0);
        JSONArray turns=new JSONArray();
        // An oversized screenshot must not cross the structured IPC boundary.
        String image="x".repeat(600000);
        for(int i=0;i<33;i++)turns.put(new JSONObject().put("id","turn-"+i).put("status","completed")
            .put("startedAt",1792710905).put("items",new JSONArray().put(new JSONObject().put("png_base64",image))));
        turns.put(new JSONObject().put("id","active").put("status","inProgress"));
        JSONArray meta=MirrorRetryPolicy.metadata(turns);
        check(meta.length()==33);check(meta.toString().length()<16384);check(!meta.toString().contains("png_base64"));
        check(meta.getJSONObject(32).getString("id").equals("turn-32"));
        check(meta.getJSONObject(0).getDouble("startedAt")==1792710905);
        check(MirrorRetryPolicy.metadata(null).length()==0);
        turns=new JSONArray().put(new JSONObject().put("id","x").put("status","failed"))
            .put(new JSONObject().put("id","y").put("status","interrupted"));
        check(MirrorRetryPolicy.metadata(turns).length()==2);
        try{MirrorRetryPolicy.metadata(new JSONArray().put(new JSONObject().put("id","x".repeat(257)).put("status","completed")));throw new AssertionError();}
        catch(IllegalArgumentException expected){}
    }
}
