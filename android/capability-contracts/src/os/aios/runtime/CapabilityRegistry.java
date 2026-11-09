package os.aios.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Core-owned availability projection. No Android calls, execution ledger or user switches. */
public final class CapabilityRegistry {
    public static final long REPORT_TTL_MS = 120000;
    private final JSONObject manifest;
    private final LinkedHashMap<String, JSONObject> modules = new LinkedHashMap<>();
    private final Map<String, Report> reports = new HashMap<>();
    private final String instanceId = UUID.randomUUID().toString();
    private long revision = 1;

    private static final class Report {
        final JSONObject value;
        final String instance;
        final long observedAt;
        boolean expired;
        Report(JSONObject value, String instance, long observedAt) throws Exception {
            this.value = new JSONObject(value.toString());
            this.instance = instance;
            this.observedAt = observedAt;
        }
    }

    public CapabilityRegistry(String json) throws Exception {
        manifest = new JSONObject(json);
        if (manifest.getInt("schema_version") != 1) throw new IllegalArgumentException("manifest_version");
        JSONArray items = manifest.getJSONArray("modules");
        Set<String> selectors = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject module = items.getJSONObject(i);
            String id = module.getString("id");
            if (modules.put(id, module) != null) throw new IllegalArgumentException("duplicate_module:" + id);
            if (module.optBoolean("fixed") && !module.getBoolean("packaged"))
                throw new IllegalArgumentException("fixed_core_missing:" + id);
            JSONArray capabilities = module.getJSONArray("capabilities");
            for (int k = 0; k < capabilities.length(); k++)
                if (!selectors.add(capabilities.getString(k))) throw new IllegalArgumentException("duplicate_capability");
        }
        for (String id : modules.keySet()) validateDependencies(id, new HashSet<>(), new HashSet<>());
    }

    private void validateDependencies(String id, Set<String> path, Set<String> done) throws Exception {
        if (done.contains(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("dependency_cycle:" + id);
        JSONObject module = modules.get(id);
        if (module == null) throw new IllegalArgumentException("unknown_module:" + id);
        JSONArray dependencies = module.getJSONArray("requires");
        for (int i = 0; i < dependencies.length(); i++) {
            String dependency = dependencies.getString(i);
            if (!modules.containsKey(dependency)) throw new IllegalArgumentException("unknown_dependency:" + dependency);
            if (module.getBoolean("packaged") && !modules.get(dependency).getBoolean("packaged"))
                throw new IllegalArgumentException("missing_dependency:" + id + ":" + dependency);
            validateDependencies(dependency, path, done);
        }
        path.remove(id);
        done.add(id);
    }

    public synchronized boolean packaged(String id) {
        JSONObject module = modules.get(id);
        return module != null && module.optBoolean("packaged");
    }

    /** Called only after the IPC owner validates the actual caller UID and signature permission. */
    public synchronized void report(String provider, String providerInstance, JSONArray values, long now) throws Exception {
        if (providerInstance == null || providerInstance.isBlank() || providerInstance.length() > 128)
            throw new IllegalArgumentException("invalid_provider_instance");
        Map<String, Report> next = new HashMap<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i);
            String id = value.getString("id");
            JSONObject module = modules.get(id);
            if (module == null || !module.getString("provider").equals(provider))
                throw new SecurityException("provider_does_not_own_module:" + id);
            if (next.containsKey(id)) throw new IllegalArgumentException("duplicate_report:" + id);
            value.getBoolean("supported");
            value.getBoolean("permission_ready");
            value.getBoolean("connected");
            next.put(id, new Report(value, providerInstance, now));
        }
        // A report is a complete provider snapshot. Omitted modules cannot inherit stale readiness.
        for (Map.Entry<String, JSONObject> entry : modules.entrySet())
            if (entry.getValue().getString("provider").equals(provider)) reports.remove(entry.getKey());
        reports.putAll(next);
        revision++;
    }

    public synchronized JSONObject module(String id, long now) throws Exception {
        expireReports(now);
        JSONObject definition = modules.get(id);
        if (definition == null) return new JSONObject().put("id", id).put("state", "unsupported_host").put("reason_code", "unknown_module");
        Report report = reports.get(id);
        JSONArray reasons = new JSONArray();
        String state;
        if (!definition.getBoolean("packaged")) { state = "not_packaged"; reasons.put("not_packaged"); }
        else {
            String dependencyState = "";
            JSONArray dependencies = definition.getJSONArray("requires");
            for (int i = 0; i < dependencies.length(); i++) {
                String dependency = dependencies.getString(i);
                if (!module(dependency, now).getString("state").equals("ready")) {
                    dependencyState = "blocked_dependency";
                    reasons.put("dependency:" + dependency);
                }
            }
            if (report != null && !report.value.getBoolean("supported")) {
                state = "unsupported_host";
                JSONArray dependenciesReasons = reasons;
                reasons = new JSONArray().put(report.value.optString("reason_code",state));
                for(int i=0;i<dependenciesReasons.length();i++)reasons.put(dependenciesReasons.get(i));
            }
            else if (!dependencyState.isEmpty()) state = dependencyState;
            else if (report == null) state = "temporarily_unavailable";
            else if (!report.value.getBoolean("permission_ready")) state = "permission_required";
            else if (!report.value.getBoolean("connected") || report.expired)
                state = "temporarily_unavailable";
            else state = "ready";
            if (!state.equals("ready")) reasons.put(report == null ? "provider_not_reported" :
                now - report.observedAt > REPORT_TTL_MS && report.value.getBoolean("supported") ? "report_expired" :
                report.value.optString("reason_code", state));
        }
        return new JSONObject().put("id", id).put("fixed", definition.optBoolean("fixed"))
            .put("packaged", definition.getBoolean("packaged")).put("provider", definition.getString("provider"))
            .put("state", state).put("reason_code", reasons.length() == 0 ? "ready" : reasons.getString(0))
            .put("reasons", reasons).put("instance_id", report == null ? JSONObject.NULL : report.instance)
            .put("observed_at", report == null ? 0 : report.observedAt)
            .put("limits", report == null ? new JSONObject() : new JSONObject(report.value.optJSONObject("limits") == null ? "{}" : report.value.getJSONObject("limits").toString()));
    }

    private void expireReports(long now) {
        boolean changed = false;
        for (Report report : reports.values()) {
            if (!report.expired && (now < report.observedAt || now - report.observedAt > REPORT_TTL_MS)) {
                report.expired = true;
                changed = true;
            }
        }
        if (changed) revision++;
    }

    public synchronized JSONObject capability(String capability, long now) throws Exception {
        String owner = null;
        int specificity = -1;
        for (JSONObject module : modules.values()) {
            JSONArray selectors = module.getJSONArray("capabilities");
            for (int i = 0; i < selectors.length(); i++) {
                String selector = selectors.getString(i);
                boolean wildcard = selector.endsWith(".*");
                String prefix = wildcard ? selector.substring(0, selector.length() - 1) : selector;
                if ((wildcard ? capability.startsWith(prefix) : capability.equals(selector)) && selector.length() > specificity) {
                    specificity = selector.length(); owner = module.getString("id");
                }
            }
        }
        JSONObject value = owner == null ? new JSONObject().put("state", "unsupported_host").put("reason_code", "unknown_capability") : module(owner, now);
        return value.put("capability", capability).put("core_instance_id", instanceId).put("revision", revision);
    }

    public synchronized JSONObject snapshot(long now) throws Exception {
        JSONArray values = new JSONArray();
        for (String id : modules.keySet()) values.put(module(id, now));
        return new JSONObject().put("core_instance_id", instanceId).put("revision", revision)
            .put("manifest_hash", manifest.getString("manifest_hash"))
            .put("product_id", manifest.getString("product_id")).put("host_id", manifest.getString("host_id"))
            .put("modules", values).put("observed_at", now);
    }
    public synchronized JSONObject catalog() throws Exception { return new JSONObject(manifest.toString()); }
}
