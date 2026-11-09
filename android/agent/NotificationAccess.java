package os.aios.agent;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

/** Public-SDK notification source contract; governance is an optional consumer. */
interface NotificationAccess {
    boolean connected();
    StatusBarNotification[] active();
    JSONObject read(Context context)throws Exception;
    JSONObject dismiss(Context context,String key)throws Exception;
    JSONObject reply(String key,String text)throws Exception;
}
