package os.aios.agent;

import android.content.Context;
import android.service.notification.StatusBarNotification;

final class NotificationGovernanceHooks implements NotificationHooks {
    @Override public void settings(android.app.Activity activity,android.widget.LinearLayout parent) {
        try{
            if(!AgentCapabilities.client(activity).module("notification-governance").optString("state").equals("ready"))return;
            String title=activity.getString(R.string.governance_title);
            android.widget.LinearLayout row=os.aios.core.AiosUi.settingRow(activity,"notification",title,
                activity.getString(R.string.governance_order_help),null,false,false,
                ()->activity.startActivity(new android.content.Intent(activity,NotificationManagementActivity.class)));
            row.setContentDescription(os.aios.core.DisplayText.clean(title));parent.addView(row);
        }catch(Exception unavailable){android.util.Log.w("AIOSCapabilities","Governance settings unavailable",unavailable);}
    }
    @Override public void posted(Context context, StatusBarNotification notification) throws Exception {
        String identity=notification.getNotification().extras.getString("android.aios.identity","");
        if(!identity.isEmpty())try(NotificationLedger ledger=new NotificationLedger(context)){
            ledger.applied(identity);
            NotificationPolicyWorker.enqueue(context,"summary",notification.getPackageName());
        }
    }
    @Override public void removed(Context context,StatusBarNotification notification) {
        try{NotificationPolicyWorker.enqueue(context,"summary",notification.getPackageName());}
        catch(Exception error){android.util.Log.w("AIOSNotifications","Governance consumer unavailable",error);}
    }
}
