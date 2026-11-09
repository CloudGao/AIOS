package os.aios.core.workspace;

import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.*;

/** Upgrade-only reader for schemas 7–11. Failure rolls back schema upgrade, never drops data. */
final class LegacySkillMigration {
 static void upgrade(WorkspaceStore store,SQLiteDatabase db){try{
  if(store.rows(db,"SELECT name FROM sqlite_master WHERE type='table' AND name='skills'").length()==0)return;
  JSONArray rows=store.rows(db,"SELECT * FROM skills ORDER BY id");
  File backup=new File(store.skillRoot(),".legacy-v12/skills.json");backup.getParentFile().mkdirs();
  String original=rows.toString();
  if(!backup.exists()){try(FileOutputStream out=new FileOutputStream(backup)){out.write(original.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
  if(!os.aios.core.CompatFiles.readString(backup.toPath(),StandardCharsets.UTF_8).equals(original))throw new IOException("Legacy backup differs; retain table for recovery");
  SkillFileStore files=new SkillFileStore(store);
  for(int i=0;i<rows.length();i++){
   JSONObject old=rows.getJSONObject(i);String id=old.getString("id"),body=old.getString("content_md");
   JSONObject current=files.metadata(id,true),same=files.findDedupe(old.getString("dedupe"));
   if(same!=null&&!same.getString("id").equals(id))throw new IOException("Legacy identity/dedupe collision: "+id);
   if(current==null&&old.optInt("deleted")!=0&&body.isBlank())current=files.importLegacyTombstone(old);
   if(current==null){
    if(body.isBlank())throw new IOException("Legacy body missing without file asset: "+id);
    JSONObject imported=new JSONObject(old.toString());imported.remove("content_md");
    imported.put("enabled",old.optInt("enabled",1)!=0).put("user_edited",old.optInt("user_edited")!=0).put("deleted",false).put("asset_paths",new JSONArray());
    String evidence=old.optString("evidence","{}");imported.put("evidence",new JSONObject(evidence));
    files.importLegacy(imported,body);
    if(old.optInt("deleted")!=0)files.delete(id,old.getLong("revision"));
    current=files.metadata(id,true);
   }
   if(!current.getString("id").equals(id)||!current.getString("dedupe").equals(old.getString("dedupe"))||current.getLong("revision")<old.getLong("revision"))throw new IOException("Legacy identity/revision not covered: "+id);
   if(old.optInt("deleted")!=0&&!current.optBoolean("deleted"))throw new IOException("Legacy tombstone not covered: "+id);
   if(!current.optBoolean("deleted")){
    JSONObject asset=files.get(id);
    if(!body.isBlank()&&!asset.getString("content_md").trim().equals(body.trim()))throw new IOException("Legacy body conflict: "+id);
    if(old.optInt("user_edited")!=0&&!asset.optBoolean("user_edited"))throw new IOException("Manual edit protection missing: "+id);
    if(old.optInt("enabled",1)==0&&asset.optBoolean("enabled",true))throw new IOException("Disabled state not covered: "+id);
   }
   db.execSQL("INSERT OR REPLACE INTO skill_migrations(skill_id,state,error,migrated_at) VALUES(?,'verified_v12','',?)",new Object[]{id,WorkspaceStore.now()});
  }
  // The UTF-8 snapshot restores the old table; immutable version files remain in place.
  db.execSQL("DROP TABLE skills");
 }catch(Exception e){throw new IllegalStateException("Skill migration incomplete; original database retained",e);}}
}
