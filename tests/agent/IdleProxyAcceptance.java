package os.aios.codex;
import android.app.Instrumentation;import android.os.Bundle;import org.json.*;import java.io.*;import java.net.*;import java.util.*;
public final class IdleProxyAcceptance extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){int checks=0;CodexProcess p=null;ServerSocket server=null;ArrayList<Socket> sockets=new ArrayList<>();JSONObject result=new JSONObject();try{
  File home=new File(getTargetContext().getCacheDir(),"idle-proxy-test-"+System.nanoTime());if(!home.mkdirs())throw new IOException("mkdir");
  server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));final ServerSocket listener=server;final java.util.concurrent.atomic.AtomicBoolean respondError=new java.util.concurrent.atomic.AtomicBoolean();
  Thread hold=new Thread(()->{try{while(!listener.isClosed()){Socket s=listener.accept();synchronized(sockets){sockets.add(s);}if(respondError.get()){s.setSoTimeout(5000);InputStream input=s.getInputStream();ByteArrayOutputStream headers=new ByteArrayOutputStream();int match=0;int[] end={13,10,13,10};while(headers.size()<65536){int b=input.read();if(b<0)throw new EOFException();headers.write(b);match=b==end[match]?match+1:(b==13?1:0);if(match==4)break;}java.util.regex.Matcher length=java.util.regex.Pattern.compile("(?i)content-length: *([0-9]+)").matcher(headers.toString("US-ASCII"));if(length.find())input.readNBytes(Integer.parseInt(length.group(1)));byte[] body="{\"error\":{\"message\":\"isolated fixture failure\",\"type\":\"invalid_request_error\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(body);out.flush();}}}catch(IOException ignored){}});hold.setDaemon(true);hold.start();
  AndroidNetwork network=new AndroidNetwork(getTargetContext());p=new CodexProcess(getTargetContext(),home,e->{},"http://127.0.0.1:"+server.getLocalPort()+"/v1",network.caFile);
  p.rpcLifecycle.readLock().lock();try{check(!p.closeForNetworkChangeIfIdle(),"in-flight guard");checks++;}finally{p.rpcLifecycle.readLock().unlock();}
  p.uncertainMutation=true;check(!p.closeForNetworkChangeIfIdle(),"uncertain write guard");checks++;p.uncertainMutation=false;
  String tid=p.startThread().getJSONObject("thread").getString("id");
  JSONObject turn=p.call("turn/start",new JSONObject().put("threadId",tid).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text","Local isolated pending response fixture"))),20).getJSONObject("turn");
  String state="";for(int i=0;i<30;i++){state=p.call("thread/read",new JSONObject().put("threadId",tid).put("includeTurns",false),10).getJSONObject("thread").getJSONObject("status").getString("type");if(state.equals("active"))break;Thread.sleep(100);}
  check(state.equals("active"),"real native turn active");checks++;check(!p.closeForNetworkChangeIfIdle()&&p.process.isAlive(),"active turn preserved");checks++;
  p.call("turn/interrupt",new JSONObject().put("threadId",tid).put("turnId",turn.getString("id")),15);
  for(int i=0;i<50;i++){state=p.call("thread/read",new JSONObject().put("threadId",tid).put("includeTurns",false),10).getJSONObject("thread").getJSONObject("status").getString("type");if(state.equals("idle"))break;Thread.sleep(100);}
  check(state.equals("idle"),"interrupted fixture became idle");checks++;check(p.closeForNetworkChangeIfIdle()&&!p.process.isAlive(),"idle refresh gate closes native child");checks++;
  respondError.set(true);p=new CodexProcess(getTargetContext(),home,e->{},"http://127.0.0.1:"+server.getLocalPort()+"/v1",network.caFile);
  tid=p.startThread().getJSONObject("thread").getString("id");
  p.call("turn/start",new JSONObject().put("threadId",tid).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text","Local failed response fixture"))),20);
  for(int i=0;i<100;i++){state=p.call("thread/read",new JSONObject().put("threadId",tid).put("includeTurns",false),10).getJSONObject("thread").getJSONObject("status").getString("type");if(state.equals("systemError"))break;Thread.sleep(100);}
  check(state.equals("systemError"),"real native failed turn is inactive systemError; observed="+state);checks++;
  check(p.closeForNetworkChangeIfIdle()&&!p.process.isAlive(),"inactive failed native child can refresh");checks++;
  result.put("passed",true).put("checks",checks);
 }catch(Throwable e){try{result.put("passed",false).put("checks",checks).put("error",e.toString());}catch(Exception ignored){}}finally{if(p!=null)p.close();try{if(server!=null)server.close();synchronized(sockets){for(Socket s:sockets)s.close();}}catch(IOException ignored){}}
 Bundle b=new Bundle();b.putString("stream",result.toString());finish(-1,b);
 }
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
