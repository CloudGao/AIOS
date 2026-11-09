package os.aios.device;
import org.json.JSONObject;

/** Requests explicit user mediation through the existing host ticket/authorization workflow. */
public interface DeviceInteractions {
    JSONObject request(String kind,JSONObject arguments) throws Exception;
}
