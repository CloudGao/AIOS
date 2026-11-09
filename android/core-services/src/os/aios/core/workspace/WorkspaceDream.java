package os.aios.core.workspace;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Host-only consolidation ledger. The model has no callable write route to this class. */
final class WorkspaceDream {
 static int bytes(String text){return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
 static final int INPUT_BYTES=220000;
 static void migrateTracking(SQLiteDatabase db){
  db.execSQL("ALTER TABLE tasks ADD COLUMN time_text TEXT NOT NULL DEFAULT ''");
  db.execSQL("ALTER TABLE tasks ADD COLUMN start_date TEXT NOT NULL DEFAULT ''");
  db.execSQL("ALTER TABLE tasks ADD COLUMN due_date TEXT NOT NULL DEFAULT ''");
  db.execSQL("CREATE TABLE task_history(id TEXT PRIMARY KEY,task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,before_json TEXT NOT NULL,after_json TEXT NOT NULL,summary TEXT NOT NULL,change_id TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX task_history_task ON task_history(task_id,created_at)");
 }
 static void timeFields(JSONObject p,ContentValues v)throws Exception{
  for(String key:new String[]{"time_text","start_date","due_date"})if(p.has(key)){
   String value=WorkspaceStore.text(p,key,key.equals("time_text")?300:10,true);
   if(!key.equals("time_text")&&!value.isEmpty())java.time.LocalDate.parse(value);
   v.put(key,value);
  }
 }
 static void history(WorkspaceStore store,SQLiteDatabase db,JSONObject before,JSONObject after,JSONObject p)throws Exception{
  String start=after.optString("start_date"),due=after.optString("due_date");if(!start.isEmpty()&&!due.isEmpty()&&java.time.LocalDate.parse(start).isAfter(java.time.LocalDate.parse(due)))throw new IllegalArgumentException("截止日期不能早于开始日期");
  boolean changed=before.length()==0;for(String key:new String[]{"title","content_md","status","time_text","start_date","due_date","parent_id","ai_summary","ai_progress"})if(!before.optString(key).equals(after.optString(key)))changed=true;if(!changed)return;
  ContentValues v=store.base(WorkspaceStore.id());v.put("task_id",after.getString("id"));v.put("before_json",before.toString());v.put("after_json",after.toString());
  v.put("summary",p.has("change_summary")?WorkspaceStore.text(p,"change_summary",1500,false):before.length()==0?"创建事项":"更新事项");
  v.put("change_id",p.optString("dream_change_id"));db.insertOrThrow("task_history",null,v);
 }
 final WorkspaceStore store; final SQLiteDatabase db;
 WorkspaceDream(WorkspaceStore s,SQLiteDatabase d){store=s;db=d;}
 static void migrate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE dream_sources(id TEXT PRIMARY KEY,session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,body TEXT NOT NULL,created_at INTEGER NOT NULL,processed INTEGER NOT NULL DEFAULT 0,weekly_processed INTEGER NOT NULL DEFAULT 0)");
  db.execSQL("CREATE TABLE dream_runs(id TEXT PRIMARY KEY,window TEXT NOT NULL UNIQUE,mode TEXT NOT NULL,status TEXT NOT NULL,submitted INTEGER NOT NULL DEFAULT 0,snapshot TEXT NOT NULL,thread_id TEXT NOT NULL DEFAULT '',turn_id TEXT NOT NULL DEFAULT '',summary TEXT NOT NULL DEFAULT '',created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE dream_changes(id TEXT PRIMARY KEY,run_id TEXT NOT NULL REFERENCES dream_runs(id),kind TEXT NOT NULL,title TEXT NOT NULL,payload TEXT NOT NULL,source_ids TEXT NOT NULL,status TEXT NOT NULL,receipt TEXT NOT NULL DEFAULT '{}',dedupe TEXT NOT NULL UNIQUE,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE dream_change_sources(change_id TEXT NOT NULL REFERENCES dream_changes(id) ON DELETE CASCADE,session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,PRIMARY KEY(change_id,session_id))");
  db.execSQL("CREATE TRIGGER dream_source_deleted BEFORE DELETE ON sessions BEGIN UPDATE dream_changes SET status='source_deleted',payload='{}' WHERE id IN (SELECT change_id FROM dream_change_sources WHERE session_id=OLD.id); UPDATE dream_runs SET status='interrupted',snapshot='{}',summary='来源已删除，停止本轮回顾' WHERE status='running'; END");

 }
 JSONArray rows(String sql,String... a)throws Exception{return store.rows(db,sql,a);}
 JSONObject call(String op,JSONObject p)throws Exception{
  switch(op){
   case "capture":{
    String session=p.getString("session_id");store.require(db,"sessions",session);
    JSONArray turns=p.getJSONArray("turns");
    for(int i=0;i<turns.length();i++){
     JSONObject turn=turns.getJSONObject(i);String status=turn.optString("status");
     if(!Set.of("completed","failed","interrupted").contains(status))continue;
     String id=session+":"+turn.getString("id");
     if(rows("SELECT id FROM dream_sources WHERE id=?",id).length()>0)continue;
     long time=Math.round(1000*turn.optDouble("startedAt",System.currentTimeMillis()/1000.0));
     if(time<WorkspaceStore.now()-14*86400000L)continue;
     JSONArray items=rows("SELECT id,role,kind,content_md,detail FROM sync_items WHERE session_id=? AND generation=(SELECT generation FROM sessions WHERE id=?) AND turn_id=? ORDER BY sequence",session,session,turn.getString("id"));
     if(items.length()==0)continue;
     JSONObject body=new JSONObject().put("turn_status",status);JSONArray compact=new JSONArray();int size=0;boolean truncated=false;
     for(int j=0;j<items.length();j++){
      JSONObject item=items.getJSONObject(j);
      if(size+bytes(item.toString())>120000){truncated=true;continue;}
      compact.put(item);size+=bytes(item.toString());
     }
     body.put("items",compact).put("truncated",truncated);
     ContentValues v=store.base(id);v.put("created_at",time);v.put("session_id",session);v.put("body",body.toString());db.insertOrThrow("dream_sources",null,v);
    }
    // Bound copied evidence, not the original conversations. Report discarded pending evidence.
    int lost=rows("SELECT id FROM dream_sources WHERE processed=0 AND (created_at<? OR id NOT IN (SELECT id FROM dream_sources ORDER BY created_at DESC,id LIMIT 2500))",Long.toString(WorkspaceStore.now()-14*86400000L)).length();
    db.delete("dream_sources","created_at<?",new String[]{Long.toString(WorkspaceStore.now()-14*86400000L)});
    db.execSQL("DELETE FROM dream_sources WHERE id NOT IN (SELECT id FROM dream_sources ORDER BY created_at DESC,id LIMIT 2500)");
    if(lost>0)db.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES('dream_evidence_gap',?)",new Object[]{Integer.toString(lost)});
    return new JSONObject().put("captured",true);
   }
   case "status":return new JSONObject().put("runs",rows("SELECT id,mode,status,summary,created_at,updated_at FROM dream_runs ORDER BY created_at DESC LIMIT 10"))
     .put("proposals",rows("SELECT id,kind,title,status FROM dream_changes WHERE status IN ('pending','deferred') ORDER BY created_at,id"))
     .put("pending",rows("SELECT count(*) n FROM dream_sources WHERE processed=0").getJSONObject(0).getLong("n"))
     .put("changes",rows("SELECT id,kind,title,status,created_at FROM dream_changes ORDER BY created_at DESC LIMIT 100"))
     .put("gaps",rows("SELECT value FROM settings WHERE key='dream_evidence_gap'"));
   case "prepare":{
    // A process death never replays an uncertain model submission. The next window may retry evidence.
    db.execSQL("UPDATE dream_runs SET status='interrupted',snapshot='{}',summary='运行中断，证据保留；未重复提交' WHERE status='running' AND updated_at<?",new Object[]{WorkspaceStore.now()-10*60000L});
    db.execSQL("UPDATE dream_runs SET window=window||':'||id WHERE status='interrupted' AND submitted=0 AND instr(window,id)=0");
    if(rows("SELECT id FROM dream_runs WHERE status='running'").length()>0)return new JSONObject().put("skip","busy");
    String window=p.getString("window");
    if(rows("SELECT id FROM dream_runs WHERE window=?",window).length()>0)return new JSONObject().put("skip","window_used");
    long day=p.getLong("day_start");int max=p.getInt("daily_limit");boolean manual=p.optBoolean("manual");
    if(!manual&&rows("SELECT id FROM dream_runs WHERE submitted=1 AND mode<>'manual' AND created_at>=?",Long.toString(day)).length()>=max)return new JSONObject().put("skip","daily_budget");
    boolean weekly=p.optBoolean("weekly");String mode=manual?"manual":weekly?"weekly":"daily";
    String pending=weekly?"weekly_processed=0 AND created_at>="+(WorkspaceStore.now()-7*86400000L):"processed=0";
    JSONArray available=rows("SELECT id,session_id,body,created_at FROM dream_sources WHERE "+pending+" ORDER BY created_at,id");
    if(available.length()==0)return new JSONObject().put("skip","no_material");
    JSONArray sources=new JSONArray();
    JSONObject snapshot=new JSONObject().put("sources",sources).put("mode",mode);
    // Current pictures and decisions are required context; never silently trim refusals.
    snapshot.put("skills",new WorkspaceSkills(store,db).call("skill.list",new JSONObject().put("limit",40)));
    snapshot.put("tasks",rows("SELECT * FROM tasks ORDER BY updated_at DESC,id"));
    for(int i=0;i<snapshot.getJSONArray("tasks").length();i++)snapshot.getJSONArray("tasks").getJSONObject(i).remove("ai_payload");
    snapshot.put("task_history",rows("SELECT id,task_id,summary,change_id,created_at FROM task_history ORDER BY created_at DESC,id LIMIT 100"));
    snapshot.put("memories",rows("SELECT * FROM memories WHERE state<>'deleted' ORDER BY updated_at DESC"));
    snapshot.put("prior_changes",rows("SELECT id,kind,title,payload,source_ids,status,receipt FROM dream_changes WHERE status IN ('rejected','pending','deferred','accepted') ORDER BY created_at DESC"));
    // The current picture is already above. Do not resend historical full snapshots in every receipt.
    JSONArray decisions=snapshot.getJSONArray("prior_changes");for(int i=0;i<decisions.length();i++){
     JSONObject decision=decisions.getJSONObject(i),payload=new JSONObject(decision.getString("payload")),receipt=new JSONObject(decision.getString("receipt")),compact=new JSONObject();payload.remove("before");
     for(String key:new String[]{"id","rejected_at","reason"})if(receipt.has(key))compact.put(key,receipt.get(key));
     decision.put("payload",payload).put("receipt",compact);
    }
    if(weekly)snapshot.put("daily_index",rows("SELECT id,summary FROM dream_runs WHERE status='completed' AND created_at>=? ORDER BY created_at DESC LIMIT 14",Long.toString(WorkspaceStore.now()-7*86400000L)));
    // Retain original evidence first; trim metadata rather than repeatedly failing an oversized batch.
    int size=bytes(snapshot.toString());
    for(int i=0;i<available.length();i++){JSONObject source=available.getJSONObject(i);int n=bytes(source.toString())+1;if(size+n>INPUT_BYTES-2000)break;sources.put(source);size+=n;}
    if(sources.length()==0)return new JSONObject().put("skip","context_capacity");
    snapshot.put("context_is_bounded",true).put("remaining_source_count",available.length()-sources.length()).put("input_budget_bytes",INPUT_BYTES);
    String id=WorkspaceStore.id();snapshot.put("batch_id",id);
    ContentValues v=store.base(id);v.put("window",window);v.put("mode",mode);v.put("status","running");v.put("snapshot",snapshot.toString());v.put("updated_at",WorkspaceStore.now());db.insertOrThrow("dream_runs",null,v);
    return snapshot;
   }
   case "checkpoint":{
    JSONObject run=store.require(db,"dream_runs",p.getString("id"));if(!run.getString("status").equals("running"))throw new IllegalStateException("Run closed");
    ContentValues v=new ContentValues();if(p.optBoolean("submitted"))v.put("submitted",1);for(String key:new String[]{"thread_id","turn_id","status","summary"})if(p.has(key))v.put(key,WorkspaceStore.text(p,key,key.equals("summary")?1500:160,true));if(p.has("status")&&!p.optString("status").equals("running"))v.put("snapshot","{}");v.put("updated_at",WorkspaceStore.now());db.update("dream_runs",v,"id=?",new String[]{p.getString("id")});return new JSONObject().put("saved",true);
   }
   case "commit":return commit(p);
   case "change":return store.require(db,"dream_changes",p.getString("id"));
   case "resolve":return resolve(p);
   case "suggestion":{JSONObject c=store.require(db,"dream_changes",p.getString("id"));if(!Set.of("pending","deferred").contains(c.optString("status")))throw new IllegalStateException("建议已变更或来源已删除，请重新打开");return c;}
   default:throw new IllegalArgumentException("Unknown host review action");
  }
 }
 JSONObject commit(JSONObject p)throws Exception{
  String id=p.getString("batch_id");JSONObject run=store.require(db,"dream_runs",id);
  if(run.getString("status").equals("completed"))return new JSONObject().put("duplicate",true);
  if(!run.getString("status").equals("running"))throw new IllegalStateException("Run closed");
  JSONObject snapshot=new JSONObject(run.getString("snapshot"));JSONArray sources=snapshot.getJSONArray("sources");Map<String,JSONObject> allowed=new HashMap<>();
  for(int i=0;i<sources.length();i++){
   JSONObject s=sources.getJSONObject(i);JSONArray current=rows("SELECT body FROM dream_sources WHERE id=?",s.getString("id"));
   if(current.length()==0||!current.getJSONObject(0).getString("body").equals(s.getString("body")))throw new IllegalStateException("Source deleted or changed");
   allowed.put(s.getString("id"),s);
  }
  String state=p.getString("status");if(!Set.of("complete","partial").contains(state))throw new IllegalArgumentException("Review incomplete");
  JSONArray remaining=p.optJSONArray("remaining_source_ids");if(remaining==null)remaining=new JSONArray();Set<String> left=new HashSet<>();for(int i=0;i<remaining.length();i++){String x=remaining.getString(i);if(!allowed.containsKey(x))throw new IllegalArgumentException("Unknown source");left.add(x);}
  if(state.equals("complete")&&!left.isEmpty()||state.equals("partial")&&left.isEmpty())throw new IllegalArgumentException("Invalid partial cursor");
  JSONArray changes=p.getJSONArray("changes");if(changes.length()>12)throw new IllegalArgumentException("Too many changes");
  int proposed=0;
  for(int i=0;i<changes.length();i++){
   JSONObject c=changes.getJSONObject(i);String kind=c.getString("kind"),action=c.getString("action");
   if(!Set.of("skill","memory","task","schedule").contains(kind)||!Set.of("create","propose","update").contains(action))throw new IllegalArgumentException("Invalid change");
   JSONArray refs=c.getJSONArray("source_ids");if(refs.length()==0)throw new IllegalArgumentException("Missing evidence");
   for(int j=0;j<refs.length();j++)if(!allowed.containsKey(refs.getString(j))||left.contains(refs.getString(j)))throw new IllegalArgumentException("Unreviewed source");
   String title=WorkspaceStore.text(c,"title",160,false);JSONObject payload=c.getJSONObject("payload");payload.remove("before");
   if(!action.equals("update")&&(payload.has("target_id")||payload.has("expected_revision")))throw new IllegalArgumentException("Updates require a top-level target and revision");
   if(kind.equals("task")){ContentValues checked=new ContentValues();timeFields(payload,checked);if(payload.has("status")&&!Set.of("note","todo","doing","done").contains(payload.getString("status")))throw new IllegalArgumentException("Invalid task status");}
   if(payload.toString().length()>24000)throw new IllegalArgumentException("Oversized result");
   String topic=kind+":"+WorkspaceSkills.normalize(c.optString("topic_key",title));
   payload.put("topic_key",topic).put("reason",c.optString("reason"));
   String reconsider=c.optString("reconsiders");long reconsideredAt=-1;
   if(!reconsider.isEmpty()){
    JSONObject old=store.require(db,"dream_changes",reconsider);boolean fresh=false;
    long rejectedAt=new JSONObject(old.getString("receipt")).optLong("rejected_at",old.getLong("created_at"));
    reconsideredAt=rejectedAt;
    for(int j=0;j<refs.length();j++)if(allowed.get(refs.getString(j)).getLong("created_at")>rejectedAt&&!new JSONArray(old.getString("source_ids")).toString().contains("\""+refs.getString(j)+"\""))fresh=true;
    if(!old.optString("status").equals("rejected")||!old.getString("kind").equals(kind)||!fresh||c.optString("reconsider_reason").trim().isEmpty())throw new IllegalArgumentException("Reconsideration requires a rejection, new evidence and explanation");
    payload.put("reconsiders",reconsider).put("reconsider_reason",WorkspaceStore.text(c,"reconsider_reason",1500,false));
   }
   String dedupe=topic+(action.equals("update")?":update:"+c.getString("target_id")+":"+c.getLong("expected_revision"):"")+(reconsider.isEmpty()?"":":reconsider:"+reconsider);
   if(rows("SELECT id FROM dream_changes WHERE dedupe=?",dedupe).length()>0)continue;
   boolean rejected=false;JSONArray denials=rows("SELECT id,title,payload,receipt,created_at FROM dream_changes WHERE kind=? AND status='rejected'",kind);
   for(int j=0;j<denials.length();j++){JSONObject old=denials.getJSONObject(j),body=new JSONObject(old.getString("payload"));long rejectedAt=new JSONObject(old.getString("receipt")).optLong("rejected_at",old.getLong("created_at"));if(rejectedAt>reconsideredAt&&topic.equals(body.optString("topic_key",kind+":"+WorkspaceSkills.normalize(old.getString("title")))))rejected=true;}
   if(rejected)continue;
   JSONObject receipt=new JSONObject();String status="pending";
   if(action.equals("update")){
    // Never overwrite a manual edit or infer completion. Updates are reviewable proposals.
    payload.put("target_id",c.getString("target_id")).put("expected_revision",c.getLong("expected_revision"));
    if(kind.equals("task")||kind.equals("memory")){JSONObject before=store.require(db,kind.equals("task")?"tasks":"memories",c.getString("target_id"));store.revision(before,payload);payload.put("before",before);}
   }else if(kind.equals("memory")){
    JSONObject source=allowed.get(refs.getString(0));payload.put("source_session_id",source.getString("session_id"));payload.remove("source_message_id");
    // Review proposals never use memory_auto_save. Only an explicit acceptance writes memory.
   }
   if(kind.equals("skill")){
    if(!payload.has("title"))payload.put("title",title);
    String evidence=payload.optString("evidence_id");boolean valid=false;
    if(!evidence.isEmpty())for(int j=0;j<refs.length();j++){
     JSONObject body=new JSONObject(allowed.get(refs.getString(j)).getString("body"));
     if(body.optString("turn_status").equals("completed")&&!body.optBoolean("truncated")&&body.toString().contains(evidence))valid=true;
    }
    if(valid){
     JSONArray proof=rows("SELECT created_at,package,app_version FROM skill_evidence WHERE id=?",evidence);
     if(proof.length()==0||WorkspaceStore.now()-proof.getJSONObject(0).getLong("created_at")>14*86400000L)valid=false;
     else {JSONObject proofRow=proof.getJSONObject(0);String pkg=payload.optString("package");if((!proofRow.optString("package").isEmpty()&&!proofRow.optString("package").equals(pkg))||(!pkg.isEmpty()&&!store.skillAppVersion(pkg).equals(proofRow.optString("app_version"))))valid=false;}
    }
    payload.put("needs_verification",!valid);status=valid?"publish_pending":"unverified";
   }
   ContentValues v=store.base(WorkspaceStore.id());v.put("run_id",id);v.put("kind",kind);v.put("title",title);v.put("payload",payload.toString());v.put("source_ids",refs.toString());v.put("status",status);v.put("receipt",receipt.toString());v.put("dedupe",dedupe);db.insertOrThrow("dream_changes",null,v);
   for(int j=0;j<refs.length();j++)db.execSQL("INSERT OR IGNORE INTO dream_change_sources VALUES(?,?)",new Object[]{v.getAsString("id"),allowed.get(refs.getString(j)).getString("session_id")});
   proposed++;
  }
  for(String source:allowed.keySet())if(!left.contains(source))db.execSQL("UPDATE dream_sources SET processed=1"+(run.getString("mode").equals("weekly")?",weekly_processed=1":"")+" WHERE id=?",new Object[]{source});
  ContentValues v=new ContentValues();v.put("status","completed");v.put("summary",WorkspaceStore.text(p,"summary",1500,true));v.put("updated_at",WorkspaceStore.now());v.put("snapshot","{}");db.update("dream_runs",v,"id=?",new String[]{id});return new JSONObject().put("saved",true).put("proposed",proposed).put("remaining",left.size());
 }
 JSONObject resolve(JSONObject p)throws Exception{
  JSONObject c=store.require(db,"dream_changes",p.getString("id"));
  if(c.optString("status").equals("accepted")&&p.optString("action").equals("accept"))return new JSONObject(c.getString("receipt")).put("status","accepted").put("duplicate",true);
  if(c.optString("status").equals("rejected")&&p.optString("action").equals("ignore"))return new JSONObject().put("status","rejected").put("duplicate",true);
  if(!Set.of("pending","deferred").contains(c.getString("status")))throw new IllegalStateException("Suggestion already resolved");
  String action=p.getString("action");JSONObject payload=new JSONObject(c.getString("payload")),receipt=new JSONObject();String status;
  if(action.equals("ignore")){status="rejected";receipt.put("rejected_at",WorkspaceStore.now());if(p.has("reason"))receipt.put("reason",WorkspaceStore.text(p,"reason",1500,true));}
  else if(action.equals("later"))status="deferred";
  else if(action.equals("accept")&&(c.getString("kind").equals("task")||c.getString("kind").equals("memory"))){
   String kind=c.getString("kind");boolean update=payload.has("target_id");JSONObject values=new JSONObject();
   if(update){JSONObject before=store.require(db,kind.equals("task")?"tasks":"memories",payload.getString("target_id"));store.revision(before,payload);values.put("id",before.getString("id")).put("expected_revision",before.getLong("revision"));if(kind.equals("memory")&&!before.optString("state").equals("active"))throw new IllegalStateException("记忆状态已变化，请重新核对");}
   String[] fields=kind.equals("task")?new String[]{"content_md","status","time_text","start_date","due_date","change_summary"}:new String[]{"content","kind","scope_type","scope_id","valid_until"};
   for(String field:fields)if(payload.has(field))values.put(field,payload.get(field));
   JSONArray sessions=rows("SELECT session_id FROM dream_change_sources WHERE change_id=?",c.getString("id"));if(sessions.length()==0)throw new IllegalStateException("来源已删除");
   if(kind.equals("task")){values.put("title",c.getString("title")).put("dream_change_id",c.getString("id"));if(!update&&payload.has("parent_id"))values.put("parent_id",payload.get("parent_id"));}
   else if(!update)values.put("source_session_id",sessions.getJSONObject(0).getString("session_id"));
   receipt=kind.equals("task")?store.execute(db,"task."+(update?"update":"create"),values):new WorkspaceMemory(store,db).call("memory."+(update?"update":"create"),values);
   for(int i=0;i<sessions.length();i++)if(kind.equals("task"))db.execSQL("INSERT OR IGNORE INTO task_session(task_id,session_id,created_at) VALUES(?,?,?)",new Object[]{receipt.getString("id"),sessions.getJSONObject(i).getString("session_id"),WorkspaceStore.now()});
   else db.execSQL("INSERT OR IGNORE INTO memory_sources(memory_id,session_id,message_id) VALUES(?,?,'')",new Object[]{receipt.getString("id"),sessions.getJSONObject(i).getString("session_id")});
   status="accepted";
  }else if(action.equals("accept")&&c.getString("kind").equals("skill")&&!payload.has("target_id")&&!payload.optBoolean("needs_verification",true)){
   JSONObject evidence=store.require(db,"skill_evidence",payload.getString("evidence_id"));String pkg=payload.optString("package");if(!pkg.isEmpty()&&!store.skillAppVersion(pkg).equals(evidence.optString("app_version")))throw new IllegalStateException("应用版本已变化，需要重新验证");
   receipt=new WorkspaceSkills(store,db).learnDream(payload);status=receipt.has("ignored")?"suppressed":"accepted";
  }else throw new IllegalArgumentException("Open existing editor to review this suggestion");
  ContentValues v=new ContentValues();v.put("status",status);v.put("receipt",receipt.toString());db.update("dream_changes",v,"id=?",new String[]{c.getString("id")});return receipt.put("status",status);
 }
 void publishPending()throws Exception{
  JSONArray pending=rows("SELECT id,payload FROM dream_changes WHERE kind='skill' AND status IN ('publish_pending','publish_retry','pending','deferred') ORDER BY created_at LIMIT 12");
  for(int i=0;i<pending.length();i++){
   JSONObject item=pending.getJSONObject(i),payload=new JSONObject(item.getString("payload"));String id=item.getString("id");
   if(payload.optBoolean("needs_verification",true))continue;
   if(rows("SELECT session_id FROM dream_change_sources WHERE change_id=?",id).length()==0)continue;
   try{
    if(payload.has("target_id"))payload.put("id",payload.getString("target_id"));
    JSONObject result=new WorkspaceSkills(store,db).learnDream(payload);
    db.execSQL("UPDATE dream_changes SET status=?,receipt=? WHERE id=? AND status<>'source_deleted'",new Object[]{result.has("ignored")?"suppressed":"accepted",result.toString(),id});
   }catch(Exception e){JSONObject failure=new JSONObject().put("error",String.valueOf(e.getMessage())).put("recoverable",true);db.execSQL("UPDATE dream_changes SET status='publish_retry',receipt=? WHERE id=? AND status<>'source_deleted'",new Object[]{failure.toString(),id});}
  }
 }

}
