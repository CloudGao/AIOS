package os.aios.core.search;

import android.content.Context;
import org.json.*;

/** Photo-index contribution; excluded together with the index implementation. */
public final class IndexedPhotoSearch implements PhotoSearchFeature {
    @Override public Page semantic(Context context, String query, String visualQuery) throws Exception {
        MediaSemanticAdapter adapter = new MediaSemanticAdapter(context, query, visualQuery);
        return (limit, ignored, filters) -> adapter.page(limit, filters);
    }
    @Override public Page derived(Context context) {
        DerivedPhotoAdapter adapter = new DerivedPhotoAdapter(context);
        return (limit, query, filters) -> adapter.page(limit, query, filters);
    }
    @Override public void enrich(Context context, JSONObject response) throws Exception {
JSONArray items=response.optJSONArray("items");if(items!=null&&items.length()==1){JSONObject item=items.getJSONObject(0);try(os.aios.core.indexing.PhotoIndexStore index=new os.aios.core.indexing.PhotoIndexStore(context)){JSONObject row=index.metadataFor(item);if(row!=null){item.put("face_state",row.optString("face_state")).put("exif_state",row.optString("exif_state"));if(!row.isNull("face_json"))item.put("faces",new JSONObject(row.getString("face_json")));if(!row.isNull("exif_json")){JSONObject exif=new JSONObject(row.getString("exif_json"));if(!item.optBoolean("gps_authorized")){JSONObject fields=exif.optJSONObject("fields");if(fields!=null){fields.remove("latitude");fields.remove("longitude");fields.remove("altitude_m");fields.put("gps_state","permission_required");}JSONObject raw=exif.optJSONObject("raw_exif");if(raw!=null){java.util.ArrayList<String> remove=new java.util.ArrayList<>();java.util.Iterator<String> k=raw.keys();while(k.hasNext()){String key=k.next();if(key.startsWith("GPS"))remove.add(key);}for(String key:remove)raw.remove(key);}}item.put("exif",exif);}}}}
    }
}
