package os.aios.device;

import android.content.Intent;
import org.json.JSONObject;

/** Existing device operation protocol; implementations do not own business state. */
public interface DeviceBackend extends AutoCloseable {
    JSONObject execute(String capability, JSONObject arguments) throws Exception;
    JSONObject query(String capability,JSONObject arguments)throws Exception;
    @Override void close();
    interface ApplicationLauncher { JSONObject open(Intent intent, JSONObject options) throws Exception; }
}
