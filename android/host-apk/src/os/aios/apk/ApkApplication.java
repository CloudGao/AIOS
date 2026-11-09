package os.aios.apk;

import android.app.Application;
import android.content.Context;
import androidx.work.Configuration;

/** One APK, one scheduling owner. Auxiliary processes never initialize the Agent or Core. */
public final class ApkApplication extends Application implements Configuration.Provider {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if(Application.getProcessName().endsWith(":miniapps"))android.webkit.WebView.setDataDirectorySuffix("miniapps");
        if(!getSharedPreferences("policy",0).contains("enabled"))getSharedPreferences("policy",0).edit().putBoolean("enabled",false).commit();
        os.aios.agent.ApkAgentBootstrap.install();
    }
    @Override public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setExecutor(java.util.concurrent.Executors.newFixedThreadPool(3)).build();
    }
    @Override public void onCreate() {
        super.onCreate();
        if (!getPackageName().equals(Application.getProcessName())) return;
        os.aios.core.ApkCoreBootstrap.initialize(this);
        os.aios.agent.ApkAgentBootstrap.initialize(this);
    }
}
