package os.aios.core.automation;
import org.json.*;
import java.util.*;
/** Bounded literal predicates evaluated before an occurrence can enter the model queue. */
final class NotificationFilter {
 static void validate(JSONObject t)throws Exception {
  if(!t.has("conditions"))return;
  if(!t.optString("topic").startsWith("notification."))throw new IllegalArgumentException("Notification conditions require a notification topic");
  JSONArray a=t.getJSONArray("conditions");if(a.length()>20)throw new IllegalArgumentException("At most 20 conditions");
  if(!Set.of("all","any").contains(t.optString("condition_mode","all")))throw new IllegalArgumentException("Invalid condition mode");
  for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);
   if(!Set.of("title","text").contains(c.getString("field"))||!Set.of("contains","not_contains","equals","starts_with","ends_with").contains(c.getString("op")))throw new IllegalArgumentException("Invalid notification predicate");
   String v=c.getString("value");if(v.isEmpty()||v.length()>500)throw new IllegalArgumentException("Condition value must be 1..500 characters");
   if(c.has("ignore_case"))c.getBoolean("ignore_case");
  }
 }
 static boolean matches(JSONObject t,JSONObject data)throws Exception {
  JSONArray a=t.optJSONArray("conditions");if(a==null||a.length()==0)return true;
  boolean any=t.optString("condition_mode","all").equals("any");
  for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);String field=c.getString("field");
   // Truncated data cannot prove a negative, equality, or suffix. Fail closed.
   boolean hit=false;
   if(data.has(field)&&!data.isNull(field)){
    String value=data.getString(field),expected=c.getString("value"),op=c.getString("op");
    if(c.optBoolean("ignore_case",true)){value=value.toLowerCase(Locale.ROOT);expected=expected.toLowerCase(Locale.ROOT);}
    boolean truncated=data.optBoolean("content_truncated");
    hit=switch(op){case "contains"->value.contains(expected);case "not_contains"->!truncated&&!value.contains(expected);case "equals"->!truncated&&value.equals(expected);case "starts_with"->value.startsWith(expected);case "ends_with"->!truncated&&value.endsWith(expected);default->false;};
   }
   if(any&&hit)return true;if(!any&&!hit)return false;
  }
  return !any;
 }
}
