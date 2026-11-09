package os.aios.runtime;
/** These screen ownership tests must never invoke the scheduler IPC boundary. */
public final class CoreRuntimeClient {
 public CoreRuntimeClient(android.content.Context context){}
 public org.json.JSONObject call(String capability,String action,org.json.JSONObject args){throw new AssertionError("Unexpected Core IPC in isolated screen ownership test");}
}
