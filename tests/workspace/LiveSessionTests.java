package os.aios.core.workspace;
import org.json.*;
final class LiveSessionTests {
 static int count;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);count++;}
 static JSONObject live(WorkspaceStore store,JSONObject p)throws Exception{return store.call("sync.live",p).getJSONObject("data");}
 static int run(WorkspaceStore store)throws Exception{
  count=0;
  JSONObject p=new JSONObject().put("conversation_id","live-test").put("request_id","request-1").put("text","First question").put("status","queued").put("submitted_at",1000);
  check(live(store,p).getString("session_id").equals("assistant:live-test"),"queued appears before native id");
  check(store.require(store.getReadableDatabase(),"sessions","assistant:live-test").getString("source_status").equals("queued"),"queued status");
  live(store,p);check(store.rows(store.getReadableDatabase(),"SELECT id FROM sessions WHERE id=?","assistant:live-test").length()==1,"queued retry deduplicates");
  p.put("thread_id","live-thread").put("status","running");live(store,p);
  check(store.rows(store.getReadableDatabase(),"SELECT id FROM sessions WHERE id=?","assistant:live-test").length()==0,"pending removed at binding");
  check(store.rows(store.getReadableDatabase(),"SELECT value FROM settings WHERE key=?","session.alias:assistant:live-test").getJSONObject(0).getString("value").equals("codex:live-thread"),"old open entry maps to canonical");
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getString("source_status").equals("running"),"running before any response");
  long revision=store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getLong("revision");live(store,p);
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getLong("revision")==revision,"unchanged lifecycle avoids history reload");
  p.remove("thread_id");live(store,p);check(store.rows(store.getReadableDatabase(),"SELECT id FROM sessions WHERE id=?","assistant:live-test").length()==0,"late unbound replay resolves alias");p.put("thread_id","live-thread");
  p.put("status","failed").put("execution_finished_at",2000);live(store,p);
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getString("source_status").equals("failed"),"failure retained without completed mirror");
  p.put("status","running").remove("execution_finished_at");live(store,p);
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getString("source_status").equals("failed"),"stale restore cannot regress terminal state");
  p.put("request_id","request-2").put("submitted_at",3000).put("text","Second question").put("status","queued");live(store,p);
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getString("title").equals("First question"),"followup preserves title");
  check(store.rows(store.getReadableDatabase(),"SELECT id FROM sessions WHERE source_id=?","live-thread").length()==1,"followup uses same session");
  p.put("status","interrupted").put("execution_finished_at",4000);live(store,p);
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getString("source_status").equals("interrupted"),"cancel retained");
  p.put("status","completed").put("execution_finished_at",5000);live(store,p);
  check(store.require(store.getReadableDatabase(),"sessions","codex:live-thread").getString("source_status").equals("completed"),"complete metadata");
  store.getWritableDatabase().execSQL("INSERT INTO sync_hidden VALUES('session','live-thread')");
  check(live(store,p).optBoolean("hidden"),"deleted native session not resurrected");
  p.put("conversation_id","live-deleted-pending").remove("thread_id");store.getWritableDatabase().execSQL("INSERT INTO sync_hidden VALUES('session','live-deleted-pending')");
  check(live(store,p).optBoolean("hidden"),"deleted pending not resurrected");p.put("thread_id","live-deleted-native");check(live(store,p).optBoolean("hidden"),"pending deletion survives binding");
  check(store.rows(store.getReadableDatabase(),"SELECT source_id FROM sync_hidden WHERE kind='session' AND source_id=?","live-deleted-native").length()==1,"pending deletion blocks terminal mirror too");
  p.put("conversation_id","live-auto").put("thread_id","live-automation");store.getWritableDatabase().execSQL("INSERT INTO sync_hidden VALUES('automation','live-automation')");
  check(live(store,p).optBoolean("hidden"),"scheduled threads excluded");
  JSONObject early=new JSONObject().put("conversation_id","live-early-failure").put("request_id","early-request").put("text","Offline question").put("status","queued").put("submitted_at",6000);live(store,early);
  early.put("status","failed").put("execution_finished_at",7000);live(store,early);
  check(store.require(store.getReadableDatabase(),"sessions","assistant:live-early-failure").getString("source_status").equals("failed"),"startup failure remains visible without native id");
  early.put("status","queued").remove("execution_finished_at");live(store,early);
  check(store.require(store.getReadableDatabase(),"sessions","assistant:live-early-failure").getString("source_status").equals("failed"),"old queue replay cannot regress startup failure");
  early.put("request_id","retry-request").put("submitted_at",8000);live(store,early);
  check(store.require(store.getReadableDatabase(),"sessions","assistant:live-early-failure").getString("source_status").equals("queued"),"explicit retry advances same pending conversation");
  store.getWritableDatabase().delete("sessions","id=?",new String[]{"assistant:live-early-failure"});
  store.getWritableDatabase().delete("sessions","id=?",new String[]{"codex:live-thread"});
  return count;
 }
}
