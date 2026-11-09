package os.aios.agent;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import org.json.JSONObject;

final class NotificationListenerAccess implements NotificationAccess {
    public boolean connected(){return PhoneNotifications.instance!=null;}
    public StatusBarNotification[] active(){
        PhoneNotifications listener=PhoneNotifications.instance;
        return listener==null?null:listener.getActiveNotifications();
    }
    public JSONObject read(Context context)throws Exception{return PhoneNotifications.read(context);}
    public JSONObject dismiss(Context context,String key)throws Exception{return PhoneNotifications.dismiss(context,key);}
    public JSONObject reply(String key,String text)throws Exception{return PhoneNotifications.reply(key,text);}
}
