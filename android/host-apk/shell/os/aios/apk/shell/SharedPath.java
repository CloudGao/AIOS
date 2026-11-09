package os.aios.apk.shell;
import java.io.*;
/** Canonical shared-storage boundary; both reads and exclusive creates use this. */
final class SharedPath {
 static File resolve(File root,String path)throws IOException {
  if(path==null||path.length()>4096||!new File(path).isAbsolute())throw new IllegalArgumentException("shared_storage_absolute_path_required");
  File base=root.getCanonicalFile(),file=new File(path).getCanonicalFile();
  if(!file.getPath().startsWith(base.getPath()+File.separator))throw new SecurityException("outside_shared_storage");return file;
 }
}
