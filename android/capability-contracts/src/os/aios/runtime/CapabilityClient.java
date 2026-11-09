package os.aios.runtime;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only public capability IPC; report is checked against the actual Binder caller by Core. */
public final class CapabilityClient {
    private final Context context;
    private final Uri endpoint;
    public CapabilityClient(Context context, Uri endpoint) {
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
        this.endpoint = endpoint;
    }
    private JSONObject call(String action, JSONObject arguments) throws Exception {
        Bundle input = new Bundle();
        input.putInt("protocol_version", 1);
        input.putString("arguments", arguments.toString());
        Bundle output = context.getContentResolver().call(endpoint, "capabilities", action, input);
        if (output == null || output.getInt("protocol_version") != 1) throw new IllegalStateException("capability_owner_unavailable");
        if (output.containsKey("error")) throw new IllegalStateException(output.getString("error"));
        return new JSONObject(output.getString("result", "{}"));
    }
    public JSONObject snapshot() throws Exception { return call("snapshot", new JSONObject()); }
    public JSONObject catalog() throws Exception { return call("catalog", new JSONObject()); }
    public JSONObject module(String id) throws Exception { return call("feature.status", new JSONObject().put("id", id)); }
    public JSONObject capability(String id) throws Exception { return call("status", new JSONObject().put("id", id)); }
    public void report(String provider, String instance, JSONArray modules) throws Exception {
        call("report", new JSONObject().put("provider", provider).put("instance_id", instance).put("modules", modules));
    }
    public JSONObject guard(String id) throws Exception {
        JSONObject status;
        try { status = capability(id); }
        catch (Exception unavailable) {
            // This is admission only: no backend operation has been submitted.
            return new JSONObject().put("capability",id).put("state","temporarily_unavailable")
                .put("reason_code","capability_owner_unavailable").put("executed",false).put("submitted",false);
        }
        if (status.getString("state").equals("ready")) return null;
        return status.put("executed", false).put("submitted", false);
    }
}
