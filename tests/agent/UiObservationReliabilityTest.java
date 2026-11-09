package os.aios.agent;
import org.json.*;
public class UiObservationReliabilityTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static JSONObject result(String state)throws Exception{return new JSONObject().put("state",state).put("session_id","s");}
 static JSONObject read(String state)throws Exception{return result(state).put("observation_id","fresh").put("windows",new JSONArray());}
 public static void main(String[] args)throws Exception{
  int[] size=UiObservationFrame.imageSize(1080,2424);check(size[0]==912&&size[1]==2048);
  check(UiObservationFrame.imageSize(800,600)[0]==800);check(UiObservationFrame.imageSize(2424,1080)[0]==2048);
  JSONObject geometry=new JSONObject().put("width",912).put("height",2048).put("display_width",1080).put("display_height",2424).put("coordinate_space","image_pixels");
  UiObservationFrame frame=new UiObservationFrame(geometry);
  JSONObject q=new JSONObject().put("op","tap").put("x",380).put("y",858),mapped=frame.input(q);
  check(mapped.getInt("x")==450);check(mapped.getInt("y")==1016);check(q.getInt("x")==380);
  check(frame.x(911)==1079);check(frame.y(2047)==2423);check(frame.x(0)==0);
  for(int x:new int[]{-1,912}){try{frame.x(x);throw new AssertionError("accepted out of bounds");}catch(IllegalArgumentException expected){check(true);}}
  try{frame.input(new JSONObject(q.toString()).put("coordinate_space","display_pixels"));throw new AssertionError("double scale");}catch(IllegalArgumentException expected){check(true);}
  UiObservationFrame tree=new UiObservationFrame(new JSONObject().put("width",1080).put("height",2424));check(tree.input(new JSONObject(q.toString()).put("x",450).put("y",1016)).getInt("y")==1016);
  JSONObject swipe=frame.input(new JSONObject().put("op","swipe").put("x1",0).put("y1",0).put("x2",911).put("y2",2047));check(swipe.getInt("x2")==1079&&swipe.getInt("y2")==2423);
  JSONObject invalid=new JSONObject().put("bounds",new JSONArray(new int[]{42,2458,540,2424})).put("clickable",true);UiObservationFrame.sanitize(invalid,1080,2424);check(!invalid.getBoolean("bounds_valid"));check(!invalid.getBoolean("clickable"));check(invalid.has("raw_bounds"));
  JSONObject clip=new JSONObject().put("bounds",new JSONArray(new int[]{-2,-10,100,200}));UiObservationFrame.sanitize(clip,1080,2424);check(clip.getBoolean("bounds_valid"));check(clip.getJSONArray("bounds").getInt(0)==0);
  JSONObject request=new JSONObject().put("op","tree").put("session_id","s");int[] attempts={0};
  JSONObject recovered=UiObservationRecovery.resolve(request,result("session_unavailable").put("session_state","preparing"),v->{attempts[0]++;return read("active");},ms->{});
  check(attempts[0]==1&&recovered.getBoolean("observation_recovered"));check(!recovered.getBoolean("page_ready_verified"));
  attempts[0]=0;recovered=UiObservationRecovery.resolve(request,result("observation_unavailable"),v->{attempts[0]++;return v.getString("op").equals("tree")?result("observation_unavailable"):result("active").put("png_base64","fake").put("observation_id","shot");},ms->{});
  check(attempts[0]==2);check(recovered.has("png_base64"));check(!recovered.getBoolean("tree_available"));check(recovered.getString("observation_id").equals("shot"));
  for(String stop:new String[]{"paused","device_locked","permission_required","user_takeover","session_owned_by_other_conversation"}){JSONObject stopped=UiObservationRecovery.resolve(request,result(stop),v->{throw new AssertionError("must stop");},ms->{});check(stopped.getString("state").equals(stop));}
  attempts[0]=0;recovered=UiObservationRecovery.resolve(request,result("preparing"),v->{attempts[0]++;return result("preparing");},ms->{});check(attempts[0]==60);check(!recovered.getBoolean("observation_recovered"));
  recovered=UiObservationRecovery.resolve(request,result("observation_unavailable").put("session_id","other"),v->{throw new AssertionError("cross session retry");},ms->{});check(!recovered.has("png_base64"));
  JSONObject input=new JSONObject().put("op","tap").put("session_id","s");JSONObject receipt=UiActionReadback.complete(input,result("active").put("executed",true),v->result("active").put("observation_id","shot").put("png_base64","fake"));
  check(receipt.getBoolean("executed"));check(receipt.getString("input_outcome").equals("submitted_once"));check(receipt.getString("observation_outcome").equals("available"));check(!receipt.getBoolean("effect_verified"));
  JSONObject durable=UiActionReadback.durable(receipt);check(durable.getBoolean("executed"));check(durable.getString("input_outcome").equals("submitted_once"));check(!durable.getJSONObject("observation").has("png_base64"));check(!durable.getJSONObject("observation").has("observation_id"));check(receipt.getJSONObject("observation").has("png_base64"));
  System.out.println(checks+" observation reliability checks passed");
 }
}
