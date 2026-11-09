package os.aios.speech;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
/** Signature-protected queue endpoint used only by the existing AIOS bridge. */
public final class MinutesProvider extends ContentProvider {
 MinutesStore store;
 public boolean onCreate(){store=new MinutesStore(getContext());return true;}
 public Bundle call(String method,String arg,Bundle extras){
 if("import_recording".equals(method)){
  try{
   android.content.pm.ApplicationInfo app=getContext().getPackageManager().getApplicationInfo("org.lineageos.recorder",0);
   if(android.os.Binder.getCallingUid()!=app.uid||(app.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0)throw new SecurityException("System Recorder required");
   Uri uri=Uri.parse(extras.getString("uri",""));
   if(!"content".equals(uri.getScheme())||!"media".equals(uri.getAuthority()))throw new IllegalArgumentException("Native MediaStore recording required");
   long identity=android.os.Binder.clearCallingIdentity();try{
    getContext().startForegroundService(new Intent(getContext(),RecordingImportService.class).setData(uri));
    Bundle accepted=new Bundle();accepted.putBoolean("accepted",true);return accepted;
   }finally{android.os.Binder.restoreCallingIdentity(identity);}
  }catch(Exception e){throw new IllegalArgumentException("Native recording import failed",e);}
 }
if("call_recordings".equals(method)||"call_capture".equals(method)){
  try{int uid=android.os.Binder.getCallingUid();android.content.pm.ApplicationInfo info=getContext().getPackageManager().getApplicationInfo("com.android.dialer",0);
   if(uid!=info.uid||(info.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0)throw new SecurityException("System Dialer required");
   long identity=android.os.Binder.clearCallingIdentity();try{Bundle reply=new Bundle();if("call_capture".equals(method)){reply.putString("json",new JSONObject().put("active",MinutesService.instance!=null&&"call".equals(MinutesService.instance.kind)).toString());return reply;}reply.putString("json",CallRecordings.find(getContext(),store,extras.getLongArray("call_ids")).toString());return reply;}finally{android.os.Binder.restoreCallingIdentity(identity);}
  }catch(Exception e){throw new SecurityException("Call recordings unavailable",e);}
 }
 getContext().enforceCallingOrSelfPermission("os.aios.permission.VOICE_MINUTES","AIOS signature required");try{
  JSONObject result=switch(method){case "capture"->new JSONObject().put("active",MinutesService.instance!=null).put("id",MinutesService.currentId).put("partial",MinutesService.partial).put("recorder_token",MinutesService.recorderToken);case "next"->store.next();case "retry"->store.retry(arg);case "update"->store.update(new JSONObject(extras.getString("json")));case "list"->new JSONObject().put("sessions",store.list());case "detail"->store.detailPage(arg,new JSONObject(extras==null?"{}":extras.getString("json","{}")));default->throw new IllegalArgumentException("Unknown minutes action");};Bundle b=new Bundle();b.putString("json",result.toString());return b;
 }catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
