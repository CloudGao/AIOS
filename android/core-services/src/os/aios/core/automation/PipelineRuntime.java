package os.aios.core.automation;

import org.json.JSONObject;
import java.util.function.BooleanSupplier;

/** Optional native processor contribution to the existing scheduler. */
public interface PipelineRuntime {
    JSONObject status() throws Exception;
    JSONObject configure(JSONObject parameters) throws Exception;
    void onStateChanged() throws Exception;
    void reconcile() throws Exception;
    void run(int version, long slice, BooleanSupplier stopped) throws Exception;
}
