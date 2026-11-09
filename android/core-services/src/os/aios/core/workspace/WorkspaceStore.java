package os.aios.core.workspace;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** On-device source of truth. No network, UI ownership, or model execution dependencies. */
public final class WorkspaceStore extends SQLiteOpenHelper {
    public static final String CHANGED="os.aios.workspace.CHANGED";
    private final Context context;
    private final String databaseName;
    public static final String DATABASE = "aios_workspace.db";
    public WorkspaceStore(Context context) { this(context, DATABASE); }
    public WorkspaceStore(Context context, String name) {
        super(context, name, null, 12);
        this.context=context.getApplicationContext();
        this.databaseName=name;
        setWriteAheadLoggingEnabled(true);
    }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, parent_id TEXT REFERENCES tasks(id) ON DELETE CASCADE, title TEXT NOT NULL, content_md TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'note' CHECK(status IN ('note','todo','doing','done')), position INTEGER NOT NULL DEFAULT 0, revision INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, CHECK(parent_id IS NULL OR parent_id<>id))");
        db.execSQL("CREATE INDEX tasks_parent ON tasks(parent_id,position,created_at)");
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY, title TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, role TEXT NOT NULL CHECK(role IN ('user','assistant','system')), content_md TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX messages_session ON messages(session_id,created_at)");
        db.execSQL("CREATE TABLE task_session(task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, created_at INTEGER NOT NULL, PRIMARY KEY(task_id,session_id))");
        db.execSQL("CREATE INDEX task_session_session ON task_session(session_id,task_id)");
        db.execSQL("CREATE TABLE attachments(id TEXT PRIMARY KEY, task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, uri TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, storage_mode TEXT NOT NULL CHECK(storage_mode IN ('reference','copy')), created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX attachments_task ON attachments(task_id)");
        db.execSQL("CREATE TABLE memories(id TEXT PRIMARY KEY, content TEXT NOT NULL, source_session_id TEXT REFERENCES sessions(id) ON DELETE SET NULL, revision INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX memories_source ON memories(source_session_id)");
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("INSERT INTO settings VALUES('media_mode','reference')");
        WorkspaceSync.migrate(db);
        WorkspaceMemory.migrate(this,db);
        WorkspaceSkills.migrate(db);
        WorkspaceDream.migrate(db);
        StandardRecords.migrate(db);
        WorkspaceDream.migrateTracking(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<7)WorkspaceSkills.migrate(db);
        if(oldVersion<8)WorkspaceDream.migrate(db);
        if(oldVersion<9)WorkspaceSkills.migrate(db);
        if(oldVersion<10)StandardRecords.migrate(db);
        if(oldVersion<2)WorkspaceSync.migrate(db);
        else if(oldVersion<3)db.execSQL("ALTER TABLE tasks ADD COLUMN user_content INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<4)WorkspaceMemory.migrate(this,db);
        else if(oldVersion<6)WorkspaceMemory.migrateUsage(db);
        if(oldVersion<11)WorkspaceDream.migrateTracking(db);
        if(oldVersion<12){WorkspaceSkills.migrate(db);LegacySkillMigration.upgrade(this,db);}
    }
    @Override public void onOpen(SQLiteDatabase db){super.onOpen(db);if(!db.isReadOnly())try{new WorkspaceDream(this,db).publishPending();}catch(Exception e){android.util.Log.e("AIOSSkills","Pending publication retained after open",e);}}
    static long now() { return System.currentTimeMillis(); }
    Context appContext(){return context;}
    java.io.File skillRoot(){return new java.io.File(context.getFilesDir(),DATABASE.equals(databaseName)?"skills":"skills-tests/"+databaseName.replaceAll("[^A-Za-z0-9_.-]","_"));}
    String contextDatabase(){return DATABASE.equals(databaseName)?"aios_context.db":"context-test-"+databaseName;}
    static String id() { return UUID.randomUUID().toString(); }
    static String nullable(JSONObject p, String key) throws Exception {
        return !p.has(key) || p.isNull(key) || p.getString(key).isEmpty() ? null : p.getString(key);
    }
    static String text(JSONObject p, String key, int max, boolean empty) throws Exception {
        String value=p.getString(key);
        if ((!empty && value.trim().isEmpty()) || value.length()>max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }
    JSONArray rows(SQLiteDatabase db, String sql, String... args) throws Exception {
        JSONArray result=new JSONArray();
        try(Cursor c=db.rawQuery(sql,args)) {
            while(c.moveToNext()) {
                JSONObject row=new JSONObject();
                for(int i=0;i<c.getColumnCount();i++) row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));
                result.put(row);
            }
        }
        return result;
    }
    JSONObject require(SQLiteDatabase db, String table, String id) throws Exception {
        JSONArray rows=rows(db,"SELECT * FROM "+table+" WHERE id=?",id);
        if(rows.length()==0) throw new IllegalArgumentException("Record not found: "+id);
        return rows.getJSONObject(0);
    }
    void revision(JSONObject row, JSONObject p) throws Exception {
        if(row.getLong("revision")!=p.getLong("expected_revision")) throw new IllegalStateException("内容已更新，请重新打开后再编辑");
    }
    ContentValues base(String id) {
        ContentValues v=new ContentValues();v.put("id",id);v.put("created_at",now());return v;
    }
    void touch(SQLiteDatabase db,String table,String id) {
        db.execSQL("UPDATE "+table+" SET updated_at=?,revision=revision+1 WHERE id=?",new Object[]{now(),id});
    }
    String skillAppLabel(String pkg){try{return String.valueOf(context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg,0)));}catch(Exception e){return pkg;}}
    String skillAppVersion(String pkg){
        try{return Long.toString(context.getPackageManager().getPackageInfo(pkg,0).getLongVersionCode());}
        catch(android.content.pm.PackageManager.NameNotFoundException e){return "not_installed";}
    }
    /** Internal receipt writer; never exposed as a Workspace action. Stores metadata only. */
    public synchronized String recordSkillEvidence(String tool,String pkg,String version)throws Exception{
        SQLiteDatabase db=getWritableDatabase();String id=id();ContentValues v=base(id);
        v.put("tool",tool);v.put("package",pkg);v.put("app_version",version);
        db.insertOrThrow("skill_evidence",null,v);
        db.delete("skill_evidence","created_at<?",new String[]{Long.toString(now()-14*86400000L)});
        db.execSQL("DELETE FROM skill_evidence WHERE id NOT IN (SELECT id FROM skill_evidence ORDER BY created_at DESC,id LIMIT 10000)");
        return id;
    }
    public synchronized String recordSkillEvidence(JSONObject p)throws Exception{
        String id=recordSkillEvidence(p.getString("tool"),p.optString("package"),p.optString("version"));
        getWritableDatabase().execSQL("INSERT INTO skill_evidence_context VALUES(?,?,?,?)",new Object[]{id,p.optString("thread_id"),p.optString("request_id"),p.optString("session_id")});return id;
    }
    /** In-process host entry only. Not routed by workspace.action or exposed to model tools. */
    public synchronized JSONObject dream(String op,JSONObject p)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        boolean committed=false;
        try{JSONObject result=new WorkspaceDream(this,db).call(op,p);db.setTransactionSuccessful();committed=true;return result;}
        finally{db.endTransaction();if(committed){try{new WorkspaceDream(this,db).publishPending();}catch(Exception e){android.util.Log.e("AIOSSkills","Committed Dream publication retained for retry",e);}if(op.equals("commit")||op.equals("resolve"))notifyChanged();}}
    }
    /** Internal execution ledger entry; model-facing Workspace actions cannot bypass ownership. */
    public synchronized JSONObject recordExecution(JSONObject p)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONObject result=new StandardRecords(this,db).call("runtime.execution.record",p);db.setTransactionSuccessful();return result;}finally{db.endTransaction();}
    }
    /** Local-only search; coordinator performs remote IPC after this monitor is released. */
    public synchronized JSONObject search(String source,JSONObject p)throws Exception {
        SQLiteDatabase db=getReadableDatabase();JSONArray input;boolean more;
        int limit=p.getInt("limit"),offset=p.optInt("offset");
        if(source.equals("tasks")){
            String q="%"+p.optString("query","").replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            input=rows(db,"SELECT id,title,substr(content_md,1,600) AS snippet,updated_at FROM tasks WHERE title LIKE ? ESCAPE '\\' OR content_md LIKE ? ESCAPE '\\' ORDER BY updated_at DESC,id LIMIT ? OFFSET ?",q,q,Integer.toString(limit+1),Integer.toString(offset));more=input.length()>limit;
        }else{
            JSONObject result=new WorkspaceMemory(this,db).call(source.equals("memory")?"memory.search":"session.search",new JSONObject().put("query",p.optString("query","")).put("limit",limit).put("offset",offset));input=result.getJSONArray("items");more=result.optBoolean("has_more");
        }
        JSONArray output=new JSONArray();for(int i=0;i<Math.min(limit,input.length());i++){
            JSONObject item=input.getJSONObject(i);String id=item.getString("id");
            output.put(new JSONObject().put("source",source).put("id",id).put("ref",source+":"+id).put("session_id",item.optString("session_id","")).put("title",item.optString("title",source.equals("memory")?"记忆":"会话记录")).put("snippet",item.optString("snippet","")).put("match_reason",source.equals("tasks")?"task_text":"full_text"));
        }
        return new JSONObject().put("items",output).put("has_more",more);
    }
    public synchronized JSONObject searchGet(String source,String id)throws Exception {
        SQLiteDatabase db=getReadableDatabase();
        if(source.equals("tasks")){JSONObject item=require(db,"tasks",id);return new JSONObject().put("item",new JSONObject().put("id",id).put("title",item.getString("title")).put("content",item.optString("content_md").substring(0,Math.min(16000,item.optString("content_md").length()))));}
        if(source.equals("memory")){
            JSONObject result=new WorkspaceMemory(this,db).call("memory.get",new JSONObject().put("id",id));
            if(!result.optString("state").equals("active")||(result.optLong("valid_until")>0&&result.optLong("valid_until")<=now())||!(result.optString("scope_type").equals("user")||result.optString("scope_type").equals("project")&&result.optString("scope_id").equals(WorkspaceMemory.PROJECT)))throw new IllegalArgumentException("Memory unavailable in this scope");
            return result;
        }
        throw new IllegalArgumentException("Open a session through its session_id; search snippets respect forgetting rules");
    }
    private static String shareId(JSONObject p,String kind)throws Exception{if(!p.has("share_key"))return java.util.UUID.randomUUID().toString();String key=p.getString("share_key");if(!key.matches("[A-Za-z0-9:_-]{1,120}"))throw new IllegalArgumentException("Invalid share key");return java.util.UUID.nameUUIDFromBytes(("aios-share:"+kind+":"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    public synchronized JSONObject call(String op, JSONObject p) throws Exception {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        boolean committed=false;
        try {
            JSONObject result=execute(db,op,p);
            db.setTransactionSuccessful();
            committed=true;
            return new JSONObject().put("state","succeeded").put("data",result);
        } finally {
            db.endTransaction();
            if(committed&&changesVisibleState(op))notifyChanged();
        }
    }
    // State belongs to Core; its UI lives in Agent. Both directions require the signature permission.
    private void notifyChanged(){if(!DATABASE.equals(databaseName))return;context.sendBroadcast(new android.content.Intent(CHANGED).setPackage("os.aios.agent"),"os.aios.permission.CORE_RUNTIME");}
    static boolean changesVisibleState(String op){
        if(op.startsWith("sync."))return op.equals("sync.commit")||op.equals("sync.incremental")||op.equals("sync.automation")||op.equals("sync.live");
        String verb=op.substring(op.lastIndexOf('.')+1);
        return Set.of("create","update","delete","move","append","link","unlink","set","learn","rollback","resolve","upsert","report","propose").contains(verb);
    }
    JSONObject execute(SQLiteDatabase db,String op,JSONObject p) throws Exception {
        if(op.equals("home.snapshot")){
            JSONObject result=new JSONObject();try(Cursor counts=db.rawQuery("SELECT status,count(*) FROM tasks GROUP BY status",null)){while(counts.moveToNext())result.put(counts.getString(0),counts.getInt(1));}
            result.put("tasks",rows(db,"SELECT id,title,status FROM tasks WHERE status IN ('doing','todo') ORDER BY CASE status WHEN 'doing' THEN 0 ELSE 1 END,updated_at DESC,id LIMIT 3"));
            result.put("sessions",rows(db,"SELECT id,title,source,source_id,source_status,updated_at FROM sessions WHERE source_id IS NULL OR source_id NOT IN (SELECT source_id FROM sync_hidden WHERE kind='automation') ORDER BY updated_at DESC,id LIMIT 3"));return result;
        }
        if(op.startsWith("runtime."))return new StandardRecords(this,db).call(op,p);
        if(op.startsWith("context.")){try(ContextStore context=new ContextStore(this)){return context.call(op,p);}}
        if(op.equals("recall.query"))return new UnifiedRecall(this,db).query(p);
        if(op.startsWith("skill."))return new WorkspaceSkills(this,db).call(op,p);
        WorkspaceMemory memory=new WorkspaceMemory(this,db);
        if(op.startsWith("memory.")||op.startsWith("session.summary.")||op.equals("session.search"))return memory.call(op,p);
        if(op.startsWith("sync.")||op.startsWith("task.ai.")){
            JSONObject result=new WorkspaceSync(this,db).call(op,p);
            if(op.equals("sync.commit")||op.equals("sync.incremental")){memory.indexSession(result.getString("session_id"));new StandardRecords(this,db).importCodexSnapshot(result.getString("session_id"),op.equals("sync.commit")?p.getString("generation"):result.getString("generation"));}
            return result;
        }
        String key=p.optString("id","");
        switch(op) {
        case "task.list": return new JSONObject().put("items",rows(db,"SELECT * FROM tasks ORDER BY position,created_at,id"));
        case "task.get": {
            JSONObject row=require(db,"tasks",key);
            row.put("history",rows(db,"SELECT id,summary,created_at,change_id FROM task_history WHERE task_id=? ORDER BY created_at DESC,id LIMIT 100",key));
            return row.put("sessions",rows(db,"SELECT s.* FROM sessions s JOIN task_session r ON r.session_id=s.id WHERE r.task_id=? ORDER BY s.updated_at DESC,s.id",key))
                .put("attachments",rows(db,"SELECT * FROM attachments WHERE task_id=? ORDER BY created_at,id",key));
        }
        case "task.history.list": {
            require(db,"tasks",key);int offset=p.optInt("offset",0);if(offset<0)throw new IllegalArgumentException("Invalid offset");
            JSONArray history=rows(db,"SELECT id,summary,created_at,change_id FROM task_history WHERE task_id=? ORDER BY created_at DESC,id LIMIT 51 OFFSET ?",key,Integer.toString(offset));boolean more=history.length()>50;if(more)history.remove(50);return new JSONObject().put("items",history).put("has_more",more);
        }
        case "task.history.get": {
            require(db,"tasks",key);JSONObject history=require(db,"task_history",p.getString("history_id"));if(!history.getString("task_id").equals(key))throw new IllegalArgumentException("History belongs to another task");return history;
        }
        case "task.create": {
            String parent=nullable(p,"parent_id"); if(parent!=null) require(db,"tasks",parent);
            String id=shareId(p,"task");if(p.has("share_key")){JSONArray existing=rows(db,"SELECT * FROM tasks WHERE id=?",id);if(existing.length()>0)return existing.getJSONObject(0);}ContentValues v=base(id);v.put("parent_id",parent);v.put("title",text(p,"title",300,false));
            v.put("content_md",p.has("content_md")?text(p,"content_md",1000000,true):"");
            v.put("status",status(p.optString("status","todo")));v.put("updated_at",now());
            WorkspaceDream.timeFields(p,v);
            JSONArray siblings=siblings(db,parent);int position=0;
            for(int i=0;i<siblings.length();i++)position=Math.max(position,require(db,"tasks",siblings.getJSONObject(i).getString("id")).getInt("position")+1);
            v.put("position",position);db.insertOrThrow("tasks",null,v);JSONObject created=require(db,"tasks",id);WorkspaceDream.history(this,db,new JSONObject(),created,p);return created;
        }
        case "task.update": {
            JSONObject row=require(db,"tasks",key);revision(row,p);ContentValues v=new ContentValues();
            WorkspaceDream.timeFields(p,v);
            if(p.has("title"))v.put("title",text(p,"title",300,false));
            if(p.has("title")&&!p.getString("title").equals(row.getString("title")))v.put("user_title",1);
            if(p.has("content_md"))v.put("content_md",text(p,"content_md",1000000,true));
            if(p.has("content_md")&&!p.getString("content_md").equals(row.getString("content_md")))v.put("user_content",1);
            if(p.has("status"))v.put("status",status(p.getString("status")));
            if(p.has("status"))v.put("user_status",1);
            if(v.size()==0)throw new IllegalArgumentException("No changes");
            db.update("tasks",v,"id=?",new String[]{key});touch(db,"tasks",key);JSONObject updated=require(db,"tasks",key);WorkspaceDream.history(this,db,row,updated,p);return updated;
        }
        case "task.move": {
            JSONObject row=require(db,"tasks",key);revision(row,p);String parent=nullable(p,"parent_id");
            Set<String> seen=new HashSet<>();String ancestor=parent;
            while(ancestor!=null) {
                if(ancestor.equals(key)||!seen.add(ancestor))throw new IllegalArgumentException("不能移到自身或子节点下");
                ancestor=nullable(require(db,"tasks",ancestor),"parent_id");
            }
            JSONArray siblings=siblings(db,parent);List<String> order=new ArrayList<>();
            for(int i=0;i<siblings.length();i++){String id=siblings.getJSONObject(i).getString("id");if(!id.equals(key))order.add(id);}
            int position=p.getInt("position");if(position<0||position>order.size())throw new IllegalArgumentException("Invalid position");
            order.add(position,key);ContentValues v=new ContentValues();v.put("parent_id",parent);db.update("tasks",v,"id=?",new String[]{key});
            for(int i=0;i<order.size();i++){db.execSQL("UPDATE tasks SET position=? WHERE id=?",new Object[]{i,order.get(i)});if(!order.get(i).equals(key))touch(db,"tasks",order.get(i));}
            touch(db,"tasks",key);return require(db,"tasks",key);
        }
        case "task.delete": {
            revision(require(db,"tasks",key),p);
            db.execSQL("WITH RECURSIVE subtree(id) AS (SELECT ? UNION ALL SELECT t.id FROM tasks t JOIN subtree s ON t.parent_id=s.id) INSERT OR IGNORE INTO sync_hidden(kind,source_id) SELECT 'task',ai_key FROM tasks WHERE id IN subtree AND ai_key IS NOT NULL",new Object[]{key});
            db.delete("tasks","id=?",new String[]{key});return new JSONObject().put("deleted",key);
        }
        case "session.list": return new JSONObject().put("items",rows(db,"SELECT s.*, CASE WHEN source='codex' THEN (SELECT count(*) FROM sync_items i WHERE i.session_id=s.id AND i.generation=s.generation) ELSE (SELECT count(*) FROM messages m WHERE m.session_id=s.id) END AS message_count FROM sessions s WHERE source_id IS NULL OR source_id NOT IN (SELECT source_id FROM sync_hidden WHERE kind='automation') ORDER BY updated_at DESC,id"));
        case "session.get": {
            if(key.startsWith("assistant:")){JSONArray aliases=rows(db,"SELECT value FROM settings WHERE key=?","session.alias:"+key);if(aliases.length()>0)key=aliases.getJSONObject(0).getString("value");}
            JSONObject row=require(db,"sessions",key);
            if(row.optString("source").equals("codex"))return new WorkspaceSync(this,db).readSession(row,p);
            int limit=p.optInt("limit",100);long before=p.optLong("before_row",Long.MAX_VALUE);
            if(limit<1||limit>500||before<1)throw new IllegalArgumentException("Invalid pagination");
            JSONArray messages=rows(db,"SELECT * FROM (SELECT rowid AS sequence,* FROM messages WHERE session_id=? AND rowid<? ORDER BY rowid DESC LIMIT ?) ORDER BY sequence",key,Long.toString(before),Integer.toString(limit));
            long oldest=messages.length()==0?0:messages.getJSONObject(0).getLong("sequence");
            boolean more=oldest>0&&rows(db,"SELECT id FROM messages WHERE session_id=? AND rowid<? LIMIT 1",key,Long.toString(oldest)).length()>0;
            return row.put("messages",messages).put("has_more",more).put("next_before_row",oldest)
                .put("tasks",rows(db,"SELECT t.* FROM tasks t JOIN task_session r ON r.task_id=t.id WHERE r.session_id=? ORDER BY t.updated_at DESC,t.id",key));
        }
        case "session.create": {
            String id=id();ContentValues v=base(id);v.put("title",text(p,"title",300,false));v.put("updated_at",now());db.insertOrThrow("sessions",null,v);
            String task=nullable(p,"task_id");if(task!=null)link(db,task,id);
            return require(db,"sessions",id);
        }
        case "session.update": {
            if(require(db,"sessions",key).optString("source").equals("codex"))throw new IllegalArgumentException("请在 AIOS 中重命名此会话");
            revision(require(db,"sessions",key),p);ContentValues v=new ContentValues();v.put("title",text(p,"title",300,false));db.update("sessions",v,"id=?",new String[]{key});touch(db,"sessions",key);return require(db,"sessions",key);
        }
        case "session.delete": {
            JSONObject source=require(db,"sessions",key);revision(source,p);
            if(Set.of("codex","assistant_pending").contains(source.optString("source")))db.execSQL("INSERT OR IGNORE INTO sync_hidden(kind,source_id) VALUES('session',?)",new Object[]{source.getString("source_id")});
            memory.deleteSession(key,p.optBoolean("delete_memories",false));
            String thread=source.optString("source_id");if(!thread.isEmpty()){if(p.optBoolean("delete_skills",false))new WorkspaceSkills(this,db).deleteLearnedFromThread(thread);db.execSQL("DELETE FROM skill_evidence WHERE id IN (SELECT id FROM skill_evidence_context WHERE thread_id=?)",new Object[]{thread});db.delete("skill_evidence_context","thread_id=?",new String[]{thread});}
            db.delete("sessions","id=?",new String[]{key});return new JSONObject().put("deleted",key);
        }
        case "message.append": {
            String session=p.getString("session_id");require(db,"sessions",session);
            if(require(db,"sessions",session).optString("source").equals("codex"))throw new IllegalArgumentException("请在 AIOS 中继续此会话");
            String role=p.getString("role");if(!Set.of("user","assistant","system").contains(role))throw new IllegalArgumentException("Invalid role");
            // A stable caller-supplied ID makes retries safe without losing equal consecutive messages.
            String id=p.optString("message_id",id());if(id.isEmpty()||id.length()>100)throw new IllegalArgumentException("Invalid message_id");
            String body=text(p,"content_md",1000000,false);
            JSONArray existing=rows(db,"SELECT * FROM messages WHERE id=?",id);
            if(existing.length()>0) {
                JSONObject old=existing.getJSONObject(0);
                if(!old.getString("session_id").equals(session)||!old.getString("role").equals(role)||!old.getString("content_md").equals(body))throw new IllegalArgumentException("message_id already used for different content");
                return old;
            }
            ContentValues v=base(id);v.put("session_id",session);v.put("role",role);v.put("content_md",body);db.insertOrThrow("messages",null,v);touch(db,"sessions",session);memory.indexMessage(id,session,role,body);return require(db,"messages",id);
        }
        case "relation.link": link(db,p.getString("task_id"),p.getString("session_id"));return new JSONObject().put("linked",true);
        case "relation.unlink": db.delete("task_session","task_id=? AND session_id=?",new String[]{p.getString("task_id"),p.getString("session_id")});return new JSONObject().put("linked",false);
        case "attachment.add": {
            String task=p.getString("task_id");require(db,"tasks",task);
            String uri=text(p,"uri",8192,false), mode=p.getString("storage_mode");
            if(!Set.of("reference","copy").contains(mode))throw new IllegalArgumentException("Invalid storage mode");
            if(!uri.startsWith("content://")&&!uri.startsWith("file://"))throw new IllegalArgumentException("Expected local media URI");
            String id=shareId(p,"attachment");if(p.has("share_key")){JSONArray existing=rows(db,"SELECT * FROM attachments WHERE id=?",id);if(existing.length()>0)return existing.getJSONObject(0);}ContentValues v=base(id);v.put("task_id",task);v.put("uri",uri);v.put("storage_mode",mode);v.put("name",text(p,"name",500,false));v.put("mime",text(p,"mime",200,false));
            db.insertOrThrow("attachments",null,v);
            String body=require(db,"tasks",task).getString("content_md");ContentValues note=new ContentValues();
            note.put("content_md",body+"\n["+v.getAsString("name").replace("[","").replace("]","").replace('\n',' ')+"](aios-attachment:"+id+")\n");
            note.put("user_content",1);
            db.update("tasks",note,"id=?",new String[]{task});touch(db,"tasks",task);return require(db,"attachments",id);
        }
        case "attachment.get": return require(db,"attachments",key);
        case "attachment.used_uris": return new JSONObject().put("items",rows(db,"SELECT uri FROM attachments ORDER BY uri"));
        case "attachment.relink": {
            JSONObject row=require(db,"attachments",key);
            String uri=text(p,"uri",8192,false);if(!uri.startsWith("content://"))throw new IllegalArgumentException("Expected content URI");
            ContentValues v=new ContentValues();v.put("uri",uri);v.put("storage_mode","reference");
            if(p.has("name"))v.put("name",text(p,"name",500,false));if(p.has("mime"))v.put("mime",text(p,"mime",200,false));
            db.update("attachments",v,"id=?",new String[]{key});touch(db,"tasks",row.getString("task_id"));return require(db,"attachments",key);
        }
        case "attachment.delete": {
            JSONObject row=require(db,"attachments",key);String task=row.getString("task_id");db.delete("attachments","id=?",new String[]{key});
            String body=require(db,"tasks",task).getString("content_md");ContentValues note=new ContentValues();note.put("content_md",body.replaceAll("!?\\[[^]\\n]*]\\(aios-attachment:"+java.util.regex.Pattern.quote(key)+"\\)",""));
            note.put("user_content",1);
            db.update("tasks",note,"id=?",new String[]{task});touch(db,"tasks",task);return new JSONObject().put("deleted",key);
        }
        case "settings.get": return new JSONObject().put("items",rows(db,"SELECT * FROM settings ORDER BY key"));
        case "settings.set": {
            String value=p.getString("value");
            String setting=p.getString("key");
            if(!(setting.equals("media_mode")&&Set.of("reference","copy").contains(value))&&!(setting.equals("memory_auto_save")&&Set.of("true","false").contains(value)))throw new IllegalArgumentException("Unsupported setting");
            ContentValues v=new ContentValues();v.put("key",setting);v.put("value",value);db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);return new JSONObject().put("key",setting).put("value",value);
        }
        default: throw new IllegalArgumentException("Unknown workspace operation: "+op);
        }
    }
    private String status(String status) {
        if(!Set.of("note","todo","doing","done").contains(status))throw new IllegalArgumentException("Invalid task status");return status;
    }
    private JSONArray siblings(SQLiteDatabase db,String parent) throws Exception {
        return parent==null?rows(db,"SELECT id FROM tasks WHERE parent_id IS NULL ORDER BY position,created_at,id")
            :rows(db,"SELECT id FROM tasks WHERE parent_id=? ORDER BY position,created_at,id",parent);
    }
    private void link(SQLiteDatabase db,String task,String session) throws Exception {
        require(db,"tasks",task);require(db,"sessions",session);ContentValues v=new ContentValues();v.put("task_id",task);v.put("session_id",session);v.put("created_at",now());db.insertWithOnConflict("task_session",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
