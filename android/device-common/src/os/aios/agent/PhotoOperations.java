package os.aios.agent;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.*;
import org.json.*;
import java.io.*;
import java.util.*;
/** Public-SDK device mechanism; selected and connected by the host. */
final class PhotoOperations {
 final Context c;final os.aios.device.DeviceInteractions interactions;
 PhotoOperations(Context context,os.aios.device.DeviceInteractions interactions){c=context;this.interactions=interactions;}
 JSONObject ok(Object value)throws Exception{return new JSONObject().put("state","succeeded").put("data",value);}
 boolean granted(String permission){return c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 JSONObject blocked(String permission)throws Exception{return new JSONObject().put("state","permission_required").put("permission",permission).put("executed",false).put("submitted",false);}
 JSONObject execute(String op,JSONObject p)throws Exception{
  switch(op){
   case "photos.select":return interactions.request("photos",p);
   case "photos.query":{
    if(!granted(Manifest.permission.READ_MEDIA_IMAGES)&&!granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))return blocked(Manifest.permission.READ_MEDIA_IMAGES);
    int limit=p.optInt("limit",50);long after=p.optLong("after_id",0);if(limit<1||limit>200||after<0)throw new IllegalArgumentException("Invalid page");JSONArray rows=new JSONArray();
    try(Cursor cur=c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name","mime_type","_size","date_added","width","height","relative_path"},"_id > ?",new String[]{String.valueOf(after)},"_id ASC")){
     if(cur!=null)while(cur.moveToNext()&&rows.length()<limit){JSONObject row=new JSONObject();for(int i=0;i<cur.getColumnCount();i++)row.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));row.put("uri",ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cur.getLong(0)).toString());rows.put(row);}
    }return ok(rows);
   }
   case "photos.read":{
    long id=p.getLong("id");if(id<0)throw new IllegalArgumentException("Invalid photo ID");Uri uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id);
    int limit=p.optInt("limit",65536);long offset=p.optLong("offset",0);if(limit<1||limit>262144||offset<0)throw new IllegalArgumentException("Invalid byte range");
    try(InputStream in=c.getContentResolver().openInputStream(uri)){in.skipNBytes(offset);byte[] bytes=in.readNBytes(limit);return ok(new JSONObject().put("uri",uri.toString()).put("mime",c.getContentResolver().getType(uri)).put("offset",offset).put("bytes",bytes.length).put("base64",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)));}
   }
   case "photos.delete":{
    if(!MediaStore.canManageMedia(c))return blocked("android.permission.MANAGE_MEDIA");JSONArray ids=p.getJSONArray("ids");if(ids.length()<1||ids.length()>50)throw new IllegalArgumentException("1..50 IDs required");
    for(int i=0;i<ids.length();i++)if(ids.getLong(i)<0)throw new IllegalArgumentException("Invalid photo ID");
    JSONArray results=new JSONArray();for(int i=0;i<ids.length();i++){long id=ids.getLong(i);try{int count=c.getContentResolver().delete(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id),null,null);results.put(new JSONObject().put("id",id).put("deleted_rows",count));}catch(Exception e){return new JSONObject().put("state","failed").put("partial_results",results).put("failed_id",id).put("error",e.toString());}}
    return ok(results);
   }
   default:throw new IllegalArgumentException("Unknown content operation: "+op);
  }
 }
}
