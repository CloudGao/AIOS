package os.aios.core.inference;

import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** App-owned authenticated loopback runtime. No desktop, tools, or network model fallback. */
public final class LocalTextInference implements os.aios.core.CoreFeatures.Inference {
    public static final String MODEL="local:lfm2.5-350m:q4_k_m";
    private static final String HASH="7e6f72643caafc9a68256686638c4d7916f2cec76d1df478d4c3ddcd95a6aed4";
    private static LocalTextInference instance;
    private final Context context;
    private Process process;
    private int port;
    private String secret;
    private String verifiedPath="";
    private long usedAt;
    private final java.util.concurrent.ScheduledExecutorService reaper=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private LocalTextInference(Context c){context=c.getApplicationContext();reaper.scheduleWithFixedDelay(()->{synchronized(this){if(process!=null&&SystemClock.elapsedRealtime()-usedAt>60000)stop();}},30,30,java.util.concurrent.TimeUnit.SECONDS);}
    public static synchronized LocalTextInference get(Context c){if(instance==null)instance=new LocalTextInference(c);return instance;}
    private File model(){File optional=os.aios.runtime.models.OptionalModelStore.file(context,"lfm");return optional.isFile()?optional:os.aios.host.HostBindings.bundledTextModel();}
    private File executable(){return os.aios.host.HostBindings.nativePayload(context,"libaios_llama_server.so");}
    public synchronized JSONObject status()throws JSONException{
        return new JSONObject().put("id",MODEL).put("installed",model().isFile()).put("backend_installed",executable().isFile())
            .put("loaded",process!=null&&process.isAlive()).put("verified",!verifiedPath.isEmpty()).put("backend","cpu")
            .put("bytes",model().isFile()?model().length():0).put("state",!model().isFile()?"model_missing":!executable().isFile()?"backend_missing":verifiedPath.isEmpty()?"unverified":"available");
    }
    public synchronized JSONObject generate(JSONObject request)throws Exception{
        if(!MODEL.equals(request.optString("model",MODEL)))throw new IllegalArgumentException("model_unavailable");
        String instruction=request.getString("instruction"),input=request.getString("input");
        if(instruction.length()>8000||input.length()>16000)throw new IllegalArgumentException("input_capacity");
        int maximum=Math.max(1,Math.min(512,request.optInt("max_tokens",128)));
        usedAt=SystemClock.elapsedRealtime();start();
        JSONObject body=new JSONObject().put("model","aios-local").put("stream",false).put("temperature",0.1)
            .put("max_tokens",maximum).put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",instruction))
                .put(new JSONObject().put("role","user").put("content",input)));
        JSONObject schema=request.optJSONObject("schema");
        if(schema!=null){
            if(schema.toString().length()>4096)throw new IllegalArgumentException("schema_capacity");
            body.put("response_format",new JSONObject().put("type","json_schema").put("json_schema",new JSONObject().put("name","notification_decision").put("strict",true).put("schema",schema)));
        }else if(request.optBoolean("json",false))body.put("response_format",new JSONObject().put("type","json_object"));
        try{
            JSONObject reply=exchange("/v1/chat/completions",body,20000);
            String text=reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            if(text.length()>16000)throw new IOException("output_capacity");
            return new JSONObject().put("text",text).put("model",MODEL).put("backend","cpu").put("elapsed_ms",SystemClock.elapsedRealtime()-usedAt);
        }catch(Exception error){stop();throw error;}finally{usedAt=SystemClock.elapsedRealtime();}
    }
    private void start()throws Exception{
        if(process!=null&&process.isAlive())return;
        File model=model(),binary=executable();
        if(!model.isFile()||!binary.isFile())throw new IOException("local_model_unavailable");
        String version=model.getAbsolutePath()+":"+model.length()+":"+model.lastModified();
        if(!version.equals(verifiedPath)){
            if(!os.aios.core.embedding.ModelRegistry.sha256(model).equals(HASH))throw new SecurityException("model_hash_mismatch");
            verifiedPath=version;
        }
        try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        secret=UUID.randomUUID().toString()+UUID.randomUUID();
        ProcessBuilder builder=new ProcessBuilder(binary.getAbsolutePath(),"--model",model.getAbsolutePath(),"--alias","aios-local",
            "--host","127.0.0.1","--port",Integer.toString(port),"--api-key",secret,"--ctx-size","4096","--parallel","1",
            "--threads","2","--n-gpu-layers","0","--no-warmup","--no-webui");
        builder.environment().put("LD_LIBRARY_PATH",context.getApplicationInfo().nativeLibraryDir);
        builder.redirectErrorStream(true);process=builder.start();Process running=process;
        Thread drain=new Thread(()->{try(InputStream stream=running.getInputStream()){byte[] block=new byte[4096];while(stream.read(block)>=0){}}catch(IOException ignored){}},"aios-inference-output");drain.setDaemon(true);drain.start();
        long deadline=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<deadline&&process.isAlive()){
            try{exchange("/health",null,1000);return;}catch(IOException temporary){Thread.sleep(100);}
        }
        stop();throw new IOException("local_runtime_start_failed");
    }
    private JSONObject exchange(String path,JSONObject body,int timeout)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(1000);c.setReadTimeout(timeout);c.setRequestProperty("Authorization","Bearer "+secret);
        try{
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            if(c.getResponseCode()!=200)throw new IOException("local_runtime_http_"+c.getResponseCode());
            try(InputStream in=c.getInputStream()){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IOException("local_response_capacity");return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
        }finally{c.disconnect();}
    }
    private void stop(){
        Process running=process;
        if(running==null){secret=null;return;}
        // Android UNIXProcess.destroyForcibly delegates to SIGTERM. llama-server may
        // retain worker threads; do not discard the only handle before confirmed exit.
        if(running.isAlive()){
            java.util.regex.Matcher pid=java.util.regex.Pattern.compile("pid=([0-9]+)").matcher(running.toString());
            if(!pid.find())throw new IllegalStateException("local_runtime_pid_unavailable");
            android.os.Process.killProcess(Integer.parseInt(pid.group(1)));
            try{if(!running.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("local_runtime_stop_failed");}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("local_runtime_stop_interrupted",error);}
        }
        running.destroy();process=null;secret=null;
    }
}
