package os.aios.codex;

import android.content.Context;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Single bounded HTTP job. No Codex thread, history, tools, response chaining or payload logging. */
final class StatelessTextInference {
    static JSONObject body(String protocol,JSONObject request)throws Exception {
        String instruction=request.getString("instruction"),input=request.getString("input");
        if(instruction.length()>8000||input.length()>16000)throw new IllegalArgumentException("input_capacity");
        int tokens=Math.max(64,Math.min(512,request.optInt("max_tokens",128)));
        JSONObject body=new JSONObject().put("model",request.getString("model")).put("stream",false);
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content",input));
        JSONObject schema=request.optJSONObject("schema");
        if(protocol.equals(ProviderProtocol.RESPONSES)){
            body.put("store",false).put("instructions",instruction).put("input",messages).put("max_output_tokens",tokens);
            if(schema!=null)body.put("text",new JSONObject().put("format",new JSONObject().put("type","json_schema").put("name","notification_classification").put("strict",true).put("schema",schema)));
            else if(request.optBoolean("json"))body.put("text",new JSONObject().put("format",new JSONObject().put("type","json_object")));
        }else if(protocol.equals(ProviderProtocol.CHAT)){
            body.put("store",false).put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",instruction)).put(messages.get(0))).put("max_tokens",tokens).put("enable_thinking",false);
            if(request.optBoolean("json"))body.put("response_format",new JSONObject().put("type","json_object"));
        }else if(protocol.equals(ProviderProtocol.MESSAGES)){
            body.put("system",instruction).put("messages",messages).put("max_tokens",tokens);
        }else throw new IllegalArgumentException("stateless_protocol_unavailable");
        return body;
    }
    static String text(String protocol,JSONObject reply)throws Exception {
        StringBuilder out=new StringBuilder();
        if(protocol.equals(ProviderProtocol.CHAT)){
            JSONObject choice=reply.getJSONArray("choices").getJSONObject(0);
            if(!choice.optString("finish_reason").equals("stop"))throw new IOException("inference_incomplete");
            out.append(choice.getJSONObject("message").getString("content"));
        }else if(protocol.equals(ProviderProtocol.MESSAGES)){
            if(!reply.optString("stop_reason").equals("end_turn"))throw new IOException("inference_incomplete");
            JSONArray parts=reply.getJSONArray("content");for(int i=0;i<parts.length();i++){JSONObject p=parts.getJSONObject(i);if(p.optString("type").equals("text"))out.append(p.getString("text"));}
        }else{
            if(!reply.optString("status").equals("completed"))throw new IOException("inference_incomplete");
            JSONArray items=reply.getJSONArray("output");for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);if(!item.optString("type").equals("message"))continue;
                JSONArray parts=item.getJSONArray("content");for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j);if(part.optString("type").equals("output_text"))out.append(part.getString("text"));}
            }
        }
        if(out.length()==0||out.length()>16000)throw new IOException("inference_output_capacity");return out.toString();
    }
    static JSONObject generate(Context context,JSONObject cfg,String secret,JSONObject request)throws Exception {
        String protocol=cfg.getString("protocol");JSONObject body=body(protocol,request);
        // The configured official model supports a no-reasoning classification request.
        if(protocol.equals(ProviderProtocol.RESPONSES)&&request.getString("model").equals("gpt-6-luna"))body.put("reasoning",new JSONObject().put("effort","none"));
        String suffix=protocol.equals(ProviderProtocol.CHAT)?"/chat/completions":protocol.equals(ProviderProtocol.MESSAGES)?"/messages":"/responses";
        URL url=new URL(cfg.getString("baseUrl").replaceAll("/+$","")+suffix);
        if(!url.getProtocol().equals("https"))throw new SecurityException("inference_https_required");
        String proxyUrl=AndroidNetwork.proxyUrl(context);Proxy proxy=Proxy.NO_PROXY;
        if(!proxyUrl.isEmpty()){URI uri=new URI(proxyUrl);proxy=new Proxy(Proxy.Type.HTTP,new InetSocketAddress(uri.getHost(),uri.getPort()));}
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection(proxy);
        // Total deadline also bounds drip-fed responses. Cancel locally; never replay an uncertain request.
        java.util.Timer timer=new java.util.Timer(true);timer.schedule(new java.util.TimerTask(){public void run(){connection.disconnect();}},20000);
        long began=android.os.SystemClock.elapsedRealtime();
        try{
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","application/json");
            if(protocol.equals(ProviderProtocol.MESSAGES)){connection.setRequestProperty("x-api-key",secret);connection.setRequestProperty("anthropic-version","2023-06-01");}
            else connection.setRequestProperty("Authorization","Bearer "+secret);
            byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(payload.length);
            try(OutputStream output=connection.getOutputStream()){output.write(payload);}
            int status=connection.getResponseCode();if(status!=200)throw new IOException("inference_http_"+status);
            byte[] data;try(InputStream input=connection.getInputStream()){data=input.readNBytes(65537);}
            if(data.length>65536)throw new IOException("inference_response_capacity");
            String value=text(protocol,new JSONObject(new String(data,StandardCharsets.UTF_8)));
            return new JSONObject().put("text",value).put("model",request.getString("model")).put("backend","cloud_stateless").put("elapsed_ms",android.os.SystemClock.elapsedRealtime()-began);
        }finally{timer.cancel();connection.disconnect();}
    }
    private StatelessTextInference(){}
}
