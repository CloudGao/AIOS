package os.aios.core.workspace;
import java.io.File;import org.json.*;import android.content.ContentValues;import android.database.sqlite.SQLiteDatabase;
/** Host boundary adapter. Business logic is the unmodified production WorkspaceSkills. */
public class WorkspaceStore{
 final SQLiteDatabase db;final File skills=new File(System.getProperty("java.io.tmpdir"),"aios-dream-host-skills-"+java.util.UUID.randomUUID()); WorkspaceStore(SQLiteDatabase db){this.db=db;WorkspaceSkills.migrate(db);}
 static long now(){return System.currentTimeMillis();} static String id(){return java.util.UUID.randomUUID().toString();}
 static String text(JSONObject p,String key,int max,boolean empty)throws Exception{String value=p.getString(key);if((!empty&&value.trim().isEmpty())||value.length()>max)throw new IllegalArgumentException("Invalid "+key);return value;}
 JSONArray rows(SQLiteDatabase d,String sql,String... args){return d.rows(sql,args);}
 JSONObject require(SQLiteDatabase d,String table,String id)throws Exception{JSONArray a=rows(d,"SELECT * FROM "+table+" WHERE id=?",id);if(a.length()==0)throw new IllegalArgumentException("missing");return a.getJSONObject(0);}
 void revision(JSONObject row,JSONObject p)throws Exception{if(row.getLong("revision")!=p.getLong("expected_revision"))throw new IllegalStateException("conflict");}
 ContentValues base(String id){ContentValues v=new ContentValues();v.put("id",id);v.put("created_at",now());return v;}
 void touch(SQLiteDatabase d,String table,String id){d.execSQL("UPDATE "+table+" SET revision=revision+1,updated_at=? WHERE id=?",new Object[]{now(),id});}
 String skillAppLabel(String pkg){return pkg;}
 String skillAppVersion(String pkg){return "2";}
 File skillRoot(){skills.mkdirs();return skills;}
 JSONObject execute(SQLiteDatabase d,String op,JSONObject p)throws Exception{
 boolean update=op.equals("task.update");if(!update&&!op.equals("task.create"))throw new UnsupportedOperationException();
 String id=update?p.getString("id"):id();JSONObject before=update?require(d,"tasks",id):new JSONObject();if(update)revision(before,p);
 ContentValues v=new ContentValues();for(String key:new String[]{"title","content_md","status"})if(p.has(key))v.put(key,p.getString(key));WorkspaceDream.timeFields(p,v);
 if(update){d.update("tasks",v,"id=?",new String[]{id});touch(d,"tasks",id);}else{v.put("id",id);v.put("revision",1);v.put("updated_at",now());if(!p.has("status"))v.put("status","todo");d.insertOrThrow("tasks",null,v);}
 JSONObject after=require(d,"tasks",id);WorkspaceDream.history(this,d,before,after,p);return after;
 }
 JSONObject dream(String op,JSONObject p)throws Exception{db.execSQL("BEGIN");try{JSONObject result=new WorkspaceDream(this,db).call(op,p);db.execSQL("COMMIT");new WorkspaceDream(this,db).publishPending();return result;}catch(Exception e){db.execSQL("ROLLBACK");throw e;}}
 public JSONObject call(String op,JSONObject p)throws Exception{return new JSONObject().put("state","succeeded").put("data",new WorkspaceSkills(this,db).call(op,p));}
 public String recordSkillEvidence(String tool,String pkg,String version){String id=id();ContentValues v=base(id);v.put("tool",tool);v.put("package",pkg);v.put("app_version",version);db.insertOrThrow("skill_evidence",null,v);return id;}
}
