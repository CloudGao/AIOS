package os.aios.codex;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Real loopback sockets, fake HTTPS upstream; never sends credentials to a service. */
public final class ProviderBridgeContractTest extends Instrumentation {
    static final String SECRET="fixture-secret-not-a-live-key";
    static final BlockingQueue<Fake> opened=new LinkedBlockingQueue<>();
    static volatile int status=200;
    static volatile boolean block=false;
    static volatile String stream="data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"你好\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
    int assertions;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;}
    static final class Fake extends HttpsURLConnection {
        final Map<String,String> headers=new HashMap<>();
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();
        final CountDownLatch disconnected=new CountDownLatch(1);
        final int response=status;final boolean wait=block;final String body=stream;final Proxy usedProxy;
        Fake(URL url,Proxy proxy){super(url);usedProxy=proxy;opened.add(this);}
        public void setRequestProperty(String k,String v){headers.put(k,v);}
        public OutputStream getOutputStream(){return sent;}
        public int getResponseCode(){return response;}
        public String getContentType(){return "text/event-stream";}
        public InputStream getInputStream(){
            if(!wait)return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            return new InputStream(){public int read()throws IOException{try{disconnected.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}throw new IOException("closed");}};
        }
        public void disconnect(){disconnected.countDown();}
        public boolean usingProxy(){return usedProxy!=Proxy.NO_PROXY;}
        public void connect(){}
        public String getCipherSuite(){return "fixture";}
        public Certificate[] getLocalCertificates(){return null;}
        public Certificate[] getServerCertificates(){return new Certificate[0];}
        public Principal getPeerPrincipal(){return null;}
        public Principal getLocalPrincipal(){return null;}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle out=new Bundle();try{
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){
            protected URLConnection openConnection(URL url){return new Fake(url,Proxy.NO_PROXY);}
            protected URLConnection openConnection(URL url,Proxy proxy){return new Fake(url,proxy);}
        }:null);
        checks();out.putString("result","PASS "+assertions+" transport fixture assertions; no live upstream");finish(-1,out);
    }catch(Throwable t){out.putString("result","FAIL "+assertions+" "+t.getClass().getSimpleName()+": "+t.getMessage());finish(0,out);}}
    ProviderBridge bridge(String protocol,String proxy)throws Exception{
        return new ProviderBridge(getTargetContext(),new JSONObject().put("protocol",protocol).put("model","fixture").put("baseUrl","https://fixture.invalid/v1"),SECRET,proxy);
    }
    byte[] body()throws Exception{return new JSONObject().put("model","fixture").put("stream",true).put("input",new JSONArray().put(new JSONObject().put("role","user").put("content","hello"))).toString().getBytes(StandardCharsets.UTF_8);}
    Socket request(ProviderBridge bridge,String auth,String extra)throws Exception{
        Socket socket=new Socket("127.0.0.1",bridge.server.getLocalPort());socket.setSoTimeout(5000);
        byte[] body=body();OutputStream out=socket.getOutputStream();
        ByteArrayOutputStream wire=new ByteArrayOutputStream();wire.write(("POST /v1/responses HTTP/1.1\r\nAuthorization: Bearer "+auth+"\r\nContent-Length: "+body.length+"\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));wire.write(body);out.write(wire.toByteArray());out.flush();return socket;
    }
    String response(Socket socket)throws Exception{try(socket){
        InputStream in=socket.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();String text="";
        while(!text.endsWith("\r\n\r\n")){int c=in.read();if(c<0)throw new EOFException("HTTP header");header.write(c);text=header.toString(StandardCharsets.US_ASCII);}
        int length=-1;for(String line:text.split("\r\n"))if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
        byte[] body=length>=0?in.readNBytes(length):in.readAllBytes();
        if(length>=0&&body.length!=length)throw new EOFException("HTTP body");
        return text+new String(body,StandardCharsets.UTF_8);
    }}
    Fake upstream()throws Exception{Fake fake=opened.poll(5,TimeUnit.SECONDS);check(fake!=null,"upstream opened");return fake;}
    void checks()throws Exception{
        try(ProviderBridge bridge=bridge("chat","")){
            String rejected=response(request(bridge,"wrong-token",""));check(rejected.startsWith("HTTP/1.1 401"),"authentication rejects");check(opened.isEmpty(),"unauthenticated request never reaches upstream");check(!rejected.contains(SECRET)&&!rejected.contains(bridge.token),"auth error redacted");
            String malformed=response(request(bridge,bridge.token,"Transfer-Encoding: chunked\r\n"));check(malformed.contains("provider_unsupported"),"ambiguous framing rejects");check(opened.isEmpty(),"framing fails before upstream");
            String ok=response(request(bridge,bridge.token,""));Fake fake=upstream();
            check(ok.contains("response.completed")&&ok.contains("你好"),"real loopback streams complete text");check(!ok.contains(SECRET),"upstream key absent from response");
            check(("Bearer "+SECRET).equals(fake.headers.get("Authorization")),"chat upstream credential");check(!fake.getInstanceFollowRedirects(),"redirects disabled");check(fake.getConnectTimeout()==20000&&fake.getReadTimeout()==90000,"bounded network wait");check(!fake.usingProxy(),"direct path");
            check(fake.disconnected.await(2,TimeUnit.SECONDS),"successful exchange closes upstream");
            for(int code:new int[]{301,302,307,308,401,403,429,500,503}){
                status=code;String error=response(request(bridge,bridge.token,""));Fake failed=upstream();
                int mapped=(code==401||code==403||code==429)?code:502;
                check(error.startsWith("HTTP/1.1 "+mapped),"status mapped "+code);check(!error.contains(SECRET),"failure redacted "+code);check(!failed.getInstanceFollowRedirects(),"no credential redirect "+code);check(failed.disconnected.await(2,TimeUnit.SECONDS),"failed upstream closed "+code);
            }
            status=200;stream="data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"},\"finish_reason\":null}]}\n\n";
            String truncated=response(request(bridge,bridge.token,""));upstream();check(truncated.contains("response.failed")&&!truncated.contains("response.completed"),"disconnect is not success");
            block=true;Socket cancel=request(bridge,bridge.token,"");Fake pending=upstream();cancel.close();check(pending.disconnected.await(3,TimeUnit.SECONDS),"client cancellation disconnects upstream");
            block=false;
        }
        stream="data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":1}}}\n\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":0}}\n\ndata: {\"type\":\"message_stop\"}\n\n";
        try(ProviderBridge bridge=bridge("messages","http://127.0.0.1:8899")){
            response(request(bridge,bridge.token,""));Fake fake=upstream();check(SECRET.equals(fake.headers.get("x-api-key")),"Messages credential header");check(!fake.headers.containsKey("Authorization"),"Messages omits bearer");check("2023-06-01".equals(fake.headers.get("anthropic-version")),"Messages version");check(fake.usingProxy(),"configured HTTP proxy honored");
            block=true;Socket socket=request(bridge,bridge.token,"");Fake pending=upstream();bridge.close();check(pending.disconnected.await(3,TimeUnit.SECONDS),"bridge shutdown closes active upstream");socket.close();check(bridge.server.isClosed()&&bridge.workers.isShutdown(),"shutdown closes listener and workers");block=false;
        }
    }
}
