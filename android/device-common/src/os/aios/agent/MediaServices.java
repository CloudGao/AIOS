package os.aios.agent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import org.json.*;
import java.io.*;
final class MediaServices {
 final Context c;MediaServices(Context c){this.c=c;}
 JSONObject execute(String op,JSONObject p)throws Exception{
  if(!java.util.Arrays.asList("media.library.query","media.library.read","media.library.delete").contains(op))throw new IllegalArgumentException("Unknown operation");
  String kind=p.getString("kind");Uri base;String permission;
  switch(kind){case "image":base=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;permission="android.permission.READ_MEDIA_IMAGES";break;case "video":base=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;permission="android.permission.READ_MEDIA_VIDEO";break;case "audio":base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;permission="android.permission.READ_MEDIA_AUDIO";break;default:throw new IllegalArgumentException("Unknown media kind");}
  if(c.checkSelfPermission(permission)!=0)return new JSONObject().put("state","permission_required").put("permission",permission);
  Object result;
  if(op.equals("media.library.query")){
   JSONArray rows=new JSONArray();int limit=p.optInt("limit",50);if(limit<1||limit>200)throw new IllegalArgumentException("Invalid limit");
   try(Cursor cur=c.getContentResolver().query(base,new String[]{"_id","_display_name","mime_type","_size","relative_path","date_modified"},"_id>?",new String[]{String.valueOf(p.optLong("after_id",0))},"_id ASC")){if(cur!=null)while(cur.moveToNext()&&rows.length()<limit){JSONObject r=new JSONObject();for(int i=0;i<cur.getColumnCount();i++)r.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));rows.put(r.put("uri",ContentUris.withAppendedId(base,cur.getLong(0)).toString()));}}result=rows;
  }else{
   long id=p.getLong("id");if(id<1)throw new IllegalArgumentException("Invalid ID");Uri uri=ContentUris.withAppendedId(base,id);
   if(op.equals("media.library.delete")){if(!MediaStore.canManageMedia(c))throw new SecurityException("Media management required");result=new JSONObject().put("deleted_rows",c.getContentResolver().delete(uri,null,null));}
   else{long offset=p.optLong("offset",0);int limit=p.optInt("limit",65536);if(offset<0||limit<1||limit>262144)throw new IllegalArgumentException("Invalid byte range");try(InputStream in=c.getContentResolver().openInputStream(uri)){in.skipNBytes(offset);byte[] bytes=in.readNBytes(limit);result=new JSONObject().put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)).put("bytes",bytes.length).put("offset",offset).put("mime",c.getContentResolver().getType(uri));}}
  }return new JSONObject().put("state","succeeded").put("data",result);
 }
}
