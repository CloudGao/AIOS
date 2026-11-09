package os.aios.codex;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;

/** Debug-only instrumentation, isolated preferences, fake credentials, no network calls. */
public final class AccountContractTest extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    interface Check {void run()throws Exception;}
    int assertions;
    void expect(boolean ok)throws Exception{if(!ok)throw new Exception("Contract assertion "+assertions+" failed");assertions++;}
    void reject(Check check)throws Exception{boolean rejected=false;try{check.run();}catch(IllegalArgumentException e){rejected=true;}expect(rejected);}
    JSONObject input(String provider,String url,String key)throws Exception{return new JSONObject().put("provider",provider).put("model","test-model").put("baseUrl",url).put("apiKey",key);}
    public void onStart(){Bundle out=new Bundle();ProviderStore store=new ProviderStore(getTargetContext(),"account_contract_test");try{
        store.prefs.edit().clear().commit();String fake="test-only-not-a-real-key";
        expect(CodexProcess.tomlString("/data/user/0/model.json").equals("\"/data/user/0/model.json\""));
        expect(CodexProcess.tomlString("https://api.openai.com/v1").equals("\"https://api.openai.com/v1\""));
        expect(CodexProcess.tomlString("a\"b").equals("\"a\\\"b\""));
        store.save(input("custom","https://example.com/v1",fake));expect(store.secret("custom").equals(fake));
        expect(!store.prefs.getString("custom","").contains(fake));expect(!store.publicConfig("custom").has("secret"));expect(store.publicConfig("custom").getBoolean("configured"));
        reject(()->store.save(input("custom","http://example.com/v1",fake)));
        reject(()->store.save(input("custom","https://user:password@example.com/v1",fake)));
        reject(()->store.save(input("custom","https://example.com/v1?key=value",fake)));
        store.save(input("qwen","https://coding.dashscope.aliyuncs.com/v1",fake));
        expect(store.publicConfig("qwen").getString("protocol").equals("chat"));
        store.save(input("qwen_coding","https://different.example/v1",fake));
        expect(store.publicConfig("qwen_coding").getString("baseUrl").equals("https://coding.dashscope.aliyuncs.com/v1"));
        expect(store.publicConfig("qwen_coding").getString("protocol").equals("chat"));
        store.save(input("qwen","https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",fake));
        expect(store.publicConfig("qwen").getString("protocol").equals("responses"));
        expect(store.secret("qwen_coding").equals(fake));
        store.save(input("kimi","https://api.kimi.com/coding/v1",fake));
        expect(store.publicConfig("kimi").getString("protocol").equals("responses"));
        reject(()->store.save(input("kimi","https://api.moonshot.cn/v1",fake)));
        reject(()->store.save(input("kimi","https://api.kimi.ai/coding/v1","")));
        store.save(input("kimi","https://api.kimi.ai/coding/v1",fake));
        store.save(input("claude","https://different.example/v1",fake).put("protocol","responses"));
        expect(store.publicConfig("claude").getString("baseUrl").equals("https://api.anthropic.com/v1"));
        expect(store.publicConfig("claude").getString("protocol").equals("messages"));
        expect(!store.publicConfig("claude").toString().contains(fake));
        // A pre-upgrade profile with no protocol keeps its endpoint, model and encrypted key.
        JSONObject legacy=store.config("custom");legacy.remove("protocol");store.prefs.edit().putString("custom",legacy.toString()).commit();
        expect(store.publicConfig("custom").getString("protocol").equals("responses"));expect(store.secret("custom").equals(fake));
        reject(()->store.save(input("custom","https://different.example/v1","")));
        store.save(input("custom","https://example.com/v1",""));expect(store.secret("custom").equals(fake));
        store.save(input("deepseek","https://different.example/v1",fake));expect(store.publicConfig("deepseek").getString("baseUrl").equals("https://api.deepseek.com"));
        store.save(input("glm","https://different.example/v1",fake));expect(store.publicConfig("glm").getString("baseUrl").equals("https://open.bigmodel.cn/api/v1"));
        expect(store.catalog("test-model").getJSONArray("models").getJSONObject(0).getString("tool_mode").equals("direct"));
        store.save(input("openai","https://untrusted.example/v1",fake).put("model","gpt-6-luna"));
        expect(store.config("openai").getString("baseUrl").equals("https://api.openai.com/v1"));
        expect(store.secret("openai").equals(fake));store.select("openai");
        expect(store.reasoningLevels("gpt-6-luna").length()==6);
        expect(store.reasoningLevels("gpt-6-astra").length()==5);
        expect(store.reasoningLevels("unrecognized").length()==0);
        JSONObject metadata=store.catalog("gpt-6-luna").getJSONArray("models").getJSONObject(0);
        expect(!metadata.getString("base_instructions").isEmpty());
        expect(!metadata.getJSONObject("model_messages").getString("instructions_template").isEmpty());
        store.effort("low");expect(store.effort().equals("low"));
        store.save(input("openai","https://api.openai.com/v1","").put("model","gpt-6-astra"));
        expect(store.effort().isEmpty());expect(store.secret("openai").equals(fake));
        expect(store.secret("custom").equals(fake));
        store.select("custom");expect(store.reasoningLevels("gpt-6-luna").length()==0);
        store.select("custom");expect(store.active().equals("custom"));store.remove("custom");expect(!store.publicConfig("custom").getBoolean("configured"));
        out.putString("result","PASS "+assertions+" account contracts; isolated prefs; no live keys or network");finish(-1,out);
    }catch(Exception e){out.putString("result","FAIL "+e.getClass().getSimpleName()+" assertion "+assertions);finish(0,out);}finally{store.prefs.edit().clear().commit();}}
}
