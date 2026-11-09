package os.aios.agent;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

/** Stable consumers never refer to an excluded listener implementation. */
final class NotificationSources {
    static boolean connected(){NotificationAccess source=AgentFeatureBindings.notificationSource();return source!=null&&source.connected();}
    static StatusBarNotification[] active(){NotificationAccess source=AgentFeatureBindings.notificationSource();return source==null?null:source.active();}
    private static JSONObject unavailable()throws Exception{return new JSONObject().put("state","not_packaged").put("reason_code","notification_events_not_packaged").put("submitted",false).put("executed",false);}
    static JSONObject read(Context context)throws Exception{NotificationAccess source=AgentFeatureBindings.notificationSource();return source==null?unavailable():source.read(context);}
    static JSONObject dismiss(Context context,String key)throws Exception{NotificationAccess source=AgentFeatureBindings.notificationSource();return source==null?unavailable():source.dismiss(context,key);}
    static JSONObject reply(String key,String text)throws Exception{NotificationAccess source=AgentFeatureBindings.notificationSource();return source==null?unavailable():source.reply(key,text);}
}
