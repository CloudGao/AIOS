package os.aios.codex;
import android.app.Instrumentation;import android.os.Bundle;import org.json.*;
/** Read-only configuration inventory: never emits keys, endpoints, or account identities. */
public final class ProviderAvailabilityProbe extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{ProviderStore store=new ProviderStore(getTargetContext());JSONArray providers=new JSONArray();for(String id:ProviderStore.IDS){JSONObject config=store.publicConfig(id);providers.put(new JSONObject().put("provider",id).put("active",id.equals(store.active())).put("configured",config.optBoolean("configured")).put("protocol",config.optString("protocol")));}out.putString("result",providers.toString());finish(-1,out);}catch(Exception e){out.putString("error",e.getClass().getSimpleName());finish(0,out);}}
}
