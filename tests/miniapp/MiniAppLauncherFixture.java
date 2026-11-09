package os.aios.miniapp;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;

/** Temporary fixtures only; production apps are read-only in this harness. */
public final class MiniAppLauncherFixture extends Instrumentation {
 Bundle args;
 public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 public void onStart(){Bundle out=new Bundle();try{
  Store s=Store.get(getTargetContext());String action=args.getString("action","list");
  if(action.equals("seed")||action.equals("update")){
   for(String id:new String[]{"launcher-test-a","launcher-test-b"}){
    int old=0;try{old=s.app(id).getJSONObject("manifest").getInt("version");}catch(IllegalArgumentException absent){}
    if(action.equals("seed")&&old!=0)throw new IllegalStateException("Fixture already exists; explicit cleanup required");
    JSONObject m=new JSONObject().put("id",id).put("name",id.endsWith("a")?(action.equals("update")?"Mini Launcher Updated":"Mini Launcher A"):"Mini Launcher B").put("version",old+1).put("entry","index.html").put("capabilities",new JSONArray().put("notifications.cancel"));
    String html="<!doctype html><meta name='viewport' content='width=device-width'><style>body{font:20px sans-serif;padding:24px}</style><h1>"+m.getString("name")+"</h1><button onclick=\"localStorage.setItem('fixture','saved');document.querySelector('p').textContent='Saved'\">Save fixture</button><p id='data'></p><script>document.querySelector('p').textContent=localStorage.getItem('fixture')||'Empty';</script>";
    s.install(new JSONObject().put("id",id).put("expected_version",old).put("request_id","launcher"+System.nanoTime()),new JSONObject().put("manifest",m).put("files",new JSONObject().put("index.html",android.util.Base64.encodeToString(html.getBytes(java.nio.charset.StandardCharsets.UTF_8),2))));
   }
  }else if(action.equals("cleanup")){
   for(String id:new String[]{"launcher-test-a","launcher-test-b"})try{s.app(id);CompletableFuture<?>[] done={null};runOnMainSync(()->done[0]=LibraryActivity.uninstall(getTargetContext(),id));done[0].get(30,TimeUnit.SECONDS);}catch(IllegalArgumentException absent){}
  }else if(action.equals("locale-en")){
   getTargetContext().getSystemService(android.app.LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags("en"));
  }else if(action.equals("locale-reset")){
   getTargetContext().getSystemService(android.app.LocaleManager.class).setApplicationLocales(LocaleList.getEmptyLocaleList());
  }else if(action.equals("identity")){
   JSONObject a=s.app("launcher-test-b");String generation=a.getString("generation");
   Intent restored=new Intent(getTargetContext(),HostActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("aios-miniapp://app/launcher-test-b/"+generation)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
   String[] identity=HostActivity.launchIdentity(restored);if(!identity[0].equals("launcher-test-b")||!identity[1].equals(generation))throw new AssertionError("Restored URI identity lost");
   HostActivity host=(HostActivity)startActivitySync(restored);if(host.web==null)throw new AssertionError("URI-only restore did not open content");runOnMainSync(host::finish);
   boolean rejected=false;try{HostActivity.launchIdentity(new Intent(restored).putExtra("app_id","launcher-test-a"));}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("Contradictory identity accepted");
  }else if(action.equals("stale")){
   HostActivity a=(HostActivity)startActivitySync(new Intent(getTargetContext(),HostActivity.class).putExtra("app_id","launcher-test-a").putExtra("generation","stale").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   if(a.web!=null)throw new AssertionError("Stale identity opened a WebView");runOnMainSync(a::finish);
  }
  JSONArray entries=new JSONArray(),list=s.list();for(int i=0;i<list.length();i++)entries.put(LauncherProvider.entry(list.getJSONObject(i)));
  out.putString("stream",new JSONObject().put("action",action).put("entries",entries).toString());finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
