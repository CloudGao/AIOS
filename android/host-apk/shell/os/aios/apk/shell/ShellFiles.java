package os.aios.apk.shell;
import android.os.*;
import android.system.*;
import java.io.*;
final class ShellFiles {
 // app_process has no bound APK attribution; Environment queries StorageManager as android.
 static File root(){return new File("/storage/emulated/"+android.os.UserHandle.myUserId());}
 static ParcelFileDescriptor open(String path,boolean create)throws Exception {
  File file=SharedPath.resolve(root(),path);
  java.io.FileDescriptor fd=Os.open(file.getPath(),OsConstants.O_CLOEXEC|OsConstants.O_NOFOLLOW|(create?OsConstants.O_WRONLY|OsConstants.O_CREAT|OsConstants.O_EXCL:OsConstants.O_RDONLY),0600);
  try{if(!OsConstants.S_ISREG(Os.fstat(fd).st_mode))throw new SecurityException("regular_file_required");return ParcelFileDescriptor.dup(fd);}finally{Os.close(fd);}
 }
 static File target(String path)throws Exception{return SharedPath.resolve(root(),path);}
 static org.json.JSONObject ok(org.json.JSONObject data)throws Exception{return new org.json.JSONObject().put("state","succeeded").put("data",data).put("backend","adb");}
 static org.json.JSONObject execute(String op,org.json.JSONObject p)throws Exception {
  if(op.equals("files.access"))return ok(new org.json.JSONObject().put("root",root().getCanonicalPath()).put("all_shared_files",root().canRead()).put("shared_storage_backend","adb").put("requires_app_all_files_permission",false).put("scope","shell_accessible_shared_storage"));
  if(op.equals("files.list")){
   String path=p.optString("path",root().getPath());File root=root().getCanonicalFile();File dir=new File(path).getCanonicalFile();if(!dir.equals(root))dir=target(path);
   File[] files=dir.listFiles();if(files==null)throw new IOException("directory_inaccessible");java.util.Arrays.sort(files,java.util.Comparator.comparing(File::getName));
   int offset=p.optInt("offset",0),limit=p.optInt("limit",100);if(offset<0||limit<1||limit>200)throw new IllegalArgumentException("invalid_page");
   org.json.JSONArray items=new org.json.JSONArray();for(int n=offset;n<files.length&&items.length()<limit;n++){File f=files[n];items.put(new org.json.JSONObject().put("name",f.getName()).put("path",f.getPath()).put("directory",f.isDirectory()).put("bytes",f.length()).put("modified_ms",f.lastModified()));}
   return ok(new org.json.JSONObject().put("path",dir.getPath()).put("items",items).put("total",files.length).put("next_offset",offset+items.length()<files.length?offset+items.length():org.json.JSONObject.NULL));
  }
  File f=target(p.getString(op.equals("files.stat")||op.equals("files.mkdir")?"path":"name"));
  switch(op){
   case "files.mkdir":if(!f.isDirectory()&&!f.mkdirs())throw new IOException("mkdir_failed");return ok(new org.json.JSONObject().put("path",f.getPath()));
   case "files.delete":{boolean exists=f.exists();if(exists&&!f.delete())throw new IOException("delete_failed_or_directory_not_empty");return ok(new org.json.JSONObject().put("existed",exists).put("deleted",exists));}
   case "files.rename":{File to=target(p.getString("new_name"));if(!f.exists()||to.exists())throw new IllegalArgumentException("source_missing_or_destination_exists");java.nio.file.Files.move(f.toPath(),to.toPath());return ok(new org.json.JSONObject().put("path",to.getPath()));}
   case "files.stat":{
    Object hash=org.json.JSONObject.NULL;
    if(f.isFile()){java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(open(f.getPath(),false))){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)md.update(bytes,0,n);}StringBuilder hex=new StringBuilder();for(byte b:md.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));hash=hex.toString();}
    return ok(new org.json.JSONObject().put("exists",f.exists()).put("directory",f.isDirectory()).put("bytes",f.length()).put("sha256",hash));
   }
   default:throw new IllegalArgumentException("unsupported_shell_file_operation");
  }
 }
}
