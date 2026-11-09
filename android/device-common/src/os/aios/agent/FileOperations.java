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
final class FileOperations {
 final Context c;final os.aios.device.DeviceInteractions interactions;
 FileOperations(Context context,os.aios.device.DeviceInteractions interactions){c=context;this.interactions=interactions;}
 JSONObject ok(Object value)throws Exception{return new JSONObject().put("state","succeeded").put("data",value);}
 boolean granted(String permission){return c.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 JSONObject blocked(String permission)throws Exception{return new JSONObject().put("state","permission_required").put("permission",permission).put("executed",false).put("submitted",false);}
 JSONObject execute(String op,JSONObject p)throws Exception{
  if(op.startsWith("files.upload.")||op.equals("files.stat"))return new FileTransfers(c).execute(op,p);
  switch(op){
   case "files.access":return ok(new JSONObject().put("all_shared_files",Environment.isExternalStorageManager()).put("root",Environment.getExternalStorageDirectory().getCanonicalPath()).put("all_photos",granted(Manifest.permission.READ_MEDIA_IMAGES)).put("all_videos",granted(Manifest.permission.READ_MEDIA_VIDEO)).put("all_audio",granted(Manifest.permission.READ_MEDIA_AUDIO)).put("media_management",MediaStore.canManageMedia(c)));
   case "files.list":{
    File dir=shared(p.optString("path",Environment.getExternalStorageDirectory().getPath()));
    File[] files=dir.listFiles();if(files==null)throw new IOException("Directory inaccessible or not a directory");
    Arrays.sort(files,Comparator.comparing(File::getName));int offset=p.optInt("offset",0),limit=p.optInt("limit",100);if(offset<0||limit<1||limit>200)throw new IllegalArgumentException("Invalid page");
    JSONArray items=new JSONArray();for(int i=offset;i<files.length&&items.length()<limit;i++){File f=files[i];items.put(new JSONObject().put("name",f.getName()).put("path",f.getPath()).put("directory",f.isDirectory()).put("bytes",f.length()).put("modified_ms",f.lastModified()));}
    return ok(new JSONObject().put("path",dir.getPath()).put("items",items).put("total",files.length).put("next_offset",offset+items.length()<files.length?offset+items.length():JSONObject.NULL));
   }
   case "files.mkdir":{File f=shared(p.getString("path"));if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Cannot create directory");return ok(new JSONObject().put("path",f.getPath()));}
   case "files.delete":{
    File f=accessible(p.getString("name"));boolean existed=f.exists();if(existed&&!f.delete())throw new IOException("Cannot delete file or non-empty directory");
    return ok(new JSONObject().put("existed",existed).put("deleted",existed));
   }
   case "files.rename":{
    File from=accessible(p.getString("name")),to=accessible(p.getString("new_name"));
    if(!from.exists()||to.exists())throw new IllegalArgumentException("Source absent or destination exists");
    if(!from.renameTo(to))throw new IOException("Rename failed");return ok(new JSONObject().put("path",to.getPath()));
   }
   case "files.read":{
    File f=accessible(p.getString("name"));try(InputStream in=new FileInputStream(f)){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("File exceeds 64KiB text limit; use files.read_bytes");return ok(new JSONObject().put("text",new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));}
   }
   case "files.read_bytes":{
    File f=shared(p.getString("path"));long offset=p.optLong("offset",0);int limit=p.optInt("limit",65536);if(offset<0||limit<1||limit>262144)throw new IllegalArgumentException("Invalid byte range");
    try(RandomAccessFile in=new RandomAccessFile(f,"r")){in.seek(offset);byte[] data=new byte[limit];int n=in.read(data);if(n<0)n=0;return ok(new JSONObject().put("base64",android.util.Base64.encodeToString(Arrays.copyOf(data,n),android.util.Base64.NO_WRAP)).put("offset",offset).put("bytes",n).put("total_bytes",in.length()).put("eof",offset+n>=in.length()));}
   }
   case "files.write":{
    File f=accessible(p.getString("name"));String text=p.getString("text");if(text.length()>16000)throw new IllegalArgumentException("Text too large");
    java.nio.file.Files.write(f.toPath(),text.getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE);return ok(new JSONObject().put("path",f.getPath()).put("bytes",f.length()));
   }
   case "files.selected_metadata":{
    Uri uri=Uri.parse(p.getString("uri"));
    if(!"content".equals(uri.getScheme())||c.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Selected URI read grant required");
    return ok(new JSONObject().put("mime",c.getContentResolver().getType(uri)).put("metadata",query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},1)));
   }
   case "files.selected_text":{
    Uri uri=Uri.parse(p.getString("uri"));
    if(!"content".equals(uri.getScheme())||c.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Selected URI read grant required");
    try(InputStream in=c.getContentResolver().openInputStream(uri)){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("Document exceeds 64KiB");return ok(new JSONObject().put("text",new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));}
   }
   case "files.select":return interactions.request("document",p);
   default:throw new IllegalArgumentException("Unknown content operation: "+op);
  }
 }
 File shared(String path)throws Exception{
  if(!Environment.isExternalStorageManager())throw new SecurityException("All-files access required");
  if(path.indexOf('\0')>=0||!new File(path).isAbsolute())throw new IllegalArgumentException("Absolute shared-storage path required");
  File f=new File(path).getCanonicalFile();File root=Environment.getExternalStorageDirectory().getCanonicalFile();
  if(!f.equals(root)&&!f.getPath().startsWith(root.getPath()+File.separator))throw new SecurityException("Path outside primary shared storage");
  return f;
 }
 File accessible(String name)throws Exception{return new File(name).isAbsolute()?shared(name):managed(name);}
 File managed(String name)throws Exception{if(!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))throw new IllegalArgumentException("Invalid managed filename");File dir=new File(c.getFilesDir(),"managed");dir.mkdirs();return new File(dir,name);}
 JSONArray query(Uri uri,String[] projection,int limit)throws Exception{
  JSONArray out=new JSONArray();
  try(Cursor cur=c.getContentResolver().query(uri,projection,null,null,null)){
   if(cur==null)return out;
   while(cur.moveToNext()&&out.length()<limit){JSONObject row=new JSONObject();
    for(int i=0;i<cur.getColumnCount();i++){
     if(cur.getType(i)==Cursor.FIELD_TYPE_BLOB)continue;
     row.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));
    }out.put(row);
   }
  }return out;
 }
}
