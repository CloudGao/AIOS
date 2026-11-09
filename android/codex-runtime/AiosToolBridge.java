package os.aios.codex;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Binder stays on-device; replies never travel via adb or a desktop service. */
final class AiosToolBridge implements AutoCloseable {
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
            synchronized(AiosToolBridge.this){
                remote=null;
                try{if(bound)context.unbindService(connection);}catch(Exception ignored){}
                bound=false;
                connected=new CompletableFuture<>();
            }
            failPending();
        }
        public void onNullBinding(ComponentName name){connected.completeExceptionally(new IOException("AIOS tool service unavailable"));}
    };
    AiosToolBridge(Context c){context=c.getApplicationContext();}
    synchronized void bind(){
        if(bound)return;
        bound=context.bindService(new Intent().setComponent(os.aios.host.HostBindings.agentService()),connection,Context.BIND_AUTO_CREATE);
        if(!bound)throw new IllegalStateException("Cannot bind AIOS tool service");
    }
    JSONObject call(JSONObject request)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Tools must run off the UI thread");
        if(request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>196608)throw new IllegalArgumentException("Tool arguments exceed 192 KiB; use bounded resources");
        bind();connected.get(10,TimeUnit.SECONDS);
        int id=ids.incrementAndGet();CompletableFuture<JSONObject> f=new CompletableFuture<>();pending.put(id,f);
        try{Message m=Message.obtain(null,1);m.arg1=id;m.replyTo=reply;Bundle b=new Bundle();b.putString("json",request.toString());m.setData(b);
            Messenger target=remote;if(target==null)throw new IOException("AIOS disconnected");target.send(m);return f.get(90,TimeUnit.SECONDS);
        }finally{pending.remove(id);}
    }
    JSONArray list()throws Exception{
        JSONObject result=call(new JSONObject().put("op","tools/list"));
        if(!"succeeded".equals(result.optString("state")))throw new IOException("AIOS tools: "+result.optString("error",result.optString("state")));
        if(result.optInt("protocol")!=1)throw new IOException("Unsupported AIOS tool protocol");
        return result.getJSONArray("tools");
    }
    JSONObject execute(String name,JSONObject arguments,String requestId)throws Exception{return execute(name,arguments,requestId,"");}
    JSONObject execute(String name,JSONObject arguments,String requestId,String threadId)throws Exception{
        return execute(name,arguments,requestId,threadId,"");
    }
    JSONObject execute(String name,JSONObject arguments,String requestId,String threadId,String turnId)throws Exception{
        return call(new JSONObject().put("turn_id",turnId).put("op","tools/call").put("name",name).put("arguments",arguments).put("request_id",requestId).put("thread_id",threadId));
    }
    void failPending(){pending.values().forEach(f->f.completeExceptionally(new IOException("AIOS disconnected; read state before retrying writes")));}
    public synchronized void close(){remote=null;failPending();transfers.shutdownNow();if(bound){context.unbindService(connection);bound=false;}}
}
