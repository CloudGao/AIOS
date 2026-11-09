package os.aios.agent;

import android.app.Notification;
import android.content.Context;
import android.provider.Settings;
import android.view.WindowManager;

/** Platform-only UI calls live in the ROM host. */
final class RomUiHost implements HostUi {
    @Override public void addOptionalAppsSettings(android.app.Activity activity,android.widget.LinearLayout panel){
        panel.addView(os.aios.core.AiosUi.settingRow(activity,"apps",activity.getString(R.string.optional_apps_title),null,null,false,false,()->activity.startActivity(new android.content.Intent(activity,OptionalAppsActivity.class))));
    }
    public String accessibilitySettingsAction(){return Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS;}
    public void configureOverlay(Context context, WindowManager.LayoutParams params){
        params.accessibilityTitle=params.getTitle();
    }
    public Notification.Builder interventionNotification(Context context, boolean visible){
        return new Notification.Builder(context,"interventions").setSilent(visible);
    }
    public SelectionController selectionController(AssistantSelectionPanel panel){return new RomSelectionController(panel);}
}
