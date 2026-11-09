package os.aios.agent;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;

/** ROM backend diagnostic reads through LineageOS Binder; no arbitrary service entry. */
final class SystemDiagnostics {
 static final int LIMIT=24000;
 private static final Set<String> SERVICES=Set.of("media_session","usb","window","wifi","power","meminfo","cpuinfo","diskstats","thermalservice","battery","activity");
 static JSONObject read(Context context,String service)throws Exception {
  if(!SERVICES.contains(service))throw new IllegalArgumentException("Unsupported diagnostic service");
  if(context.checkSelfPermission(android.Manifest.permission.DUMP)!=PackageManager.PERMISSION_GRANTED)
   return new JSONObject().put("state","permission_required").put("permission",android.Manifest.permission.DUMP);
  IBinder remote=ServiceManager.checkService(service);
  if(remote==null)return new JSONObject().put("state","failed").put("error","System diagnostic service unavailable").put("service",service);
  ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
  FutureTask<String> read=new FutureTask<>(()->{
   try(Reader reader=new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]),StandardCharsets.UTF_8)){
    StringBuilder text=new StringBuilder();char[] buffer=new char[1024];
    while(text.length()<=LIMIT){int n=reader.read(buffer,0,Math.min(buffer.length,LIMIT+1-text.length()));if(n<0)break;text.append(buffer,0,n);}
    return text.toString();
   }
  });
  Thread worker=new Thread(read,"aios-diagnostic-read");worker.setDaemon(true);worker.start();
  try {
   remote.dumpAsync(pipe[1].getFileDescriptor(),new String[0]);pipe[1].close();
   String text=read.get(5,TimeUnit.SECONDS);boolean truncated=text.length()>LIMIT;
   if(text.startsWith("Permission Denial")||text.startsWith("Permission denial"))
    return new JSONObject().put("state","permission_required").put("permission",text.contains("PACKAGE_USAGE_STATS")?android.Manifest.permission.PACKAGE_USAGE_STATS:android.Manifest.permission.DUMP).put("service",service).put("reason",text.substring(0,Math.min(text.length(),1000)).trim());
   return new JSONObject().put("state","succeeded").put("data",new JSONObject().put("raw",truncated?text.substring(0,LIMIT):text)
    .put("limit_chars",LIMIT).put("truncated",truncated).put("service",service));
  }finally{read.cancel(true);try{pipe[0].close();}catch(IOException ignored){}try{pipe[1].close();}catch(IOException ignored){}}
 }
 private SystemDiagnostics(){}
}
