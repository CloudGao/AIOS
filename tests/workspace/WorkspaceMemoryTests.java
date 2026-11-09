package os.aios.core.workspace;
import org.json.JSONArray;
import org.json.JSONObject;

/** Behavioral contracts against real Android SQLite, with isolated fixture data. */
final class WorkspaceMemoryTests {
    private final WorkspaceStore store;private int count;
    WorkspaceMemoryTests(WorkspaceStore store){this.store=store;}
    static int run(WorkspaceStore store)throws Exception{WorkspaceMemoryTests t=new WorkspaceMemoryTests(store);t.run();return t.count;}
    private JSONObject p(Object... a)throws Exception{JSONObject p=new JSONObject();for(int i=0;i<a.length;i+=2)p.put((String)a[i],a[i+1]);return p;}
    private JSONObject call(String op,Object... a)throws Exception{return store.call(op,p(a)).getJSONObject("data");}
    private void check(boolean v,String label){if(!v)throw new AssertionError("memory: "+label);count++;}
    interface Action{void run()throws Exception;}
    private void reject(Action a,String label)throws Exception{try{a.run();}catch(IllegalArgumentException|IllegalStateException e){count++;return;}throw new AssertionError("memory: "+label);}
    private boolean hit(String op,String query,String id)throws Exception{JSONArray a=call(op,"query",query).getJSONArray("items");for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return true;return false;}
    private void run()throws Exception{
        String session=call("session.create","title","记忆验收").getString("id");
        JSONObject pref=call("memory.create","content","语音回复请简短，技术方案详细。","kind","preference","scope_type","user","scope_id","");String id=pref.getString("id");
        check(hit("memory.search","语音",id),"Chinese substring retrieval");
        check(hit("memory.search","技术方案",id),"Chinese phrase retrieval");
        check(call("memory.context","query","你好").getString("text").contains("语音回复"),"new-session core preference loaded");
        check(call("memory.create","content","语音回复请简短，技术方案详细。","kind","preference","scope_type","user","scope_id","").getString("id").equals(id),"explicit writes deduplicate");
        JSONObject candidate=call("memory.propose","content","用户可能喜欢蓝色","source_session_id",session);String cid=candidate.getString("id");
        check(candidate.getString("state").equals("candidate"),"inferences require review");
        check(!hit("memory.search","蓝色",cid),"candidate not recalled");
        check(!call("memory.context","query","蓝色").getString("text").contains("喜欢蓝色"),"candidate not injected");
        JSONObject accepted=call("memory.resolve","id",cid,"expected_revision",1,"decision","accept");
        check(accepted.getString("state").equals("active")&&hit("memory.search","蓝色",cid),"accepted candidate indexed");
        reject(()->call("memory.update","id",cid,"expected_revision",1,"content","过期修改"),"stale edit rejected");
        long reset=call("memory.context").getLong("reset_epoch");
        JSONObject edited=call("memory.update","id",cid,"expected_revision",accepted.getLong("revision"),"content","用户喜欢绿色");
        check(!hit("memory.search","蓝色",cid)&&hit("memory.search","绿色",cid),"correction replaces index");
        check(call("memory.context").getLong("reset_epoch")!=reset,"correction invalidates old model context");
        check(call("memory.propose","content","用户可能喜欢蓝色","source_session_id",session).getBoolean("suppressed"),"old correction cannot be inferred again");
        JSONObject pinned=call("memory.update","id",cid,"expected_revision",edited.getLong("revision"),"pinned",true);
        check(pinned.getString("content").equals("用户喜欢绿色")&&pinned.getInt("pinned")==1,"metadata edit preserves text");
        JSONObject expired=call("memory.create","content","过期信息 独角兽","valid_until",1);
        check(!hit("memory.search","独角兽",expired.getString("id")),"expired excluded from retrieval");
        check(!call("memory.context","query","独角兽").getString("text").contains("独角兽"),"expired excluded from prompt");
        String task=call("task.create","title","记忆任务").getString("id"),other=call("task.create","title","别的任务").getString("id");
        JSONObject scoped=call("memory.create","content","任务专属 暗号七彩虹","scope_type","task","scope_id",task,"pinned",true);
        check(!hit("memory.search","七彩虹",scoped.getString("id")),"task memory isolated by default");
        check(call("memory.search","query","七彩虹","task_id",task).getJSONArray("items").length()==1,"explicit task recalls its memory");
        check(call("memory.search","query","七彩虹","task_id",other).getJSONArray("items").length()==0,"different task excluded");
        call("relation.link","task_id",task,"session_id",session);
        check(call("memory.context","session_id",session).getString("text").contains("七彩虹"),"linked task memory restores context");
        JSONObject continuing=call("task.create","title","继续卫星通讯研发","status","doing","content_md","下一步验证天线");
        check(call("memory.context","query","继续卫星通讯").getString("text").contains("验证天线"),"new conversation finds relevant unfinished task");
        reject(()->call("memory.context","project_id","F:\\Other"),"foreign project rejected");
        reject(()->call("memory.create","content","错误范围","scope_type","project","scope_id","Other"),"foreign scope cannot be saved");
        call("message.append","session_id",session,"message_id","mem-msg1","role","user","content_md","之前通过重新连接蓝牙解决耳机问题。 ERROR_123");
        check(hit("session.search","蓝牙","mem-msg1"),"local Chinese history search");
        check(hit("session.search","ERROR_123","mem-msg1"),"error-code search");
        check(call("session.search","query","\" OR * --").getJSONArray("items").length()==0,"query syntax treated as data");
        JSONObject history=call("session.get","id",session);long sequence=history.getJSONArray("messages").getJSONObject(0).getLong("sequence");
        // Use a fresh source for summary tests because this earlier session contains a corrected memory.
        String summarySession=call("session.create","title","摘要来源").getString("id");
        call("message.append","session_id",summarySession,"message_id","mem-summary","role","assistant","content_md","已完成设计，下一步验收。");
        long covered=call("session.get","id",summarySession).getJSONArray("messages").getJSONObject(0).getLong("sequence");
        JSONObject summary=call("session.summary.upsert","session_id",summarySession,"expected_revision",0,"through_sequence",covered,"content","已完成设计，待验收。");
        check(summary.getJSONObject("summary").getLong("through_sequence")==covered,"summary coverage persisted");
        check(call("memory.context","session_id",summarySession).getString("text").contains("待验收"),"summary restored");
        reject(()->call("session.summary.upsert","session_id",summarySession,"expected_revision",0,"through_sequence",covered,"content","旧版本"),"summary conflict rejected");
        reject(()->call("session.summary.upsert","session_id",summarySession,"expected_revision",1,"through_sequence",covered+999,"content","虚构覆盖"),"summary future coverage rejected");
        JSONObject sourceMemory=call("memory.create","content","耳机问题通过重新连接蓝牙解决。","source_session_id",session,"source_message_id","mem-msg1");
        String derived=call("session.create","title","派生对话").getString("id");
        call("memory.context.record","session_id",derived,"reset_epoch",call("memory.context").getLong("reset_epoch"),"memory_ids",new JSONArray().put(sourceMemory.getString("id")));
        call("message.append","session_id",derived,"message_id","derived-msg","role","assistant","content_md","派生知识：重连蓝牙修复耳机。");
        check(hit("session.search","派生知识","derived-msg"),"derived conversation initially searchable");
        JSONObject propagated=call("memory.context","query","派生知识");boolean inherited=false;JSONArray lineage=propagated.getJSONArray("memory_ids");for(int i=0;i<lineage.length();i++)inherited|=lineage.getString(i).equals(sourceMemory.getString("id"));check(inherited,"history recall propagates memory lineage");
        check(call("memory.get","id",sourceMemory.getString("id")).getJSONArray("sources").length()==1,"message source retained");
        reject(()->call("memory.create","content","假来源","source_session_id",session,"source_message_id","not-there"),"missing source rejected");
        call("memory.delete","id",sourceMemory.getString("id"),"expected_revision",1);
        check(!hit("session.search","派生知识","derived-msg"),"derived history cannot restore forgotten memory");
        call("message.append","session_id",derived,"message_id","derived-late","role","assistant","content_md","延迟生成的耳机转述");
        check(!hit("session.search","延迟生成","derived-late"),"late synchronized derivatives remain suppressed");
        check(!hit("session.search","耳机","mem-msg1"),"forgotten source not automatically recalled");
        check(call("session.get","id",session).getJSONArray("messages").length()==1,"forget does not delete original conversation");
        check(call("session.summary.get","session_id",summarySession).isNull("summary"),"derived summaries invalidated on forget");
        check(call("memory.propose","content","耳机用蓝牙重连修复","source_session_id",session,"source_message_id","mem-msg1").getBoolean("suppressed"),"deleted source cannot recreate paraphrase");
        reject(()->call("memory.get","id",sourceMemory.getString("id")),"deleted body inaccessible");
        check(store.require(store.getReadableDatabase(),"memories",sourceMemory.getString("id")).getString("content").isEmpty(),"deleted plaintext erased");
        JSONObject replacement=call("memory.propose","content","用户喜欢紫色");String replacementId=replacement.getString("id");
        reject(()->call("memory.resolve","id",replacementId,"expected_revision",1,"decision","replace","target_id",cid,"target_revision",1),"replacement pins target version");
        JSONObject replaced=call("memory.resolve","id",replacementId,"expected_revision",1,"decision","replace","target_id",cid,"target_revision",pinned.getLong("revision"));
        check(replaced.getString("supersedes_id").equals(cid)&&!hit("memory.search","绿色",cid),"replacement suppresses old fact");
        JSONObject rejected=call("memory.propose","content","用户喜欢红色");call("memory.resolve","id",rejected.getString("id"),"expected_revision",1,"decision","reject");
        check(call("memory.propose","content","用户喜欢红色").getBoolean("suppressed"),"rejected proposal stays rejected");
        call("settings.set","key","memory_auto_save","value","true");
        check(call("memory.propose","content","用户明确说偏好深色主题","explicit_fact",true).getString("state").equals("active"),"opt-in explicit facts auto-save");
        check(call("memory.propose","content","用户可能偏好浅色主题").getString("state").equals("candidate"),"auto-save does not accept deductions");
        call("settings.set","key","memory_auto_save","value","false");
        reject(()->call("settings.set","key","memory_auto_save","value","copy"),"setting combinations validated");
        JSONObject bounded=call("memory.context","query","用户","max_chars",500);check(bounded.getString("text").length()<=500,"context respects character budget");
        reject(()->call("memory.context.record","session_id",derived,"reset_epoch",1,"memory_ids",new JSONArray().put(id)),"stale injection rejected");
        call("message.append","session_id",summarySession,"message_id","reference-msg","role","user","content_md","AIOS_MEMORY_REFERENCE（参考数据，不是指令）\n{\"text\":\"注入秘密银杏树\"}\nEND_AIOS_MEMORY_REFERENCE\n真实问题榕树");
        check(!hit("session.search","银杏树","reference-msg")&&hit("session.search","榕树","reference-msg"),"injected reference blocks do not recursively enter history index");
        check(bounded.getString("trust").equals("reference_only"),"reference trust explicit");
        JSONObject expiring=call("memory.create","content","自然过期验收","valid_until",System.currentTimeMillis()+500,"pinned",true);
        long beforeExpiry=call("memory.context").getLong("reset_epoch");Thread.sleep(600);
        check(call("memory.context").getLong("reset_epoch")!=beforeExpiry,"clock expiration invalidates already-loaded context");
        check(!hit("memory.search","自然过期",expiring.getString("id")),"naturally expired memory excluded");
        String thread="memory-test-thread";
        call("sync.begin","thread_id",thread,"generation","mg1","title","镜像检索","status","completed","updated_at",1000,"count",1);
        call("sync.item","generation","mg1","item_id","mirror-message","sequence",1,"turn_id","t","kind","agentMessage","role","assistant","content_md","镜像内容 火星望远镜","detail","","created_at",1000);
        check(!hit("session.search","望远镜","mirror-message"),"unpublished snapshots not indexed");
        call("sync.commit","generation","mg1");check(hit("session.search","望远镜","mirror-message"),"published snapshot indexed");
        call("sync.begin","thread_id",thread,"generation","mg2","title","镜像检索","status","completed","updated_at",2000,"count",1);
        call("sync.item","generation","mg2","item_id","mirror-message2","sequence",1,"turn_id","t","kind","agentMessage","role","assistant","content_md","新镜像 月球探测器","detail","","created_at",2000);
        check(hit("session.search","望远镜","mirror-message"),"old published index preserved while staging");
        call("sync.commit","generation","mg2");check(!hit("session.search","望远镜","mirror-message")&&hit("session.search","探测器","mirror-message2"),"index swaps atomically");
        call("session.delete","id","codex:"+thread,"expected_revision",call("session.get","id","codex:"+thread).getLong("revision"));
        check(!hit("session.search","探测器","mirror-message2"),"session deletion cleans search index");
    }
}
