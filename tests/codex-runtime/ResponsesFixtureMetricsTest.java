package os.aios.codex;
import org.json.*;
public final class ResponsesFixtureMetricsTest{
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] a)throws Exception{String one="data:image/png;base64,AAAA",two="data:image/png;base64,BBBBBB";JSONArray input=new JSONArray().put(new JSONObject().put("content",new JSONArray().put(new JSONObject().put("image_url",one)).put(new JSONObject().put("image_url",one)))).put(new JSONObject().put("nested",two));JSONObject m=ResponsesFixture.measure(input);check(m.getLong("image_references")==3);check(m.getLong("unique_images")==2);check(m.getLong("encoded_characters")==14);check(!m.toString().contains("AAAA"));System.out.println("ResponsesFixtureMetricsTest passed");}
}
