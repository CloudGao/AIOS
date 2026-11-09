package os.aios.miniapp;
import android.content.*;
import android.net.Uri;
import android.provider.CalendarContract;
import androidx.webkit.WebViewFeature;
import org.json.*;
import java.util.*;

final class Capabilities {
 static Set<String> names(){return Set.of("notifications.post","notifications.cancel","calendar.compose","sms.compose","files.pickText","files.saveText");}
 static JSONObject result(String state,Object data)throws Exception{return new JSONObject().put("state",state).put("data",data);}
 static JSONObject error(String state,String detail){try{return new JSONObject().put("state",state).put("error",detail);}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean supported(){return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)&&WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA);}
 static JSONObject catalog(Context c)throws Exception {
  JSONArray entries=new JSONArray();
  for(String name:new TreeSet<>(names())){
   String state="available",reason="";Intent intent=null;
   if(name.equals("calendar.compose"))intent=new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI);
   if(name.equals("sms.compose"))intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"));
   if(intent!=null&&intent.resolveActivity(c.getPackageManager())==null){state="not_supported";reason="No installed editor handles this action";}
   JSONObject params=new JSONObject();
   switch(name){
    case "notifications.post":params.put("id","string 1..80").put("title","string 1..100").put("body","string 0..2000");break;
    case "notifications.cancel":params.put("id","string 1..80");break;
    case "calendar.compose":params.put("title","string 1..200").put("begin_ms","integer >=0").put("end_ms","integer >begin_ms");break;
    case "sms.compose":params.put("number","string 1..40 phone characters").put("text","string 0..2000");break;
    case "files.pickText":params.put("none","Opens picker; UTF-8 text <=64KiB");break;
    case "files.saveText":params.put("name","string 1..80 filename").put("text","UTF-8 <=64KiB");break;
   }
   entries.put(new JSONObject().put("name",name).put("state",state).put("reason",reason).put("parameters",params).put("authorization","manifest + native user consent; Android permission where applicable").put("completion",name.endsWith("compose")?"handed_to_system; no proof of save/send":"read state; no blind replay of outcome_unknown"));
  }
  for(String name:List.of("alarm.create","reminders.schedule","calendar.query","calendar.sync","sms.send","ai.generate","context.search","background.javascript"))entries.put(new JSONObject().put("name",name).put("state","not_supported").put("reason","MiniApp adapter not implemented in this release"));
  return new JSONObject().put("api_version",1).put("host_supported",supported()).put("capabilities",entries).put("bundle_limits",new JSONObject().put("file_bytes",49152).put("total_bytes",2097152).put("files",128)).put("storage","separate WebView profile and HTTPS origin; IndexedDB/localStorage").put("toolchain","HTML/CSS/JS static bundles; no bundled Node/npm guarantee");
 }
 static String str(JSONObject p,String key,int max,boolean empty)throws Exception {String s=p.getString(key);if(s.length()>max||!empty&&s.trim().isEmpty())throw new IllegalArgumentException("Invalid "+key);return s;}
}
