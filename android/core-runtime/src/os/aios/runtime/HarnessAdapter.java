package os.aios.runtime;
import org.json.JSONArray;import org.json.JSONObject;

/** Engine boundary. Adapters must not claim checkpoint portability they cannot provide. */
public interface HarnessAdapter{
 String id();int version();JSONObject capabilities()throws Exception;
 JSONObject create(JSONObject request)throws Exception;
 JSONObject continueSession(String nativeThreadId,JSONObject request)throws Exception;
 JSONObject interrupt(String nativeThreadId,String executionId)throws Exception;
 JSONArray readEvents(String nativeThreadId,String afterCursor,int limit)throws Exception;
 default JSONObject exportVisibleHistory(String nativeThreadId)throws Exception{throw new UnsupportedOperationException("history_export unsupported");}
 default JSONObject restoreCheckpoint(String checkpoint)throws Exception{throw new UnsupportedOperationException("checkpoint_restore unsupported");}
}

