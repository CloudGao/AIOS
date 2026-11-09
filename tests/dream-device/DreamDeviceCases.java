package os.aios.core.workspace;
import android.database.sqlite.SQLiteDatabase;import org.json.*;
public class DreamDeviceCases{
 static void clearFixture(java.io.File f){if(f.isDirectory()){java.io.File[] children=f.listFiles();if(children!=null)for(java.io.File child:children)clearFixture(child);}if(f.exists()&&!f.delete())throw new IllegalStateException("Cannot clear isolated Dream fixture");}
 static int n;static void check(boolean v,String name){if(!v)throw new AssertionError(name);n++;}
 static JSONObject prep(String w,boolean weekly)throws Exception{return new JSONObject().put("window",w).put("day_start",0).put("daily_limit",100).put("weekly",weekly);}
 static JSONObject output(JSONObject b,JSONArray changes)throws Exception{return new JSONObject().put("batch_id",b.getString("batch_id")).put("status","complete").put("changes",changes).put("summary","Reviewed").put("remaining_source_ids",new JSONArray());}
 static JSONObject change(String kind,String title,String source,JSONObject payload)throws Exception{return new JSONObject().put("kind",kind).put("action","create").put("title",title).put("source_ids",new JSONArray().put(source)).put("payload",payload);}
 static void source(SQLiteDatabase db,String id)throws Exception{db.execSQL("INSERT INTO dream_sources(id,session_id,body,created_at) VALUES(?,'s',?,?)",new Object[]{id,"{\"turn_status\":\"completed\",\"items\":[]}",System.currentTimeMillis()});}
 public static int run(android.content.Context context)throws Exception{
 n=0;context.deleteDatabase("dream_acceptance_only.db");clearFixture(new java.io.File(context.getFilesDir(),"skills-tests/dream_acceptance_only.db"));
 try(WorkspaceStore s=new WorkspaceStore(context,"dream_acceptance_only.db")){SQLiteDatabase db=s.getWritableDatabase();
  db.execSQL("INSERT INTO sessions(id,title,generation,created_at,updated_at) VALUES('s','Acceptance only','g',1,1)");
  db.execSQL("INSERT INTO tasks(id,title,status,revision,created_at,updated_at) VALUES('existing','Existing task','done',1,1,1)");
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

  db.execSQL("INSERT INTO sessions(id,title,generation,created_at,updated_at) VALUES('s','Acceptance only','g',1,1)");source(db,"s:budget");
  JSONObject unsubmitted=s.dream("prepare",prep("setup-failure",false));
  s.dream("checkpoint",new JSONObject().put("id",unsubmitted.getString("batch_id")).put("status","interrupted"));
  JSONObject retry=s.dream("prepare",prep("setup-failure",false));check(retry.has("batch_id"),"setup failure can retry without consuming model budget");
  s.dream("checkpoint",new JSONObject().put("id",retry.getString("batch_id")).put("submitted",true));
  s.dream("checkpoint",new JSONObject().put("id",retry.getString("batch_id")).put("status","interrupted"));
  check(s.dream("prepare",prep("setup-failure",false)).optString("skip").equals("window_used"),"uncertain submission never repeats in same window");
  check(s.dream("prepare",prep("budget-limit",false).put("daily_limit",1)).optString("skip").equals("daily_budget"),"daily model budget includes interrupted requests");
  JSONObject skillBatch=s.dream("prepare",prep("learn",false));
  String evidence=s.recordSkillEvidence("ui.tree","os.aios.agent",s.skillAppVersion("os.aios.agent"));
  JSONObject proofBody=new JSONObject().put("turn_status","completed").put("truncated",false).put("items",new JSONArray().put(new JSONObject().put("learning_evidence_id",evidence)));
  // Change the source before snapshot creation, not after it: close this preparation, then capture the real proof.
  s.dream("checkpoint",new JSONObject().put("id",skillBatch.getString("batch_id")).put("status","interrupted"));
  db.execSQL("UPDATE dream_sources SET body=? WHERE id='s:budget'",new Object[]{proofBody.toString()});
  skillBatch=s.dream("prepare",prep("learn",false));
  JSONObject skill=new JSONObject().put("title","Verified skill").put("description","Read actual result").put("content_md","Observe and verify").put("package","os.aios.agent").put("evidence_id",evidence).put("verification","Actual readback");
  s.dream("commit",output(skillBatch,new JSONArray().put(change("skill","Verified skill","s:budget",skill))));
  JSONArray learned=s.call("skill.list",new JSONObject().put("query","Verified skill")).getJSONObject("data").getJSONArray("items");
  check(learned.length()==1&&learned.getJSONObject(0).getString("source").equals("learned"),"verified skill automatically published in file store");
  source(db,"s:memory");
  JSONObject facts=new JSONObject().put("turn_status","completed").put("items",new JSONArray().put(new JSONObject().put("role","user").put("content_md","验收事实一：偏好简短列表。验收事实二：偏好中文回复。")));
  db.execSQL("UPDATE dream_sources SET body=? WHERE id='s:memory'",new Object[]{facts.toString()});
  JSONObject memoryBatch=s.dream("prepare",prep("memory",false));
  JSONObject memory=new JSONObject().put("kind","preference").put("content","偏好简短列表").put("explicit_quote","偏好简短列表");
  s.dream("commit",output(memoryBatch,new JSONArray().put(change("memory","Acceptance memory","s:memory",memory)).put(change("schedule","Acceptance schedule","s:memory",new JSONObject().put("intent","Read battery").put("timezone","Asia/Shanghai").put("recurrence","每天晚上")))));
  check(s.rows(db,"SELECT id FROM memories").length()==0,"review creates no memory before confirmation");
  String memoryId=s.rows(db,"SELECT id FROM dream_changes WHERE title='Acceptance memory'").getJSONObject(0).getString("id");
  s.dream("resolve",new JSONObject().put("id",memoryId).put("action","accept"));
  check(s.rows(db,"SELECT id FROM memories WHERE state='active'").length()==1,"confirmed memory active immediately");
  check(s.rows(db,"SELECT id FROM dream_changes WHERE kind='schedule' AND status='pending'").length()==1,"schedule is suggestion only");
  s.call("settings.set",new JSONObject().put("key","memory_auto_save").put("value","true"));source(db,"s:memory2");db.execSQL("UPDATE dream_sources SET body=? WHERE id='s:memory2'",new Object[]{facts.toString()});
  JSONObject automatic=s.dream("prepare",prep("memory-auto",false));memory.put("content","偏好中文回复").put("explicit_quote","偏好中文回复");
  s.dream("commit",output(automatic,new JSONArray().put(change("memory","Acceptance memory two","s:memory2",memory))));
  check(s.rows(db,"SELECT id FROM memories WHERE state='active'").length()==1,"autosave preference cannot bypass Dream confirmation");
  String denied=s.rows(db,"SELECT id FROM dream_changes WHERE title='Acceptance memory two'").getJSONObject(0).getString("id");
  s.dream("resolve",new JSONObject().put("id",denied).put("action","ignore"));
  check(s.rows(db,"SELECT id FROM memories").length()==1,"reject does not create candidate or active memory");
  JSONObject tracked=s.call("task.create",new JSONObject().put("title","History acceptance").put("time_text","2026年中—长期")).getJSONObject("data");String trackedId=tracked.getString("id");
  s.call("task.update",new JSONObject().put("id",trackedId).put("expected_revision",tracked.getLong("revision")).put("content_md","Current picture").put("change_summary","更新全貌"));
  JSONArray history=s.call("task.history.list",new JSONObject().put("id",trackedId)).getJSONObject("data").getJSONArray("items");check(history.length()==2&&!history.getJSONObject(0).has("before_json"),"history lists summaries without large IPC payload");
  JSONObject historical=s.call("task.history.get",new JSONObject().put("id",trackedId).put("history_id",history.getJSONObject(0).getString("id"))).getJSONObject("data");check(historical.has("before_json")&&historical.has("after_json"),"history detail read separately");
  failed=false;try{s.call("task.create",new JSONObject().put("title","Invalid dates").put("start_date","2027-01-01").put("due_date","2026-01-01"));}catch(Exception expected){failed=true;}check(failed&&s.rows(db,"SELECT id FROM tasks WHERE title='Invalid dates'").length()==0,"invalid date order rolls back task and history");
  return n;
 }finally{context.deleteDatabase("dream_acceptance_only.db");clearFixture(new java.io.File(context.getFilesDir(),"skills-tests/dream_acceptance_only.db"));}}
}
