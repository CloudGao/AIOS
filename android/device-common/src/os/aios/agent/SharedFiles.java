package os.aios.agent;
import android.content.Context;
import android.os.Environment;
import java.io.*;
import java.security.*;
/** Internal file access policy reused by independent recording/package backends. */
class SharedFiles {
 final Context c;
 SharedFiles(Context context){c=context;}
 File target(String path)throws Exception{File f=new File(path).getCanonicalFile(),root=Environment.getExternalStorageDirectory().getCanonicalFile();if(!f.getPath().startsWith(root.getPath()+"/")||!new File(path).isAbsolute())throw new IllegalArgumentException("shared_storage_absolute_path_required");if(!Environment.isExternalStorageManager())throw new SecurityException("all_files_access_required: android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");return f;}
 String digest(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)md.update(buf,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format("%02x",b));return s.toString();}
}
