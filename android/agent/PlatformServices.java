package os.aios.agent;

import android.content.Context;
import org.json.JSONObject;
import os.aios.device.DeviceBackend;

/** Shared routing and capability admission. Device operations live in the selected backend. */
public final class PlatformServices {
    private final Context context;
    private final DeviceBackend backend;
    private final DeviceOperationRegistry features;
    public PlatformServices(Context context) {
        this.context=context;
        features=AgentFeatureBindings.operations(context);
        backend=DeviceHost.platform(context,(intent,options)->{
            AgentService service=AgentService.instance;
            if(service==null)throw new IllegalStateException("ui_service_unavailable");
            return service.ui.open(intent,options.optString("mode","background"),options.optString("session_id"));
        });
    }
    void close(){features.close();backend.close();}
    public JSONObject execute(String operation,JSONObject arguments)throws Exception {
        if(operation.startsWith("capabilities.")){
            os.aios.runtime.CapabilityClient client=AgentCapabilities.client(context);
            JSONObject data;
            switch(operation){
                case "capabilities.catalog":data=client.catalog();break;
                case "capabilities.snapshot":data=client.snapshot();break;
                case "capabilities.status":data=client.capability(arguments.getString("id"));break;
                default:throw new IllegalArgumentException("unknown_capability_query");
            }
            return new JSONObject().put("state","succeeded").put("data",data);
        }
        if(!java.util.Set.of("requests.get","requests.cancel").contains(operation)){
            JSONObject unavailable=AgentCapabilities.guard(context,operation);
            if(unavailable!=null)return unavailable;
        }
        if(operation.startsWith("events.")){
            JSONObject data;
            switch(operation){
                case "events.read":data=EventStore.read(arguments.optLong("after",0));break;
                case "events.subscribe":data=EventStore.subscribe(arguments.getJSONArray("topics"));break;
                case "events.poll":data=EventStore.poll(arguments.getString("subscription"));break;
                case "events.ack":data=EventStore.ack(arguments.getString("subscription"),arguments.getJSONArray("event_ids"));break;
                case "events.fail":data=EventStore.fail(arguments.getString("subscription"),arguments.getString("event_id"),arguments.optString("error","consumer failed"));break;
                case "events.unsubscribe":data=EventStore.unsubscribe(arguments.getString("subscription"));break;
                default:throw new IllegalArgumentException("unknown_event_action");
            }
            return new JSONObject().put("state","succeeded").put("data",data);
        }
        if(operation.equals("shares.get"))return ShareNotes.call(context,"source",arguments);
        if(operation.startsWith("notes."))return ShareNotes.call(context,operation.substring(6),arguments);
        if(operation.equals("apps.capabilities"))return AppCapabilityCatalog.query(context,arguments);
        JSONObject contributed=features.execute(operation,arguments);if(contributed!=null)return contributed;
        if(operation.startsWith("apps.cli.")||operation.startsWith("apps.mcp.")){
            if(!arguments.optString("owner_package").isEmpty())context.getPackageManager().getApplicationInfo(arguments.getString("owner_package"),0);
            try(CodexAccountClient runtime=new CodexAccountClient(context)){
                runtime.call(new JSONObject().put("op","start").put("mode","live"));
                return operation.startsWith("apps.cli.")
                    ?runtime.call(new JSONObject().put("op","cli/action").put("action",operation.substring(9)).put("params",arguments))
                    :runtime.call(new JSONObject().put("op","mcp/action").put("action",operation).put("params",arguments));
            }
        }
        return backend.execute(operation,arguments);
    }
}
