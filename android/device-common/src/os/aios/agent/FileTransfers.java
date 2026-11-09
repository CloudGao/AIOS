package os.aios.agent;
import android.content.*;
import android.os.Environment;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
final class FileTransfers extends SharedFiles {
 FileTransfers(Context c){super(c);}
 SharedPreferences store(){return c.getSharedPreferences("uploads",0);}
 JSONObject ok(JSONObject r)throws Exception{return new JSONObject().put("state","succeeded").put("data",r).put("completion_verified",true);}
 synchronized JSONObject execute(String op,JSONObject p)throws Exception{
  if(op.equals("files.stat")){File f=target(p.getString("path"));return ok(new JSONObject().put("exists",f.exists()).put("directory",f.isDirectory()).put("bytes",f.length()).put("sha256",f.isFile()?digest(f):JSONObject.NULL));}
  if(op.equals("files.upload.begin")){
   File dst=target(p.getString("path"));if(!dst.getParentFile().isDirectory())throw new IllegalArgumentException("Parent directory missing");String expected=p.optString("expected_sha256","");
   if(dst.exists()&&(expected.isEmpty()||!digest(dst).equals(expected)))throw new IllegalArgumentException("Destination exists or changed; supply current expected_sha256 to replace");
   long bytes=p.getLong("bytes");if(bytes<0)throw new IllegalArgumentException("Invalid size");String id=UUID.randomUUID().toString();File stage=new File(dst.getParentFile(),".aios-upload-"+id);if(!stage.createNewFile())throw new IOException("Cannot create staged upload");
   JSONObject r=new JSONObject().put("id",id).put("path",dst.getPath()).put("stage",stage.getPath()).put("bytes",bytes).put("offset",0).put("expected_sha256",expected).put("state","uploading");store().edit().putString(id,r.toString()).commit();return ok(r);
  }
  String id=p.getString("id");String text=store().getString(id,null);if(text==null)throw new IllegalArgumentException("Upload not found");JSONObject r=new JSONObject(text);
  File dst=target(r.getString("path")),stage=target(r.getString("stage"));
  if(op.equals("files.upload.status")){if(r.getString("state").equals("uploading"))r.put("offset",stage.length());return ok(r);}
  if(!r.getString("state").equals("uploading"))throw new IllegalArgumentException("Upload already finished");
  if(op.equals("files.upload.cancel")){if(stage.exists()&&!stage.delete())throw new IOException("Cannot remove staged upload");r.put("state","cancelled");}
  else if(op.equals("files.upload.append")){
   byte[] bytes=Base64.decode(p.getString("base64"),Base64.DEFAULT);if(bytes.length>65536||stage.length()!=p.getLong("offset")||stage.length()+bytes.length>r.getLong("bytes"))throw new IllegalArgumentException("Invalid chunk size/offset");
   try(FileOutputStream out=new FileOutputStream(stage,true)){out.write(bytes);out.getFD().sync();}r.put("offset",stage.length());
  }else if(op.equals("files.upload.commit")){
   if(stage.length()!=r.getLong("bytes"))throw new IllegalArgumentException("Upload incomplete");String hash=digest(stage);if(!hash.equals(p.getString("sha256")))throw new IllegalArgumentException("SHA256 mismatch");String expected=r.getString("expected_sha256");if(dst.exists()&&(expected.isEmpty()||!digest(dst).equals(expected)))throw new IllegalArgumentException("Destination changed");if(!dst.exists()&&!expected.isEmpty())throw new IllegalArgumentException("Original destination disappeared");
   Files.move(stage.toPath(),dst.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);r.put("state","committed").put("sha256",hash).put("offset",dst.length());android.media.MediaScannerConnection.scanFile(c,new String[]{dst.getPath()},null,null);
  }else throw new IllegalArgumentException("Unknown upload operation");
  store().edit().putString(id,r.toString()).commit();return ok(r);
 }
}
