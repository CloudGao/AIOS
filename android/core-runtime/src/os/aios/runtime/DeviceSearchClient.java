package os.aios.runtime;

import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Bounded, read-only RPC. Never invoke while holding a business database transaction. */
public final class DeviceSearchClient {
    private final Context context;
    public DeviceSearchClient(Context c) { context=c.getApplicationContext(); }
    public JSONObject call(String operation, JSONObject arguments) throws Exception { return new JSONObject(request(operation,arguments).getString("json","{}")); }
    public ParcelFileDescriptor open(JSONObject arguments)throws Exception{return request("open",arguments).getParcelable("fd",ParcelFileDescriptor.class);}
    private Bundle request(String operation, JSONObject arguments) throws Exception {
        if (Looper.myLooper()==Looper.getMainLooper()) throw new IllegalStateException("Device search requires a worker thread");
        HandlerThread thread=new HandlerThread("device-search-reply"); thread.start();
        CompletableFuture<Bundle> result=new CompletableFuture<>();
        Messenger reply=new Messenger(new Handler(thread.getLooper()) {
            public void handleMessage(Message message) {
                try { Bundle b=message.getData(); String error=b.getString("error");
                    if(error!=null) result.completeExceptionally(new IllegalStateException(error));
                    else result.complete(b);
                } catch(Exception e) { result.completeExceptionally(e); }
            }
        });
        ServiceConnection connection=new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) {
                try { Message request=Message.obtain(null,1); request.replyTo=reply;
                    Bundle data=new Bundle(); data.putString("operation",operation); data.putString("json",arguments.toString()); request.setData(data);
                    new Messenger(binder).send(request);
                } catch(Exception e) { result.completeExceptionally(e); }
            }
            public void onServiceDisconnected(ComponentName name) { result.completeExceptionally(new IllegalStateException("device_service_disconnected")); }
            public void onNullBinding(ComponentName name) { result.completeExceptionally(new IllegalStateException("device_service_unavailable")); }
        };
        boolean bound=false;
        try { bound=context.bindService(new Intent().setComponent(os.aios.host.HostBindings.deviceSearchService()),connection,Context.BIND_AUTO_CREATE);
            if(!bound) throw new IllegalStateException("device_service_unavailable");
            return result.get(8,TimeUnit.SECONDS);
        } finally { if(bound)context.unbindService(connection);thread.quitSafely(); }
    }
}
