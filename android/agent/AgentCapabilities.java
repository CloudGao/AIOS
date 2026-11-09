package os.aios.agent;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import os.aios.host.HostBindings;
import os.aios.host.HostProduct;
import os.aios.runtime.AvailabilityReporter;
import os.aios.runtime.CapabilityClient;

/** Shared consumer of Core's capability projection; no ROM/APK business branching. */
final class AgentCapabilities {
    private static AvailabilityReporter reporter;
    static CapabilityClient client(Context context) { return new CapabilityClient(context, HostBindings.CAPABILITY_ENDPOINT); }
    static synchronized void start(Context context) {
        if (reporter != null) return;
        Context app = HostBindings.applicationContext(context);
        reporter = new AvailabilityReporter(client(app), "agent", () -> {
            JSONArray definitions = HostProduct.providerModules("agent"), values = new JSONArray();
            for (int i = 0; i < definitions.length(); i++) values.put(DeviceHost.probe(app, definitions.getJSONObject(i).getString("id")));
            return values;
        });
        reporter.start();
    }
    static synchronized void refresh(Context context) { start(context); reporter.refresh(); }
    static synchronized void publishNow(Context context) throws Exception { start(context); reporter.publishNow(); }
    static JSONObject guard(Context context, String capability) throws Exception { return client(context).guard(capability); }
    static JSONArray tools(Context context, java.util.Map<String,JSONObject> catalog) throws Exception {
        publishNow(context);
        JSONArray states=client(context).snapshot().getJSONArray("modules");
        java.util.Map<String,String> availability=new java.util.HashMap<>();
        for(int i=0;i<states.length();i++){JSONObject value=states.getJSONObject(i);availability.put(value.getString("id"),value.getString("state"));}
        JSONArray tools=new JSONArray();
        for(java.util.Map.Entry<String,JSONObject> entry:catalog.entrySet()){
            String capability=entry.getKey(),module=HostProduct.moduleFor(capability);
            // Generic dispatchers and recovery tools stay discoverable; every concrete action is guarded.
            boolean dispatcher=java.util.Set.of("phone.action","workspace.action","automation.action","ui.action","execution.resource","user_intervention.request").contains(capability);
            String state=availability.getOrDefault(module,"unsupported_host");
            if(dispatcher||capability.startsWith("capabilities.")||!(state.equals("not_packaged")||state.equals("unsupported_host")||state.equals("blocked_dependency")))tools.put(entry.getValue());
        }
        return tools;
    }
}
