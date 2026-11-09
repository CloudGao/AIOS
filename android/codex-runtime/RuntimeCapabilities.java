package os.aios.codex;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import os.aios.host.HostBindings;
import os.aios.host.HostProduct;
import os.aios.runtime.AvailabilityReporter;
import os.aios.runtime.CapabilityClient;

final class RuntimeCapabilities {
    private static final java.util.Map<String,String> failures=new java.util.concurrent.ConcurrentHashMap<>();
    static void failed(String id,Exception failure){failures.put(id,"initialization_failed");android.util.Log.w("AIOSCapabilities",id+" initialization unavailable",failure);}
    static void recovered(String id){failures.remove(id);}
    static JSONObject unavailable(String id)throws Exception{
        boolean packaged=HostProduct.includes(id);
        return new JSONObject().put("state",packaged?"temporarily_unavailable":"not_packaged")
            .put("reason_code",packaged?failures.getOrDefault(id,"provider_unavailable"):"not_packaged")
            .put("executed",false).put("submitted",false);
    }
    static AvailabilityReporter start(Context context) {
        AvailabilityReporter reporter = new AvailabilityReporter(new CapabilityClient(context, HostBindings.CAPABILITY_ENDPOINT), "runtime", () -> {
            JSONArray modules = HostProduct.providerModules("runtime"), values = new JSONArray();
            for (int i = 0; i < modules.length(); i++) {
                String id = modules.getJSONObject(i).getString("id");
                boolean supported = true;
                if (id.equals("harness")) try { HostBindings.codexExecutable(context); } catch (java.io.IOException error) { supported = false; }
                if (id.equals("cli")) supported = HostBindings.nativePayload(context, "libaios_node.so").canExecute()
                    && HostBindings.nativePayload(context, "libaios_cli_launcher.so").canExecute();
                values.put(new JSONObject().put("id", id).put("supported", supported).put("permission_ready", true)
                    .put("connected", !failures.containsKey(id)).put("reason_code", supported ? failures.getOrDefault(id,"ready") : "required_resource_missing"));
            }
            return values;
        });
        reporter.start();
        return reporter;
    }
}
