package os.aios.agent;
import android.app.Application;
import androidx.work.Configuration;
import os.aios.automation.AutomationService;
public final class AiosApplication extends Application implements Configuration.Provider {
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(base);RomDeviceBindings.install();AgentFeatureInstaller.install();}
    @Override public Configuration getWorkManagerConfiguration(){return AgentHostLifecycle.workConfiguration();}
    @Override public void onCreate(){super.onCreate();AgentHostLifecycle.initialize(this);}
}
