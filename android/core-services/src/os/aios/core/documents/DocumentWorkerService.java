package os.aios.core.documents;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import os.aios.runtime.DocumentRpc;

/** A fresh isolated UID per operation; no storage, network or Core database privileges. */
public final class DocumentWorkerService extends Service {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()){
        public void handleMessage(Message m){if(m.replyTo==null)return;Messenger reply=m.replyTo;Bundle b=m.getData();if(m.sendingUid!=getApplicationInfo().uid){DocumentRpc.reply(reply,null,null,"core_identity_required");return;}worker.execute(()->{
            try(ParcelFileDescriptor in=b.getParcelable("input",ParcelFileDescriptor.class);ParcelFileDescriptor out=b.getParcelable("output",ParcelFileDescriptor.class)){
                String payload=b.getString("json","{}");if(payload.length()>65536)throw new IllegalArgumentException("request_budget_exceeded");
                JSONObject result=DocumentEngine.execute(DocumentWorkerService.this,b.getString("operation"),new JSONObject(payload),in,out);DocumentRpc.reply(reply,result,null,null);
            }catch(Throwable e){DocumentRpc.reply(reply,null,null,e instanceof OutOfMemoryError?"memory_limit":e instanceof com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException?"password_required":e.getMessage()==null?"invalid_document":e.getMessage());}
        });}
    });
    public IBinder onBind(Intent i){return endpoint.getBinder();}
    public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
