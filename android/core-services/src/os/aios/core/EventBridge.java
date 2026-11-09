package os.aios.core;
import org.json.JSONObject;import os.aios.core.events.EventHub;
/** Internal publishers share the same durable Hub and Scheduler dispatch. */
final class EventBridge{static JSONObject publish(String topic,String source,String version,JSONObject data,boolean critical)throws Exception{JSONObject event=EventHub.publish(null,topic,source,version,data,critical);CoreState.automation().onEvent(new JSONObject().put("topic",topic).put("data",data).put("event",event));return event;}}
