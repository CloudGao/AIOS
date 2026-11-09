package os.aios.agent;
import android.app.*;import android.os.*;import android.content.*;import org.json.*;import java.nio.charset.StandardCharsets;
/** Explicit instrumentation entry for this acceptance batch, never shipped in the product. */
public final class SchedulerDeviceCommand extends Instrumentation {
 Bundle input;public void onCreate(Bundle b){super.onCreate(b);input=b;start();}
 public void onStart(){
  if(!input.getBoolean("unused",false)&&input.getString("operation","").equals("serve")){
   try{getTargetContext().startForegroundService(new Intent(getTargetContext(),AgentService.class));
    try(android.net.LocalServerSocket server=new android.net.LocalServerSocket("aios_scheduler_acceptance")){
     boolean running=true;while(running)try(android.net.LocalSocket socket=server.accept()){
      java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
      JSONObject request=new JSONObject(reader.readLine());if(request.optString("operation").equals("stop")){running=false;socket.getOutputStream().write("{\"stopped\":true}\n".getBytes(StandardCharsets.UTF_8));continue;}
      JSONObject response;try{response=new JSONObject().put("result",execute(request.optString("operation","core"),request.optString("domain","automation"),request.optString("action"),request.optJSONObject("parameters")==null?new JSONObject():request.getJSONObject("parameters")));}catch(Throwable error){response=new JSONObject().put("error",android.util.Log.getStackTraceString(error));}
      socket.getOutputStream().write((response.toString()+"\n").getBytes(StandardCharsets.UTF_8));
     }
    }finish(-1,new Bundle());
   }catch(Throwable error){Bundle out=new Bundle();out.putString("error",android.util.Log.getStackTraceString(error));finish(0,out);}return;
  }
  oneShot();
 }
 private JSONObject execute(String op,String domain,String action,JSONObject p)throws Exception{
  JSONObject result;
  if(op.equals("core"))result=new os.aios.runtime.CoreRuntimeClient(getTargetContext()).call(domain,action,p);
  else if(op.equals("editor.tree")){JSONArray nodes=new JSONArray();tree(getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow(),nodes);result=new JSONObject().put("nodes",nodes);}
  else if(op.equals("editor.fill")){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();boolean filled=fill(root,p.getString("hint"),p.getString("value"));result=new JSONObject().put("filled",filled);}
  else if(op.equals("dream.request")){new DreamDispatchReceiver().onReceive(getTargetContext(),new Intent("os.aios.core.RUN_DREAM").putExtra("occurrence",p.getString("occurrence")).putExtra("manual",p.optBoolean("manual")));result=new JSONObject().put("queued",true);}
  else if(op.equals("runtime")){try(CodexAccountClient c=new CodexAccountClient(getTargetContext())){result=c.call(p);}}
  else if(op.equals("notify")){NotificationManager nm=getTargetContext().getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("scheduler_acceptance","自动化验收通知",NotificationManager.IMPORTANCE_LOW));nm.notify(p.getString("tag"),91,new Notification.Builder(getTargetContext(),"scheduler_acceptance").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(p.getString("title")).setContentText(p.optString("text","本机自动化验收样本")).build());result=new JSONObject().put("posted",true);}
  else if(op.equals("notify.cancel")){getTargetContext().getSystemService(NotificationManager.class).cancel(p.getString("tag"),p.optInt("id",91));result=new JSONObject().put("removed",true);}
  else if(op.equals("ui")){AgentService s=AgentService.instance;if(s==null)throw new IllegalStateException("AgentService not running");String thread=p.optString("thread_id");if(!thread.isEmpty())UiSessionManager.caller.set(thread);try{result=s.ui.call(p.getJSONObject("request"));}finally{UiSessionManager.caller.remove();}}
  else if(op.equals("cleanup_test_notifications")){NotificationManager nm=getTargetContext().getSystemService(NotificationManager.class);int removed=0;for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications()){String title=String.valueOf(n.getNotification().extras.getCharSequence("android.title",""));if(title.startsWith("acceptance-")){nm.cancel(n.getTag(),n.getId());removed++;}}result=new JSONObject().put("removed",removed);}
  else if(op.equals("register_user")){String thread=p.getString("thread_id"),id="acceptance-user:"+thread;getTargetContext().getSharedPreferences("assistant_codex_threads",0).edit().putString(id,thread).commit();result=new JSONObject().put("registered",true);}
  else throw new IllegalArgumentException("Unknown test operation");
  return result;
 }
 private void tree(android.view.accessibility.AccessibilityNodeInfo n,JSONArray out)throws Exception{if(n==null)return;android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);out.put(new JSONObject().put("text",n.getText()==null?"":n.getText()).put("content-desc",n.getContentDescription()==null?"":n.getContentDescription()).put("hint",n.getHintText()==null?"":n.getHintText()).put("class",n.getClassName()).put("bounds",b.toShortString()).put("checked",n.isChecked()).put("selected",n.isSelected()).put("editable",n.isEditable()));for(int i=0;i<n.getChildCount();i++)tree(n.getChild(i),out);}
 private boolean fill(android.view.accessibility.AccessibilityNodeInfo node,String hint,String value){if(node==null)return false;if(node.isEditable()&&hint.equals(String.valueOf(node.getHintText()))){Bundle b=new Bundle();b.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,b);}for(int i=0;i<node.getChildCount();i++)if(fill(node.getChild(i),hint,value))return true;return false;}
 private void oneShot(){Bundle out=new Bundle();try{JSONObject p=new JSONObject(new String(android.util.Base64.decode(input.getString("parameters","e30="),android.util.Base64.DEFAULT),StandardCharsets.UTF_8));out.putString("result",execute(input.getString("operation","core"),input.getString("domain","automation"),input.getString("action",""),p).toString());finish(-1,out);}catch(Throwable error){out.putString("error",android.util.Log.getStackTraceString(error));finish(0,out);}}
}
