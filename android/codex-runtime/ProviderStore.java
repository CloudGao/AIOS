package os.aios.codex;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import java.net.URI;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials stay in this application's UID; only encrypted keys are persisted. */
final class ProviderStore {
    final SharedPreferences prefs;
    final Context context;
    static final java.util.List<String> IDS=java.util.List.of("chatgpt","openai","deepseek","glm","qwen_coding","qwen","kimi","claude","custom");
    ProviderStore(Context c){this(c,"model_accounts");}
    ProviderStore(Context c,String name){context=c;prefs=c.getSharedPreferences(name,0);}
    String active(){return prefs.getString("active","chatgpt");}
    String model()throws Exception{return active().equals("chatgpt")?prefs.getString("chatgpt_model",""):config(active()).optString("model");}
    String effort()throws Exception{return prefs.getString("effort:"+active()+":"+model(),"");}
    void effort(String value)throws Exception{if(!prefs.edit().putString("effort:"+active()+":"+model(),value).commit())throw new IllegalStateException(context.getString(R.string.provider_save));}
    void select(String id){check(id);if(!prefs.edit().putString("active",id).commit())throw new IllegalStateException(context.getString(R.string.provider_save));}
    void check(String id){if(!IDS.contains(id))throw new IllegalArgumentException(context.getString(R.string.provider_unknown));}
    JSONObject config(String id)throws Exception{check(id);JSONObject cfg=new JSONObject(prefs.getString(id,"{}"));
        cfg.put("protocol",protocol(id,cfg.optString("baseUrl")));return cfg;}
    static String protocol(String id,String url)throws Exception {
        if(id.equals("claude"))return ProviderProtocol.MESSAGES;
        if(id.equals("qwen_coding")||(id.equals("qwen")&&"coding.dashscope.aliyuncs.com".equalsIgnoreCase(new URI(url).getHost())))return ProviderProtocol.CHAT;
        return ProviderProtocol.RESPONSES;
    }
    JSONObject publicConfig(String id)throws Exception{JSONObject v=config(id);v.remove("secret");return v.put("configured",prefs.contains(id));}
    javax.crypto.SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("codex-provider-keys")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("codex-provider-keys",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
        return (javax.crypto.SecretKey)ks.getKey("codex-provider-keys",null);
    }
    String secret(String id)throws Exception{
        JSONObject s=config(id).getJSONObject("secret");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(s.getString("iv"),Base64.NO_WRAP)));
        cipher.updateAAD(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.decode(s.getString("data"),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    synchronized void save(JSONObject p)throws Exception{
        String id=p.getString("provider");check(id);if(id.equals("chatgpt"))throw new IllegalArgumentException(context.getString(R.string.provider_web_login));
        String url=p.getString("baseUrl").trim(),model=p.getString("model").trim(),secret=p.optString("apiKey").trim();
        if(id.equals("openai"))url="https://api.openai.com/v1";
        if(id.equals("deepseek"))url="https://api.deepseek.com";
        if(id.equals("glm"))url="https://open.bigmodel.cn/api/v1";
        if(id.equals("qwen_coding"))url="https://coding.dashscope.aliyuncs.com/v1";
        if(id.equals("claude"))url="https://api.anthropic.com/v1";
        URI uri;
        try{uri=new URI(url);}catch(java.net.URISyntaxException invalid){throw new IllegalArgumentException(context.getString(R.string.provider_url));}
        if(id.equals("kimi")&&!java.util.Set.of("https://api.kimi.com/coding/v1","https://api.kimi.ai/coding/v1").contains(url))throw new IllegalArgumentException(context.getString(R.string.provider_kimi_url));
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException(context.getString(R.string.provider_url));
        if(!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))throw new IllegalArgumentException(context.getString(R.string.provider_model));
        JSONObject old=config(id);
        if(secret.isEmpty()&&!old.has("secret"))throw new IllegalArgumentException(context.getString(R.string.provider_key_required));
        if(secret.isEmpty()&&!url.equals(old.optString("baseUrl")))throw new IllegalArgumentException(context.getString(R.string.provider_key_changed));
        if(secret.length()>8192||secret.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException(context.getString(R.string.provider_key_invalid));
        JSONObject encrypted=old.optJSONObject("secret");
        if(!secret.isEmpty()){Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));encrypted=new JSONObject().put("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(c.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP));}
        JSONObject value=new JSONObject().put("baseUrl",url).put("model",model).put("protocol",protocol(id,url)).put("secret",encrypted);
        if(!prefs.edit().putString(id,value.toString()).commit())throw new IllegalStateException(context.getString(R.string.provider_save));
    }
    void remove(String id){check(id);if(!prefs.edit().remove(id).commit())throw new IllegalStateException(context.getString(R.string.provider_remove));}
    boolean officialOpenAI()throws Exception {
        if(active().equals("openai"))return true;
        return active().equals("custom")&&config("custom").optString("baseUrl").replaceAll("/+$", "").equals("https://api.openai.com/v1");
    }
    String defaultEffort(String model)throws Exception{return officialOpenAI()&&java.util.Set.of("gpt-6-luna","gpt-6.1-sol","gpt-6.1-terra").contains(model)?"medium":"";}
    JSONArray reasoningLevels(String model)throws Exception {
        JSONArray levels=new JSONArray();if(!officialOpenAI())return levels;
        String[] values;
        switch(model){
            case "gpt-6-luna":case "gpt-6.1-terra":values=new String[]{"none","low","medium","high","xhigh","max"};break;
            case "gpt-6-astra":case "gpt-6.1-sol":values=new String[]{"low","medium","high","xhigh","max"};break;
            default:return levels;
        }
        for(String value:values)levels.put(new JSONObject().put("effort",value).put("description",value));
        return levels;
    }
    JSONArray modalities(){JSONArray values=new JSONArray().put("text");if(active().equals("openai")||active().equals("claude")||active().equals("kimi"))values.put("image");return values;}
    JSONObject catalog(String model)throws Exception{
        JSONObject m=new JSONObject().put("slug",model).put("display_name",model).put("description","AIOS configured provider")
            .put("base_instructions",CodexProcess.PHONE_INSTRUCTIONS)
            .put("model_messages",new JSONObject().put("instructions_template",CodexProcess.PHONE_INSTRUCTIONS))
            .put("supported_reasoning_levels",reasoningLevels(model)).put("shell_type","shell_command").put("visibility","list").put("supported_in_api",true).put("priority",0)
            .put("supports_search_tool",false).put("supports_experimental_context",false).put("support_verbosity",false).put("supports_reasoning_summary_parameter",false).put("tool_mode","direct")
            .put("truncation_policy",new JSONObject().put("mode","bytes").put("limit",10000)).put("experimental_supported_tools",new JSONArray()).put("input_modalities",modalities());
        if(!defaultEffort(model).isEmpty())m.put("default_reasoning_level",defaultEffort(model));
        if(officialOpenAI()&&model.equals("gpt-6-luna"))m.put("context_window",1050000);
        return new JSONObject().put("models",new JSONArray().put(m));
    }
}
