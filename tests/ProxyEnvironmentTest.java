package os.aios.codex;
import java.util.HashMap;
import java.util.Map;
public final class ProxyEnvironmentTest {
 public static void main(String[] args) {
  Map<String,String> env=new HashMap<>();
  for(String k:new String[]{"HTTP_PROXY","http_proxy","HTTPS_PROXY","https_proxy","ALL_PROXY","all_proxy"}) env.put(k,"http://obsolete.invalid:99");
  env.put("UNRELATED","keep");
  AndroidNetwork.applyProxy(env,"");
  for(String k:new String[]{"HTTP_PROXY","http_proxy","HTTPS_PROXY","https_proxy","ALL_PROXY","all_proxy"}) if(env.containsKey(k)) throw new AssertionError(k);
  if(!"keep".equals(env.get("UNRELATED"))) throw new AssertionError("unrelated environment");
  AndroidNetwork.applyProxy(env,"http://127.0.0.1:7890");
  if(!"http://127.0.0.1:7890".equals(env.get("HTTP_PROXY"))||!env.get("HTTP_PROXY").equals(env.get("HTTPS_PROXY")))throw new AssertionError("system proxy");
  if(!"127.0.0.1,localhost,::1".equals(env.get("NO_PROXY"))||!env.get("NO_PROXY").equals(env.get("no_proxy")))throw new AssertionError("loopback exclusion");
  AndroidNetwork.applyProxy(env,"");
  if(env.containsKey("HTTP_PROXY")||env.containsKey("HTTPS_PROXY"))throw new AssertionError("proxy removal");
  System.out.println("PASS stale proxy cleanup, direct/proxy transitions, loopback exclusion, unrelated environment preservation");
 }
}
