package os.aios.codex;
import org.json.*;
import java.util.*;

/** Host-side MCP adapter using the packaged native client and phone-owned credentials. */
final class PhoneMcp implements RuntimeExtension.Mcp {
 private final CodexProcess process;
 private String thread="";
 PhoneMcp(CodexProcess p){process=p;}
 private JSONObject configured()throws Exception {
  JSONObject c=process.call("config/read",new JSONObject().put("includeLayers",false),20).getJSONObject("config");
  JSONObject servers=c.optJSONObject("mcp_servers");return servers==null?new JSONObject():servers;
 }
 private JSONObject status(String provider)throws Exception{
  JSONObject result=process.call("mcpServerStatus/list",new JSONObject().put("serverName",provider).put("limit",100).put("detail","toolsAndAuthOnly"),30);
  JSONArray rows=result.optJSONArray("data");if(rows!=null)for(int i=0;i<rows.length();i++)if(provider.equals(rows.getJSONObject(i).optString("name")))return rows.getJSONObject(i);
  throw new IllegalStateException(process.context.getString(R.string.extension_error_provider_missing));
 }
 private synchronized String thread()throws Exception{
  if(thread.isEmpty())thread=process.call("thread/start",new JSONObject().put("cwd",process.workspace.getAbsolutePath()).put("sandbox","read-only").put("approvalPolicy","never")
    .put("environments",new JSONArray()).put("dynamicTools",new JSONArray()).put("baseInstructions","Host-owned MCP transport thread. No model turns are submitted to this thread."),30).getJSONObject("thread").getString("id");
  return thread;
 }
 synchronized private JSONObject configure(String operation,JSONObject args)throws Exception{
  String provider=args.getString("provider");if(!provider.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException(process.context.getString(R.string.extension_error_invalid_provider));
  JSONObject read=process.call("config/read",new JSONObject().put("includeLayers",true),20);
  Object updated=JSONObject.NULL;
  if(!operation.equals("apps.mcp.remove")){
   JSONObject value=new JSONObject(args.getJSONObject("config").toString());
   Set<String> fields=Set.of("command","args","env","env_vars","cwd","url","bearer_token_env_var","http_headers","env_http_headers","enabled","enabled_tools","disabled_tools","startup_timeout_sec","tool_timeout_sec");
   for(Iterator<String> it=value.keys();it.hasNext();)if(!fields.contains(it.next()))throw new IllegalArgumentException(process.context.getString(R.string.extension_error_config_field));
   boolean local=value.has("command"),remote=value.has("url");if(local==remote)throw new IllegalArgumentException(process.context.getString(R.string.extension_error_transport));
   if(remote){java.net.URI uri=new java.net.URI(value.getString("url"));if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException(process.context.getString(R.string.extension_error_https));}
   if(local){String command=value.getString("command");if(command.isBlank()||command.indexOf('\0')>=0)throw new IllegalArgumentException(process.context.getString(R.string.extension_error_invalid_command));}
   if(local){
    // The native MCP launcher deliberately filters inherited variables. Pass names,
    // not APK-specific paths, so updates resolve the current installed runtime.
    LinkedHashSet<String> names=new LinkedHashSet<>();JSONArray supplied=value.optJSONArray("env_vars");
    if(supplied!=null)for(int i=0;i<supplied.length();i++)names.add(supplied.getString(i));
    names.addAll(Arrays.asList("AIOS_NODE","AIOS_CLI_ROOT","npm_config_cache","npm_config_script_shell","TMPDIR","NODE_EXTRA_CA_CERTS","HTTP_PROXY","HTTPS_PROXY","ALL_PROXY","NO_PROXY","http_proxy","https_proxy","all_proxy","no_proxy"));
    value.put("env_vars",new JSONArray(names));
   }
   if(value.toString().length()>64000)throw new IllegalArgumentException(process.context.getString(R.string.extension_error_config_size));
   // Configuring a transport does not automatically authorize every exposed tool.
   if(!value.has("enabled_tools"))value.put("enabled_tools",new JSONArray());updated=value;
  }
  JSONObject write=new JSONObject().put("keyPath","mcp_servers."+provider).put("value",updated).put("mergeStrategy","replace");
  JSONArray layers=read.optJSONArray("layers");if(layers!=null)for(int i=0;i<layers.length();i++){JSONObject layer=layers.getJSONObject(i),name=layer.optJSONObject("name");if(name!=null&&name.optString("type").equals("user")&&name.isNull("profile")){write.put("expectedVersion",layer.getString("version"));break;}}
  JSONObject saved=process.call("config/value/write",write,30);
  if(!saved.optString("status").equals("ok"))return new JSONObject().put("state","configuration_overridden").put("provider",provider);
  android.content.SharedPreferences.Editor owners=process.context.getSharedPreferences("mcp_owners_"+process.home.getName(),0).edit();
  if(operation.endsWith("remove"))owners.remove(provider);else if(args.has("owner_package")){if(args.getString("owner_package").isEmpty())owners.remove(provider);else owners.putString(provider,args.getString("owner_package"));}owners.commit();
  try{process.call("config/mcpServer/reload",null,30);thread="";}catch(Exception e){return new JSONObject().put("state","saved_refresh_pending").put("provider",provider);}
  return new JSONObject().put("state","succeeded").put("provider",provider).put("scope","active_provider").put("removed",operation.endsWith("remove"));
 }
 public synchronized void reconcile(JSONObject packages)throws Exception{
  android.content.SharedPreferences owners=process.context.getSharedPreferences("mcp_owners_"+process.home.getName(),0),versions=process.context.getSharedPreferences("mcp_owner_versions_"+process.home.getName(),0);boolean changed=false;
  for(Map.Entry<String,?> entry:owners.getAll().entrySet()){String provider=entry.getKey(),pkg=String.valueOf(entry.getValue());if(!packages.has(pkg)){configure("apps.mcp.remove",new JSONObject().put("provider",provider));versions.edit().remove(provider).commit();}else{long version=packages.getLong(pkg);changed|=versions.contains(provider)&&versions.getLong(provider,0)!=version;versions.edit().putLong(provider,version).commit();}}
  if(changed){process.call("config/mcpServer/reload",null,30);thread="";}
 }
 public synchronized JSONObject call(String operation,JSONObject args)throws Exception {
  if(operation.equals("apps.mcp.configure")||operation.equals("apps.mcp.remove"))return configure(operation,args);
  if(operation.equals("apps.mcp.refresh")){process.call("config/mcpServer/reload",null,30);thread="";return new JSONObject().put("state","succeeded").put("scope","active_provider");}
  if(!Set.of("apps.mcp.discover","apps.mcp.tools","apps.mcp.call","apps.mcp.authorize","apps.mcp.authorization").contains(operation))throw new IllegalArgumentException("Unknown MCP action");
  JSONObject configured=configured();
  if(operation.equals("apps.mcp.discover")){
   JSONArray names=new JSONArray();Iterator<String> keys=configured.keys();while(keys.hasNext()){String name=keys.next();JSONObject server=configured.optJSONObject(name);if(server!=null&&server.optBoolean("enabled",true))names.put(name);}
   return new JSONObject().put("state","succeeded").put("providers",names);
  }
  String provider=args.getString("provider");if(provider.isEmpty()||provider.length()>100)throw new IllegalArgumentException("Invalid provider");
  JSONObject server=configured.optJSONObject(provider);if(server==null||!server.optBoolean("enabled",true))throw new IllegalArgumentException("Provider is not configured on this phone");
  if(operation.equals("apps.mcp.authorize")){
   JSONObject login=process.call("mcpServer/oauth/login",new JSONObject().put("name",provider).put("timeoutSecs",600),30);
   return new JSONObject().put("state","needs_user_consent").put("provider",provider).put("authorization_url",login.getString("authorizationUrl")).put("login_id",login.optString("loginId"));
  }
  JSONObject current=status(provider);
  String connection=current.optString("runtimeStatus"),auth=current.optString("authStatus");
  if(operation.equals("apps.mcp.authorization")){
   String state=connection.equals("authenticationRequired")||auth.equals("notLoggedIn")?"permission_required":connection.equals("connected")?"succeeded":connection.equals("failed")?"failed":connection.equals("disabled")?"denied":"pending";
   return new JSONObject().put("state",state).put("provider",provider).put("auth_status",auth).put("connection_status",connection);
  }
  if(connection.equals("authenticationRequired")||auth.equals("notLoggedIn"))return new JSONObject().put("state","permission_required").put("permission","provider_oauth").put("provider",provider);
  JSONObject tools=current.optJSONObject("tools");if(tools==null||!current.isNull("toolsError")&&!current.optString("toolsError").isEmpty())throw new IllegalStateException("Provider tool discovery failed");
  if(operation.equals("apps.mcp.tools")){
   JSONArray values=new JSONArray();Iterator<String> keys=tools.keys();while(keys.hasNext())values.put(tools.get(keys.next()));
   return new JSONObject().put("state","succeeded").put("tools",values);
  }
  String tool=args.getString("tool");if(tool.isEmpty()||tool.length()>200)throw new IllegalArgumentException("Invalid tool");
  JSONArray allow=server.optJSONArray("enabled_tools"),deny=server.optJSONArray("disabled_tools");
  boolean allowed=false;if(allow!=null)for(int i=0;i<allow.length();i++)allowed|=tool.equals(allow.getString(i));
  if(deny!=null)for(int i=0;i<deny.length();i++)if(tool.equals(deny.getString(i)))allowed=false;
  if(!allowed)throw new SecurityException("Tool requires an explicit phone-local enabled_tools allowlist");
  boolean exposed=tools.has(tool);Iterator<String> keys=tools.keys();while(!exposed&&keys.hasNext()){JSONObject entry=tools.optJSONObject(keys.next());exposed=entry!=null&&tool.equals(entry.optString("name"));}
  if(!exposed)throw new IllegalArgumentException("Provider does not expose tool");
  JSONObject result=process.call("mcpServer/tool/call",new JSONObject().put("threadId",thread()).put("server",provider).put("tool",tool).put("arguments",args.getJSONObject("arguments")),45);
  return new JSONObject().put("state",result.optBoolean("isError")?"failed":"succeeded").put("result",result);
 }
}
