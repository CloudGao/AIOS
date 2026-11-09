package os.aios.core.workspace;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Set;

/** Source snapshots are staged, then published atomically. Human edits own separate fields. */
final class WorkspaceSync {
    private final WorkspaceStore store;
    private final SQLiteDatabase db;
    WorkspaceSync(WorkspaceStore store,SQLiteDatabase db){this.store=store;this.db=db;}
    static void migrate(SQLiteDatabase db){
        db.execSQL("ALTER TABLE tasks ADD COLUMN ai_key TEXT");
        db.execSQL("CREATE UNIQUE INDEX tasks_ai_key ON tasks(ai_key)");
        db.execSQL("ALTER TABLE tasks ADD COLUMN ai_summary TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE tasks ADD COLUMN ai_progress TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE tasks ADD COLUMN ai_status TEXT NOT NULL DEFAULT 'todo'");
        db.execSQL("ALTER TABLE tasks ADD COLUMN ai_version INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE tasks ADD COLUMN ai_payload TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE tasks ADD COLUMN user_title INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE tasks ADD COLUMN user_content INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE tasks ADD COLUMN user_status INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE sessions ADD COLUMN source TEXT NOT NULL DEFAULT 'local'");
        db.execSQL("ALTER TABLE sessions ADD COLUMN source_id TEXT");
        db.execSQL("CREATE UNIQUE INDEX sessions_source ON sessions(source,source_id)");
        db.execSQL("ALTER TABLE sessions ADD COLUMN generation TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE sessions ADD COLUMN source_status TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE sessions ADD COLUMN synced_at INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE sync_hidden(kind TEXT NOT NULL,source_id TEXT NOT NULL,PRIMARY KEY(kind,source_id))");
        db.execSQL("CREATE TABLE sync_batches(id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, base_generation TEXT NOT NULL, title TEXT NOT NULL, source_status TEXT NOT NULL, source_updated INTEGER NOT NULL, expected_count INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE sync_items(session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,generation TEXT NOT NULL,id TEXT NOT NULL,sequence INTEGER NOT NULL,turn_id TEXT NOT NULL,kind TEXT NOT NULL,role TEXT NOT NULL,content_md TEXT NOT NULL,detail TEXT NOT NULL,created_at INTEGER NOT NULL,PRIMARY KEY(session_id,generation,id),UNIQUE(session_id,generation,sequence))");
        db.execSQL("CREATE INDEX sync_items_page ON sync_items(session_id,generation,sequence)");
    }
    private JSONObject result(String key,Object value)throws Exception{return new JSONObject().put(key,value);}
    private String text(JSONObject p,String key,int max,boolean empty)throws Exception{return WorkspaceStore.text(p,key,max,empty);}
    private boolean hidden(String kind,String id)throws Exception{return store.rows(db,"SELECT source_id FROM sync_hidden WHERE kind=? AND source_id=?",kind,id).length()>0;}
    JSONObject call(String op,JSONObject p)throws Exception{
        switch(op){
        case "sync.live": {
            String conversation=text(p,"conversation_id",100,false),thread=p.optString("thread_id");
            if(thread.length()>100)throw new IllegalArgumentException("Invalid thread");
            String pending="assistant:"+conversation;
            if(thread.isEmpty()){JSONArray aliases=store.rows(db,"SELECT value FROM settings WHERE key=?","session.alias:"+pending);if(aliases.length()>0)thread=aliases.getJSONObject(0).getString("value").substring("codex:".length());}
            String source=thread.isEmpty()?conversation:thread,session=thread.isEmpty()?pending:"codex:"+thread;
            if(hidden("session",conversation)){
                // Deleting an entry while thread/start is in flight also hides the later native mirror.
                if(!thread.isEmpty()){db.execSQL("INSERT OR IGNORE INTO sync_hidden(kind,source_id) VALUES('session',?)",new Object[]{thread});db.delete("sessions","id=?",new String[]{session});}
                return result("hidden",true);
            }
            if(!thread.isEmpty()&&(hidden("session",thread)||hidden("automation",thread))){db.delete("sessions","id=?",new String[]{pending});return result("hidden",true);}
            String title=text(p,"text",12000,false).trim();if(title.length()>300)title=title.substring(0,300);
            long updated=Math.max(p.optLong("submitted_at"),Math.max(p.optLong("message_updated_at"),p.optLong("execution_finished_at")));
            JSONArray found=store.rows(db,"SELECT * FROM sessions WHERE id=?",session);
            if(found.length()==0){ContentValues initial=store.base(session);initial.put("title",title);initial.put("source",thread.isEmpty()?"assistant_pending":"codex");initial.put("source_id",source);initial.put("updated_at",updated);db.insertOrThrow("sessions",null,initial);}
            JSONObject row=store.require(db,"sessions",session);
            // A delayed recovery snapshot must not move a newer conversation backwards.
            if(updated>=row.optLong("updated_at")){
                ContentValues values=new ContentValues();values.put("source_status",text(p,"status",100,false));values.put("updated_at",updated);
                if(!p.optString("status").equals(row.optString("source_status")))values.put("revision",row.optLong("revision")+1);
                db.update("sessions",values,"id=?",new String[]{session});
            }
            if(!thread.isEmpty()){
                db.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)",new Object[]{"session.alias:"+pending,session});
                db.execSQL("INSERT OR IGNORE INTO task_session(task_id,session_id,created_at) SELECT task_id,?,created_at FROM task_session WHERE session_id=?",new Object[]{session,pending});
                db.execSQL("UPDATE memories SET source_session_id=? WHERE source_session_id=?",new Object[]{session,pending});
                db.delete("sessions","id=?",new String[]{pending});
            }
            String task=p.optString("task_id");if(!task.isEmpty()&&store.rows(db,"SELECT id FROM tasks WHERE id=?",task).length()>0)
                db.execSQL("INSERT OR IGNORE INTO task_session(task_id,session_id,created_at) VALUES(?,?,?)",new Object[]{task,session,WorkspaceStore.now()});
            return result("session_id",session);
        }
        case "sync.automation": {String source=text(p,"thread_id",100,false);db.execSQL("INSERT OR IGNORE INTO sync_hidden(kind,source_id) VALUES('automation',?)",new Object[]{source});return result("hidden",true);}
        case "sync.incremental": {
            String source=text(p,"thread_id",100,false);if(hidden("session",source))return result("hidden",true);String session="codex:"+source;JSONArray found=store.rows(db,"SELECT * FROM sessions WHERE id=?",session);
            if(found.length()==0){ContentValues initial=store.base(session);initial.put("title",text(p,"title",300,false));initial.put("source","codex");initial.put("source_id",source);initial.put("generation","incremental-v1");initial.put("updated_at",p.getLong("updated_at"));db.insertOrThrow("sessions",null,initial);found=store.rows(db,"SELECT * FROM sessions WHERE id=?",session);}
            JSONObject row=found.getJSONObject(0);String generation=row.getString("generation");if(generation.isEmpty()){generation="incremental-v1";ContentValues set=new ContentValues();set.put("generation",generation);db.update("sessions",set,"id=?",new String[]{session});}
            JSONArray items=p.getJSONArray("items");if(items.length()>800)throw new IllegalArgumentException("Incremental history page too large");long next=store.rows(db,"SELECT coalesce(max(sequence),0)+1 AS n FROM sync_items WHERE session_id=? AND generation=?",session,generation).getJSONObject(0).getLong("n");
            for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);String kind=text(item,"kind",100,false),role=item.getString("role");if(kind.equals("reasoning")||!Set.of("user","assistant","system").contains(role))continue;String id=text(item,"item_id",300,false);JSONArray prior=store.rows(db,"SELECT sequence FROM sync_items WHERE session_id=? AND generation=? AND id=?",session,generation,id);long sequence=prior.length()==0?next++:prior.getJSONObject(0).getLong("sequence");ContentValues v=new ContentValues();v.put("id",id);v.put("session_id",session);v.put("generation",generation);v.put("sequence",sequence);v.put("turn_id",text(item,"turn_id",100,false));v.put("kind",kind);v.put("role",role);v.put("content_md",text(item,"content_md",16000000,true));v.put("detail",text(item,"detail",32000000,true));v.put("created_at",item.getLong("created_at"));db.insertWithOnConflict("sync_items",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
            ContentValues update=new ContentValues();update.put("title",text(p,"title",300,false));update.put("source_status",text(p,"status",100,true));update.put("updated_at",p.getLong("updated_at"));update.put("synced_at",WorkspaceStore.now());update.put("revision",row.getLong("revision")+1);db.update("sessions",update,"id=?",new String[]{session});return result("session_id",session).put("generation",generation).put("updated",items.length());
        }
        case "sync.items": {
            JSONArray items=p.getJSONArray("items");if(items.length()>50)throw new IllegalArgumentException("Batch too large");
            for(int i=0;i<items.length();i++)call("sync.item",items.getJSONObject(i).put("generation",p.getString("generation")));
            return result("staged",items.length());
        }
        case "sync.begin": {
            String source=text(p,"thread_id",100,false);
            if(hidden("session",source))return result("hidden",true);
            String session="codex:"+source;
            JSONArray found=store.rows(db,"SELECT * FROM sessions WHERE id=?",session);
            if(found.length()==0){ContentValues v=store.base(session);v.put("title",text(p,"title",300,false));v.put("source","codex");v.put("source_id",source);v.put("updated_at",p.getLong("updated_at"));db.insertOrThrow("sessions",null,v);}
            JSONObject row=store.require(db,"sessions",session);
            String generation=text(p,"generation",100,false);
            if(generation.equals(row.getString("generation")))return result("unchanged",true);
            int count=p.getInt("count");if(count<0||count>1000000)throw new IllegalArgumentException("Invalid item count");
            // A new attempt supersedes unfinished work; the last published snapshot stays visible.
            db.delete("sync_batches","session_id=?",new String[]{session});
            db.delete("sync_items","session_id=? AND generation<>?",new String[]{session,row.getString("generation")});
            ContentValues v=store.base(generation);v.put("session_id",session);v.put("base_generation",row.getString("generation"));v.put("title",text(p,"title",300,false));v.put("source_status",text(p,"status",100,true));v.put("source_updated",p.getLong("updated_at"));v.put("expected_count",count);db.insertOrThrow("sync_batches",null,v);
            return result("session_id",session).put("generation",generation);
        }
        case "sync.item": {
            String generation=p.getString("generation");JSONObject batch=store.require(db,"sync_batches",generation);
            String kind=text(p,"kind",100,false),role=p.getString("role");
            if(kind.equals("reasoning")||!Set.of("user","assistant","system").contains(role))throw new IllegalArgumentException("Unsupported visible item");
            long sequence=p.getLong("sequence");if(sequence<1||sequence>batch.getLong("expected_count"))throw new IllegalArgumentException("Invalid sequence");
            ContentValues v=new ContentValues();v.put("id",text(p,"item_id",300,false));v.put("session_id",batch.getString("session_id"));v.put("generation",generation);v.put("sequence",sequence);v.put("turn_id",text(p,"turn_id",100,false));v.put("kind",kind);v.put("role",role);v.put("content_md",text(p,"content_md",16000000,true));v.put("detail",text(p,"detail",32000000,true));v.put("created_at",p.getLong("created_at"));
            db.insertWithOnConflict("sync_items",null,v,SQLiteDatabase.CONFLICT_REPLACE);return result("staged",true);
        }
        case "sync.commit": {
            String generation=p.getString("generation");JSONObject batch=store.require(db,"sync_batches",generation);String session=batch.getString("session_id");JSONObject row=store.require(db,"sessions",session);
            if(!row.getString("generation").equals(batch.getString("base_generation")))throw new IllegalStateException("Snapshot superseded");
            long count=store.rows(db,"SELECT count(*) AS n FROM sync_items WHERE session_id=? AND generation=?",session,generation).getJSONObject(0).getLong("n");
            if(count!=batch.getLong("expected_count"))throw new IllegalStateException("Incomplete snapshot");
            ContentValues v=new ContentValues();v.put("title",batch.getString("title"));v.put("generation",generation);v.put("source_status",batch.getString("source_status"));v.put("updated_at",batch.getLong("source_updated"));v.put("synced_at",WorkspaceStore.now());v.put("revision",row.getLong("revision")+1);db.update("sessions",v,"id=?",new String[]{session});
            db.delete("sync_items","session_id=? AND generation<>?",new String[]{session,generation});db.delete("sync_batches","id=?",new String[]{generation});return result("session_id",session).put("count",count);
        }
        case "task.ai.report":return report(p);
        case "task.ai.release": {
            String id=p.getString("id");JSONObject row=store.require(db,"tasks",id);store.revision(row,p);
            if(row.isNull("ai_key"))throw new IllegalArgumentException("This task is not AI managed");
            ContentValues v=new ContentValues();v.put("user_status",0);v.put("status",row.getString("ai_status"));db.update("tasks",v,"id=?",new String[]{id});store.touch(db,"tasks",id);return store.require(db,"tasks",id);
        }
        default:throw new IllegalArgumentException("Unknown synchronization operation");
        }
    }
    JSONObject readSession(JSONObject row,JSONObject p)throws Exception{
        int limit=p.optInt("limit",100);long before=p.optLong("before_row",Long.MAX_VALUE);if(limit<1||limit>500||before<1)throw new IllegalArgumentException("Invalid pagination");
        String id=row.getString("id"),generation=row.getString("generation");
        // Keep complete source records in SQLite and bound only the structured IPC preview.
        JSONArray candidates=store.rows(db,"SELECT session_id,generation,id,sequence,turn_id,kind,role,substr(content_md,1,160000) AS content_md,substr(detail,1,2000) AS detail,created_at FROM sync_items WHERE session_id=? AND generation=? AND sequence<? ORDER BY sequence DESC LIMIT ?",id,generation,Long.toString(before),Integer.toString(limit));
        java.util.ArrayList<JSONObject> selected=new java.util.ArrayList<>();int bytes=0;
        for(int i=0;i<candidates.length();i++){JSONObject item=candidates.getJSONObject(i);int size=item.toString().length();if(!selected.isEmpty()&&bytes+size>384000)break;selected.add(item);bytes+=size;}
        JSONArray items=new JSONArray();for(int i=selected.size()-1;i>=0;i--)items.put(selected.get(i));
        long oldest=items.length()==0?0:items.getJSONObject(0).getLong("sequence");
        boolean more=oldest>0&&store.rows(db,"SELECT id FROM sync_items WHERE session_id=? AND generation=? AND sequence<? LIMIT 1",id,generation,Long.toString(oldest)).length()>0;
        return row.put("messages",items).put("has_more",more).put("next_before_row",oldest).put("tasks",store.rows(db,"SELECT t.* FROM tasks t JOIN task_session r ON r.task_id=t.id WHERE r.session_id=? ORDER BY t.updated_at DESC",id));
    }
    private JSONObject report(JSONObject p)throws Exception{
        String key=text(p,"work_key",200,false),title=text(p,"title",300,false),summary=text(p,"summary",1000000,true),progress=text(p,"progress",1000000,true),status=p.getString("status");
        if(!Set.of("todo","doing","done").contains(status))throw new IllegalArgumentException("Invalid AI status");
        if(status.equals("done")&&text(p,"evidence",100000,true).trim().isEmpty())throw new IllegalArgumentException("Completion evidence is required");
        long version=p.getLong("version");if(version<1)throw new IllegalArgumentException("Invalid report version");
        if(hidden("task",key))return result("hidden",true);
        JSONArray found=store.rows(db,"SELECT * FROM tasks WHERE ai_key=?",key);
        String payload=new JSONObject().put("title",title).put("summary",summary).put("progress",progress).put("status",status).put("evidence",p.optString("evidence","")).put("session_ids",p.getJSONArray("session_ids")).toString();
        if(found.length()>0&&!p.optString("task_id").isEmpty()&&!p.getString("task_id").equals(found.getJSONObject(0).getString("id")))throw new IllegalArgumentException("Task and work key do not match");
        String id;
        JSONObject old=null;
        // Explicit adoption keeps the user's existing goal, content and status authoritative.
        if(found.length()==0&&!p.optString("task_id").isEmpty()){
            JSONObject adopt=store.require(db,"tasks",p.getString("task_id"));store.revision(adopt,p);
            if(!adopt.isNull("ai_key"))throw new IllegalStateException("Task already belongs to another work key");
            ContentValues ownership=new ContentValues();ownership.put("ai_key",key);ownership.put("user_title",1);ownership.put("user_content",1);ownership.put("user_status",1);
            db.update("tasks",ownership,"id=?",new String[]{adopt.getString("id")});
            found=store.rows(db,"SELECT * FROM tasks WHERE ai_key=?",key);
        }
        if(found.length()>0){old=found.getJSONObject(0);id=old.getString("id");long previous=old.getLong("ai_version");
            if(version<previous)throw new IllegalStateException("Stale AI report");
            if(version==previous){if(!payload.equals(old.getString("ai_payload")))throw new IllegalStateException("Report version conflict");return old;}
        }else{id=WorkspaceStore.id();ContentValues v=store.base(id);v.put("ai_key",key);v.put("title",title);v.put("status",status);v.put("updated_at",WorkspaceStore.now());db.insertOrThrow("tasks",null,v);}
        ContentValues v=new ContentValues();v.put("ai_summary",summary);v.put("ai_progress",progress+(status.equals("done")?"\n\n验收："+p.getString("evidence"):""));v.put("ai_status",status);v.put("ai_version",version);v.put("ai_payload",payload);
        if(old==null||old.getInt("user_title")==0)v.put("title",title);
        if(old==null||old.getInt("user_content")==0)v.put("content_md",summary);
        if(old==null||old.getInt("user_status")==0)v.put("status",status);
        db.update("tasks",v,"id=?",new String[]{id});store.touch(db,"tasks",id);
        JSONArray sessions=p.getJSONArray("session_ids");for(int i=0;i<sessions.length();i++){String session=sessions.getString(i);store.require(db,"sessions",session);db.execSQL("INSERT OR IGNORE INTO task_session(task_id,session_id,created_at) VALUES(?,?,?)",new Object[]{id,session,WorkspaceStore.now()});}
        JSONObject result=store.require(db,"tasks",id);WorkspaceDream.history(store,db,old==null?new JSONObject():old,result,new JSONObject().put("change_summary","助手进度更新"));return result;
    }
}
