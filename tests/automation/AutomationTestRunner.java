package os.aios.agent;

import android.app.Instrumentation;
import android.os.Bundle;
import java.time.Instant;
import org.json.*;
import os.aios.automation.AutomationService;
import os.aios.core.automation.Schedule;

/** Real Android date/time and host authorization contracts. No workspace fixture writes. */
public final class AutomationTestRunner extends Instrumentation {
    private int count;
    interface Attempt {void run()throws Exception;}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
    void rejected(Attempt action,String label)throws Exception{try{action.run();}catch(Exception expected){count++;return;}throw new AssertionError(label);}
    JSONObject json(String text)throws Exception{return new JSONObject(text);}
    long at(String time){return Instant.parse(time).toEpochMilli();}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle out=new Bundle();boolean enabled=Authorization.enabled(getTargetContext());try{
        JSONObject daily=json("{kind:'daily',time:'09:00',zone:'Asia/Hong_Kong'}");
        check(Schedule.next(daily,at("2026-10-10T00:59:00Z"))==at("2026-10-10T01:00:00Z"),"daily named timezone");
        check(Schedule.next(daily,at("2026-10-10T01:00:00Z"))==at("2026-10-11T01:00:00Z"),"strictly next calendar occurrence");
        check(Schedule.next(daily,at("2026-10-13T03:00:00Z"))==at("2026-10-14T01:00:00Z"),"missed days coalesce");
        JSONObject weekly=json("{kind:'weekly',time:'09:00',zone:'Asia/Hong_Kong',day:1}");
        check(Schedule.next(weekly,at("2026-10-10T00:00:00Z"))==at("2026-10-12T01:00:00Z"),"ISO Monday weekly");
        JSONObject gap=json("{kind:'daily',time:'02:30',zone:'America/New_York'}");
        check(Schedule.next(gap,at("2026-03-08T05:00:00Z"))==at("2026-03-08T07:30:00Z"),"DST gap shifts forward");
        JSONObject overlap=json("{kind:'daily',time:'01:30',zone:'America/New_York'}");
        check(Schedule.next(overlap,at("2026-11-01T04:00:00Z"))==at("2026-11-01T05:30:00Z"),"DST overlap uses earlier offset once");
        rejected(()->Schedule.validate(json("{kind:'daily',time:'25:00',zone:'Asia/Hong_Kong'}")),"bad time rejected");
        rejected(()->Schedule.validate(json("{kind:'daily',time:'09:00',zone:'invented-zone'}")),"bad zone rejected");
        rejected(()->Schedule.validate(json("{kind:'weekly',time:'09:00',zone:'UTC',day:8}")),"bad weekday rejected");
        Authorization.set(getTargetContext(),true);String graph="test-"+java.util.UUID.randomUUID();
        AutomationService.get().putAuthorization(new JSONObject().put("id","test-grant").put("subject","instrumentation").put("task_id","").put("approved_version",1).put("capabilities",new JSONArray().put("scheduler.execute")).put("resources",new JSONArray()));
        JSONObject definition=new JSONObject().put("id",graph).put("nodes",new JSONArray().put(new JSONObject().put("id","wake").put("kind","trigger")).put(new JSONObject().put("id","work").put("kind","action").put("side_effect","non_idempotent_write").put("queryable",true))).put("edges",new JSONArray().put(new JSONObject().put("from","wake").put("to","work")));
        check(AutomationService.get().call("graph.put",definition).getInt("version")==1,"immutable graph version");
        JSONObject run=AutomationService.get().call("run.start",new JSONObject().put("graph_id",graph).put("occurrence","once").put("authorization_id","test-grant"));
        check(run.getJSONArray("nodes").length()==2,"graph run materializes nodes");
        JSONObject claim=AutomationService.get().call("node.claim",new JSONObject().put("run_id",run.getString("id")).put("node_id","work").put("owner","worker-a"));
        rejected(()->AutomationService.get().call("node.finish",new JSONObject().put("run_id",run.getString("id")).put("node_id","work").put("owner","stale").put("generation",claim.getInt("generation")).put("state","succeeded")),"stale lease rejected");
        JSONObject finished=AutomationService.get().call("node.finish",new JSONObject().put("run_id",run.getString("id")).put("node_id","work").put("owner","worker-a").put("generation",claim.getInt("generation")).put("state","succeeded"));check(finished.getString("state").equals("succeeded"),"AND graph completes after dependency");
        JSONObject cyclic=new JSONObject(definition.toString()).put("id",graph+"-cycle").put("edges",new JSONArray().put(new JSONObject().put("from","wake").put("to","work")).put(new JSONObject().put("from","work").put("to","wake")));rejected(()->AutomationService.get().call("graph.put",cyclic),"cycle rejected");
        Authorization.set(getTargetContext(),false);
        rejected(()->AutomationService.get().call("validate",json("{source:'function main(){return 1;}'}")),"authorization revoke blocks engine execution");
        check(!AutomationService.get().call("status",new JSONObject()).getBoolean("authorized"),"UI exposes authorization state");
        Authorization.set(getTargetContext(),enabled);
        out.putString("stream","PASS: "+count+" Android schedule/authorization assertions\n");finish(-1,out);
    }catch(Throwable e){Authorization.set(getTargetContext(),enabled);out.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,out);}}
}
