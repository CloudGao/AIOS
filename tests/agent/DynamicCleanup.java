package os.aios.agent;
import android.app.*;import android.os.*;import android.content.*;import org.json.*;
/** Remove only files created by the dedicated acceptance share fixture. */
public final class DynamicCleanup extends Instrumentation {
 public void onCreate(Bundle p){super.onCreate(p);start();}
 public void onStart(){Bundle out=new Bundle();try{Context c=getTargetContext();int jobs=0,files=0;android.content.SharedPreferences prefs=c.getSharedPreferences("share_jobs",0);
 for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet()){JSONObject job=new JSONObject(String.valueOf(entry.getValue()));if(!job.optString("body").startsWith("AIOS acceptance shared note:"))continue;JSONArray list=job.optJSONArray("copied");if(list==null)list=new JSONArray();if(job.has("original"))list.put(job.getJSONObject("original"));if(job.has("document"))list.put(job.getJSONObject("document"));for(int i=0;i<list.length();i++){JSONObject file=list.getJSONObject(i);String path=file.optString("path");if(!path.startsWith("/storage/emulated/0/Download/AIOS/")&&!path.startsWith("/storage/emulated/0/Documents/AIOS Notes/"))throw new SecurityException("Unexpected fixture path "+path);AssistantAttachments.delete(c,android.net.Uri.parse(file.getString("uri")));files++;}prefs.edit().remove(entry.getKey()).commit();jobs++;}
 out.putString("result","PASS removed only acceptance fixture jobs="+jobs+" files="+files);finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
}
