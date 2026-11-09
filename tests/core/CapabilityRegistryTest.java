package os.aios.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

/** Local contract tests, without a device backend or ROM class stubs. */
public final class CapabilityRegistryTest {
    static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
    static JSONObject module(String id, String provider, boolean packaged, String dependency, String selector) throws Exception {
        return new JSONObject().put("id",id).put("provider",provider).put("packaged",packaged)
            .put("requires",dependency.isEmpty()?new JSONArray():new JSONArray().put(dependency))
            .put("capabilities",new JSONArray().put(selector));
    }
    static JSONObject report(String id, boolean supported, boolean permission, boolean connected) throws Exception {
        return new JSONObject().put("id",id).put("supported",supported)
            .put("permission_ready",permission).put("connected",connected)
            .put("reason_code",!supported?"backend_missing":!permission?"permission_missing":!connected?"disconnected":"ready");
    }
    static String state(CapabilityRegistry registry,String id,long now)throws Exception {
        return registry.module(id,now).getString("state");
    }
    public static void main(String[] args) throws Exception {
        JSONObject manifest = new JSONObject().put("schema_version",1).put("manifest_hash","fixture")
            .put("product_id","test").put("host_id","local-test")
            .put("modules",new JSONArray()
                .put(module("core","core",true,"","workspace.*").put("fixed",true))
                .put(module("device","agent",true,"core","device.*"))
                .put(module("photos","agent",false,"device","photos.*")));
        CapabilityRegistry registry = new CapabilityRegistry(manifest.toString());
        check(state(registry,"photos",0).equals("not_packaged"),"build exclusion wins");
        registry.report("agent","agent-1",new JSONArray().put(report("device",true,true,true)),0);
        check(state(registry,"device",0).equals("blocked_dependency"),"dependency is not implicitly ready");
        registry.report("core","core-1",new JSONArray().put(report("core",true,true,true)),0);
        check(state(registry,"device",0).equals("ready"),"ready after core report");
        JSONObject copy=registry.snapshot(0);
        copy.getJSONArray("modules").getJSONObject(0).put("state","corrupted");
        check(state(registry,"core",0).equals("ready"),"snapshot does not mutate owner");
        registry.report("agent","agent-1",new JSONArray().put(report("device",true,false,true)),1);
        check(state(registry,"device",1).equals("permission_required"),"permission distinct from connection");
        registry.report("agent","agent-2",new JSONArray().put(report("device",true,true,false)),2);
        check(state(registry,"device",2).equals("temporarily_unavailable"),"disconnected");
        check(registry.module("device",2).getString("instance_id").equals("agent-2"),"new instance identity");
        registry.report("agent","agent-2",new JSONArray(),3);
        check(state(registry,"device",3).equals("temporarily_unavailable"),"omitted report removes stale readiness");
        registry.report("agent","agent-3",new JSONArray().put(report("device",false,false,false)),4);
        check(state(registry,"device",200000).equals("unsupported_host"),"unsupported precedes dependency");
        check(registry.module("device",200000).getString("reason_code").equals("backend_missing"),"primary reason matches primary state");
        long revision=registry.snapshot(200000).getLong("revision");
        check(registry.snapshot(200001).getLong("revision")==revision,"expiration changes revision once");
        boolean rejected=false;
        try { registry.report("runtime","spoof",new JSONArray().put(report("core",true,true,true)),200001); }
        catch(SecurityException expected){rejected=true;}
        check(rejected,"provider ownership");
        check(registry.capability("missing.operation",200001).getString("state").equals("unsupported_host"),"unknown capability");
        rejected=false;
        manifest.getJSONArray("modules").getJSONObject(0).put("packaged",false);
        try { new CapabilityRegistry(manifest.toString()); } catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"fixed core cannot be excluded");
        System.out.println("Capability registry contracts passed");
    }
}
