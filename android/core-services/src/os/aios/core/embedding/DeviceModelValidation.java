package os.aios.core.embedding;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Private disposable process: real inference + bounded native delegation evidence.
 * Missing/ambiguous log evidence fails closed, even if inference returned successfully.
 */
public final class DeviceModelValidation extends Service {
    private boolean running;
    public IBinder onBind(Intent intent){return null;}
    public synchronized int onStartCommand(Intent intent,int flags,int startId){
        if(running)return START_NOT_STICKY;running=true;
        new Thread(()->{try{validate();}catch(Throwable e){Log.w("AIOSModelValidation","Device GPU validation unavailable",e);}finally{stopSelf();}},"aios-model-validation").start();
        return START_NOT_STICKY;
    }
    void validate()throws Exception{
        ModelRegistry registry=new ModelRegistry(this);
        if(registry.validated("image")&&registry.validated("text")&&(!registry.faceAvailable()||registry.validated("face")))return;
        JSONObject manifest=registry.manifest();
        JSONObject evidence=new JSONObject().put("fingerprint",Build.FINGERPRINT)
            .put("system_incremental",os.aios.host.HostIdentity.systemIncremental())
            .put("vendor_fingerprint",os.aios.host.HostIdentity.vendorFingerprint())
            .put("space_id",manifest.getString("space_id"));
        String[] roles={"image","text","face"};
        String[] pinned={"a810c23746c0990964eb0a445172d6307f3ba74ac8c6b207e27273e31410adce","d633b6c65c1632316889a0994aeaae4b40081bcdf3757a12ffb7d1f0a6ee4d20","3e82b513e65d00b56c35b572f964a594fdbd6e56717361bc0b64cbaf9e140a4d"};
        for(int i=0;i<roles.length;i++){
            String role=roles[i];if(role.equals("face")&&!registry.faceAvailable())continue;
            try{File model=registry.model(role);
            if(!ModelRegistry.sha256(model).equals(pinned[i]))throw new SecurityException("gpu_probe_model_not_approved");
            String token=UUID.randomUUID().toString();Log.i("AIOSModelValidation","BEGIN-"+token);
            try(LiteRtGpuBackend backend=new LiteRtGpuBackend(this,model.getPath())){
                float[][] result=backend.run(role.equals("text")?null:new float[3*(role.equals("image")?224*224:192*192)],role.equals("text")?new ClipTokenizer(this).encode("a photo"):null);
                if(result.length==0)throw new IllegalStateException("gpu_probe_empty_output");
                for(float[] tensor:result){if(tensor.length==0)throw new IllegalStateException("gpu_probe_empty_tensor");for(float value:tensor)if(!Float.isFinite(value))throw new IllegalStateException("gpu_probe_nonfinite");}
            }
            Log.i("AIOSModelValidation","END-"+token);
            String logs=logs();int start=logs.indexOf("BEGIN-"+token),end=logs.indexOf("END-"+token);
            if(start<0||end<=start)throw new IllegalStateException("gpu_probe_log_boundaries_missing");
            String window=logs.substring(start,end);
            java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("Replacing (\\d+) out of (\\d+) node\\(s\\) with delegate.*?yielding (\\d+) partitions?",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(window);
            int matched=0,delegated=0;while(matcher.find()){
                if(Integer.parseInt(matcher.group(1))<1||Integer.parseInt(matcher.group(1))!=Integer.parseInt(matcher.group(2))||Integer.parseInt(matcher.group(3))!=1)throw new IllegalStateException("gpu_probe_partial_delegation");delegated=Integer.parseInt(matcher.group(1));matched++;
            }
            if(matched!=1||!window.toLowerCase(Locale.ROOT).contains("gpu"))throw new IllegalStateException("gpu_probe_full_delegation_unproven");
            File log=new File(registry.directory(),"gpu-"+role+".log");os.aios.core.CompatFiles.writeString(log.toPath(),window);
            evidence.put(role,new JSONObject().put("full_gpu",true).put("model_sha256",pinned[i]).put("evidence_sha256",ModelRegistry.sha256(log)).put("backend","LITERT_GPU").put("delegated_nodes",delegated).put("partitions",1));
            }catch(Exception error){if(!role.equals("face"))throw error;Log.w("AIOSModelValidation","Optional face GPU validation unavailable",error);}
        }
        // Image/text must pass; face evidence is published only when its independent probe passes.
        File temp=new File(registry.directory(),"validated.tmp"),dest=new File(registry.directory(),"validated.json");
        os.aios.core.CompatFiles.writeString(temp.toPath(),evidence.toString());Files.move(temp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    String logs()throws Exception{
        java.lang.Process child=new ProcessBuilder("/system/bin/logcat","-d","-v","brief","--pid="+Process.myPid(),"-t","2000").redirectErrorStream(true).start();
        ExecutorService reader=Executors.newSingleThreadExecutor();
        try{
            Future<String> output=reader.submit(()->{try(InputStream in=child.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("gpu_probe_log_limit");bytes.write(buffer,0,n);}return bytes.toString(StandardCharsets.UTF_8);
            }});
            String result=output.get(15,TimeUnit.SECONDS);
            if(!child.waitFor(1,TimeUnit.SECONDS)||child.exitValue()!=0)throw new IOException("gpu_probe_logs_unavailable");
            return result;
        }finally{child.destroyForcibly();reader.shutdownNow();}
    }
}
