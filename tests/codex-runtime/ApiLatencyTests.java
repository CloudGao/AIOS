package os.aios.codex;
import android.app.Instrumentation;import android.os.*;import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;
/** Same-device direct Responses timings; credentials remain solely inside Runtime UID. */
public final class ApiLatencyTests extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{ProviderStore store=new ProviderStore(getTargetContext());String provider=store.active();if(!provider.equals("openai")&&!provider.equals("custom"))throw new IllegalStateException("Expected existing OpenAI API configuration");JSONObject cfg=store.publicConfig(provider);String model=store.model(),effort=store.effort();if(effort.isEmpty())effort=store.defaultEffort(model);JSONArray samples=new JSONArray();
 for(int i=0;i<3;i++){
  URL url=new URL(cfg.getString("baseUrl").replaceAll("/+$","")+"/responses");if(!url.getHost().equals("api.openai.com"))throw new IllegalStateException("Official endpoint required");
  String proxy=AndroidNetwork.proxyUrl(getTargetContext());HttpURLConnection conn=(HttpURLConnection)(proxy.isEmpty()?url.openConnection():url.openConnection(new Proxy(Proxy.Type.HTTP,new InetSocketAddress(new URI(proxy).getHost(),new URI(proxy).getPort()))));
  conn.setConnectTimeout(30000);conn.setReadTimeout(90000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+store.secret(provider));
  JSONObject body=new JSONObject().put("model",model).put("input","Reply with exactly LATENCY_OK. Do not call tools or save memories.").put("stream",true).put("store",false);if(!effort.isEmpty())body.put("reasoning",new JSONObject().put("effort",effort));
  long start=SystemClock.elapsedRealtime(),first=-1,event=-1;StringBuilder reply=new StringBuilder();boolean completed=false;long headers;JSONObject usage=null;
  try{byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(bytes.length);try(OutputStream stream=conn.getOutputStream()){stream.write(bytes);}int status=conn.getResponseCode();headers=SystemClock.elapsedRealtime()-start;if(status!=200)throw new IOException("HTTP "+status);
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(!line.startsWith("data: ")||line.equals("data: [DONE]"))continue;JSONObject data=new JSONObject(line.substring(6));if(event<0)event=SystemClock.elapsedRealtime()-start;String type=data.optString("type");if(type.equals("response.output_text.delta")){if(first<0)first=SystemClock.elapsedRealtime()-start;reply.append(data.optString("delta"));}if(type.equals("response.completed")){completed=true;JSONObject response=data.optJSONObject("response");usage=response==null?null:response.optJSONObject("usage");break;}if(type.equals("response.failed")||type.equals("error"))throw new IOException("API stream "+type);}}
   if(!completed||!reply.toString().contains("LATENCY_OK"))throw new AssertionError("No completed expected answer");samples.put(new JSONObject().put("usage",usage==null?JSONObject.NULL:usage).put("server_processing_ms",conn.getHeaderField("openai-processing-ms")).put("headers_ms",headers).put("first_event_ms",event).put("first_text_ms",first).put("completion_ms",SystemClock.elapsedRealtime()-start));
  }finally{conn.disconnect();}
 }
 out.putString("result",new JSONObject().put("model",model).put("effort",effort).put("samples",samples).toString());finish(-1,out);
 }catch(Throwable t){out.putString("error",t.getClass().getSimpleName()+": "+t.getMessage());finish(0,out);}}
}
