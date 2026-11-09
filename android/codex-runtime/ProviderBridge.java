package os.aios.codex;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import static os.aios.codex.ProviderProtocol.obj;

/** Per-child, authenticated loopback transport. Upstream credentials never reach Codex. */
final class ProviderBridge implements AutoCloseable {
    static final int MAX_BODY=48*1024*1024,MAX_EVENT=4*1024*1024;
    final Context context;final String protocol,model,secret;final URL upstream;final Proxy proxy;
    final ServerSocket server;final String token;
    final ThreadPoolExecutor workers=new ThreadPoolExecutor(0,8,30,TimeUnit.SECONDS,new SynchronousQueue<>());
    final Set<Exchange> active=ConcurrentHashMap.newKeySet();
    // Account for concurrent large image/history requests before allocating their bodies.
    final Semaphore bodyBudget=new Semaphore(48);
    volatile boolean closed;
    ProviderBridge(Context context,JSONObject cfg,String secret,String systemProxy)throws Exception {
        this.context=context;this.protocol=cfg.getString("protocol");this.model=cfg.getString("model");this.secret=secret;
        String base=cfg.getString("baseUrl").replaceAll("/+$","");
        upstream=new URL(base+(protocol.equals(ProviderProtocol.CHAT)?"/chat/completions":"/messages"));
        if(!upstream.getProtocol().equals("https"))throw new ProviderProtocol.Failure("protocol");
        if(systemProxy.isEmpty())proxy=Proxy.NO_PROXY;else {URI uri=new URI(systemProxy);proxy=new Proxy(Proxy.Type.HTTP,new InetSocketAddress(uri.getHost(),uri.getPort()));}
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        Thread accept=new Thread(this::accept,"provider-accept");accept.setDaemon(true);accept.start();
    }
    String url(){return "http://127.0.0.1:"+server.getLocalPort()+"/v1";}
    void accept(){while(!closed)try{
        Socket socket=server.accept();Exchange exchange=new Exchange(socket);active.add(exchange);
        if(closed){exchange.close();continue;}
        try{workers.execute(()->handle(exchange));}catch(RejectedExecutionException busy){exchange.close();}
    }catch(IOException ignored){if(!closed)close();}}
    final class Exchange implements AutoCloseable {
        final Socket socket;volatile HttpsURLConnection connection;volatile boolean stopped;OutputStream output;boolean streaming;
        Exchange(Socket socket){this.socket=socket;}
        synchronized void send(String type,JSONObject event)throws Exception {
            if(stopped)throw new EOFException();event.put("type",type);
            output.write(("event: "+type+"\ndata: "+event+"\n\n").getBytes(StandardCharsets.UTF_8));output.flush();
        }
        public void close(){stopped=true;try{socket.close();}catch(IOException ignored){}HttpsURLConnection c=connection;if(c!=null)c.disconnect();active.remove(this);}
    }
    void handle(Exchange ex){ProviderProtocol.Stream stream=null;int permits=0;
        try{
            ex.socket.setSoTimeout(15000);InputStream input=ex.socket.getInputStream();ex.output=ex.socket.getOutputStream();
            String request=line(input,8192);Map<String,String> headers=new HashMap<>();int headerSize=0;
            for(int i=0;;i++){
                String line=line(input,8192);headerSize+=line.length();if(i>64||headerSize>32768)throw new ProviderProtocol.Failure("limit");if(line.isEmpty())break;
                int colon=line.indexOf(':');if(colon<1)throw ProviderProtocol.invalid();String name=line.substring(0,colon).toLowerCase(Locale.ROOT);
                if(headers.put(name,line.substring(colon+1).trim())!=null)throw ProviderProtocol.invalid();
            }
            String auth=headers.getOrDefault("authorization","");
            if(!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),auth.getBytes(StandardCharsets.UTF_8))){httpError(ex,401,"authentication");return;}
            if(!request.equals("POST /v1/responses HTTP/1.1")&&!request.equals("POST /v1/responses HTTP/1.0")){httpError(ex,400,"unsupported");return;}
            if(headers.containsKey("transfer-encoding")||headers.containsKey("content-encoding"))throw new ProviderProtocol.Failure("unsupported");
            int length=Integer.parseInt(headers.getOrDefault("content-length","0"));if(length<1||length>MAX_BODY)throw new ProviderProtocol.Failure("limit");
            int needed=(length+1024*1024-1)/(1024*1024);if(!bodyBudget.tryAcquire(needed))throw new ProviderProtocol.Failure("limit");permits=needed;
            byte[] data=input.readNBytes(length);if(data.length!=length)throw ProviderProtocol.invalid();
            JSONObject source=new JSONObject(new String(data,StandardCharsets.UTF_8));
            if(!source.optBoolean("stream"))throw new ProviderProtocol.Failure("unsupported");
            ProviderProtocol adapter=new ProviderProtocol(protocol,model,source.optJSONArray("tools"));JSONObject body=adapter.request(source);
            byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);if(payload.length>MAX_BODY)throw new ProviderProtocol.Failure("limit");
            HttpsURLConnection c=(HttpsURLConnection)upstream.openConnection(proxy);ex.connection=c;
            c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(90000);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","text/event-stream");c.setRequestProperty("User-Agent","AIOS/1.0");
            if(protocol.equals(ProviderProtocol.MESSAGES)){c.setRequestProperty("x-api-key",secret);c.setRequestProperty("anthropic-version","2023-06-01");}
            else c.setRequestProperty("Authorization","Bearer "+secret);
            c.setFixedLengthStreamingMode(payload.length);
            // The native HTTP connection is never reused. EOF or unexpected pipelining cancels this upstream request.
            ex.socket.setSoTimeout(0);Thread cancellation=new Thread(()->{try{input.read();}catch(IOException ignored){}finally{ex.close();}},"provider-cancel");cancellation.setDaemon(true);cancellation.start();
            if(ex.stopped)throw new EOFException();
            try(OutputStream out=c.getOutputStream()){out.write(payload);}
            int status=c.getResponseCode();
            if(status!=200){httpError(ex,status==401||status==403?status:status==429?429:502,"http",status);return;}
            String contentType=c.getContentType();if(contentType==null||!contentType.toLowerCase(Locale.ROOT).startsWith("text/event-stream"))throw ProviderProtocol.invalid();
            ex.output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));ex.streaming=true;
            stream=adapter.new Stream(ex::send);stream.start();
            try(InputStream upstreamInput=c.getInputStream()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(upstreamInput,StandardCharsets.UTF_8));
                while(!ex.stopped&&!stream.terminal){String event=event(reader);if(event==null)throw new ProviderProtocol.Failure("disconnected");
                    if(event.equals("[DONE]")){if(!protocol.equals(ProviderProtocol.CHAT))throw ProviderProtocol.invalid();stream.finish();}
                    else if(!event.isEmpty()){JSONObject e=new JSONObject(event);if(protocol.equals(ProviderProtocol.CHAT))stream.chat(e);else stream.anthropic(e);}
                }
            }
        }catch(Exception error){
            if(!ex.stopped)try{
                String code=error instanceof ProviderProtocol.Failure?((ProviderProtocol.Failure)error).code:error instanceof SocketTimeoutException?"timeout":"protocol";
                if(ex.streaming){JSONObject response=stream==null?new JSONObject():stream.response("failed",new JSONArray());response.put("error",obj("code","provider_"+code,"message",message(code,0)));ex.send("response.failed",obj("response",response));}
                else httpError(ex,400,code);
            }catch(Exception ignored){}
        }finally{ex.close();if(permits>0)bodyBudget.release(permits);}
    }
    void httpError(Exchange ex,int status,String code)throws Exception{httpError(ex,status,code,0);}
    void httpError(Exchange ex,int status,String code,int upstreamStatus)throws Exception {
        byte[] body=obj("error",obj("type","provider_error","code","provider_"+code,"message",message(code,upstreamStatus))).toString().getBytes(StandardCharsets.UTF_8);
        ex.output.write(("HTTP/1.1 "+status+" Error\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));ex.output.write(body);ex.output.flush();
    }
    String message(String code,int status){
        int resource;
        switch(code){
            case "http":return context.getString(R.string.provider_http,status);
            case "authentication":resource=R.string.provider_authentication;break;
            case "unsupported":resource=R.string.provider_unsupported;break;
            case "limit":resource=R.string.provider_limit;break;
            case "truncated":resource=R.string.provider_truncated;break;
            case "refused":resource=R.string.provider_refused;break;
            case "timeout":resource=R.string.provider_timeout;break;
            case "disconnected":resource=R.string.provider_disconnected;break;
            case "upstream":resource=R.string.provider_upstream;break;
            default:resource=R.string.provider_protocol;
        }return context.getString(resource);
    }
    static String line(InputStream in,int max)throws Exception {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1&&c!=10){if(b.size()>=max)throw new ProviderProtocol.Failure("limit");if(c!=13)b.write(c);}if(c==-1)throw new EOFException();return b.toString(StandardCharsets.US_ASCII);
    }
    static String event(Reader reader)throws Exception {
        StringBuilder data=new StringBuilder(),line=new StringBuilder();int count=0;
        while(true){int c=reader.read();if(c==-1){if(count>0)throw new ProviderProtocol.Failure("disconnected");return null;}
            if(++count>MAX_EVENT)throw new ProviderProtocol.Failure("limit");
            if(c=='\n'){
                String s=line.toString();line.setLength(0);if(s.endsWith("\r"))s=s.substring(0,s.length()-1);
                if(s.isEmpty())return data.length()==0?"":data.substring(0,data.length()-1);
                if(s.startsWith("data:")){String value=s.substring(5);if(value.startsWith(" "))value=value.substring(1);data.append(value).append('\n');}
            }else line.append((char)c);
        }
    }
    public void close(){closed=true;try{server.close();}catch(IOException ignored){}for(Exchange ex:active)ex.close();workers.shutdownNow();}
}
