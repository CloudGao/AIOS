package os.aios.miniapp;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;

public final class ManagerProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public Bundle call(String method,String arg,Bundle extras){
  try{
   int caller=Binder.getCallingUid();int agent=getContext().getPackageManager().getPackageUid("os.aios.agent",0);
   if(caller!=agent&&!(caller==android.os.Process.myUid()&&"desktop".equals(method)))throw new SecurityException("Only the AIOS development gateway can manage apps");
   JSONObject p=new JSONObject(extras==null?"{}":extras.getString("json","{}"));if(p.toString().length()>100000)throw new IllegalArgumentException("Request too large");
   long token=Binder.clearCallingIdentity();try{
    if(method.equals("install")){
     ParcelFileDescriptor fd=extras.getParcelable("bundle_fd",ParcelFileDescriptor.class);if(fd==null)throw new IllegalArgumentException("Project snapshot required");
     byte[] bytes;try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
      byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>3*1024*1024)throw new IllegalArgumentException("Snapshot too large");out.write(buffer,0,n);}bytes=out.toByteArray();
     }
     JSONObject receipt=Store.get(getContext()).install(p,new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
     onMain(()->{HostActivity.closeApp(p.getString("id"));DesktopIntegration.published(getContext(),receipt);return true;});return reply(Capabilities.result("succeeded",receipt));
    }
    return reply(execute(method,p));
   }finally{Binder.restoreCallingIdentity(token);}
  }catch(SecurityException e){throw e;}catch(Exception e){return reply(Capabilities.error(e instanceof java.util.concurrent.TimeoutException?"outcome_unknown":"failed",e.getMessage()));}
 }
 JSONObject execute(String method,JSONObject p)throws Exception {
  Store s=Store.get(getContext());Object data;
  switch(method){
   case "desktop":{
    JSONObject app=s.app(p.getString("id"));if(!app.getString("generation").equals(p.getString("generation")))throw new IllegalArgumentException("Stale mini app");
    data=onMain(()->DesktopIntegration.request(getContext(),p.getString("id"),false));break;
   }
   case "catalog":data=onMain(()->Capabilities.catalog(getContext()));break;
   case "list":data=s.list();break;
   case "get":data=s.app(p.getString("id"));break;
   case "begin":data=s.begin(p);break;
   case "write":data=s.write(p);break;
   case "publish":{JSONObject receipt=s.publish(p.getString("stage_id"));onMain(()->{HostActivity.closeApp(receipt.getString("id"));DesktopIntegration.published(getContext(),receipt);return true;});data=receipt;break;}
   case "discard":s.discard(p.getString("stage_id"));data=true;break;
   case "rollback":data=s.rollback(p.getString("id"),p.getInt("expected_version"),p.getInt("version"));onMain(()->{HostActivity.closeApp(p.getString("id"));return true;});break;
   case "uninstall":{CompletableFuture<?> done=(CompletableFuture<?>)onMain(()->LibraryActivity.uninstall(getContext(),p.getString("id")));done.get(25,TimeUnit.SECONDS);data=true;break;}
   case "open":s.app(p.getString("id"));getContext().startActivity(new Intent(getContext(),HostActivity.class).putExtra("app_id",p.getString("id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return Capabilities.result("handed_to_system",new JSONObject().put("id",p.getString("id")));
   default:throw new IllegalArgumentException("Unknown MiniApp management operation");
  }
  return Capabilities.result("succeeded",data);
 }
 interface MainCall {Object call()throws Exception;}
 static Object onMain(MainCall c)throws Exception {if(Looper.myLooper()==Looper.getMainLooper())return c.call();FutureTask<Object> f=new FutureTask<>(()->c.call());new Handler(Looper.getMainLooper()).post(f);return f.get(30,TimeUnit.SECONDS);}
 static Bundle reply(JSONObject j){Bundle b=new Bundle();b.putString("json",j.toString());return b;}
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
