package os.aios.device;

import org.json.JSONObject;

/** Small device events enter the existing outbox; large payloads use a URI/FD reference. */
public interface DeviceEventSink {
    /** The tuple (topic, sourceId, sourceVersion) identifies one immutable event across retries. */
    void publish(String topic,String sourceId,String sourceVersion,JSONObject data) throws Exception;
}
