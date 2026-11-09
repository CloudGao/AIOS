package os.aios.agent;

import org.json.*;
import java.util.Set;

/** Bounded, demand-driven recovery of reads only. Does not resume, move screens, or replay input. */
final class UiObservationRecovery {
    interface Reader {JSONObject read(JSONObject request)throws Exception;}
    interface Pause {void waitMs(long millis)throws Exception;}
    static boolean retryable(JSONObject value){
        String state=value.optString("state"),session=value.optString("session_state",state);
        return Set.of("observation_unavailable","observation_pending").contains(state)
            ||Set.of("waiting_for_app","creating","preparing","resuming").contains(session);
    }
    static JSONObject resolve(JSONObject request,JSONObject first,Reader reader,Pause pause)throws Exception{
        String op=request.optString("op"),id=request.optString("session_id");
        if(!Set.of("tree","screenshot").contains(op)||id.isEmpty())return first;
        JSONObject result=first;int attempts=1,empty=0;
        for(int i=0;i<60&&retryable(result);i++){
            if(!id.equals(result.optString("session_id")))break;
            if("observation_unavailable".equals(result.optString("state"))&&++empty>=2)break;
            pause.waitMs(500);result=reader.read(request);attempts++;
        }
        boolean fallback=op.equals("tree")&&"observation_unavailable".equals(result.optString("state"))
            &&id.equals(result.optString("session_id"));
        if(fallback){
            JSONObject shot=new JSONObject(request.toString()).put("op","screenshot");
            for(int i=0;i<3;i++){
                pause.waitMs(500);result=reader.read(shot);attempts++;
                if(!retryable(result))break;
            }
            result.put("tree_available",false).put("requested_observation","tree");
            if(result.has("png_base64"))result.put("next_step","Tree unavailable; inspect the included image on this same screen. Use this image's width/height and observation_id for input. Do not move screens or stop solely because the tree is unavailable.");
        }
        return result.put("observation_attempts",attempts).put("observation_recovered",attempts>1&&!result.optString("observation_id").isEmpty())
            .put("page_ready_verified",false);
    }
}
