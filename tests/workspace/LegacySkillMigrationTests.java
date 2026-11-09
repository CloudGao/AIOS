package os.aios.core.workspace;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
final class LegacySkillMigrationTests {
 static int run(Context context)throws Exception{
  String name="skill-upgrade-"+java.util.UUID.randomUUID()+".db";WorkspaceStore before=new WorkspaceStore(context,name);SQLiteDatabase db=before.getWritableDatabase();
  db.execSQL("CREATE TABLE skills(id TEXT PRIMARY KEY,title TEXT,description TEXT,content_md TEXT,domain TEXT,package TEXT,source TEXT,enabled INTEGER,deleted INTEGER,user_edited INTEGER,dedupe TEXT,evidence TEXT,revision INTEGER,created_at INTEGER,updated_at INTEGER)");
  db.execSQL("INSERT INTO skills VALUES('legacy-one','旧方法','旧说明','不能丢失的正文','','','manual',0,0,1,':旧方法','{}',4,1,2)");db.execSQL("INSERT INTO skills VALUES('legacy-deleted','','','','','','manual',0,1,1,':removed','{}',5,1,2)");db.setVersion(11);before.close();
  try(WorkspaceStore after=new WorkspaceStore(context,name)){
   if(after.getWritableDatabase().getVersion()!=12)throw new AssertionError("schema upgrade");
   JSONObject skill=after.call("skill.get",new JSONObject().put("id","legacy-one")).getJSONObject("data");
   if(!skill.getString("content_md").trim().equals("不能丢失的正文")||skill.getLong("revision")!=4||skill.optBoolean("enabled",true)||!skill.optBoolean("user_edited"))throw new AssertionError("legacy coverage");
   java.io.File backup=new java.io.File(after.skillRoot(),".legacy-v12/skills.json");JSONArray rows=new JSONArray(java.nio.file.Files.readString(backup.toPath()));if(rows.length()!=2||!rows.toString().contains("不能丢失的正文"))throw new AssertionError("recoverable snapshot");
   JSONObject tomb=new SkillFileStore(after).metadata("legacy-deleted",true);if(tomb==null||!tomb.optBoolean("deleted")||tomb.getLong("revision")!=5)throw new AssertionError("deleted empty legacy tombstone preserved");
   if(after.rows(after.getReadableDatabase(),"SELECT name FROM sqlite_master WHERE name='skills'").length()!=0)throw new AssertionError("legacy table remains");
  }
  context.deleteDatabase(name);return 5;
 }
}
