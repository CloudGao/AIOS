package os.aios.agent;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** Optional first-run guide and manually opened settings share the same controls. */
public final class ApkSettingsActivity extends Activity {
    private boolean onboarding,leaving;
    private TextView connection,speechSelectionSummary;
    private LinearLayout connectionRow;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        int label=ApkUiHost.connectionLabel();connection.setText(label);
        // statusRow child 0 is the 8dp status dot (see AiosUi.statusRow).
        connectionRow.getChildAt(0).setBackground(AiosUi.shape(ApkSettingsActivity.this,label==R.string.apk_connected?AiosTheme.accent(ApkSettingsActivity.this):label==R.string.apk_connected_without_monitor?AiosTheme.warn(ApkSettingsActivity.this):AiosTheme.outlineStrong(ApkSettingsActivity.this),4));
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);onboarding=getIntent().getBooleanExtra("onboarding",false);
        setTitle(onboarding?R.string.apk_setup_title:R.string.apk_settings_title);
        LinearLayout shell=AiosUi.column(this);
        shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        LinearLayout appBar=AiosUi.appBar(this,getString(onboarding?R.string.apk_setup_title:R.string.apk_settings_title),onboarding?null:this::finish);
        appBar.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1,AiosUi.dp(this,56)));
        shell.addView(appBar,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setId(7001);LinearLayout page=AiosUi.column(this);
        page.setPadding(0,0,0,AiosUi.dp(this,24));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);AiosUi.page(this,shell);
        TextView intro=AiosUi.secondary(this,getString(onboarding?R.string.apk_setup_intro:R.string.apk_settings_intro));intro.setPadding(AiosUi.dp(this,16),0,AiosUi.dp(this,16),AiosUi.dp(this,8));page.addView(intro);

        // Device: activation status row + activation page.
        page.addView(AiosUi.label(this,getString(R.string.apk_settings_group_device)));
        LinearLayout device=AiosUi.group(this);page.addView(device);
        connectionRow=AiosUi.statusRow(this,AiosTheme.outlineStrong(this),getString(R.string.adb_activation_title),getString(ApkUiHost.connectionLabel()),null,true,false,()->open(new Intent(this,AdbActivationActivity.class)));
        connection=(TextView)((LinearLayout)connectionRow.getChildAt(1)).getChildAt(1);device.addView(connectionRow);
        row(device,"shield",R.string.apk_authorize,R.string.apk_authorization_hint,false,true,()->open(new Intent(this,ControlActivity.class)));

        // Model.
        page.addView(AiosUi.label(this,getString(R.string.apk_settings_group_model)));
        LinearLayout model=AiosUi.group(this);page.addView(model);
        row(model,"account",R.string.apk_accounts,R.string.apk_account_hint,true,false,()->open(new Intent(this,CodexAccountsActivity.class)));

        LinearLayout speechSelection=AiosUi.settingRow(this,"mic",getString(R.string.model_choice_speech),SpeechModelSelection.label(this),null,false,true,()->open(new Intent(this,SpeechModelSelectionActivity.class)));
        speechSelectionSummary=(TextView)((LinearLayout)speechSelection.getChildAt(1)).getChildAt(1);model.addView(speechSelection);

        // System permissions.
        page.addView(AiosUi.label(this,getString(R.string.apk_settings_group_permissions)));
        LinearLayout permissions=AiosUi.group(this);page.addView(permissions);
        row(permissions,"shield",R.string.apk_permissions_title,R.string.apk_permissions_intro,true,false,()->open(new Intent(this,ApkPermissionsActivity.class)));
        row(permissions,"touch",R.string.apk_accessibility,R.string.apk_accessibility_hint,true,false,()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        row(permissions,"assistant",R.string.apk_overlay,R.string.apk_overlay_hint,false,false,()->open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        row(permissions,"notification",R.string.apk_notifications,R.string.apk_notifications_hint,false,false,()->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        row(permissions,"bell",R.string.apk_post_notifications,R.string.apk_post_notifications_hint,false,false,()->{
            if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
            else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        row(permissions,"mic",R.string.apk_default_assistant,R.string.apk_assistant_invocation_help,false,true,()->open(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)));

        // More.
        page.addView(AiosUi.label(this,getString(R.string.apk_settings_group_more)));
        LinearLayout more=AiosUi.group(this);page.addView(more);
        row(more,"apps",R.string.apk_miniapps,R.string.apk_miniapps_hint,true,true,()->open(new Intent().setClassName(this,"os.aios.miniapp.LibraryActivity")));
        if(onboarding){
            LinearLayout actions=AiosUi.actions(this,AiosUi.textButton(this,getString(R.string.apk_setup_later),this::finish),AiosUi.filledButton(this,getString(R.string.apk_setup_done),()->open(new Intent(this,ApkPermissionsActivity.class).putExtra("finish_setup",true))));
            actions.setPadding(AiosUi.dp(this,16),AiosUi.dp(this,8),AiosUi.dp(this,16),0);page.addView(actions);
        }
    }
    private void row(LinearLayout group,String icon,int title,int summary,boolean first,boolean last,Runnable action){group.addView(AiosUi.settingRow(this,icon,getString(title),getString(summary),null,first,last,action));}
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){AiosUi.dialog(this).setMessage(R.string.apk_settings_unavailable).setPositiveButton(android.R.string.ok,null).show();}}
    @Override protected void onResume(){super.onResume();speechSelectionSummary.setText(SpeechModelSelection.label(this));handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void finish(){
        if(!leaving){leaving=true;
            if(onboarding){getSharedPreferences("apk-onboarding",0).edit().putBoolean("handled",true).apply();startActivity(new Intent(this,ApkHomeActivity.class));}
            else if(getIntent().getBooleanExtra("return_to_assistant",false)&&Settings.canDrawOverlays(this))startForegroundService(new Intent(this,AssistantOrbService.class).setAction("continue").putExtra("reload_draft",true).putExtra("restore_keyboard",getIntent().getBooleanExtra("return_keyboard",false)));
        }
        super.finish();
    }
}
