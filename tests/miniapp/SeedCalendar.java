package os.aios.codex;
import android.app.*;import android.os.*;import java.io.*;import java.nio.file.*;import org.json.*;
/** One-shot acceptance fixture: copies only the requested calendar source into its own project. */
public final class SeedCalendar extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();try{
  String provider=new ProviderStore(getTargetContext()).active();File home=new File(getTargetContext().getFilesDir(),provider.equals("chatgpt")?"codex":"provider-"+provider),project=new File(home,"workspace/miniapps/ling-calendar");
  if(project.exists())throw new IOException("Calendar project already exists; refusing overwrite");if(!project.mkdirs())throw new IOException("Cannot create calendar project");
  for(String name:new String[]{"manifest.json","index.html","style.css","app.js"})try(InputStream in=getContext().getAssets().open("calendar/"+name)){Files.write(new File(project,name).toPath(),in.readAllBytes());}
  result.putString("stream",new JSONObject().put("state","succeeded").put("project",project.getAbsolutePath()).put("files",4).toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
