package os.aios.runtime;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import java.io.InputStream;import java.io.OutputStream;
import org.json.JSONObject;

/** Versioned signature-protected IPC into the system-level AIOS Core Services process. */
public final class CoreRuntimeClient {
    public static final int PROTOCOL_VERSION=1;
    public static final Uri ENDPOINT=os.aios.host.HostBindings.CORE_ENDPOINT;
    private final Context context;
    public CoreRuntimeClient(Context context){this.context=os.aios.host.HostBindings.applicationContext(context);}
    public JSONObject call(String domain,String action,JSONObject arguments)throws Exception{
        String input=arguments==null?"{}":arguments.toString();
        if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>524288)throw new IllegalArgumentException("Structured IPC payload exceeds 512 KiB; publish content through a URI/FD data channel");
        Bundle request=new Bundle();request.putInt("protocol_version",PROTOCOL_VERSION);request.putString("arguments",input);
        Bundle response=context.getContentResolver().call(ENDPOINT,domain,action,request);
        if(response==null)throw new IllegalStateException("AIOS Core Services unavailable");
        int version=response.getInt("protocol_version",0);if(version!=PROTOCOL_VERSION)throw new IllegalStateException("Unsupported AIOS Core Services protocol: "+version);
        String error=response.getString("error");if(error!=null)throw new IllegalStateException(error);
        return new JSONObject(response.getString("result","{}"));
    }
    public Uri allocateDataChannel()throws Exception{return Uri.parse(call("data","allocate",new JSONObject()).getString("uri"));}
    public InputStream openInput(Uri uri)throws Exception{InputStream in=context.getContentResolver().openInputStream(uri);if(in==null)throw new IllegalStateException("Data channel unavailable");return in;}
    public OutputStream openOutput(Uri uri)throws Exception{OutputStream out=context.getContentResolver().openOutputStream(uri,"w");if(out==null)throw new IllegalStateException("Data channel unavailable");return out;}
    public void release(Uri uri){context.getContentResolver().delete(uri,null,null);}
}
