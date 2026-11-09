package os.aios.codex;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Deterministic protocol fixture; bundled only in debug APK, runs entirely on the phone. */
public final class ResponsesFixture implements AutoCloseable {
    final ServerSocket server;
    final ExecutorService workers=Executors.newCachedThreadPool();
    volatile boolean closed;
    volatile JSONObject lastMetrics=new JSONObject();
    public ResponsesFixture()throws Exception{server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));workers.execute(()->{
        while(!closed)try{Socket s=server.accept();workers.execute(()->handle(s));}catch(IOException ignored){}
    });}
    public String url(){return "http://127.0.0.1:"+server.getLocalPort()+"/v1";}
    void handle(Socket socket){try(Socket s=socket){s.setSoTimeout(10000);InputStream input=s.getInputStream();String request=AndroidNetwork.line(input);int length=0;
        for(int i=0;i<80;i++){String line=AndroidNetwork.line(input);if(line==null||line.isEmpty())break;if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());}
        if(request==null||!request.matches("POST /+v1/responses HTTP/1\\.[01]")||length<=0||length>2_000_000)throw new IOException("Unexpected request: "+request+" length="+length);
        JSONObject body=new JSONObject(new String(input.readNBytes(length),StandardCharsets.UTF_8));
        if(!body.optString("model").equals("aios-fixture"))throw new IOException("Unexpected model");
        JSONArray history=body.optJSONArray("input");if(history==null)throw new IOException("Missing history");lastMetrics=measure(history);
        int lastUser=-1;String text="";for(int i=0;i<history.length();i++){JSONObject item=history.getJSONObject(i);if(item.optString("role").equals("user")){lastUser=i;text=item.toString();}}
        String toolResult=null;for(int i=lastUser+1;i<history.length();i++){JSONObject item=history.getJSONObject(i);if(item.optString("type").equals("function_call_output"))toolResult=item.optString("output");}
        if(text.contains("slow-test"))Thread.sleep(12000);
        String id="resp_"+UUID.randomUUID().toString().replace("-","");
        JSONObject item;
        if(text.contains("device-test")&&toolResult==null){
            item=new JSONObject().put("id","fc_"+id).put("type","function_call").put("call_id","call_"+id)
                .put("name","aios_device_info").put("arguments","{}").put("status","completed");
        }else{
            String answer=text.contains("device-test")?"DEVICE_RESULT "+toolResult:
                (history.toString().contains("memory-sentinel-7391")?"FIXTURE_MEMORY memory-sentinel-7391":"FIXTURE_OK");
            item=new JSONObject().put("id","msg_"+id).put("type","message").put("role","assistant").put("status","completed")
                .put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",answer).put("annotations",new JSONArray())));
        }
        JSONObject response=new JSONObject().put("id",id).put("object","response").put("created_at",System.currentTimeMillis()/1000)
            .put("model","aios-fixture").put("status","completed").put("output",new JSONArray().put(item))
            .put("usage",new JSONObject().put("input_tokens",10).put("output_tokens",10).put("total_tokens",20));
        OutputStream out=s.getOutputStream();out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        emit(out,"response.created",new JSONObject().put("response",new JSONObject().put("id",id).put("status","in_progress").put("output",new JSONArray())));
        emit(out,"response.output_item.added",new JSONObject().put("output_index",0).put("item",item));
        if(item.optString("type").equals("message"))emit(out,"response.output_text.delta",new JSONObject().put("output_index",0).put("content_index",0).put("item_id",item.getString("id")).put("delta",item.getJSONArray("content").getJSONObject(0).getString("text")));
        emit(out,"response.output_item.done",new JSONObject().put("output_index",0).put("item",item));
        emit(out,"response.completed",new JSONObject().put("response",response));
    }catch(Exception error){android.util.Log.e("CodexFixture","Fixture request failed: "+error);}}
    public JSONObject metrics()throws Exception{return new JSONObject(lastMetrics.toString());}
    static JSONObject measure(Object root)throws Exception{java.util.HashSet<String> hashes=new java.util.HashSet<>();long[] values=new long[2];measureInto(root,hashes,values);return new JSONObject().put("image_references",values[0]).put("unique_images",hashes.size()).put("encoded_characters",values[1]);}
    static void measureInto(Object value,java.util.Set<String> hashes,long[] metrics)throws Exception{if(value instanceof JSONObject){JSONObject o=(JSONObject)value;for(java.util.Iterator<String> it=o.keys();it.hasNext();)measureInto(o.get(it.next()),hashes,metrics);}else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)measureInto(a.get(i),hashes,metrics);}else if(value instanceof String){String s=(String)value;if(s.startsWith("data:image/")){metrics[0]++;int comma=s.indexOf(',');String encoded=comma<0?s:s.substring(comma+1);metrics[1]+=encoded.length();java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");hashes.add(java.util.HexFormat.of().formatHex(d.digest(encoded.getBytes(StandardCharsets.US_ASCII))));}}}
    static void emit(OutputStream out,String type,JSONObject value)throws Exception{value.put("type",type);out.write(("event: "+type+"\ndata: "+value+"\n\n").getBytes(StandardCharsets.UTF_8));out.flush();}
    public void close(){closed=true;try{server.close();}catch(IOException ignored){}workers.shutdownNow();}
}
