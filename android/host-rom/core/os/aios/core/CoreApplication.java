package os.aios.core;
import android.app.Application;import androidx.work.Configuration;
/** Android process entry point supplied by this host. */
public final class CoreApplication extends Application implements Configuration.Provider{
 public Configuration getWorkManagerConfiguration(){return CoreHostLifecycle.workConfiguration();}
 public void onCreate(){super.onCreate();CoreHostLifecycle.initialize(this);}
}
