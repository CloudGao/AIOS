package os.aios.core.workspace;
import org.json.*;
final class WorkspaceSkillTests {
    static int run(WorkspaceStore store)throws Exception{
        JSONObject manual=store.call("skill.create",new JSONObject().put("title","查询地图").put("description","查找地点").put("content_md","读取页面并核对地点")).getJSONObject("data");
        String id=manual.getString("id");
        if(!manual.getString("source").equals("manual"))throw new AssertionError("manual provenance");
        store.call("skill.update",new JSONObject().put("id",id).put("expected_revision",1).put("enabled",false));
        if(store.call("skill.search",new JSONObject().put("enabled_only",true)).getJSONObject("data").getLong("total")!=0)throw new AssertionError("disabled retrieval");
        boolean stale=false;try{store.call("skill.update",new JSONObject().put("id",id).put("expected_revision",1).put("title","overwritten"));}catch(IllegalStateException e){stale=true;}if(!stale)throw new AssertionError("revision conflict");
        JSONObject learn=new JSONObject().put("title","高德地点查询").put("description","搜索指定地点").put("content_md","输入 {地点} 并读取结果").put("package","com.autonavi.minimap").put("verification","页面显示查询结果").put("evidence_id","missing");
        boolean rejected=false;try{store.call("skill.learn",learn);}catch(IllegalArgumentException e){rejected=true;}if(!rejected)throw new AssertionError("fabricated evidence");
        String proof=store.recordSkillEvidence("ui.tree","com.autonavi.minimap",store.skillAppVersion("com.autonavi.minimap"));learn.put("evidence_id",proof);
        JSONObject learned=store.call("skill.learn",learn).getJSONObject("data");
        if(!learned.getString("source").equals("learned"))throw new AssertionError("learned provenance");
        JSONObject merged=store.call("skill.learn",learn).getJSONObject("data");
        if(!merged.getString("id").equals(learned.getString("id")))throw new AssertionError("deduplication");
        store.call("skill.delete",new JSONObject().put("id",merged.getString("id")).put("expected_revision",merged.getLong("revision")));
        if(!store.call("skill.learn",learn).getJSONObject("data").optString("ignored").equals("user_deleted"))throw new AssertionError("tombstone");
        JSONObject resources=new JSONObject().put("references/one.md","中文🙂分页测试").put("references/two.md","保留的其他功能");
        JSONObject pack=store.call("skill.create",new JSONObject().put("title","分页功能测试").put("description","测试有界读取").put("content_md","短正文").put("assets",resources)).getJSONObject("data");String packId=pack.getString("id");
        JSONObject view=store.call("skill.get",new JSONObject().put("id",packId).put("view","body")).getJSONObject("data");if(view.has("assets")||!view.getString("content_md").contains("短正文"))throw new AssertionError("progressive body read");
        StringBuilder text=new StringBuilder();int offset=0;for(;;){JSONObject page=store.call("skill.resource.get",new JSONObject().put("id",packId).put("revision",1).put("path","references/one.md").put("offset",offset).put("limit",5)).getJSONObject("data");text.append(page.getString("content"));if(page.optBoolean("complete"))break;int next=page.getInt("next_offset");if(next<=offset)throw new AssertionError("cursor progress");offset=next;}if(!text.toString().equals(resources.getString("references/one.md")))throw new AssertionError("UTF-8 pagination");
        JSONObject patched=store.call("skill.update",new JSONObject().put("id",packId).put("expected_revision",1).put("assets_patch",new JSONObject().put("references/one.md","新功能"))).getJSONObject("data");if(!store.call("skill.resource.get",new JSONObject().put("id",packId).put("revision",2).put("path","references/two.md")).getJSONObject("data").getString("content").equals(resources.getString("references/two.md")))throw new AssertionError("patch preserves unrelated resource");
        store.call("skill.update",new JSONObject().put("id",packId).put("expected_revision",2).put("enabled",false));JSONObject rolled=store.call("skill.rollback",new JSONObject().put("id",packId).put("expected_revision",3).put("target_revision",1)).getJSONObject("data");if(rolled.optBoolean("enabled",true))throw new AssertionError("rollback cannot re-enable");
        rejected=false;try{store.call("skill.resource.get",new JSONObject().put("id",packId).put("revision",1).put("path","references/one.md"));}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("disabled historical read");
        rejected=false;try{store.call("skill.get",new JSONObject().put("id","../escape"));}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("identity traversal");
        if(store.rows(store.getReadableDatabase(),"SELECT name FROM sqlite_master WHERE type='table' AND name='skills'").length()!=0)throw new AssertionError("fresh schema still creates skills table");
        String fresh=store.recordSkillEvidence("ui.tree","","");
        JSONObject learning=new JSONObject().put("title","分支验证测试").put("description","两个独立功能").put("content_md","先观察再执行指定功能").put("verification","已读回成功状态").put("evidence_id",fresh).put("capabilities",new JSONArray().put(new JSONObject().put("capability_id","a").put("name","功能甲").put("summary","甲方法").put("path","references/a.md")).put(new JSONObject().put("capability_id","b").put("name","功能乙").put("summary","乙方法").put("path","references/b.md"))).put("assets",new JSONObject().put("references/a.md","甲").put("references/b.md","乙"));
        JSONObject branch=store.call("skill.learn",learning).getJSONObject("data");String branchId=branch.getString("id");
        store.call("skill.report",new JSONObject().put("id",branchId).put("expected_revision",1).put("reason","method_error").put("execution_id","failure-one").put("capability_id","a"));
        JSONObject state=store.call("skill.get",new JSONObject().put("id",branchId).put("view","body")).getJSONObject("data");boolean paused=false,bPaused=false;JSONArray states=state.getJSONArray("health");for(int n=0;n<states.length();n++){JSONObject h=states.getJSONObject(n);paused|=h.optString("capability_id").equals("a")&&h.optString("state").equals("auto_paused");bPaused|=h.optString("capability_id").equals("b")&&h.optString("state").equals("auto_paused");}if(!paused||bPaused||state.getLong("revision")!=1)throw new AssertionError("failure scoped to capability and revision");
        learning.put("id",branchId).put("capability_id","a").put("evidence_id",store.recordSkillEvidence("ui.tree","",""));JSONObject reverified=store.call("skill.learn",learning).getJSONObject("data");if(reverified.getLong("revision")!=1||!reverified.getString("merge_status").equals("evidence_added"))throw new AssertionError("evidence does not create revision");
        states=store.call("skill.get",new JSONObject().put("id",branchId)).getJSONObject("data").getJSONArray("health");boolean available=false;for(int n=0;n<states.length();n++){JSONObject h=states.getJSONObject(n);available|=h.optString("capability_id").equals("a")&&h.optString("state").equals("available");}if(!available)throw new AssertionError("verified branch recovers");
        if(!store.call("skill.learn",learning).getJSONObject("data").getString("merge_status").equals("unchanged"))throw new AssertionError("repeat evidence not counted");
        learning.put("goal_id","renamed-goal");if(!store.call("skill.learn",learning).getJSONObject("data").getString("merge_status").equals("unchanged"))throw new AssertionError("goal rename cannot duplicate evidence");
        learning.put("_caller_thread","other-thread");rejected=false;try{store.call("skill.learn",learning);}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("unowned evidence rejected");
        String deleteThread="skill-delete-source";
        String deleteProof=store.recordSkillEvidence(new JSONObject().put("tool","ui.tree").put("thread_id",deleteThread).put("request_id","request").put("session_id","screen"));
        JSONObject toDelete=store.call("skill.learn",new JSONObject().put("title","来源删除测试").put("description","可删除的通用方法").put("content_md","读取测试结果").put("verification","已验证测试结果").put("evidence_id",deleteProof)).getJSONObject("data");
        new WorkspaceSkills(store,store.getWritableDatabase()).deleteLearnedFromThread(deleteThread);
        rejected=false;try{store.call("skill.get",new JSONObject().put("id",toDelete.getString("id")));}catch(Exception expected){rejected=true;}if(!rejected)throw new AssertionError("explicit source deletion removes learned asset");
        if(!store.call("skill.learn",new JSONObject().put("title","来源删除测试").put("description","可删除的通用方法").put("content_md","读取测试结果").put("verification","已验证测试结果").put("evidence_id",deleteProof)).getJSONObject("data").optString("ignored").equals("user_deleted"))throw new AssertionError("source deletion cannot revive");
        return 22;
    }
}
