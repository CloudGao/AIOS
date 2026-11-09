package os.aios.agent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
final class MiniAppManagerClient {
 static JSONObject execute(Context c,String action,JSONObject p)throws Exception {
  if(!java.util.Set.of("install","catalog","list","get","begin","write","publish","discard","open","rollback","uninstall").contains(action))throw new IllegalArgumentException("Unknown MiniApp operation");
  Bundle b=new Bundle();b.putString("json",p.toString());
  if(action.equals("install")){
   String id=p.getString("id");if(!id.matches("[a-z][a-z0-9-]{2,47}"))throw new IllegalArgumentException("Invalid app id");
   try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(Uri.parse("content://os.aios.codex.miniapps").buildUpon().appendPath(id).build(),"r")){
    if(fd==null)throw new IllegalStateException("Project snapshot unavailable");b.putParcelable("bundle_fd",fd);
    Bundle result=c.getContentResolver().call(Uri.parse("content://os.aios.miniapp.manage"),action,null,b);
    if(result==null)throw new IllegalStateException("MiniApp host unavailable");return new JSONObject(result.getString("json"));
   }
  }
  Bundle result=c.getContentResolver().call(Uri.parse("content://os.aios.miniapp.manage"),action,null,b);
  if(result==null)throw new IllegalStateException("MiniApp host unavailable");return new JSONObject(result.getString("json"));
 }
}
