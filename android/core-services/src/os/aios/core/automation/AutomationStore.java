package os.aios.core.automation;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

/** Separate durable database; all methods are serialized by AutomationService's monitor. */
final class AutomationStore extends SQLiteOpenHelper {
    AutomationStore(Context c){this(c,"aios_automation.db");}
    AutomationStore(Context c,String database){super(c,database,null,6);setWriteAheadLoggingEnabled(true);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY, job_key TEXT UNIQUE NOT NULL, doc TEXT NOT NULL)");
        db.execSQL("CREATE TABLE runs(id TEXT PRIMARY KEY, job_id TEXT NOT NULL, started INTEGER NOT NULL, doc TEXT NOT NULL)");
        db.execSQL("CREATE INDEX runs_job ON runs(job_id,started DESC)");
        db.execSQL("CREATE TABLE versions(job_id TEXT NOT NULL, revision INTEGER NOT NULL, doc TEXT NOT NULL, PRIMARY KEY(job_id,revision))");
        createRuntime(db);
        createNative(db);
        AssistantScheduler.schema(db);
    }
    public void onUpgrade(SQLiteDatabase db,int a,int b){
        if(a<6)AssistantScheduler.schema(db);
        if(a<5)createNative(db);
        if(a<2)createRuntime(db);
        if(a>=2&&a<3)db.execSQL("ALTER TABLE graph_nodes ADD COLUMN trigger_spec TEXT NOT NULL DEFAULT '{}'");
        if(a>=2&&a<4)db.execSQL("ALTER TABLE graph_nodes ADD COLUMN resource TEXT NOT NULL DEFAULT ''");
    }
    private static void createNative(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS native_pipelines(id TEXT PRIMARY KEY,doc TEXT NOT NULL)");}
    private static void createRuntime(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS graphs(id TEXT NOT NULL,version INTEGER NOT NULL,definition TEXT NOT NULL,created_at INTEGER NOT NULL,PRIMARY KEY(id,version))");
        db.execSQL("CREATE TABLE IF NOT EXISTS graph_runs(id TEXT PRIMARY KEY,graph_id TEXT NOT NULL,graph_version INTEGER NOT NULL,occurrence TEXT NOT NULL,state TEXT NOT NULL,task_id TEXT NOT NULL DEFAULT '',authorization_id TEXT NOT NULL,generation INTEGER NOT NULL DEFAULT 1,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(graph_id,occurrence))");
        db.execSQL("CREATE TABLE IF NOT EXISTS graph_nodes(run_id TEXT NOT NULL REFERENCES graph_runs(id) ON DELETE CASCADE,node_id TEXT NOT NULL,kind TEXT NOT NULL,state TEXT NOT NULL,generation INTEGER NOT NULL DEFAULT 1,attempt INTEGER NOT NULL DEFAULT 0,lease_owner TEXT NOT NULL DEFAULT '',lease_until INTEGER NOT NULL DEFAULT 0,not_before INTEGER NOT NULL DEFAULT 0,request_id TEXT NOT NULL,param_fingerprint TEXT NOT NULL,side_effect TEXT NOT NULL,result TEXT NOT NULL DEFAULT '{}',error TEXT NOT NULL DEFAULT '',updated_at INTEGER NOT NULL,trigger_spec TEXT NOT NULL DEFAULT '{}',resource TEXT NOT NULL DEFAULT '',PRIMARY KEY(run_id,node_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS graph_edges(run_id TEXT NOT NULL,node_id TEXT NOT NULL,requires_node TEXT NOT NULL,on_state TEXT NOT NULL DEFAULT 'succeeded',PRIMARY KEY(run_id,node_id,requires_node))");
        db.execSQL("CREATE TABLE IF NOT EXISTS graph_attempts(id TEXT PRIMARY KEY,run_id TEXT NOT NULL,node_id TEXT NOT NULL,attempt INTEGER NOT NULL,generation INTEGER NOT NULL,state TEXT NOT NULL,intent TEXT NOT NULL,receipt TEXT NOT NULL DEFAULT '{}',started_at INTEGER NOT NULL,finished_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS task_authorizations(id TEXT PRIMARY KEY,doc TEXT NOT NULL,revoked INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS resource_leases(resource TEXT PRIMARY KEY,id TEXT UNIQUE NOT NULL,owner TEXT NOT NULL,generation INTEGER NOT NULL,expires_at INTEGER NOT NULL)");
    }
    SQLiteDatabase database(){return getWritableDatabase();}
    JSONObject get(String id)throws Exception{return one("SELECT doc FROM jobs WHERE id=?",id);}
    JSONObject byKey(String key)throws Exception{return one("SELECT doc FROM jobs WHERE job_key=?",key);}
    JSONObject one(String sql,String arg)throws Exception{
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{arg})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}
    }
    JSONArray jobs()throws Exception{
        JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT doc FROM jobs ORDER BY rowid DESC",null)){
            while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));
        }return rows;
    }
    void save(JSONObject job,boolean version)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            ContentValues v=new ContentValues();v.put("id",job.getString("id"));v.put("job_key",job.getString("job_key"));v.put("doc",job.toString());
            db.insertWithOnConflict("jobs",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            if(version){ContentValues h=new ContentValues();h.put("job_id",job.getString("id"));h.put("revision",job.getLong("revision"));h.put("doc",job.toString());db.insertOrThrow("versions",null,h);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    JSONObject run(String id)throws Exception{return one("SELECT doc FROM runs WHERE id=?",id);}
    void saveRun(JSONObject run)throws Exception{
        ContentValues v=new ContentValues();v.put("id",run.getString("id"));v.put("job_id",run.getString("job_id"));v.put("started",run.getLong("started_at"));v.put("doc",run.toString());
        getWritableDatabase().insertWithOnConflict("runs",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        // Keep the latest 100 runs per job, including all in-progress runs.
        // Records remain durable for request deduplication; readers are bounded to 100.
    }
    JSONArray runs(String id,int limit)throws Exception{
        JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT doc FROM runs WHERE job_id=? ORDER BY started DESC LIMIT ?",new String[]{id,Integer.toString(limit)})){
            while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));
        }return rows;
    }
    JSONArray versions(String id)throws Exception{
        JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT doc FROM versions WHERE job_id=? ORDER BY revision DESC LIMIT 100",new String[]{id})){
            while(c.moveToNext())rows.put(new JSONObject(c.getString(0)));
        }return rows;
    }
}
