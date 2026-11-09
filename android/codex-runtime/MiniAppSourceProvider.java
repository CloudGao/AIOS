package os.aios.codex;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Exports one bounded static project; never exposes the runtime home or credentials. */
public final class MiniAppSourceProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
  try{
   if(!mode.equals("r")||Binder.getCallingUid()!=getContext().getPackageManager().getPackageUid("os.aios.agent",0))throw new SecurityException("Only the development gateway may read a project");
   List<String> segments=uri.getPathSegments();if(segments.size()!=1||!segments.get(0).matches("[a-z][a-z0-9-]{2,47}"))throw new IllegalArgumentException("Invalid project id");String id=segments.get(0);
   String provider=new ProviderStore(getContext()).active();File home=new File(getContext().getFilesDir(),provider.equals("chatgpt")?"codex":"provider-"+provider);
   File workspace=new File(home,"workspace"),projects=new File(workspace,"miniapps"),project=new File(projects,id);
   if(Files.isSymbolicLink(home.toPath())||Files.isSymbolicLink(workspace.toPath())||Files.isSymbolicLink(projects.toPath())||Files.isSymbolicLink(project.toPath())||!project.isDirectory()||!project.getCanonicalFile().equals(new File(projects.getCanonicalFile(),id)))throw new IllegalArgumentException("Expected a real directory under workspace/miniapps");
   File manifest=new File(project,"manifest.json");if(Files.isSymbolicLink(manifest.toPath())||!manifest.isFile()||manifest.length()>8192)throw new IllegalArgumentException("manifest.json must be a regular file <=8KiB");
   JSONObject m=new JSONObject(os.aios.core.CompatFiles.readString(manifest.toPath()));if(!m.getString("id").equals(id))throw new IllegalArgumentException("Manifest id does not match project directory");
   JSONObject files=new JSONObject();long[] total={0};int[] count={0};
   try(java.util.stream.Stream<Path> stream=Files.walk(project.toPath(),16)){
    for(Iterator<Path> i=stream.iterator();i.hasNext();){Path p=i.next();if(p.equals(project.toPath()))continue;
     if(Files.isSymbolicLink(p))throw new IllegalArgumentException("Symlinks are not publishable");
     if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;
     if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Only static regular files are publishable");
     String name=project.toPath().relativize(p).toString();if(name.equals("manifest.json"))continue;
     if(!name.matches("[A-Za-z0-9_./-]+")||name.startsWith("_ling/")||Arrays.asList(name.split("/")).contains(".."))throw new IllegalArgumentException("Invalid file path");
     if(++count[0]>128||Files.size(p)>49152)throw new IllegalArgumentException("Bundle limit: 128 files; each <=48KiB");
     byte[] bytes=Files.readAllBytes(p);total[0]+=bytes.length;if(bytes.length>49152||total[0]>2097152)throw new IllegalArgumentException("Bundle limit exceeded");
     files.put(name,android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));
    }
   }
   byte[] bytes=new JSONObject().put("manifest",m).put("files",files).toString().getBytes(StandardCharsets.UTF_8);
   File temp=File.createTempFile("miniapp-export-",".json",getContext().getCacheDir());
   try{Files.write(temp.toPath(),bytes);ParcelFileDescriptor fd=ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY);if(!temp.delete()){fd.close();throw new IOException("Cannot unlink export snapshot");}return fd;}finally{temp.delete();}
  }catch(SecurityException e){throw e;}catch(Exception e){throw new FileNotFoundException(e.getMessage());}
 }
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}public String getType(Uri u){return "application/json";}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
