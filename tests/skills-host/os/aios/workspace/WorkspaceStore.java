package os.aios.core.workspace;
import org.json.*;import android.content.ContentValues;import android.database.sqlite.SQLiteDatabase;
/** Host boundary adapter. Business logic is the unmodified production WorkspaceSkills. */
public class WorkspaceStore{
 SQLiteDatabase getReadableDatabase(){return db;}
 SQLiteDatabase getWritableDatabase(){return db;}
 public String recordSkillEvidence(JSONObject p)throws Exception{String id=recordSkillEvidence(p.getString("tool"),p.optString("package"),p.optString("version"));db.execSQL("INSERT INTO skill_evidence_context VALUES(?,?,?,?)",new Object[]{id,p.optString("thread_id"),p.optString("request_id"),p.optString("session_id")});return id;}
 final SQLiteDatabase db; WorkspaceStore(SQLiteDatabase db){this.db=db;WorkspaceSkills.migrate(db);}
 static long now(){return System.currentTimeMillis();} static String id(){return java.util.UUID.randomUUID().toString();}
 static String text(JSONObject p,String key,int max,boolean empty)throws Exception{String value=p.getString(key);if((!empty&&value.trim().isEmpty())||value.length()>max)throw new IllegalArgumentException("Invalid "+key);return value;}
 JSONArray rows(SQLiteDatabase d,String sql,String... args){return d.rows(sql,args);}
 JSONObject require(SQLiteDatabase d,String table,String id)throws Exception{JSONArray a=rows(d,"SELECT * FROM "+table+" WHERE id=?",id);if(a.length()==0)throw new IllegalArgumentException("missing");return a.getJSONObject(0);}
 void revision(JSONObject row,JSONObject p)throws Exception{if(row.getLong("revision")!=p.getLong("expected_revision"))throw new IllegalStateException("conflict");}
 ContentValues base(String id){ContentValues v=new ContentValues();v.put("id",id);v.put("created_at",now());return v;}
 void touch(SQLiteDatabase d,String table,String id){d.execSQL("UPDATE "+table+" SET revision=revision+1,updated_at=? WHERE id=?",new Object[]{now(),id});}
 String skillAppLabel(String pkg){return pkg;}
 String appVersion="2";String skillAppVersion(String pkg){return appVersion;}
 java.io.File skillRoot(){java.io.File f=new java.io.File(System.getProperty("java.io.tmpdir"),"aios-skills-host-"+ProcessHandle.current().pid());f.mkdirs();return f;}
 public JSONObject call(String op,JSONObject p)throws Exception{return new JSONObject().put("state","succeeded").put("data",new WorkspaceSkills(this,db).call(op,p));}
 public String recordSkillEvidence(String tool,String pkg,String version){String id=id();ContentValues v=base(id);v.put("tool",tool);v.put("package",pkg);v.put("app_version",version);db.insertOrThrow("skill_evidence",null,v);return id;}
}
