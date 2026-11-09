package os.aios.agent;
import org.json.*;

public final class NotificationRulesTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    static JSONObject when(String field,String op,String value)throws Exception{return new JSONObject().put("field",field).put("op",op).put("value",value);}
    static JSONObject rule(String id,String action,JSONObject condition)throws Exception{return new JSONObject().put("id",id).put("action",action).put("when",condition);}
    public static void main(String[] args)throws Exception{
        JSONObject notification=new JSONObject().put("package","chat").put("title","Bank update").put("text","New discount today");
        int[] calls={0};NotificationRules.Judge judge=(m,i,n)->{calls[0]++;return new JSONObject().put("decision","filter").put("importance",99).put("category","invalid");};
        JSONArray rules=new JSONArray().put(rule("chat","allow",when("package","equals","chat"))).put(rule("fallback","model",null));
        NotificationRules.validate(rules);check(NotificationRules.evaluate(rules,notification,judge).getString("decision").equals("allow"));check(calls[0]==0);
        rules.getJSONObject(0).put("enabled",false);JSONObject result=NotificationRules.evaluate(rules,notification,judge);check(calls[0]==1);check(result.getString("decision").equals("filter"));check(result.getInt("importance")==2);check(result.getString("category").equals("uncategorized"));
        NotificationRules.Judge failing=(m,i,n)->{throw new Exception("offline");};check(NotificationRules.evaluate(rules,notification,failing).getString("decision").equals("allow"));rules.getJSONObject(1).put("on_error","filter");check(NotificationRules.evaluate(rules,notification,failing).getString("decision").equals("filter"));
        JSONObject group=new JSONObject().put("all",new JSONArray().put(when("package","equals","chat")).put(new JSONObject().put("any",new JSONArray().put(when("title","contains","Bank")).put(when("text","equals","none")))).put(new JSONObject().put("not",when("text","contains","OTP"))));
        check(NotificationRules.matches(group,notification,0));notification.put("text","OTP");check(!NotificationRules.matches(group,notification,0));
        check(NotificationRules.matches(when("title","contains","bank").put("ignore_case",true),notification,0));check(!NotificationRules.matches(when("title","contains","bank"),notification,0));
        check(NotificationRules.evaluate(new JSONArray(),notification,judge).getString("decision").equals("allow"));
        boolean rejected=false;try{NotificationRules.validate(new JSONArray().put(rule("duplicate","filter",null)).put(rule("duplicate","allow",null)));}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        rejected=false;try{NotificationRules.validate(new JSONArray().put(rule("bad","filter",when("text","regex",".*"))));}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        JSONObject nested=when("text","equals","x");for(int i=0;i<10;i++)nested=new JSONObject().put("not",nested);
        rejected=false;try{NotificationRules.validate(new JSONArray().put(rule("deep","filter",nested)));}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        JSONObject generated=rule("similar","filter",new JSONObject().put("all",new JSONArray().put(when("package","equals","chat")).put(when("text","contains","discount"))));
        notification.put("text","discount").put("package","other");check(NotificationRules.evaluate(new JSONArray().put(generated),notification,judge).getString("decision").equals("allow"));
        notification.put("package","chat");check(NotificationRules.evaluate(new JSONArray().put(generated),notification,judge).getString("decision").equals("filter"));
        System.out.println("PASS "+checks+" notification rule assertions");
    }
}
