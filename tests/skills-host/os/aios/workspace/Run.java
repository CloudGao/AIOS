package os.aios.core.workspace;
import android.database.sqlite.SQLiteDatabase;import org.json.*;
public class Run{
 public static void main(String[] args)throws Exception{try(SQLiteDatabase db=new SQLiteDatabase(args[0])){
 WorkspaceStore store=new WorkspaceStore(db);int n=WorkspaceSkillTests.run(store);
 JSONObject p=new JSONObject().put("title","version test").put("description","test").put("content_md","Read result").put("package","app.test").put("verification","readback").put("evidence_id",store.recordSkillEvidence("ui.tree","app.test",store.appVersion));
 JSONObject row=store.call("skill.learn",p).getJSONObject("data");String id=row.getString("id");
 store.appVersion="3";if(!store.call("skill.get",new JSONObject().put("id",id)).getJSONObject("data").getBoolean("requires_revalidation"))throw new AssertionError("version change");n++;
 store.appVersion="2";store.call("skill.update",new JSONObject().put("id",id).put("expected_revision",1).put("content_md","User method"));
 if(!store.call("skill.learn",p).getJSONObject("data").getString("content_md").trim().equals("User method"))throw new AssertionError("user priority");n++;
 p.put("package","other.app");boolean rejected=false;try{store.call("skill.learn",p);}catch(IllegalArgumentException e){rejected=true;}if(!rejected)throw new AssertionError("wrong app proof");n++;
 p.put("package","app.test");db.execSQL("UPDATE skill_evidence SET created_at=0");rejected=false;try{store.call("skill.learn",p);}catch(IllegalArgumentException e){rejected=true;}if(!rejected)throw new AssertionError("expired proof");n++;
 System.out.println("PASS: "+n+" skill business-logic assertions on host SQLite boundary adapter");
 }}
}
