package os.aios.codex;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.util.*;
/** Installs the official compatible MCP package on the phone, without service credentials. */
public final class FeishuPackageAcceptance extends Instrumentation{
 public void onCreate(Bundle p){super.onCreate(p);start();}
 public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();c.startForegroundService(new Intent(c,RuntimeService.class));long end=SystemClock.elapsedRealtime()+20000;while(RuntimeService.instance==null&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);RuntimeService s=RuntimeService.instance;if(s==null)throw new AssertionError("Runtime unavailable");s.start("live");
  CliPackages cli=s.cliPackages;cli.call("install",new JSONObject().put("id","feishu-mcp").put("package","@larksuiteoapi/lark-mcp@0.5.1").put("owner_package","com.ss.android.lark"));
  end=SystemClock.elapsedRealtime()+300000;JSONObject job;do{job=cli.call("status",new JSONObject().put("id","feishu-mcp"));if(!Set.of("queued","running").contains(job.optString("state")))break;Thread.sleep(500);}while(SystemClock.elapsedRealtime()<end);
  if(!job.optString("state").equals("succeeded"))throw new AssertionError(job.toString());
  JSONArray entries=cli.call("list",new JSONObject()).getJSONArray("items");JSONObject pkg=null;for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).optString("id").equals("feishu-mcp"))pkg=entries.getJSONObject(i);if(pkg==null)throw new AssertionError("Registry missing package");
  String command=pkg.getJSONObject("commands").keys().next();File log=new File(c.getCacheDir(),"feishu-help.txt");ProcessBuilder builder=new ProcessBuilder(new File(c.getFilesDir(),"cli/bin/"+command).getAbsolutePath(),"--help").redirectErrorStream(true).redirectOutput(log);CliPackages.environment(c,builder.environment());java.lang.Process child=builder.start();if(!child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Help timed out");String help=java.nio.file.Files.readString(log.toPath());if(child.exitValue()!=0||!help.toLowerCase(Locale.ROOT).contains("mcp"))throw new AssertionError("exit="+child.exitValue()+" command="+command+" output="+help);
  result.putString("help",help);result.putString("result","PASS official Feishu MCP installed and executed as Runtime UID; credentials not configured");
 }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}finally{finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}}
}
