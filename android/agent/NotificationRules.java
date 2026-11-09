package os.aios.agent;

import org.json.*;
import java.util.Locale;

/** Ordered terminal rules. Input text is data, never executable policy. */
final class NotificationRules {
    interface Judge { JSONObject judge(String model, String instruction, JSONObject notification) throws Exception; }
    static JSONObject evaluate(JSONArray rules, JSONObject notification, Judge judge) throws Exception {
        for (int i=0;i<rules.length();i++) {
            JSONObject rule=rules.getJSONObject(i);
            if (!rule.optBoolean("enabled",true) || !matches(rule.optJSONObject("when"),notification,0)) continue;
            String action=rule.optString("action","allow");
            JSONObject result;
            if (action.equals("model")) {
                try {
                    result=judge.judge(rule.optString("model","inherit"),rule.optString("instruction"),notification);
                    String decision=result.getString("decision");
                    if (!decision.equals("allow")&&!decision.equals("filter")) throw new IllegalArgumentException("invalid_decision");
                } catch (Exception failure) {
                    result=new JSONObject().put("decision",rule.optString("on_error","allow"))
                        .put("reason_code","model_failed");
                }
            } else result=new JSONObject().put("decision",action).put("reason_code","rule_match");
            result.put("rule_id",rule.getString("id"));
            if (!result.has("importance")) result.put("importance",rule.optInt("importance",1));
            result.put("importance",Math.max(0,Math.min(2,result.optInt("importance",1))));
            String category=result.optString("category",rule.optString("category","uncategorized"));
            if (!java.util.Set.of("uncategorized","advertising","suspected_fraud","other").contains(category)) category="uncategorized";
            result.put("category",category);
            return result;
        }
        return new JSONObject().put("decision","allow").put("importance",1).put("reason_code","default");
    }
    static boolean matches(JSONObject condition,JSONObject notification,int depth)throws JSONException {
        if(depth>8)throw new IllegalArgumentException("condition_depth");
        if(condition==null||condition.length()==0)return true;
        if(condition.has("all")){
            JSONArray children=condition.getJSONArray("all");
            for(int i=0;i<children.length();i++)if(!matches(children.getJSONObject(i),notification,depth+1))return false;
            return true;
        }
        if(condition.has("any")){
            JSONArray children=condition.getJSONArray("any");
            for(int i=0;i<children.length();i++)if(matches(children.getJSONObject(i),notification,depth+1))return true;
            return false;
        }
        if(condition.has("not"))return !matches(condition.getJSONObject("not"),notification,depth+1);
        String field=condition.getString("field"),value=condition.getString("value"),actual=notification.optString(field,"");
        if(condition.optBoolean("ignore_case",false)){actual=actual.toLowerCase(Locale.ROOT);value=value.toLowerCase(Locale.ROOT);}
        switch(condition.getString("op")){
            case "equals":return actual.equals(value);
            case "contains":return actual.contains(value);
            case "starts_with":return actual.startsWith(value);
            case "ends_with":return actual.endsWith(value);
            default:throw new IllegalArgumentException("condition_operator");
        }
    }
    static void validate(JSONArray rules)throws JSONException{
        if(rules.length()>256)throw new IllegalArgumentException("rule_limit");
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(int i=0;i<rules.length();i++){
            JSONObject rule=rules.getJSONObject(i);String id=rule.getString("id");
            if(id.isEmpty()||id.length()>128||!ids.add(id))throw new IllegalArgumentException("rule_id");
            if(!java.util.Set.of("allow","filter","model").contains(rule.getString("action")))throw new IllegalArgumentException("rule_action");
            if(!java.util.Set.of("allow","filter").contains(rule.optString("on_error","allow")))throw new IllegalArgumentException("rule_fallback");
            if(rule.optString("instruction").length()>4000)throw new IllegalArgumentException("instruction_limit");
            validateCondition(rule.optJSONObject("when"),0);
        }
    }
    private static void validateCondition(JSONObject c,int depth)throws JSONException{
        if(c==null||c.length()==0)return;
        if(depth>8)throw new IllegalArgumentException("condition_depth");
        for(String key:new String[]{"all","any"})if(c.has(key)){
            JSONArray children=c.getJSONArray(key);if(children.length()>32)throw new IllegalArgumentException("condition_limit");
            for(int i=0;i<children.length();i++)validateCondition(children.getJSONObject(i),depth+1);return;
        }
        if(c.has("not")){validateCondition(c.getJSONObject("not"),depth+1);return;}
        if(!java.util.Set.of("package","channel","title","text","conversation").contains(c.getString("field")))throw new IllegalArgumentException("condition_field");
        if(!java.util.Set.of("equals","contains","starts_with","ends_with").contains(c.getString("op")))throw new IllegalArgumentException("condition_operator");
        if(c.getString("value").length()>4000)throw new IllegalArgumentException("condition_value");
    }
}
