package os.aios.agent;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import android.content.pm.PackageManager;

/** Binder stays on-device; replies never travel via adb or a desktop service. */
final class CodexAccountClient implements AutoCloseable {
    final Context context;
    final ConcurrentHashMap<Integer,CompletableFuture<JSONObject>> pending=new ConcurrentHashMap<>();
    final AtomicInteger ids=new AtomicInteger();
    final ExecutorService transfers=Executors.newCachedThreadPool();
    volatile Messenger remote;
    boolean bound;
    volatile CompletableFuture<Void> connected=new CompletableFuture<>();
    final Messenger reply=new Messenger(new Handler(Looper.getMainLooper()){
        public void handleMessage(Message m){
            CompletableFuture<JSONObject> f=pending.remove(m.arg1);
            ParcelFileDescriptor fd=m.getData().getParcelable("json_fd",ParcelFileDescriptor.class);
            if(fd==null){if(f!=null)try{f.complete(new JSONObject(m.getData().getString("json","{}")));}catch(Exception e){f.completeExceptionally(e);}return;}
            if(f==null){try{fd.close();}catch(IOException ignored){}return;}
            try{transfers.execute(()->{
                try(java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
                    byte[] bytes=input.readNBytes(64*1024*1024+1);
                    if(bytes.length>64*1024*1024)throw new IOException("Runtime reply too large");
                    f.complete(new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
                }catch(Exception e){f.completeExceptionally(e);}
            });}catch(RejectedExecutionException closed){try{fd.close();}catch(IOException ignored){}f.completeExceptionally(closed);}
        }
    });
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){remote=new Messenger(binder);connected.complete(null);}
        public void onServiceDisconnected(ComponentName name){remote=null;connected=new CompletableFuture<>();failPending();}
        public void onBindingDied(ComponentName name){
            synchronized(CodexAccountClient.this){
                remote=null;
                try{if(bound)context.unbindService(connection);}catch(Exception ignored){}
                bound=false;
                connected=new CompletableFuture<>();
            }
            failPending();
        }
        public void onNullBinding(ComponentName name){connected.completeExceptionally(new IOException("AIOS account service unavailable"));}
    };
    CodexAccountClient(Context c){context=c.getApplicationContext();}
    boolean isRuntimeInstalled(){
        try{context.getPackageManager().getPackageInfo(os.aios.host.HostBindings.RUNTIME_PACKAGE,PackageManager.PackageInfoFlags.of(0));return true;}
        catch(PackageManager.NameNotFoundException e){return false;}
    }
    synchronized void bind(){
        if(bound)return;
        if(!isRuntimeInstalled())throw new IllegalStateException("AIOS 运行时未安装");
        bound=context.bindService(new Intent().setComponent(os.aios.host.HostBindings.runtimeService()),connection,Context.BIND_AUTO_CREATE);
        if(!bound)throw new IllegalStateException("Cannot bind AIOS account service");
    }
    JSONObject call(JSONObject request)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Tools must run off the UI thread");
        bind();connected.get(10,TimeUnit.SECONDS);
        int id=ids.incrementAndGet();CompletableFuture<JSONObject> f=new CompletableFuture<>();pending.put(id,f);
        ParcelFileDescriptor descriptor=null;java.io.File temporary=null;
        try{Message m=Message.obtain(null,1);m.arg1=id;m.replyTo=reply;Bundle b=new Bundle();String json=request.toString();byte[] bytes=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(bytes.length<=48_000)b.putString("json",json);else{
                if(bytes.length>16*1024*1024)throw new IOException("Runtime request exceeds 16 MiB transfer limit");
                temporary=java.io.File.createTempFile("binder-request-",".json",context.getCacheDir());
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(temporary)){output.write(bytes);output.getFD().sync();}
                descriptor=ParcelFileDescriptor.open(temporary,ParcelFileDescriptor.MODE_READ_ONLY);b.putParcelable("json_fd",descriptor);
            }
            m.setData(b);Messenger target=remote;if(target==null)throw new IOException("AIOS disconnected");target.send(m);
            JSONObject response=f.get(60,TimeUnit.SECONDS);if(!response.optBoolean("ok"))throw new IOException(response.optString("error","账号操作失败"));return response.getJSONObject("result");
        }finally{pending.remove(id);if(descriptor!=null)try{descriptor.close();}catch(IOException ignored){}if(temporary!=null)temporary.delete();}
    }
    void failPending(){pending.values().forEach(f->f.completeExceptionally(new IOException("AIOS disconnected; read state before retrying writes")));}
    public synchronized void close(){remote=null;failPending();transfers.shutdownNow();if(bound){context.unbindService(connection);bound=false;}}
}
