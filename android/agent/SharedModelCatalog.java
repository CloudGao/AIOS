package os.aios.agent;

import android.content.Context;
import org.json.*;
import java.util.Set;

/** Capability bindings are separate from the interactive assistant's active account/model. */
final class SharedModelCatalog {
    static final String PREFS="shared_model_bindings";
    static final String LOCAL="local:lfm2.5-350m:q4_k_m";
    static final Set<String> TEXT=Set.of("text.default","notification.filter","notification.importance","notification.summary","text.extract","text.summary","text.judge");
    static final Set<String> SPECIAL=Set.of("photo.image_embedding","photo.text_embedding","photo.face_detection","photo.description");
    static String owner(String capability) {
        if(capability.startsWith("photo."))return "photo-index";
        if(capability.startsWith("notification."))return "notification-governance";
        return "core";
    }
    static boolean ready(Context c,String module) {
        try{return AgentCapabilities.client(c).module(module).optString("state").equals("ready");}
        catch(Exception unavailable){return false;}
    }
    static JSONObject binding(Context c,String capability)throws JSONException {
        if(!TEXT.contains(capability)&&!SPECIAL.contains(capability))throw new IllegalArgumentException("unknown_capability");
        String saved=c.getSharedPreferences(PREFS,0).getString(capability,null);
        if(saved!=null)return new JSONObject(saved);
        return new JSONObject().put("model",capability.equals("text.default")?LOCAL:TEXT.contains(capability)?"inherit":"builtin").put("fallback",capability.equals("text.default")?"none":TEXT.contains(capability)?"inherit":"none");
    }
    static JSONObject resolve(Context c,String capability)throws JSONException {
        JSONObject selected=binding(c,capability);
        JSONObject defaults=binding(c,"text.default");
        return new JSONObject().put("model",selected.optString("model").equals("inherit")?defaults.getString("model"):selected.getString("model"))
            .put("fallback",selected.optString("fallback","none").equals("inherit")?defaults.optString("fallback","none"):selected.optString("fallback","none"));
    }
    static void save(Context c,String capability,JSONObject binding)throws JSONException {
        if(!TEXT.contains(capability))throw new IllegalArgumentException("specialized_model_requires_profile");
        String model=binding.getString("model"),fallback=binding.optString("fallback","none");
        if(!ready(c,owner(capability)))throw new IllegalStateException("capability_unavailable");
        JSONObject previous=binding(c,capability);
        // Preserve a saved offline binding; only a new selection requires current availability.
        if(!ready(c,"local-inference")&&
            ((model.equals(LOCAL)&&!model.equals(previous.optString("model")))||
             (fallback.equals(LOCAL)&&!fallback.equals(previous.optString("fallback")))))
            throw new IllegalStateException("local_inference_unavailable");
        validateModel(model,!capability.equals("text.default"));
        if(!fallback.equals("none"))validateModel(fallback,!capability.equals("text.default"));
        if(model.equals(fallback)&&!model.equals("inherit"))throw new IllegalArgumentException("recursive_fallback");
        JSONObject clean=new JSONObject().put("model",model).put("fallback",fallback);
        if(!c.getSharedPreferences(PREFS,0).edit().putString(capability,clean.toString()).commit())throw new IllegalStateException("model_binding_write_failed");
    }
    private static void validateModel(String model,boolean inherit){
        if(model.equals(LOCAL)||(inherit&&model.equals("inherit")))return;
        // Provider is explicit so an account change cannot silently change where private data goes.
        if(model.matches("cloud:[a-zA-Z0-9_-]{1,64}:[a-zA-Z0-9_./:@+-]{1,160}"))return;
        throw new IllegalArgumentException("model_identifier");
    }
    static JSONArray capabilities(Context c)throws JSONException{
        JSONArray items=new JSONArray();
        for(String capability:new String[]{"text.default","notification.filter","notification.importance","notification.summary","text.extract","text.summary","text.judge","photo.image_embedding","photo.text_embedding","photo.face_detection","photo.description"}){
            String state;
            try{state=AgentCapabilities.client(c).module(owner(capability)).optString("state");}
            catch(Exception unavailable){state="temporarily_unavailable";}
            if(state.equals("not_packaged")||state.equals("unsupported_host")||state.equals("blocked_dependency"))continue;
            JSONObject row=new JSONObject().put("id",capability).put("binding",binding(c,capability));
            row.put("selectable",TEXT.contains(capability)&&state.equals("ready"));
            if(capability.equals("photo.description"))row.put("state","not_integrated");
            items.put(row);
        }return items;
    }
}
