package os.aios.core.workspace;
import android.database.sqlite.SQLiteDatabase;import org.json.*;
public class Run{
 static int n;static void check(boolean v,String name){if(!v)throw new AssertionError(name);n++;}
 static JSONObject prep(String w,boolean weekly)throws Exception{return new JSONObject().put("window",w).put("day_start",0).put("daily_limit",100).put("weekly",weekly);}
 static JSONObject output(JSONObject b,JSONArray changes)throws Exception{return new JSONObject().put("batch_id",b.getString("batch_id")).put("status","complete").put("changes",changes).put("summary","Reviewed").put("remaining_source_ids",new JSONArray());}
 static JSONObject change(String kind,String title,String source,JSONObject payload)throws Exception{return new JSONObject().put("kind",kind).put("action","create").put("title",title).put("source_ids",new JSONArray().put(source)).put("payload",payload);}
 static void source(SQLiteDatabase db,String id)throws Exception{db.execSQL("INSERT INTO dream_sources(id,session_id,body,created_at) VALUES(?,'s',?,?)",new Object[]{id,"{\"turn_status\":\"completed\",\"items\":[]}",System.currentTimeMillis()});}
 public static void main(String[] args)throws Exception{try(SQLiteDatabase db=new SQLiteDatabase(args[0])){
  WorkspaceStore s=new WorkspaceStore(db);
  db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,generation TEXT)");db.execSQL("INSERT INTO sessions VALUES('s','g')");
  db.execSQL("CREATE TABLE sync_items(id TEXT,role TEXT,kind TEXT,content_md TEXT,detail TEXT,session_id TEXT,generation TEXT,turn_id TEXT,sequence INTEGER)");
  db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY,title TEXT,status TEXT,revision INTEGER,updated_at INTEGER)");
  db.execSQL("INSERT INTO tasks VALUES('existing','Existing task','done',1,1)");
  db.execSQL("ALTER TABLE tasks ADD COLUMN content_md TEXT NOT NULL DEFAULT ''");
  db.execSQL("CREATE TABLE task_session(task_id TEXT,session_id TEXT,created_at INTEGER,PRIMARY KEY(task_id,session_id))");
  db.execSQL("CREATE TABLE memories(id TEXT,content TEXT,state TEXT,revision INTEGER,updated_at INTEGER)");db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT)");
  WorkspaceDream.migrate(db);
  WorkspaceDream.migrateTracking(db);
  db.execSQL("CREATE TABLE memory_sources(memory_id TEXT,session_id TEXT,message_id TEXT,PRIMARY KEY(memory_id,session_id,message_id))");
  check(s.dream("prepare",prep("empty",false)).optString("skip").equals("no_material"),"empty skips model");
  check(s.rows(db,"SELECT id FROM dream_runs").length()==0,"empty consumes no budget");
  source(db,"s:a");JSONObject b=s.dream("prepare",prep("first",false));
  check(s.dream("prepare",prep("concurrent",false)).optString("skip").equals("busy"),"single lease");
  JSONObject invalid=change("task","Bad","invented",new JSONObject());boolean failed=false;
  try{s.dream("commit",output(b,new JSONArray().put(change("task","Good","s:a",new JSONObject())).put(invalid)));}catch(Exception e){failed=true;}
  check(failed&&s.rows(db,"SELECT id FROM dream_changes").length()==0,"atomic rollback on invalid evidence");
  JSONObject good=change("task","Good","s:a",new JSONObject().put("content_md","Follow up"));
  s.dream("commit",output(b,new JSONArray().put(good)));
  check(s.rows(db,"SELECT id FROM tasks").length()==1,"proposal never executes or creates task");
  check(s.dream("commit",output(b,new JSONArray().put(good))).optBoolean("duplicate"),"idempotent commit");
  check(s.dream("prepare",prep("next",false)).optString("skip").equals("no_material"),"daily cursor");
  JSONObject week=s.dream("prepare",prep("weekly",true));check(week.getJSONArray("sources").length()==1,"weekly revisits daily evidence");
  s.dream("commit",output(week,new JSONArray().put(good)));check(s.rows(db,"SELECT id FROM dream_changes").length()==1,"weekly dedupe");
  String changeId=s.rows(db,"SELECT id FROM dream_changes").getJSONObject(0).getString("id");
  s.dream("resolve",new JSONObject().put("id",changeId).put("action","ignore"));
  source(db,"s:b");JSONObject next=s.dream("prepare",prep("more",false));
  JSONObject unknownSkill=change("skill","Unverified","s:b",new JSONObject().put("title","Unverified"));
  s.dream("commit",output(next,new JSONArray().put(unknownSkill)));check(s.call("skill.list",new JSONObject()).getJSONObject("data").getJSONArray("items").length()==0,"unsupported success never becomes skill");
  check(s.rows(db,"SELECT status FROM dream_changes WHERE kind='skill'").getJSONObject(0).getString("status").equals("unverified"),"unverified evidence retained");
  source(db,"s:c");JSONObject partial=s.dream("prepare",prep("partial",false));
  s.dream("commit",output(partial,new JSONArray()).put("status","partial").put("remaining_source_ids",new JSONArray().put("s:c")));
  check(s.rows(db,"SELECT id FROM dream_sources WHERE processed=0").length()==1,"partial checkpoint");
  JSONObject deleted=s.dream("prepare",prep("deleted",false));db.execSQL("DELETE FROM sessions WHERE id='s'");failed=false;
  try{s.dream("commit",output(deleted,new JSONArray()));}catch(Exception e){failed=true;}
  check(failed,"source deletion blocks stale commit");check(s.rows(db,"SELECT id FROM dream_changes WHERE status='source_deleted'").length()==2,"source deletion invalidates suggestions");
  check(s.rows(db,"SELECT id FROM tasks WHERE id='existing'").length()==1,"migration and deletion preserve tasks");

  db.execSQL("INSERT INTO sessions VALUES('s','g')");source(db,"s:budget");
  JSONObject unsubmitted=s.dream("prepare",prep("setup-failure",false));
  s.dream("checkpoint",new JSONObject().put("id",unsubmitted.getString("batch_id")).put("status","interrupted"));
  JSONObject retry=s.dream("prepare",prep("setup-failure",false));check(retry.has("batch_id"),"setup failure can retry without consuming model budget");
  check(!retry.getString("batch_id").equals(unsubmitted.getString("batch_id")),"retry has a fresh ledger identity rather than reclaiming a terminal failed node");
  check(retry.getJSONArray("sources").getJSONObject(0).getString("id").equals("s:budget"),"unsubmitted retry retains the original pending evidence");
  s.dream("checkpoint",new JSONObject().put("id",retry.getString("batch_id")).put("submitted",true));
  s.dream("checkpoint",new JSONObject().put("id",retry.getString("batch_id")).put("status","interrupted"));
  check(s.dream("prepare",prep("setup-failure",false)).optString("skip").equals("window_used"),"uncertain submission never repeats in same window");
  check(s.dream("prepare",prep("budget-limit",false).put("daily_limit",1)).optString("skip").equals("daily_budget"),"daily model budget includes interrupted requests");
  JSONObject skillBatch=s.dream("prepare",prep("learn",false));
  String evidence=s.recordSkillEvidence("ui.tree","test.app","2");
  JSONObject proofBody=new JSONObject().put("turn_status","completed").put("truncated",false).put("items",new JSONArray().put(new JSONObject().put("learning_evidence_id",evidence)));
  // Change the source before snapshot creation, not after it: close this preparation, then capture the real proof.
  s.dream("checkpoint",new JSONObject().put("id",skillBatch.getString("batch_id")).put("status","interrupted"));
  db.execSQL("UPDATE dream_sources SET body=? WHERE id='s:budget'",new Object[]{proofBody.toString()});
  skillBatch=s.dream("prepare",prep("learn",false));
  JSONObject skill=new JSONObject().put("title","Verified skill").put("description","Read actual result").put("content_md","Observe and verify").put("package","test.app").put("evidence_id",evidence).put("verification","Actual readback");
  s.dream("commit",output(skillBatch,new JSONArray().put(change("skill","Verified skill","s:budget",skill))));
  check(s.call("skill.list",new JSONObject()).getJSONObject("data").getInt("total")==1,"verified skill automatically publishes after committed evidence");
  String skillId=s.rows(db,"SELECT id FROM dream_changes WHERE title='Verified skill'").getJSONObject(0).getString("id");
  check(s.dream("resolve",new JSONObject().put("id",skillId).put("action","accept")).optBoolean("duplicate"),"already published acceptance is idempotent");
  check(s.call("skill.list",new JSONObject()).getJSONObject("data").getInt("total")==1,"duplicate acceptance preserves the single published asset");
  source(db,"s:tracking");JSONObject tracking=s.dream("prepare",prep("tracking",false));
  JSONObject proposal=change("task","Future study","s:tracking",new JSONObject().put("content_md","Plan autumn study").put("time_text","2026秋—2027秋").put("change_summary","用户计划继续学习")).put("topic_key","study-plan");
  s.dream("commit",output(tracking,new JSONArray().put(proposal)));
  JSONObject pending=s.rows(db,"SELECT * FROM dream_changes WHERE title='Future study'").getJSONObject(0);String pid=pending.getString("id");
  check(s.rows(db,"SELECT id FROM tasks WHERE title='Future study'").length()==0,"no task before acceptance");
  JSONObject accepted=s.dream("resolve",new JSONObject().put("id",pid).put("action","accept"));String taskId=accepted.getString("id");
  check(accepted.getString("time_text").equals("2026秋—2027秋")&&accepted.getString("due_date").isEmpty(),"rough time preserved without invented date");
  check(s.rows(db,"SELECT id FROM task_history WHERE task_id=?",taskId).length()==1,"accepted creation writes one history row");
  check(s.dream("resolve",new JSONObject().put("id",pid).put("action","accept")).optBoolean("duplicate"),"acceptance idempotent");
  check(s.rows(db,"SELECT * FROM task_session WHERE task_id=?",taskId).length()==1,"source session linked");
  source(db,"s:update");JSONObject updateBatch=s.dream("prepare",prep("update",false));
  check(updateBatch.getJSONArray("task_history").length()==1,"history supplied to review");
  JSONObject update=change("task","Future study","s:update",new JSONObject().put("content_md","Changed study plan").put("change_summary","改为长期学习").put("time_text","2026年中—长期")).put("action","update").put("target_id",taskId).put("expected_revision",1).put("topic_key","study-plan");
  s.dream("commit",output(updateBatch,new JSONArray().put(update)));
  String updateId=s.rows(db,"SELECT id FROM dream_changes WHERE status='pending' AND kind='task'").getJSONObject(0).getString("id");
  s.dream("resolve",new JSONObject().put("id",updateId).put("action","accept"));
  check(s.require(db,"tasks",taskId).getString("content_md").equals("Changed study plan")&&s.rows(db,"SELECT id FROM task_history WHERE task_id=?",taskId).length()==2,"update picture and append history atomically");
  source(db,"s:stale");JSONObject staleBatch=s.dream("prepare",prep("stale",false));update.put("expected_revision",2).put("source_ids",new JSONArray().put("s:stale"));s.dream("commit",output(staleBatch,new JSONArray().put(update)));
  String staleId=s.rows(db,"SELECT id FROM dream_changes WHERE status='pending' AND kind='task'").getJSONObject(0).getString("id");s.touch(db,"tasks",taskId);failed=false;
  try{s.dream("resolve",new JSONObject().put("id",staleId).put("action","accept"));}catch(Exception expected){failed=true;}
  check(failed&&s.rows(db,"SELECT id FROM task_history WHERE task_id=?",taskId).length()==2,"stale approval cannot overwrite or append history");
  s.dream("resolve",new JSONObject().put("id",staleId).put("action","ignore"));
  source(db,"s:repeat");JSONObject repeat=s.dream("prepare",prep("repeat",false));check(repeat.getJSONArray("prior_changes").toString().contains("study-plan"),"rejection details supplied");
  update.put("expected_revision",3).put("title","Another wording").put("source_ids",new JSONArray().put("s:repeat"));s.dream("commit",output(repeat,new JSONArray().put(update)));
  check(s.rows(db,"SELECT id FROM dream_changes WHERE title='Another wording'").length()==0,"same topic rejection suppresses new wording and revision");
  for(int i=0;i<65;i++)source(db,"s:bulk"+i);JSONObject bulk=s.dream("prepare",prep("bulk",false));check(bulk.getJSONArray("sources").length()==65,"one review includes all sources beyond old sixty limit");s.dream("commit",output(bulk,new JSONArray()));
  source(db,"s:new-evidence");db.execSQL("UPDATE dream_sources SET created_at=? WHERE id='s:new-evidence'",new Object[]{System.currentTimeMillis()+1000});JSONObject reconsider=s.dream("prepare",prep("reconsider",false));
  update.put("reconsiders",staleId).put("source_ids",new JSONArray().put("s:new-evidence"));failed=false;
  try{s.dream("commit",output(reconsider,new JSONArray().put(update)));}catch(Exception expected){failed=true;}check(failed,"reconsideration without explanation rolls back");
  update.put("reconsider_reason","用户明确重新要求记录学习计划");s.dream("commit",output(reconsider,new JSONArray().put(update)));check(s.rows(db,"SELECT id FROM dream_changes WHERE title='Another wording'").length()==1,"fresh evidence plus explanation permits explicit reconsideration");
  source(db,"s:memory-confirm");JSONObject mb=s.dream("prepare",prep("memory-confirm",false));s.dream("commit",output(mb,new JSONArray().put(change("memory","Language preference","s:memory-confirm",new JSONObject().put("content","Prefer Chinese")))));
  check(WorkspaceMemory.writes==0,"review never invokes memory write boundary");String mid=s.rows(db,"SELECT id FROM dream_changes WHERE title='Language preference'").getJSONObject(0).getString("id");s.dream("resolve",new JSONObject().put("id",mid).put("action","later"));check(WorkspaceMemory.writes==0,"deferral never writes memory");s.dream("resolve",new JSONObject().put("id",mid).put("action","accept"));check(WorkspaceMemory.writes==1,"explicit approval invokes memory create once");s.dream("resolve",new JSONObject().put("id",mid).put("action","accept"));check(WorkspaceMemory.writes==1,"memory acceptance idempotent");
  check(!s.dream("status",new JSONObject()).getJSONArray("changes").getJSONObject(0).has("payload"),"status uses compact metadata");check(s.dream("change",new JSONObject().put("id",mid)).has("payload"),"proposal detail loads separately");
  source(db,"s:capacity");db.execSQL("UPDATE tasks SET content_md=? WHERE id='existing'",new Object[]{"x".repeat(230000)});check(s.dream("prepare",prep("capacity",false)).optString("skip").equals("context_capacity"),"oversized necessary context stops without dropping rejection history");check(s.rows(db,"SELECT id FROM dream_sources WHERE processed=0").length()==1,"capacity failure preserves cursor");
  System.out.println("PASS: "+n+" production Dream ledger assertions with real SQLite; memory/task boundaries adapted");
 }}
}
