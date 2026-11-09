package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.os.*;
import android.provider.MediaStore;
import android.net.Uri;
import java.util.*;
import org.json.*;

/** Existing MediaProvider metadata only. Never reads document bodies or creates an index. */
final class FileSearchAdapter {
    private final Context context;
    FileSearchAdapter(Context c){context=c;}
    JSONObject source()throws Exception{return new JSONObject().put("source","files").put("state",Environment.isExternalStorageManager()?"available":"permission_required").put("permission","android.permission.MANAGE_EXTERNAL_STORAGE").put("coverage","media_provider_primary_volume_only").put("complete_filesystem_coverage",false).put("content_scan",true).put("filters",new JSONArray(List.of("file.name","file.directory","file.mime_type","file.extension","file.min_bytes","file.max_bytes","file.modified_from_ms","file.modified_to_ms","file.added_from_ms","file.added_to_ms")));}
    JSONObject call(String op,JSONObject p,CancellationSignal cancel)throws Exception{
        if(!Set.of("query","get").contains(op))throw new IllegalArgumentException("operation_unsupported");
        if(!Environment.isExternalStorageManager())return new JSONObject().put("state","permission_required").put("permission","android.permission.MANAGE_EXTERNAL_STORAGE").put("items",new JSONArray()).put("has_more",false);
        int limit=p.optInt("limit",20);if(limit<1||limit>50)throw new IllegalArgumentException("invalid_limit");
        StringBuilder where=new StringBuilder("is_pending=0 AND is_trashed=0 AND format!=12289 AND relative_path NOT LIKE 'Android/%'");ArrayList<String> args=new ArrayList<>();
        if(op.equals("get")){where.append(" AND _id=?");args.add(Long.toString(p.getLong("id")));}else{where.append(" AND _id>?");args.add(Long.toString(p.optLong("after_id",0)));}
        String query=p.optString("query","");if(!query.isEmpty())like(where,args,"_display_name","%"+escape(query)+"%");
        JSONObject filters=p.optJSONObject("filters"),f=filters==null?null:filters.optJSONObject("file");
        if(filters!=null){if(filters.has("from_ms"))bound(where,args,"date_modified",">=",(filters.getLong("from_ms")+999)/1000);if(filters.has("to_ms"))bound(where,args,"date_modified","<",(filters.getLong("to_ms")+999)/1000);}
        if(f!=null){Iterator<String> keys=f.keys();while(keys.hasNext()){String key=keys.next();switch(key){
            case "name":like(where,args,"_display_name","%"+escape(f.getString(key))+"%");break;
            case "directory":{String directory=f.getString(key);if(directory.startsWith("/")||directory.contains("..")||directory.length()>1024)throw new IllegalArgumentException("invalid_relative_directory");if(!directory.isEmpty()&&!directory.endsWith("/"))directory+="/";like(where,args,"relative_path",escape(directory)+"%");break;}
            case "mime_type":where.append(" AND mime_type=?");args.add(f.getString(key));break;
            case "extension":{String ext=f.getString(key);if(!ext.matches("[A-Za-z0-9]{1,12}"))throw new IllegalArgumentException("invalid_extension");like(where,args,"_display_name","%."+ext);break;}
            case "min_bytes":case "max_bytes":bound(where,args,"_size",key.equals("min_bytes")?">=":"<=",f.getLong(key));break;
            case "modified_from_ms":case "modified_to_ms":case "added_from_ms":case "added_to_ms":bound(where,args,key.startsWith("modified")?"date_modified":"date_added",key.contains("_from_")?">=":"<",(f.getLong(key)+999)/1000);break;
            default:throw new IllegalArgumentException("unsupported_file_filter");
        }}}
        Bundle b=new Bundle();b.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,where.toString());b.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,args.toArray(new String[0]));b.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"_id ASC");b.putInt(ContentResolver.QUERY_ARG_LIMIT,limit+1);
        Uri uri=MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);String[] columns={"_id","_display_name","_data","relative_path","mime_type","_size","date_added","date_modified","generation_modified"};JSONArray items=new JSONArray();boolean more=false;
        try(Cursor c=context.getContentResolver().query(uri,columns,b,cancel)){if(c==null)throw new IllegalStateException("provider_unavailable");while(c.moveToNext()){cancel.throwIfCanceled();if(items.length()==limit){more=true;break;}JSONObject fields=new JSONObject();for(int i=0;i<columns.length;i++)fields.put(columns[i],c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));long id=c.getLong(0);JSONObject item=new JSONObject().put("source","files").put("id",Long.toString(id)).put("ref","files:"+id).put("title",c.getString(1)).put("snippet",c.isNull(3)?"":c.getString(3)).put("uri",ContentUris.withAppendedId(uri,id).toString()).put("path",fields.opt("_data")).put("mime_type",fields.opt("mime_type")).put("size_bytes",fields.opt("_size")).put("source_version",fields.optString("generation_modified")+":"+fields.optString("date_modified")+":"+fields.optString("_size")).put("match_reason","file_metadata").put("coverage","media_provider_primary_volume_only");
            for(String key:List.of("date_added","date_modified")){Object ms=fields.isNull(key)||fields.optLong(key)<=0?JSONObject.NULL:fields.getLong(key)*1000;item.put(key.equals("date_added")?"added_at_ms":"modified_at_ms",ms);if(key.equals("date_modified"))item.put("time_ms",ms);}if(op.equals("get"))item.put("fields",fields);items.put(item);
        }}
        if(!Environment.isExternalStorageManager())throw new SecurityException("permission_revoked");return new JSONObject().put("state","available").put("items",items).put("has_more",more).put("snapshot",false).put("coverage","media_provider_primary_volume_only");
    }
    private static void bound(StringBuilder where,List<String> args,String field,String operator,long value){if(value<0)throw new IllegalArgumentException("invalid_range");where.append(" AND ").append(field).append(operator).append('?');args.add(Long.toString(value));}
    private static String escape(String s){return s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private static void like(StringBuilder where,List<String> args,String field,String pattern){where.append(" AND ").append(field).append(" LIKE ? ESCAPE '\\'");args.add(pattern);}
}
