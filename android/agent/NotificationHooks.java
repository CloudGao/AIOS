package os.aios.agent;

import android.content.Context;
import android.service.notification.StatusBarNotification;

/** Optional governance consumers; notification event collection remains independent. */
interface NotificationHooks {
    void posted(Context context, StatusBarNotification notification) throws Exception;
    void removed(Context context, StatusBarNotification notification);
    void settings(android.app.Activity activity,android.widget.LinearLayout parent);
}
