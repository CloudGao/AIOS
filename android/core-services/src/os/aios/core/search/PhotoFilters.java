package os.aios.core.search;
import org.json.*;
import java.util.*;

/** Explicit filter schema; unknown fields are rejected instead of silently broadening results. */
final class PhotoFilters {
    static void validate(JSONObject f)throws Exception{
        if(f==null)return;Iterator<String> keys=f.keys();while(keys.hasNext()){
            String key=keys.next();switch(key){case "from_ms":case "to_ms":if(f.getLong(key)<0)throw new IllegalArgumentException("Invalid timestamp");break;
            case "file":object(f.getJSONObject(key),Set.of("name","directory","mime_type","extension","min_bytes","max_bytes","modified_from_ms","modified_to_ms","added_from_ms","added_to_ms"));break;
            case "photo":object(f.getJSONObject(key),Set.of("camera_make","camera_model","lens_model","taken_time","gps_bbox","exposure_seconds","f_number","iso","focal_length_mm"));break;
            case "faces":object(f.getJSONObject(key),Set.of("present","detected_count_min","detected_count_max"));break;
            default:throw new IllegalArgumentException("unsupported_filter: "+key);}
        }
        JSONObject face=f.optJSONObject("faces");if(face!=null){if(face.has("present"))face.getBoolean("present");for(String k:List.of("detected_count_min","detected_count_max"))if(face.has(k)&&face.getInt(k)<0)throw new IllegalArgumentException("Negative face count");if(face.optInt("detected_count_min",0)>face.optInt("detected_count_max",Integer.MAX_VALUE))throw new IllegalArgumentException("Invalid face count range");}
        JSONObject photo=f.optJSONObject("photo");if(photo!=null){Iterator<String> pk=photo.keys();while(pk.hasNext()){String key=pk.next();if(Set.of("camera_make","camera_model","lens_model").contains(key)){if(photo.getString(key).length()>300)throw new IllegalArgumentException("Filter too long");}
            else if(key.equals("gps_bbox")){JSONArray b=photo.getJSONArray(key);for(int n=0;n<b.length();n++)if(!Double.isFinite(b.getDouble(n)))throw new IllegalArgumentException("Nonfinite coordinate");if(b.length()!=4||b.getDouble(0)<-180||b.getDouble(2)>180||b.getDouble(1)<-90||b.getDouble(3)>90||b.getDouble(0)>b.getDouble(2)||b.getDouble(1)>b.getDouble(3))throw new IllegalArgumentException("gps_bbox requires west,south,east,north");}
            else{JSONObject range=photo.getJSONObject(key);object(range,key.equals("taken_time")?Set.of("field","min","max"):Set.of("min","max"));if(range.has("min")&&!Double.isFinite(range.getDouble("min"))||range.has("max")&&!Double.isFinite(range.getDouble("max")))throw new IllegalArgumentException("Nonfinite range");if(range.optDouble("min",-Double.MAX_VALUE)>range.optDouble("max",Double.MAX_VALUE))throw new IllegalArgumentException("Invalid range");if(key.equals("taken_time")&&!Set.of("exif","media_taken").contains(range.getString("field")))throw new IllegalArgumentException("Explicit time field exif/media_taken required");}
        }}
    }
    private static void object(JSONObject o,Set<String> supported)throws Exception{Iterator<String> k=o.keys();while(k.hasNext()){String field=k.next();if(!supported.contains(field))throw new IllegalArgumentException("unsupported_filter: "+field);}}
    static boolean derived(JSONObject filters){return filters!=null&&(filters.has("photo")||filters.has("faces"));}
    static boolean matches(JSONObject row,JSONObject item,JSONObject filters)throws Exception{
        if(filters==null)return true;long time=item.optLong("time_ms");if(filters.has("from_ms")&&time<filters.getLong("from_ms")||filters.has("to_ms")&&time>=filters.getLong("to_ms"))return false;
        JSONObject face=filters.optJSONObject("faces");if(face!=null){if(!row.optString("face_state").equals("ready"))return false;int count=new JSONObject(row.getString("face_json")).getInt("detected_count");if(face.has("present")&&face.getBoolean("present")!=(count>0)||count<face.optInt("detected_count_min",0)||count>face.optInt("detected_count_max",Integer.MAX_VALUE))return false;}
        JSONObject photo=filters.optJSONObject("photo");if(photo==null)return true;JSONObject fields=row.isNull("exif_json")?new JSONObject():new JSONObject(row.getString("exif_json")).optJSONObject("fields");if(fields==null)fields=new JSONObject();Iterator<String> keys=photo.keys();while(keys.hasNext()){
            String key=keys.next();if(Set.of("camera_make","camera_model","lens_model").contains(key)){if(!fields.has(key)||!fields.getString(key).equalsIgnoreCase(photo.getString(key)))return false;}
            else if(key.equals("gps_bbox")){if(!item.optBoolean("gps_authorized")||!fields.has("latitude")||!fields.has("longitude"))return false;JSONArray b=photo.getJSONArray(key);double lat=fields.getDouble("latitude"),lon=fields.getDouble("longitude");if(lon<b.getDouble(0)||lat<b.getDouble(1)||lon>b.getDouble(2)||lat>b.getDouble(3))return false;}
            else{JSONObject range=photo.getJSONObject(key);double value;if(key.equals("taken_time")){if(range.getString("field").equals("media_taken")){if(time<=0)return false;value=time;}else{if(!fields.has("taken_at_ms"))return false;value=fields.getDouble("taken_at_ms");}}else{if(!fields.has(key))return false;value=fields.getDouble(key);}if(value<range.optDouble("min",-Double.MAX_VALUE)||value>range.optDouble("max",Double.MAX_VALUE))return false;}
        }return true;
    }
}
