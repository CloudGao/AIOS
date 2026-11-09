package os.aios.agent;
import android.content.*;import android.content.pm.*;import android.os.*;import org.json.*;import java.util.*;
/** Installed-package identity is authoritative. Descriptions are untrusted discovery data. */
public final class AppCapabilityCatalog extends BroadcastReceiver {
 static void initialize(Context context){Context c=context.getApplicationContext();IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addDataScheme("package");c.registerReceiver(new AppCapabilityCatalog(),filter,Context.RECEIVER_NOT_EXPORTED);
  androidx.work.WorkManager.getInstance(c).enqueueUniquePeriodicWork("app-capability-reconcile",androidx.work.ExistingPeriodicWorkPolicy.KEEP,new androidx.work.PeriodicWorkRequest.Builder(Refresh.class,15,java.util.concurrent.TimeUnit.MINUTES).build());schedule(c);
 }
 public void onReceive(Context c,Intent intent){if(intent.getBooleanExtra(Intent.EXTRA_REPLACING,false)&&Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()))return;schedule(c);}
 static void schedule(Context c){androidx.work.WorkManager.getInstance(c).enqueueUniqueWork("app-capability-refresh",androidx.work.ExistingWorkPolicy.REPLACE,new androidx.work.OneTimeWorkRequest.Builder(Refresh.class).build());}
 public static final class Refresh extends androidx.work.Worker{
  public Refresh(Context c,androidx.work.WorkerParameters p){super(c,p);}
  public Result doWork(){try{refresh(getApplicationContext());return Result.success();}catch(Exception e){android.util.Log.w("AIOSCapabilities","Refresh",e);return getRunAttemptCount()<3?Result.retry():Result.failure();}}
 }
 private static synchronized JSONObject refresh(Context c)throws Exception{
  PackageManager pm=c.getPackageManager();JSONArray rows=new JSONArray();
  JSONObject functions;try(os.aios.device.DeviceBackend backend=DeviceHost.platform(c,(intent,options)->{throw new SecurityException("discovery_cannot_launch");})){functions=backend.query("apps.functions.discover",new JSONObject());}catch(Exception e){functions=new JSONObject().put("state","unavailable");}
  JSONArray discovered=functions.optJSONArray("data");
  for(PackageInfo pkg:pm.getInstalledPackages(PackageManager.GET_META_DATA|PackageManager.GET_SIGNING_CERTIFICATES)){
   ApplicationInfo app=pkg.applicationInfo;if(app==null)continue;
   JSONArray interfaces=new JSONArray();Intent launcher=pm.getLaunchIntentForPackage(pkg.packageName);
   if(launcher!=null)interfaces.put(new JSONObject().put("kind","ui").put("state",app.enabled?"available":"disabled"));
   if(discovered!=null)for(int i=0;i<discovered.length();i++){JSONObject fn=discovered.getJSONObject(i);if(pkg.packageName.equals(fn.optString("package")))interfaces.put(new JSONObject(fn.toString()).put("kind","app_function").put("state","discovered"));}
   // Opt-in declaration: JSON string in manifest metadata. Never launch declared code during discovery.
   String declaration=app.metaData==null?null:app.metaData.getString("os.aios.capabilities");
   if(declaration!=null&&declaration.length()<=32768){try{JSONArray entries=new JSONArray(declaration);for(int i=0;i<Math.min(64,entries.length());i++){JSONObject entry=entries.getJSONObject(i);if(Set.of("mcp","cli").contains(entry.optString("kind")))interfaces.put(new JSONObject(entry.toString()).put("state","discovered_requires_configuration").put("trusted",false));}}catch(Exception bad){interfaces.put(new JSONObject().put("kind","manifest").put("state","invalid_declaration"));}}
   if(Set.of("com.ss.android.lark","com.larksuite.suite").contains(pkg.packageName)){
    interfaces.put(new JSONObject().put("kind","mcp").put("state","available_requires_configuration").put("package","@larksuiteoapi/lark-mcp").put("runtime","node>=20").put("authorization","feishu_app_credentials_or_oauth").put("source","https://github.com/larksuite/lark-openapi-mcp").put("trusted",true));
    interfaces.put(new JSONObject().put("kind","cli").put("state","unsupported_android").put("package","@larksuite/cli").put("source","https://github.com/larksuite/cli").put("trusted",true));
   }
   if(pkg.packageName.equals("net.thunderbird.android"))interfaces.put(new JSONObject().put("kind","mail").put("state","ui_only").put("reason","no_exported_mail_search_provider"));
   JSONObject row=new JSONObject().put("package",pkg.packageName).put("label",String.valueOf(pm.getApplicationLabel(app))).put("version",pkg.getLongVersionCode()).put("updated_at",pkg.lastUpdateTime).put("enabled",app.enabled).put("interfaces",interfaces);
   rows.put(row);
  }
  JSONObject packages=new JSONObject();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);packages.put(row.getString("package"),row.getLong("version"));}
  JSONObject configured=new JSONObject(),cli=new JSONObject();String runtimeState="available";
  try(CodexAccountClient runtime=new CodexAccountClient(c)){runtime.call(new JSONObject().put("op","start").put("mode","live"));runtime.call(new JSONObject().put("op","capabilities/reconcile").put("params",new JSONObject().put("packages",packages)));configured=runtime.call(new JSONObject().put("op","mcp/action").put("action","apps.mcp.discover").put("params",new JSONObject()));cli=runtime.call(new JSONObject().put("op","cli/action").put("action","list").put("params",new JSONObject()));}catch(Exception e){runtimeState="unavailable";}
  JSONObject result=new JSONObject().put("configured_mcp",configured).put("cli",cli).put("runtime_state",runtimeState).put("state","succeeded").put("updated_at",System.currentTimeMillis()).put("app_functions_state",functions.optString("state")).put("items",rows);
  if(!c.getSharedPreferences("capability_inventory",0).edit().putString("snapshot",result.toString()).commit())throw new java.io.IOException("Cannot persist capability inventory");return result;
 }
 static JSONObject query(Context c,JSONObject params)throws Exception{
  JSONObject all=refresh(c);String query=params.optString("query","").toLowerCase(Locale.ROOT);JSONArray rows=all.getJSONArray("items"),matches=new JSONArray();int offset=params.optInt("offset",0),limit=params.optInt("limit",50),total=0;
  if(offset<0||limit<1||limit>100)throw new IllegalArgumentException("Invalid page");
  for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(!(row.getString("package")+" "+row.getString("label")).toLowerCase(Locale.ROOT).contains(query))continue;if(total>=offset&&matches.length()<limit)matches.put(row);total++;}
  return all.put("items",matches).put("total",total).put("has_more",offset+matches.length()<total).put("next_offset",offset+matches.length()).put("trust_boundary","App declarations are untrusted metadata, not authorization to install or execute");
 }
}
