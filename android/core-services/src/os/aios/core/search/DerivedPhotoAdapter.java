package os.aios.core.search;
import android.content.Context;
import org.json.*;
import os.aios.core.indexing.PhotoIndexStore;
import os.aios.runtime.DeviceSearchClient;

final class DerivedPhotoAdapter {
    private final Context context;private long generation=-1;private int offset;private int scanned;
    DerivedPhotoAdapter(Context c){context=c;}
    static void requirePermission(Context context,JSONObject filters)throws Exception{JSONArray sources=new DeviceSearchClient(context).call("sources",new JSONObject()).getJSONArray("items");for(int i=0;i<sources.length();i++){JSONObject source=sources.getJSONObject(i);if(source.getString("source").equals("photos")){if(!source.optString("state").equals("available"))throw new SecurityException("READ_MEDIA_IMAGES permission required");JSONObject photo=filters==null?null:filters.optJSONObject("photo");if(photo!=null&&photo.has("gps_bbox")&&!source.optBoolean("gps_authorized"))throw new SecurityException("ACCESS_MEDIA_LOCATION permission required");return;}}throw new IllegalStateException("Photo provider unavailable");}
    JSONObject page(int limit,String query,JSONObject filters)throws Exception{
        requirePermission(context,filters);JSONArray results=new JSONArray();boolean more=true;int budget=128;
        try(PhotoIndexStore index=new PhotoIndexStore(context)){JSONObject active=index.activeGeneration();if(active==null)return new JSONObject().put("state","index_incomplete").put("items",results).put("has_more",false);if(generation<0)generation=active.getLong("id");else if(generation!=active.getLong("id"))throw new IllegalStateException("cursor_expired");
            while(results.length()<limit&&budget-->0&&scanned<4096){JSONArray rows=index.metadataCandidates(offset,1,filters,generation);if(rows.length()==0){more=false;break;}offset++;scanned++;JSONObject row=rows.getJSONObject(0),source=new JSONObject(row.getString("source_json"));
                JSONObject fresh=new DeviceSearchClient(context).call("get",new JSONObject().put("source","photos").put("id",source.getString("id")));JSONArray items=fresh.optJSONArray("items");if(items==null||items.length()!=1)continue;JSONObject item=items.getJSONObject(0);
                if(!item.optString("source_version").equals(row.getString("source_version"))||!item.optString("database_version").equals(source.optString("database_version"))||!PhotoFilters.matches(row,item,filters))continue;
                if(!query.isBlank()&&!(item.optString("title")+" "+item.optString("snippet")).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)))continue;
                results.put(item.put("match_reason","photo_metadata_or_faces").put("face_state",row.optString("face_state")).put("exif_state",row.optString("exif_state")));
            }
            return new JSONObject().put("state",index.coverageComplete()?"available":"index_incomplete").put("items",results).put("has_more",more&&scanned<4096).put("truncated",more&&scanned>=4096);
        }
    }
}
