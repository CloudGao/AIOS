package os.aios.core.workspace;

import android.app.Instrumentation;
import os.aios.workspace.MarkdownView;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs against Android SQLite, independently of the user's workspace database. */
public final class WorkspaceTestRunner extends Instrumentation {
    private WorkspaceStore store;
    private int assertions;
    private final String database="workspace-instrumentation-test.db";
    interface Throwing {void run() throws Exception;}
    private JSONObject p(Object... values)throws Exception{JSONObject p=new JSONObject();for(int i=0;i<values.length;i+=2)p.put((String)values[i],values[i+1]==null?JSONObject.NULL:values[i+1]);return p;}
    private JSONObject call(String op,Object... values)throws Exception{return store.call(op,p(values)).getJSONObject("data");}
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);assertions++;}
    private void rejected(Throwing fn,String label)throws Exception{try{fn.run();}catch(IllegalArgumentException|IllegalStateException expected){assertions++;return;}throw new AssertionError(label);}
    private String task(String title,String parent)throws Exception{return call("task.create","title",title,"parent_id",parent).getString("id");}
    private String session(String title)throws Exception{return call("session.create","title",title).getString("id");}
    private long revision(String type,String id)throws Exception{return call(type+".get","id",id).getLong("revision");}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();try{
        getTargetContext().deleteDatabase(database);getTargetContext().deleteDatabase("context-test-"+database);delete(new java.io.File(getTargetContext().getFilesDir(),"skills-tests/"+database));store=new WorkspaceStore(getTargetContext(),database);runCases();out.putString("stream","PASS: "+assertions+" Android SQLite/Markdown assertions\n");finish(-1,out);
    }catch(Throwable e){out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,out);}finally{if(store!=null)store.close();getTargetContext().deleteDatabase(database);}}
    private void runCases()throws Exception{
        assertions+=LiveSessionTests.run(store);
        assertions+=WorkspaceSkillTests.run(store);
        assertions+=LegacySkillMigrationTests.run(getTargetContext());
        assertions+=DreamDeviceCases.run(getTargetContext());
        assertions+=CoreRuntimeContractTests.run(store);
        String root=task("项目 🧠",null),child=task("子任务",root),leaf=task("第三层",child),other=task("另一个项目",null);
        String s1=session("第一次讨论"),s2=session("第二次讨论");
        call("relation.link","task_id",root,"session_id",s1);call("relation.link","task_id",root,"session_id",s2);call("relation.link","task_id",other,"session_id",s1);call("relation.link","task_id",root,"session_id",s1);
        check(call("task.get","id",root).getJSONArray("sessions").length()==2,"one task to many sessions, duplicate links ignored");
        check(call("session.get","id",s1).getJSONArray("tasks").length()==2,"one session to many tasks");
        check(call("session.list").getJSONArray("items").length()==2,"session list returns fixtures");
        call("session.update","id",s1,"expected_revision",revision("session",s1),"title","更新会话标题");
        check(call("session.get","id",s1).getString("title").equals("更新会话标题"),"session update readback");
        call("relation.unlink","task_id",other,"session_id",s1);
        check(call("session.get","id",s1).getJSONArray("tasks").length()==1,"unlink only specified relation");
        call("relation.link","task_id",other,"session_id",s1);
        rejected(()->call("relation.link","task_id","missing","session_id",s1),"FK rejects missing task");
        rejected(()->call("task.move","id",root,"parent_id",leaf,"position",0,"expected_revision",revision("task",root)),"cycle rejected");
        rejected(()->call("task.move","id",root,"parent_id",root,"position",0,"expected_revision",revision("task",root)),"self parent rejected");
        check(call("task.get","id",root).isNull("parent_id"),"failed move rolled back");
        call("task.move","id",child,"parent_id",other,"position",0,"expected_revision",revision("task",child));
        check(call("task.get","id",child).getString("parent_id").equals(other),"move persists parent");
        check(call("task.get","id",leaf).getString("parent_id").equals(child),"move preserves descendants");
        String markdown="# 计划\n- [ ] 完成设计\n- [x] 已完成\n[配置](aios://settings)\n**粗体** `代码`\n";
        long before=revision("task",root);call("task.update","id",root,"expected_revision",before,"content_md",markdown);
        rejected(()->call("task.update","id",root,"expected_revision",before,"content_md","stale"),"stale write rejected");
        check(call("task.get","id",root).getString("content_md").equals(markdown),"unicode and markdown preserved");
        check(MarkdownView.toggleLine(markdown,1).equals(markdown.replace("- [ ] 完成设计","- [x] 完成设计")),"toggle only target checkbox");
        check(MarkdownView.toggleLine(MarkdownView.toggleLine(markdown,1),1).equals(markdown),"checkbox reversible");
        rejected(()->MarkdownView.toggleLine(markdown,0),"heading is not checkbox");
        call("message.append","session_id",s1,"message_id","m1","role","user","content_md","问题");
        call("message.append","session_id",s1,"message_id","m1","role","user","content_md","问题");
        rejected(()->call("message.append","session_id",s1,"message_id","m1","role","user","content_md","changed"),"message ID conflict");
        call("message.append","session_id",s1,"message_id","m2","role","assistant","content_md","真实回复");
        call("message.append","session_id",s1,"message_id","m3","role","user","content_md","问题");
        check(call("session.get","id",s1).getJSONArray("messages").length()==3,"retry dedup and repeated text allowed");
        JSONObject latest=call("session.get","id",s1,"limit",2);check(latest.getBoolean("has_more"),"message pagination has_more");
        JSONObject older=call("session.get","id",s1,"limit",2,"before_row",latest.getLong("next_before_row"));check(older.getJSONArray("messages").length()==1&&!older.getBoolean("has_more"),"message pagination older page");
        check(older.getJSONArray("messages").getJSONObject(0).getString("id").equals("m1"),"message pagination chronological");
        String attachment=call("attachment.add","task_id",leaf,"uri","content://media/external/images/media/999999","name","图片.png","mime","image/png","storage_mode","reference").getString("id");
        check(call("task.get","id",leaf).getString("content_md").contains("aios-attachment:"+attachment),"attachment uses stable markdown ID");
        call("attachment.relink","id",attachment,"uri","content://media/external/images/media/999998");
        check(call("attachment.get","id",attachment).getString("uri").endsWith("999998"),"relink stable ID");
        check(call("task.get","id",leaf).getString("content_md").contains(attachment),"relink leaves markdown unchanged");
        call("attachment.delete","id",attachment);check(!call("task.get","id",leaf).getString("content_md").contains(attachment),"remove attachment removes rendered link");
        call("memory.create","content","保留我的偏好","source_session_id",s1);
        call("session.delete","id",s1,"expected_revision",revision("session",s1));
        check(call("task.get","id",root).getJSONArray("sessions").length()==1,"session deletion cleans only its relations");
        check(call("task.get","id",other).getString("title").equals("另一个项目"),"session deletion preserves tasks");
        check(call("memory.list").getJSONArray("items").getJSONObject(0).isNull("source_session_id"),"default keeps memory on session delete");
        call("memory.create","content","随来源删除","source_session_id",s2);
        call("session.delete","id",s2,"expected_revision",revision("session",s2),"delete_memories",true);
        check(call("memory.list").getJSONArray("items").length()==1,"explicit delete removes source memories only");
        String retained=session("保留会话");call("relation.link","task_id",leaf,"session_id",retained);
        call("attachment.add","task_id",leaf,"uri","content://media/external/video/media/111","name","视频.mp4","mime","video/mp4","storage_mode","reference");
        call("task.delete","id",other,"expected_revision",revision("task",other));
        rejected(()->call("task.get","id",leaf),"subtree cascade");
        check(call("session.get","id",retained).getJSONArray("tasks").length()==0,"task deletion preserves session and cleans relations");
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT count(*) FROM attachments",null)){c.moveToFirst();check(c.getInt(0)==0,"subtree attachment cascade");}
        call("settings.set","key","media_mode","value","copy");store.close();store=new WorkspaceStore(getTargetContext(),database);
        check(call("task.get","id",root).getString("content_md").equals(markdown),"task survives reopen");
        check(call("settings.get").getJSONArray("items").getJSONObject(0).getString("value").equals("copy"),"settings survive reopen");
        check(call("memory.list").getJSONArray("items").length()==1,"memory survives reopen");
        rejected(()->call("task.create","title","bad","status","running"),"invalid state rejected");
        rejected(()->call("task.create","title","   "),"empty title rejected");
        syncCases();
        migrationCase();
        assertions+=WorkspaceMemoryTests.run(store);
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)){check(!c.moveToFirst(),"foreign key integrity");}
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("PRAGMA integrity_check",null)){c.moveToFirst();check(c.getString(0).equals("ok"),"SQLite integrity");}
    }
    private static void delete(java.io.File file){if(!file.exists())return;if(file.isDirectory()){java.io.File[] children=file.listFiles();if(children!=null)for(java.io.File child:children)delete(child);}file.delete();}
    private void migrationCase()throws Exception{
        String name="workspace-v1-migration-test.db";getTargetContext().deleteDatabase(name);
        try{
            try(android.database.sqlite.SQLiteDatabase old=getTargetContext().openOrCreateDatabase(name,0,null)){
                old.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY,parent_id TEXT,title TEXT NOT NULL,content_md TEXT NOT NULL DEFAULT '',status TEXT NOT NULL DEFAULT 'note',position INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
                old.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,title TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
                old.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,session_id TEXT,role TEXT,content_md TEXT,created_at INTEGER)");
                old.execSQL("CREATE TABLE memories(id TEXT PRIMARY KEY,content TEXT NOT NULL,source_session_id TEXT,revision INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
                old.execSQL("INSERT INTO memories VALUES('legacy-memory','旧中文记忆','legacy-session',1,1,1)");
                old.execSQL("INSERT INTO tasks(id,title,content_md,status,created_at,updated_at) VALUES('legacy','旧任务','- [x] 人工记录','done',1,1)");
                old.execSQL("INSERT INTO sessions(id,title,created_at,updated_at) VALUES('legacy-session','旧会话',1,1)");old.setVersion(1);
            }
            try(WorkspaceStore upgraded=new WorkspaceStore(getTargetContext(),name)){
                JSONObject task=upgraded.call("task.list",new JSONObject()).getJSONObject("data").getJSONArray("items").getJSONObject(0);
                check(task.getString("content_md").equals("- [x] 人工记录")&&task.getString("status").equals("done"),"v1 user content and completion preserved");
                check(upgraded.getReadableDatabase().getVersion()==12,"v1 upgraded to v12");
                check(upgraded.call("memory.search",p("query","中文")).getJSONObject("data").getJSONArray("items").length()==0,"legacy project memory stays outside phone default scope");
                rejected(()->upgraded.call("memory.search",p("query","中文","project_id",WorkspaceMemory.LEGACY_PROJECT)),"phone API rejects legacy project scope");
                JSONObject legacy=upgraded.require(upgraded.getReadableDatabase(),"memories","legacy-memory");
                check(legacy.getString("content").equals("旧中文记忆")&&legacy.getString("scope_id").equals(WorkspaceMemory.LEGACY_PROJECT),"legacy content and scope preserved");
                try(android.database.Cursor index=upgraded.getReadableDatabase().rawQuery("SELECT body FROM memory_fts WHERE ref_id=? AND kind='memory'",new String[]{"legacy-memory"})){
                    check(index.moveToFirst()&&index.getString(0).equals("旧中文记忆"),"legacy memory index preserved without widening API scope");
                }
                check(upgraded.require(upgraded.getReadableDatabase(),"sessions","legacy-session").getString("source").equals("local"),"v1 sessions stay local");
            }
        }finally{getTargetContext().deleteDatabase(name);}
    }
    private void syncCases()throws Exception{
        call("sync.begin","thread_id","test-thread","generation","g1","title","真实会话","status","completed","updated_at",1000,"count",2);
        call("sync.item","generation","g1","item_id","u","sequence",1,"turn_id","turn","kind","userMessage","role","user","content_md","完整问题\n第二行","detail","","created_at",1000);
        rejected(()->call("sync.commit","generation","g1"),"incomplete snapshot cannot publish");
        check(call("session.get","id","codex:test-thread").getJSONArray("messages").length()==0,"staging not visible");
        call("sync.item","generation","g1","item_id","a","sequence",2,"turn_id","turn","kind","agentMessage","role","assistant","content_md","完整答复","detail","","created_at",1000);
        call("sync.commit","generation","g1");
        JSONObject portable=call("runtime.history.export","session_id",store.rows(store.getReadableDatabase(),"SELECT id FROM standard_sessions WHERE native_thread_id=?","test-thread").getJSONObject(0).getString("id"));check(portable.getString("portable_state").equals("visible_history_only")&&portable.getJSONArray("records").length()==2,"standard visible history import");
        check(call("session.get","id","codex:test-thread").getJSONArray("messages").length()==2,"atomic snapshot publication");
        check(call("sync.begin","thread_id","test-thread","generation","g1","title","真实会话","status","completed","updated_at",1000,"count",2).getBoolean("unchanged"),"replay is idempotent");
        JSONObject latest=call("session.get","id","codex:test-thread","limit",1);check(latest.getBoolean("has_more"),"mirrored timeline pages");
        check(call("session.get","id","codex:test-thread","before_row",latest.getLong("next_before_row")).getJSONArray("messages").getJSONObject(0).getString("content_md").equals("完整问题\n第二行"),"mirror preserves exact text");
        rejected(()->call("message.append","session_id","codex:test-thread","role","user","content_md","local"),"mirror rejects divergent local messages");
        String manualId=task("人工长期目标",null);
        JSONObject manual=call("task.get","id",manualId);
        call("task.update","id",manualId,"expected_revision",manual.getLong("revision"),"content_md","用户约束：保留原数据");
        JSONObject adoption=p("work_key","task:"+manualId,"task_id",manualId,"expected_revision",manual.getLong("revision"),"version",1,"title","AI 新标题","summary","AI 计划","progress","准备下一步","status","doing","session_ids",new JSONArray());
        rejected(()->store.call("task.ai.report",adoption),"stale adoption cannot overwrite edited goal");
        check(call("task.get","id",manualId).isNull("ai_key"),"failed adoption rolls back ownership");
        adoption.put("expected_revision",revision("task",manualId));
        JSONObject adopted=store.call("task.ai.report",adoption).getJSONObject("data");
        check(adopted.getString("id").equals(manualId),"adoption does not create a duplicate goal");
        check(adopted.getString("title").equals("人工长期目标")&&adopted.getString("content_md").contains("保留原数据"),"adoption retains human constraints");
        String another=task("另一目标",null);adoption.put("task_id",another).put("version",2);
        rejected(()->store.call("task.ai.report",adoption),"task id and work key cannot address different goals");
        JSONArray sessions=new JSONArray().put("codex:test-thread");
        JSONObject report=p("work_key","test-work","version",1,"title","AI 工作","summary","较大工作摘要","progress","已完成设计","status","doing","session_ids",sessions);
        JSONObject ai=store.call("task.ai.report",report).getJSONObject("data");String id=ai.getString("id");
        check(store.call("task.ai.report",report).getJSONObject("data").getString("id").equals(id),"AI report retries deduplicate");
        call("task.update","id",id,"expected_revision",ai.getLong("revision"),"title","用户标题","content_md","用户记录\n- [x] 已核对","status","done");
        report.put("version",2).put("progress","继续测试");store.call("task.ai.report",report);
        JSONObject edited=call("task.get","id",id);
        check(edited.getString("title").equals("用户标题")&&edited.getString("content_md").contains("[x]"),"AI preserves human title and notes");
        check(edited.getString("status").equals("done")&&edited.getString("ai_status").equals("doing"),"human completion wins over AI update");
        report.put("version",1);rejected(()->store.call("task.ai.report",report),"stale AI report rejected");
        report.put("version",3).put("status","done").put("evidence","");rejected(()->store.call("task.ai.report",report),"AI completion requires evidence");
        call("task.ai.release","id",id,"expected_revision",edited.getLong("revision"));
        check(call("task.get","id",id).getString("status").equals("doing"),"user can release status override");
        report.put("evidence","Android tests passed");store.call("task.ai.report",report);
        check(call("task.get","id",id).getString("status").equals("done"),"verified AI completion");
        call("task.delete","id",id,"expected_revision",revision("task",id));
        report.put("version",4);check(store.call("task.ai.report",report).getJSONObject("data").getBoolean("hidden"),"deleted AI tasks do not resurrect");
        call("session.delete","id","codex:test-thread","expected_revision",revision("session","codex:test-thread"));
        check(call("sync.begin","thread_id","test-thread","generation","g2","title","真实会话","status","completed","updated_at",2000,"count",0).getBoolean("hidden"),"deleted mirrors stay hidden");
    }
}
