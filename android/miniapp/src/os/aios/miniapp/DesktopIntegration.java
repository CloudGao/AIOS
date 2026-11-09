package os.aios.miniapp;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;
import org.json.*;

/** Common mini-app lifecycle; the application manifest selects its desktop host. */
public final class DesktopIntegration {
 public interface Host { String request(Context context,JSONObject app)throws Exception; }
 public static Uri uri(JSONObject app)throws JSONException{return new Uri.Builder().scheme("aios-miniapp").authority("app").appendPath(app.getJSONObject("manifest").getString("id")).appendPath(app.getString("generation")).build();}
 public static String request(Context c,String id,boolean automatic){
  try{
   JSONObject app=Store.get(c).app(id);if(!"installed".equals(app.getString("state")))return "unavailable";
   String generation=app.getString("generation");
   android.content.SharedPreferences prefs=c.getSharedPreferences("mini_desktop_requests",0);
   if(automatic&&prefs.getBoolean(generation,false))return "already_requested";
   android.os.Bundle meta=c.getPackageManager().getApplicationInfo(c.getPackageName(),PackageManager.GET_META_DATA).metaData;
   String impl=meta==null?null:meta.getString("os.aios.miniapp.DESKTOP_HOST");
   Host host=impl==null?new RomHost():(Host)Class.forName(impl).getDeclaredConstructor().newInstance();
   String result=host.request(c,app);
   if(automatic&&("requested".equals(result)||"pinned".equals(result)))prefs.edit().putBoolean(generation,true).commit();
   return result;
  }catch(Exception e){return "failed";}
 }
 public static void manual(Context c,String id){String result=request(c,id,false);int message=switch(result){case "requested"->R.string.mini_desktop_requested;case "pinned"->R.string.mini_desktop_pinned;case "unsupported"->R.string.mini_desktop_unsupported;default->R.string.mini_desktop_failed;};Toast.makeText(c,message,Toast.LENGTH_LONG).show();}
 public static void published(Context c,JSONObject receipt){
  if(!receipt.optBoolean("first_install",false))return;
  String result=request(c,receipt.optString("id"),true);
  try{receipt.put("desktop_request",result);}catch(JSONException ignored){}
 }
 public static final class RomHost implements Host {
  public String request(Context c,JSONObject app)throws Exception{Store.get(c).requestDesktop(app.getJSONObject("manifest").getString("id"),app.getString("generation"));return "requested";}
 }
}
