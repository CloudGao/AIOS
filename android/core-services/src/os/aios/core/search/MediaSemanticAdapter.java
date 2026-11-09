package os.aios.core.search;
import android.content.Context;
import org.json.*;
import os.aios.core.embedding.*;
import os.aios.core.indexing.*;
import os.aios.runtime.DeviceSearchClient;

/** Query vector lives only in this bounded search session, never rerun per photo or page. */
final class MediaSemanticAdapter {
    private final Context context;private final String space;private final long generation;private final float[] vector;private int consumed;private int expansion=64;private int requested;private final java.util.Set<Long> examined=new java.util.HashSet<>();private JSONArray candidates=new JSONArray();
    final String original,visual,rewrite;
    MediaSemanticAdapter(Context c,String query,String rewritten)throws Exception{
        DerivedPhotoAdapter.requirePermission(c,null);context=c;try(PhotoIndexStore store=new PhotoIndexStore(c)){JSONObject active=store.activeGeneration();if(active==null)throw new IllegalStateException("index_incomplete");generation=active.getLong("id");space=active.getString("space_id");}ModelRegistry registry=new ModelRegistry(c,space);if(!registry.validated("image")||!registry.validated("text"))throw new IllegalStateException("image_semantic_unavailable");original=query;visual=rewritten.isEmpty()?ClipTokenizer.rewrite(query):rewritten;rewrite=!rewritten.isEmpty()?"agent_visual_query":visual.equals(query)?"none":"local_dictionary";
        vector=new EmbeddingClient(c).run("text",null,new ClipTokenizer(c,space).encode(visual),false,space).getFloatArray("output_0");
    }
    JSONObject page(int limit,JSONObject filters)throws Exception{
        DerivedPhotoAdapter.requirePermission(context,filters);try(PhotoIndexStore store=new PhotoIndexStore(context)){JSONObject active=store.activeGeneration();if(active==null||active.getLong("id")!=generation)throw new IllegalStateException("cursor_expired");}JSONArray out=new JSONArray();int scanned=0;boolean exhausted=false;boolean complete=false;
        try(VectorIndex index=new VectorIndex(context,space,generation);PhotoIndexStore ledger=new PhotoIndexStore(context)){
            complete=ledger.coverageComplete();
            while(out.length()<limit&&scanned<128){
                if(consumed>=candidates.length()){
                    if(candidates.length()>0&&candidates.length()<requested){exhausted=true;break;}if(expansion>4096)break;
                    requested=expansion;candidates=index.nearest(vector,expansion);consumed=0;if(candidates.length()==0){exhausted=true;break;}expansion=Math.min(8192,expansion*2);
                }
                JSONObject hit=candidates.getJSONObject(consumed++);if(!examined.add(hit.getLong("id")))continue;scanned++;JSONObject row=ledger.photo(hit.getLong("id"),generation);if(row==null||!row.optString("space_id").equals(space)||!row.optString("embedding_state").equals("ready"))continue;
                JSONObject source=new JSONObject(row.getString("source_json"));if(!PhotoFilters.matches(row,new JSONObject(source.toString()).put("gps_authorized",true),filters))continue;JSONObject actual=new DeviceSearchClient(context).call("get",new JSONObject().put("source","photos").put("id",source.getString("id")));JSONArray items=actual.optJSONArray("items");if(items==null||items.length()!=1)continue;JSONObject item=items.getJSONObject(0);
                if(!item.optString("source_version").equals(row.getString("source_version"))||!item.optString("database_version").equals(source.optString("database_version")))continue;
                if(!PhotoFilters.matches(row,item,filters))continue;
                item.put("semantic_state","available").put("match_reason","image_semantic").put("distance",hit.get("distance")).put("space_id",space);out.put(item);
            }
        }
        boolean truncated=!exhausted&&examined.size()>=4096;return new JSONObject().put("state",complete?"available":"index_incomplete").put("index_generation",generation).put("items",out).put("has_more",!exhausted&&!truncated).put("truncated",truncated).put("original_query",original).put("visual_query",visual).put("rewrite_method",rewrite);
    }
}
