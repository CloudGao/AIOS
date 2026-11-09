package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import org.json.*;
import java.util.*;

/** Parameterized Provider search, shared by search RPC and available to native adapters. */
final class DeviceSearchAdapter {
    private final Context context;
    DeviceSearchAdapter(Context c){context=c;}
    private static final class Source {
        final Uri uri; final String permission,title,body,date,base; final String[] columns;
        Source(Uri u,String p,String t,String b,String d,String where,String... cols){uri=u;permission="android.permission."+p;title=t;body=b;date=d;base=where;columns=cols;}
    }
    private Source source(String name){switch(name){
        case "photos":return new Source(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),"READ_MEDIA_IMAGES","_display_name","bucket_display_name","datetaken","is_pending=0 AND is_trashed=0","_id","_display_name","bucket_display_name","datetaken","date_modified","_size","width","height","mime_type","volume_name","generation_modified");
        case "sms":return new Source(Telephony.Sms.CONTENT_URI,"READ_SMS","address","body","date","1=1","_id","address","body","date","thread_id","type");
        case "calls":return new Source(CallLog.Calls.CONTENT_URI,"READ_CALL_LOG","number","name","date","1=1","_id","number","name","date","duration","type");
        case "contacts":return new Source(ContactsContract.Contacts.CONTENT_URI,"READ_CONTACTS","display_name","display_name",null,"1=1","_id","display_name","lookup","has_phone_number");
        case "calendar":return new Source(CalendarContract.Events.CONTENT_URI,"READ_CALENDAR","title","description","dtstart","deleted=0","_id","title","description","dtstart","dtend","eventLocation","calendar_id");
        default:throw new IllegalArgumentException("Unsupported device source: "+name);
    }}
    private boolean allowed(Source s,String name){return context.checkSelfPermission(s.permission)==0 || name.equals("photos")&&context.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==0;}
    JSONObject call(String op,JSONObject p,CancellationSignal cancel)throws Exception {
        if(p.optString("source").equals("email"))return mail(p);
        if(p.optString("source").equals("files"))return new FileSearchAdapter(context).call(op,p,cancel);
        if(op.equals("sources")){JSONArray list=new JSONArray();for(String name:List.of("photos","sms","calls","contacts","calendar")){
            Source s=source(name);list.put(new JSONObject().put("source",name).put("state",allowed(s,name)?"available":"permission_required").put("permission",s.permission).put("gps_authorized",name.equals("photos")&&context.checkSelfPermission("android.permission.ACCESS_MEDIA_LOCATION")==0)
                .put("coverage",name.equals("photos")?"authorized_primary_volume_metadata_only":"provider").put("filters",new JSONArray(s.date==null?List.of():List.of("from_ms","to_ms"))));
        }list.put(new FileSearchAdapter(context).source());list.put(mailSource());return new JSONObject().put("items",list);}
        if(!op.equals("query")&&!op.equals("get"))throw new IllegalArgumentException("Unknown device search operation");
        String name=p.getString("source");Source s=source(name);
        if(!allowed(s,name))return new JSONObject().put("state","permission_required").put("permission",s.permission).put("items",new JSONArray()).put("has_more",false);
        int limit=p.optInt("limit",20);if(limit<1||limit>50)throw new IllegalArgumentException("limit 1..50");
        String query=p.optString("query","");if(query.length()>1000)throw new IllegalArgumentException("Query too long");
        StringBuilder where=new StringBuilder(s.base);ArrayList<String> args=new ArrayList<>();
        if(op.equals("get")){where.append(" AND _id=?");args.add(Long.toString(p.getLong("id")));limit=1;}
        else{where.append(" AND _id>?");args.add(Long.toString(p.optLong("after_id",0)));}
        if(!query.isBlank()){
            where.append(" AND (").append(s.title).append(" LIKE ? ESCAPE '\\' OR ").append(s.body).append(" LIKE ? ESCAPE '\\')");
            String pattern="%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";args.add(pattern);args.add(pattern);
        }
        JSONObject filters=p.optJSONObject("filters");if(filters!=null){Iterator<String> keys=filters.keys();while(keys.hasNext()){
            String key=keys.next();if(s.date==null||!Set.of("from_ms","to_ms").contains(key))throw new IllegalArgumentException("unsupported_filter: "+name+"."+key);
            long value=filters.getLong(key);if(value<0)throw new IllegalArgumentException("Negative timestamp");where.append(" AND ").append(s.date).append(key.equals("from_ms")?">=?":"<?");args.add(Long.toString(value));
        }}
        Bundle options=new Bundle();options.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,where.toString());options.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,args.toArray(new String[0]));options.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"_id ASC");options.putInt(ContentResolver.QUERY_ARG_LIMIT,limit+1);
        JSONArray items=new JSONArray();boolean more=false;long after=p.optLong("after_id",0);
        try(Cursor c=context.getContentResolver().query(s.uri,s.columns,options,cancel)){
            if(c==null)throw new IllegalStateException("provider_unavailable");
            while(c.moveToNext()){
                cancel.throwIfCanceled();if(items.length()==limit){more=true;break;}
                JSONObject raw=new JSONObject();for(int i=0;i<c.getColumnCount();i++)raw.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):clip(c.getString(i),1600));
                after=raw.getLong("_id");Uri uri=ContentUris.withAppendedId(s.uri,after);
                if(name.equals("contacts"))uri=ContactsContract.Contacts.getLookupUri(after,raw.optString("lookup"));
                if(name.equals("photos"))uri=ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(raw.getString("volume_name")),after);
                JSONObject item=new JSONObject().put("source",name).put("id",Long.toString(after)).put("ref",name+":"+after).put("title",clip(raw.optString(s.title,""),240)).put("snippet",clip(raw.optString(s.body,""),600)).put("uri",uri.toString()).put("match_reason",name.equals("photos")?"media_metadata":"provider_text");if(op.equals("get")||name.equals("photos"))item.put("fields",raw);
                if(s.date!=null)item.put("time_ms",raw.optLong(s.date));
                if(name.equals("photos"))item.put("gps_authorized",context.checkSelfPermission("android.permission.ACCESS_MEDIA_LOCATION")==0).put("database_version",MediaStore.getVersion(context,raw.getString("volume_name"))).put("source_version",raw.optString("generation_modified")+":"+raw.optString("date_modified")+":"+raw.optString("_size")).put("face_state","unknown").put("exif_state","unknown").put("semantic_state","unknown");
                items.put(item);
            }
        }
        // Recheck authorization after the Provider read; never turn revocation into cached hits.
        if(!allowed(s,name))throw new SecurityException("Permission revoked during search");
        return new JSONObject().put("state","available").put("items",items).put("has_more",more).put("after_id",after).put("snapshot",false);
    }
    private boolean mailInstalled(){try{return context.getPackageManager().getApplicationInfo("net.thunderbird.android",0).enabled;}catch(Exception e){return false;}}
    private JSONObject mailSource()throws Exception{return new JSONObject().put("source","email").put("state",mailInstalled()?"handoff":"not_installed").put("coverage","application_search_only").put("filters",new JSONArray());}
    private JSONObject mail(JSONObject p)throws Exception{
        if(p.optString("query").length()>1000)throw new IllegalArgumentException("Query too long");
        if(p.optJSONObject("filters")!=null&&p.getJSONObject("filters").length()>0)return new JSONObject().put("state","unsupported_filter").put("items",new JSONArray()).put("has_more",false);
        JSONArray items=new JSONArray();if(mailInstalled())items.put(new JSONObject().put("source","email").put("id","1").put("ref","email:thunderbird").put("title",context.getString(R.string.mail_search,p.optString("query"))).put("snippet",context.getString(R.string.mail_handoff)).put("query",p.optString("query")).put("match_reason","application_handoff").put("package","net.thunderbird.android"));
        return new JSONObject().put("state",mailInstalled()?"handoff":"not_installed").put("items",items).put("has_more",false);
    }
    static String clip(String s,int max){return s==null?"":s.substring(0,Math.min(s.length(),max));}
}
