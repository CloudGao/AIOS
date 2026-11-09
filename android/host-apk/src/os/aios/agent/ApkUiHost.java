package os.aios.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.provider.Settings;
import android.view.WindowManager;

/** Public SDK equivalents owned by the standalone host, not shared business code. */
final class ApkUiHost implements HostUi {

    @Override public boolean showSetupIfNeeded(android.app.Activity activity){return false;}
    static int connectionLabel(){return AdbBridge.connected()?(AdbBridge.monitorsInput()?R.string.apk_connected:R.string.apk_connected_without_monitor):R.string.apk_disconnected;}
    @Override public void addSetupSettings(android.app.Activity activity,android.widget.LinearLayout panel){
        panel.addView(os.aios.core.AiosUi.settingRow(activity,"settings",activity.getString(R.string.apk_settings_title),activity.getString(R.string.apk_settings_intro),null,true,true,()->activity.startActivity(new android.content.Intent(activity,ApkSettingsActivity.class))));
    }
    @Override public String assistantSettingsLabel(Context context){return context.getString(R.string.apk_settings_title);}
    @Override public android.content.Intent assistantSettingsIntent(Context context){return new android.content.Intent(context,ApkSettingsActivity.class).putExtra("return_to_assistant",true);}
    @Override public android.content.ComponentName speechRecognizerComponent(Context context){return ApkSpeech.select(context);}
    @Override public android.content.ComponentName speechRecognizerFallback(Context context,android.content.ComponentName failed,int error){return ApkSpeech.fallback(context,failed,error);}
    public String accessibilitySettingsAction() {
        return Settings.ACTION_ACCESSIBILITY_SETTINGS;
    }

    public void configureOverlay(Context context, WindowManager.LayoutParams params) {
        // accessibilityTitle is platform-only; use the public localized window title.
        params.setTitle(context.getString(R.string.assistant_overlay_title));
    }

    public Notification.Builder interventionNotification(Context context, boolean visible) {
        String id = "interventions";
        if (visible) {
            id = "apk_interventions_visible";
            NotificationChannel channel = new NotificationChannel(id,
                    context.getString(R.string.apk_interventions),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        return new Notification.Builder(context, id);
    }
    public SelectionController selectionController(AssistantSelectionPanel panel){return new ApkSelectionController(panel);}
}
