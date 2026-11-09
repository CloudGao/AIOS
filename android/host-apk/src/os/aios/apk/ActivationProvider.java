package os.aios.apk;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
/** The sole exported activation endpoint. It does not expose arbitrary device or app operations. */
public final class ActivationProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(Binder.getCallingUid()!=2000)throw new SecurityException("adb_shell_required");
        if(!"register".equals(method)||extras==null)throw new IllegalArgumentException("invalid_activation");
        try{os.aios.agent.AdbBridge.install(getContext(),extras.getBinder("service"),extras.getString("instance"),extras.getBoolean("input_monitor"));}
        catch(RemoteException e){throw new IllegalStateException("shell_disconnected",e);}
        Bundle result=new Bundle();result.putBinder("events",os.aios.agent.AdbBridge.events(getContext()));
        result.putInt("uid",android.os.Process.myUid());return result;
    }
    public Cursor query(Uri u,String[]p,String s,String[]a,String o){throw new UnsupportedOperationException();}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
}
