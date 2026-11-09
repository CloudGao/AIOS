package os.aios.agent;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import os.aios.core.AiosUi;

/** Resumable local setup. No credentials, automatic grants, or network requests. */
public final class FirstUseActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);setTitle(R.string.setup_title);}
    @Override protected void onResume(){super.onResume();render();}
    void open(Intent intent){try{startActivity(intent);}catch(android.content.ActivityNotFoundException|SecurityException e){Toast.makeText(this,R.string.setup_unavailable,Toast.LENGTH_LONG).show();}}
    void render(){
        LinearLayout body=AiosUi.column(this);int pad=AiosUi.dp(this,24);body.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
        body.addView(AiosUi.heading(this,getString(R.string.setup_title)));
        body.addView(AiosUi.secondary(this,getString(R.string.setup_intro)));
        ConnectivityManager manager=getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps=manager.getNetworkCapabilities(manager.getActiveNetwork());
        boolean online=caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        body.addView(AiosUi.settingRow(this,"network",getString(R.string.setup_network),getString(online?R.string.setup_online:R.string.setup_offline),null,true,false,()->open(new Intent(Settings.ACTION_WIRELESS_SETTINGS))));
        body.addView(AiosUi.settingRow(this,"account",getString(R.string.account_title),getString(R.string.setup_account),null,false,false,()->open(new Intent(this,CodexAccountsActivity.class))));
        body.addView(AiosUi.settingRow(this,"shield",getString(R.string.setup_control),getString(R.string.setup_permissions),null,false,false,()->open(new Intent(this,ControlActivity.class))));
        body.addView(AiosUi.settingRow(this,"touch",getString(R.string.setup_accessibility),getString(R.string.setup_optional),null,false,true,()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        boolean bundled=new java.io.File("/system_ext/etc/aios/search-models/manifest.json").isFile();
        body.addView(AiosUi.secondary(this,getString(bundled?R.string.setup_models:R.string.setup_models_missing)));
        body.addView(AiosUi.filledButton(this,getString(R.string.setup_continue),()->{
            getSharedPreferences("first-use",0).edit().putBoolean("dismissed",true).apply();finish();}));
    }
}
