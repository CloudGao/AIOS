package os.aios.agent;

import android.content.Context;
import org.json.*;

/** Capability router; cloud fallback exists only when explicitly saved by the user. */
final class SharedTextInference {
    static JSONObject generate(Context context,String capability,String override,String instruction,String input,boolean json,int tokens)throws Exception{
        return generate(context,capability,override,instruction,input,json,tokens,null);
    }
    static JSONObject generate(Context context,String capability,String override,String instruction,String input,boolean json,int tokens,JSONObject schema)throws Exception{
        if(!new os.aios.runtime.CoreRuntimeClient(context).call("policy","get",new JSONObject()).optBoolean("enabled"))throw new SecurityException("authorization_revoked");
        JSONObject binding=SharedModelCatalog.resolve(context,capability);
        String model=override==null||override.equals("inherit")?binding.getString("model"):override;
        try{return invoke(context,capability,model,instruction,input,json,tokens,schema);}
        catch(Exception primary){String fallback=binding.optString("fallback","none");if(fallback.equals("none")||fallback.equals(model))throw primary;return invoke(context,capability,fallback,instruction,input,json,tokens,schema).put("fallback_used",true);}
    }
    private static JSONObject invoke(Context context,String capability,String model,String instruction,String input,boolean json,int tokens,JSONObject schema)throws Exception{
        JSONObject request=new JSONObject().put("model",model).put("instruction",instruction).put("input",input).put("json",json).put("max_tokens",tokens);
        if(schema!=null)request.put("schema",schema);
        if(model.equals(SharedModelCatalog.LOCAL))return new os.aios.runtime.CoreRuntimeClient(context).call("inference","generate",request);
        if(!model.startsWith("cloud:"))throw new IllegalArgumentException("model_unavailable");
        int separator=model.indexOf(':',6);if(separator<0)throw new IllegalArgumentException("model_identifier");
        request.put("stateless",capability.startsWith("notification."));
        request.put("provider",model.substring(6,separator)).put("model",model.substring(separator+1));
        try(CodexAccountClient client=new CodexAccountClient(context)){return client.call(new JSONObject().put("op","inference/generate").put("params",request));}
    }
    static JSONObject judge(Context context,String model,String instruction,JSONObject notification)throws Exception{
        return classify(context,"notification.filter",model,instruction,notification);
    }
    static JSONObject classify(Context context,String capability,String model,String instruction,JSONObject notification)throws Exception{
        JSONObject output=generate(context,capability,model,NotificationClassifier.instruction(instruction),
            NotificationClassifier.input(notification).toString(),true,24,NotificationClassifier.schema());
        return NotificationClassifier.decode(output.getString("text"));
    }
}
