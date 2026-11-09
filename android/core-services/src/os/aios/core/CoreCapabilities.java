package os.aios.core;

import android.content.Context;
import android.os.Binder;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import os.aios.host.HostBindings;
import os.aios.host.ProductManifest;
import os.aios.runtime.CapabilityRegistry;

/** Existing Core provider owns the projection; no new service or durable business state. */
public final class CoreCapabilities {
    private static CapabilityRegistry registry;
    private static final String INSTANCE = java.util.UUID.randomUUID().toString();
    private static long lastLocalReport = -1;

    static synchronized CapabilityRegistry registry() {
        if (registry == null) try { registry = new CapabilityRegistry(ProductManifest.JSON); }
        catch (Exception e) { throw new IllegalStateException("invalid_product_manifest", e); }
        return registry;
    }
    static boolean packaged(String id) { return registry().packaged(id); }

    public static synchronized JSONObject feature(Context context, String id) throws Exception {
        long now = SystemClock.elapsedRealtime();
        refreshLocal(context, now);
        return registry().module(id, now);
    }
    public static synchronized JSONObject capability(Context context,String id)throws Exception{
        long now=SystemClock.elapsedRealtime();refreshLocal(context,now);
        return registry().capability(id,now);
    }

    static void require(Context context, String id) throws Exception {
        JSONObject value = feature(context, id);
        if (!value.getString("state").equals("ready"))
            throw new IllegalStateException("capability_unavailable:" + id + ":" +
                value.getString("state") + ":" + value.getString("reason_code"));
    }

    private static void refreshLocal(Context context, long now) throws Exception {
        if (lastLocalReport >= 0 && now - lastLocalReport < 30000) return;
        JSONArray definitions = registry().catalog().getJSONArray("modules"), values = new JSONArray();
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject definition = definitions.getJSONObject(i);
            if (!definition.getString("provider").equals("core") || !definition.getBoolean("packaged")) continue;
            String id = definition.getString("id");
            // Probe deployment prerequisites only; model quality and per-call permissions stay at execution.
            boolean supported = true;
            if (id.equals("local-inference")) supported = (os.aios.runtime.models.OptionalModelStore.file(context, "lfm").isFile()
                || HostBindings.bundledTextModel().isFile())
                && HostBindings.nativePayload(context, "libaios_llama_server.so").canExecute();
            if (id.equals("photo-index")) supported = new java.io.File(context.getFilesDir(), "search-models/manifest.json").isFile()
                || new java.io.File(HostBindings.bundledSearchModels(), "manifest.json").isFile();
            values.put(new JSONObject().put("id", id).put("supported", supported)
                .put("permission_ready", true).put("connected", true)
                .put("reason_code", supported ? "ready" : "required_resource_missing"));
        }
        registry().report("core", INSTANCE, values, now);
        lastLocalReport = now;
    }

    static synchronized JSONObject call(Context context, String action, JSONObject args) throws Exception {
        long now = SystemClock.elapsedRealtime();
        refreshLocal(context, now);
        switch (action) {
            case "catalog": return registry().catalog();
            case "snapshot": return registry().snapshot(now);
            case "feature.status": return registry().module(args.getString("id"), now);
            case "status": return registry().capability(args.getString("id"), now);
            case "report": {
                String provider = args.getString("provider");
                int expected;
                if (provider.equals("agent")) expected = HostBindings.agentUid(context);
                else if (provider.equals("runtime")) expected = HostBindings.runtimeUid(context);
                else throw new SecurityException("external_core_report_forbidden");
                if (Binder.getCallingUid() != expected) throw new SecurityException("capability_report_owner_mismatch");
                registry().report(provider, args.getString("instance_id"), args.getJSONArray("modules"), now);
                return registry().snapshot(now);
            }
            default: throw new IllegalArgumentException("unknown_capability_action");
        }
    }
}
