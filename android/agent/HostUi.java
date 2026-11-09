package os.aios.agent;

import android.app.Notification;
import android.content.Context;
import android.view.WindowManager;

/** Public-SDK contract. Implementations are installed by the trusted product host. */
interface HostUi {
    default boolean showSetupIfNeeded(android.app.Activity activity) {
        if(activity.getSharedPreferences("first-use",0).getBoolean("dismissed",false))return false;
        activity.startActivity(new android.content.Intent(activity,FirstUseActivity.class));return true;
    }
    default void addSetupSettings(android.app.Activity activity,android.widget.LinearLayout panel) {
        panel.addView(os.aios.core.AiosUi.settingRow(activity,"settings",activity.getString(R.string.setup_title),activity.getString(R.string.setup_resume),null,true,true,()->activity.startActivity(new android.content.Intent(activity,FirstUseActivity.class))));
    }
    default void addOptionalAppsSettings(android.app.Activity activity,android.widget.LinearLayout panel) {}
    default String assistantSettingsLabel(Context context){return context.getString(R.string.setup_title);}
    default android.content.Intent assistantSettingsIntent(Context context) {return null;}
    default android.content.ComponentName speechRecognizerComponent(Context context){return null;}
    default android.content.ComponentName speechRecognizerFallback(Context context,android.content.ComponentName failed,int error){return null;}
    String accessibilitySettingsAction();
    void configureOverlay(Context context, WindowManager.LayoutParams params);
    Notification.Builder interventionNotification(Context context, boolean visible);
    SelectionController selectionController(AssistantSelectionPanel panel);
}
