package os.aios.codex;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.io.*;import java.nio.file.*;import java.util.*;
public final class RuntimeExtensionTests extends Instrumentation {
 int checks;void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
 public void onCreate(Bundle p){super.onCreate(p);start();}
 JSONObject await(CliPackages cli,String id)throws Exception{long end=SystemClock.elapsedRealtime()+180000;while(SystemClock.elapsedRealtime()<end){JSONObject job=cli.call("status",new JSONObject().put("id",id));if(!Set.of("queued","running").contains(job.optString("state")))return job;Thread.sleep(500);}throw new AssertionError("CLI installation timed out");}
 String command(Context c,String... args)throws Exception{ProcessBuilder builder=new ProcessBuilder(args).redirectErrorStream(true);CliPackages.environment(c,builder.environment());java.lang.Process child=builder.start();check(child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS),"command completed");String output=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);check(child.exitValue()==0,"command exit: "+output);return output;}
 public void onStart(){Bundle result=new Bundle();PhoneMcp mcp=null;CliPackages cli=null;String suffix=UUID.randomUUID().toString().substring(0,8),id="acceptance_"+suffix,server="aios_acceptance_"+suffix;try{
  Context context=getTargetContext();context.startForegroundService(new Intent(context,RuntimeService.class));long until=SystemClock.elapsedRealtime()+20000;while(RuntimeService.instance==null&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);RuntimeService service=RuntimeService.instance;check(service!=null,"runtime available");service.start("live");mcp=service.phoneMcp;cli=service.cliPackages;
  check(command(context,CliPackages.node(context),"--version").trim().equals("v22.23.4"),"Android node version");
  check(!command(context,new File(context.getFilesDir(),"cli/bin/npm").getAbsolutePath(),"--version").isBlank(),"npm launcher runnable");
  check(command(context,CliPackages.node(context),"-p","process.platform+':'+process.arch").trim().equals("android:arm64"),"actual Android ARM64 runtime");
  check(command(context,"/system/bin/sh","-c","command -v node && node --version").contains("cli/bin/node"),"shell discovers phone node from PATH");
  JSONArray failures=new JSONArray();
  try{
  File script=new File(context.getCacheDir(),"acceptance-mcp.cjs");Files.writeString(script.toPath(),"const rl=require('node:readline').createInterface({input:process.stdin});rl.on('line',s=>{const q=JSON.parse(s);if(q.id===undefined)return;let r={};if(q.method==='initialize')r={protocolVersion:'2024-11-05',capabilities:{tools:{}},serverInfo:{name:'echo',version:'1'}};else if(q.method==='tools/list')r={tools:[{name:'echo',description:'Read-only echo test',inputSchema:{type:'object',properties:{text:{type:'string'}}}}]};else if(q.method==='tools/call')r={content:[{type:'text',text:process.argv[2]+q.params.arguments.text}]};console.log(JSON.stringify({jsonrpc:'2.0',id:q.id,result:r}));});");
  cli.call("register",new JSONObject().put("id",id+"_mcp").put("command","acceptance-mcp-"+suffix).put("path",script.getAbsolutePath()).put("runtime","node"));
  for(String prefix:new String[]{"first:","updated:"}){
   boolean alias=prefix.equals("updated:");JSONObject config=new JSONObject().put("command",alias?new File(context.getFilesDir(),"cli/bin/acceptance-mcp-"+suffix).getAbsolutePath():CliPackages.node(context)).put("args",alias?new JSONArray().put(prefix):new JSONArray().put(script.getAbsolutePath()).put(prefix)).put("enabled_tools",new JSONArray().put("echo"));
   JSONObject saved=mcp.call("apps.mcp.configure",new JSONObject().put("provider",server).put("config",config));check(saved.getString("state").equals("succeeded"),"config saved and reloaded");
   JSONObject tools=null;for(int i=0;i<30;i++){try{tools=mcp.call("apps.mcp.tools",new JSONObject().put("provider",server));if(tools.optJSONArray("tools")!=null&&tools.getJSONArray("tools").length()>0)break;}catch(Exception ignored){}Thread.sleep(200);}
   check(tools!=null&&tools.getJSONArray("tools").length()>0,"MCP discovery");JSONObject response=mcp.call("apps.mcp.call",new JSONObject().put("provider",server).put("tool","echo").put("arguments",new JSONObject().put("text","phone")));check(response.toString().contains(prefix+"phone"),"MCP live call after refresh");
  }
  mcp.call("apps.mcp.remove",new JSONObject().put("provider",server));check(!mcp.call("apps.mcp.discover",new JSONObject()).toString().contains(server),"MCP removed");script.delete();
  }catch(Throwable failure){failures.put("MCP: "+android.util.Log.getStackTraceString(failure));}finally{try{mcp.call("apps.mcp.remove",new JSONObject().put("provider",server));cli.call("remove",new JSONObject().put("id",id+"_mcp"));}catch(Exception ignored){}}
  try{
  for(String version:new String[]{"2.2.3","2.2.2"}){
   cli.call("install",new JSONObject().put("id",id).put("package","json5@"+version));JSONObject job=await(cli,id);check(job.getString("state").equals("succeeded"),"npm install "+job);
   String output=command(context,new File(context.getFilesDir(),"cli/bin/json5").getAbsolutePath(),"--version");check(output.contains(version),"CLI version after install/update");
  }
  cli.call("remove",new JSONObject().put("id",id));check(!new File(context.getFilesDir(),"cli/bin/json5").exists(),"removed command unreachable");
  check(!cli.call("list",new JSONObject()).toString().contains(id),"registry removal");File custom=new File(context.getCacheDir(),"acceptance-cli-"+suffix+".cjs");Files.writeString(custom.toPath(),"console.log('registered-ok')");
  cli.call("register",new JSONObject().put("id",id).put("command","acceptance-"+suffix).put("path",custom.getAbsolutePath()).put("runtime","node"));check(command(context,new File(context.getFilesDir(),"cli/bin/acceptance-"+suffix).getAbsolutePath()).contains("registered-ok"),"custom registration executes");cli.call("remove",new JSONObject().put("id",id));check(custom.isFile(),"unregister preserves user source");custom.delete();
  }catch(Throwable failure){failures.put("CLI: "+android.util.Log.getStackTraceString(failure));}
  if(failures.length()>0)throw new AssertionError(failures.toString());
  result.putString("result","PASS "+checks+" checks");
 }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}finally{try{if(mcp!=null)mcp.call("apps.mcp.remove",new JSONObject().put("provider",server));if(cli!=null){cli.call("cancel",new JSONObject().put("id",id));cli.call("remove",new JSONObject().put("id",id));}}catch(Exception ignored){}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}}
}
