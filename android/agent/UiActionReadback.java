package os.aios.agent;

import org.json.*;
import java.util.Set;

/** Append a bounded readback, without ever retrying an input or changing its outcome. */
final class UiActionReadback {
    /** On-demand sampling, never an event-driven screenshot loop. */
    static final class Cadence {
        private final java.util.Map<String,Long> last=new java.util.HashMap<>();
        synchronized long reserve(String session,long now){
            last.entrySet().removeIf(e->now-e.getValue()>60000);
            Long previous=last.get(session);
            long next=previous==null?now:Math.max(now,previous+500);
            last.put(session,next);return next-now;
        }
    }
    static String target(JSONObject node,int x,int y)throws Exception {
        JSONArray b=node.optJSONArray("bounds");
        if(b==null||b.length()!=4||x<b.getInt(0)||y<b.getInt(1)||x>=b.getInt(2)||y>=b.getInt(3))return "";
        JSONArray children=node.optJSONArray("children");
        if(children!=null)for(int i=children.length()-1;i>=0;i--){String found=target(children.getJSONObject(i),x,y);if(!found.isEmpty())return found;}
        if(!node.optBoolean("clickable")||!node.optBoolean("enabled"))return "";
        return new JSONArray().put(node.optString("id")).put(node.optString("class")).put(node.optString("text"))
            .put(node.optString("description")).put(b).toString();
    }
    static boolean sameTarget(JSONObject before,JSONObject after,int x,int y)throws Exception {
        if(before==null||after==null)return false;
        String original=target(before,x,y);return !original.isEmpty()&&original.equals(target(after,x,y));
    }
    /** Keep action deduplication durable without persisting large ephemeral screenshots. */
    static JSONObject durable(JSONObject receipt)throws Exception{
        JSONObject copy=new JSONObject(receipt.toString());
        if(copy.has("png_base64")){
            copy.remove("png_base64");copy.remove("observation_id");copy.remove("observation_token");
            copy.put("requires_observation",true).put("image_omitted_from_journal",true);
            copy.put("next_step","This is a saved action receipt, not a current image. Read current tree/screenshot; do not replay the action.");
        }
        JSONObject observation=copy.optJSONObject("observation");if(observation!=null)copy.put("observation",durable(observation));
        return copy;
    }
    interface Reader { JSONObject read(JSONObject request) throws Exception; }
    static JSONObject complete(JSONObject request, JSONObject receipt, Reader reader) throws Exception {
        String operation=request.optString("op"),state=receipt.optString("state");
        if(!Set.of("tap","double_tap","long_press","swipe","gesture","text","back").contains(operation))return receipt;
        boolean rejected=state.equals("observation_required")&&!receipt.optBoolean("executed",false);
        boolean accepted=receipt.optBoolean("executed",false)&&Set.of("active","action_submitted").contains(state);
        String session=receipt.optString("session_id");
        if((!accepted&&!rejected)||session.isEmpty()||!session.equals(request.optString("session_id")))return receipt;
        // Let a submitted gesture finish before observing. This is not a page-ready assertion.
        receipt.put("input_outcome",accepted?"submitted_once":"not_executed").put("effect_verified",false);
        JSONObject observed;
        try {
            if(accepted)Thread.sleep(operation.equals("long_press")?750:operation.equals("gesture")?Math.min(5500,request.optInt("hold_ms")+request.optInt("duration_ms",400)+100):operation.equals("swipe")?Math.min(5200,request.optInt("duration_ms",400)+100):180);
            observed=reader.read(new JSONObject().put("op","tree").put("session_id",session));
            if("observation_unavailable".equals(observed.optString("state"))){
                Thread.sleep(150);
                observed=reader.read(new JSONObject().put("op","tree").put("session_id",session));
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();return receipt.put("observation_read_failed",true).put("next_step","Input outcome is unchanged. Retry only tree/screenshot; never replay this input.");}
        catch(Exception e){return receipt.put("observation_read_failed",true).put("observation_error",e.getClass().getSimpleName()).put("next_step","Input outcome is unchanged. Retry only tree/screenshot; never replay this input.");}
        // Two successive post-input tree samples reduce transition races. Equality is only
        // structural stability, never proof that a semantic task has completed.
        if(accepted&&observed.has("windows")&&"active".equals(observed.optString("state"))){
            boolean stable=false;int samples=1;
            try{for(int i=0;i<2;i++){
                Thread.sleep(500);JSONObject next=reader.read(new JSONObject().put("op","tree").put("session_id",session));samples++;
                stable=next.has("windows")&&observed.getJSONArray("windows").toString().equals(next.getJSONArray("windows").toString());
                observed=next;if(stable||!observed.has("windows"))break;
            }}catch(InterruptedException e){Thread.currentThread().interrupt();observed=new JSONObject().put("state","observation_unavailable").put("session_id",session);}
            catch(Exception e){observed=new JSONObject().put("state","observation_unavailable").put("session_id",session).put("reason",e.getClass().getSimpleName());}
            observed.put("structure_stable",stable).put("post_input_samples",samples);
        }
        receipt.put("observation",observed);
        boolean valid=session.equals(observed.optString("session_id"))&&"active".equals(observed.optString("state"))
            &&!observed.optString("observation_id").isEmpty()&&(observed.has("windows")||observed.has("png_base64"));
        receipt.put("observation_read_unavailable",!valid).put("observation_outcome",valid?"available":"unavailable");
        if(valid)receipt.put("requires_observation",false).put("next_step",rejected?
            "Input was NOT executed. Inspect observation, choose the target again and use its observation_id; do not replay old coordinates or end the task just because observation expired.":
            "Input was submitted once. Inspect observation to verify the effect and decide the next action using its observation_id. The page may still be loading.");
        else receipt.put("next_step","Inspect observation state; retry only observation if unavailable. Respect pause/takeover/authorization. Never replay the input.");
        return receipt;
    }
}
