package os.aios.runtime;

import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** FD-only document transport. Each isolated worker binding has a unique process instance. */
public final class DocumentRpc {
    public static Bundle request(Context context,String component,String operation,JSONObject args,ParcelFileDescriptor input,ParcelFileDescriptor output,int seconds)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("worker_thread_required");
        HandlerThread thread=new HandlerThread("document-reply");thread.start();
        CompletableFuture<Bundle> future=new CompletableFuture<>();
        Messenger reply=new Messenger(new Handler(thread.getLooper()){
            public void handleMessage(Message m){Bundle b=m.getData();if(b.containsKey("error"))future.completeExceptionally(new IllegalStateException(b.getString("error")));else if(!future.complete(b)){ParcelFileDescriptor fd=b.getParcelable("fd",ParcelFileDescriptor.class);if(fd!=null)try{fd.close();}catch(Exception ignored){}}}
        });
        ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){try{Message m=Message.obtain();Bundle b=new Bundle();b.putString("operation",operation);b.putString("json",args.toString());if(input!=null)b.putParcelable("input",input);if(output!=null)b.putParcelable("output",output);m.setData(b);m.replyTo=reply;new Messenger(binder).send(m);}catch(Exception e){future.completeExceptionally(e);}}
            public void onServiceDisconnected(ComponentName n){future.completeExceptionally(new IllegalStateException("worker_exited"));}
            public void onNullBinding(ComponentName n){future.completeExceptionally(new IllegalStateException("worker_unavailable"));}
            public void onBindingDied(ComponentName n){future.completeExceptionally(new IllegalStateException("worker_exited"));}
        };
        boolean bound=false;
        try{
            Intent intent=new Intent().setComponent(ComponentName.unflattenFromString(component));
            if(component.endsWith("DocumentWorkerService"))bound=context.bindIsolatedService(intent,Context.BIND_AUTO_CREATE,"document_"+java.util.UUID.randomUUID().toString().replace('-','_'),context.getMainExecutor(),connection);
            else bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);
            if(!bound)throw new IllegalStateException("worker_unavailable");
            Bundle result=future.get(seconds,TimeUnit.SECONDS);return result;
        }catch(TimeoutException e){future.cancel(false);throw new IllegalStateException("processing_timeout",e);}
        finally{if(bound)context.unbindService(connection);thread.quitSafely();}
    }
    public static void reply(Messenger endpoint,JSONObject result,ParcelFileDescriptor fd,String error){try{Bundle b=new Bundle();if(error!=null)b.putString("error",error);else{String json=result.toString();if(json.length()>90000)b.putString("error","result_budget_exceeded");else{b.putString("json",json);if(fd!=null)b.putParcelable("fd",fd);}}Message m=Message.obtain();m.setData(b);endpoint.send(m);}catch(Exception ignored){}}
}
