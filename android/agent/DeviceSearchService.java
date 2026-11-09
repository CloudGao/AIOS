package os.aios.agent;

import android.app.Service;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Core-only read service. Does not initialize Codex or call Core. */
public final class DeviceSearchService extends Service {
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message message) {
            final Messenger reply=message.replyTo;
            try {
                if(message.sendingUid!=os.aios.host.HostBindings.coreUid(DeviceSearchService.this) ||
                   getPackageManager().checkSignatures(message.sendingUid,android.os.Process.myUid())!=android.content.pm.PackageManager.SIGNATURE_MATCH)
                    throw new SecurityException("Core UID and matching signature required");
                if(reply==null) return;
                String op=message.getData().getString("operation","");
                String payload=message.getData().getString("json","{}");
                if(payload.length()>16384)throw new IllegalArgumentException("Request too large");
                workers.execute(()->{
                    CancellationSignal cancellation=new CancellationSignal();
                    Runnable cancel=cancellation::cancel;handler.postDelayed(cancel,6000);
                    try {
                        JSONObject args=new JSONObject(payload);DeviceSearchAdapter adapter=new DeviceSearchAdapter(DeviceSearchService.this);
                        if(op.equals("open")){
                            if(!java.util.Set.of("photos","files").contains(args.optString("source")))throw new SecurityException("Unsupported FD source");
                            JSONObject data=adapter.call("get",args,cancellation);org.json.JSONArray rows=data.getJSONArray("items");
                            if(rows.length()!=1)throw new SecurityException("Photo unavailable");JSONObject item=rows.getJSONObject(0);
                            if(!item.getString("source_version").equals(args.getString("source_version")))throw new IllegalStateException("source_changed");
                            android.net.Uri uri=android.net.Uri.parse(item.getString("uri"));
                            if(args.optBoolean("original")&&args.optString("source").equals("photos")){
                                if(checkSelfPermission("android.permission.ACCESS_MEDIA_LOCATION")!=0)throw new SecurityException("Original location permission required");
                                uri=android.provider.MediaStore.setRequireOriginal(uri);
                            }
                            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r",cancellation)){
                                if(fd==null)throw new java.io.IOException("Photo unavailable");Bundle b=new Bundle();b.putParcelable("fd",fd);Message m=Message.obtain();m.setData(b);reply.send(m);
                            }
                        }else send(reply,adapter.call(op,args,cancellation),null);
                    }
                    catch(Exception e) { send(reply,null,e.getClass().getSimpleName()+": "+e.getMessage()); }
                    finally { handler.removeCallbacks(cancel); }
                });
            } catch(Exception e) { if(reply!=null)send(reply,null,e.getClass().getSimpleName()+": "+e.getMessage()); }
        }
    });
    private static void send(Messenger reply,JSONObject result,String error) {
        try { Bundle data=new Bundle();String json=result==null?"{}":result.toString();
            if(json.length()>120000)error="Result budget exceeded";
            if(error!=null)data.putString("error",error);else data.putString("json",json);
            Message message=Message.obtain();message.setData(data);reply.send(message);
        }catch(RemoteException ignored){}
    }
    public IBinder onBind(Intent intent){return endpoint.getBinder();}
    public void onDestroy(){workers.shutdownNow();super.onDestroy();}
}
