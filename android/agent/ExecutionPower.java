package os.aios.agent;
import android.os.*;
import org.json.JSONObject;
/** Bounded CPU lease only while a real assistant request is advancing. Never lights the display. */
final class ExecutionPower implements AutoCloseable {
 final AgentService service;final Handler handler=new Handler(Looper.getMainLooper());final PowerManager.WakeLock lock;
 final Runnable renew=()->refresh();boolean closed;
 ExecutionPower(AgentService s){service=s;lock=s.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AIOS:execution");lock.setReferenceCounted(false);}
 void refresh(){if(closed)return;handler.removeCallbacks(renew);boolean active=false;try{JSONObject s=AssistantConversation.state(service);active=Authorization.enabled(service)&&s.optString("status").equals("running")&&!s.optBoolean("cancel_requested")&&s.optString("boundary_pause").isEmpty()&&!service.interventions.waiting(s.optString("thread_id"))&&!service.interventions.store.undelivered(s.optString("thread_id"),s.optString("request_id"));}catch(Exception ignored){}
  if(active){lock.acquire(60000);handler.postDelayed(renew,30000);}else if(lock.isHeld())lock.release();
 }
 public void close(){closed=true;handler.removeCallbacks(renew);if(lock.isHeld())lock.release();}
}
