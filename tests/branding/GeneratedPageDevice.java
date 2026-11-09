package os.aios.miniapp;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.util.concurrent.*;
public final class GeneratedPageDevice extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 String dialog(HostActivity host,String script,String text,int button)throws Exception {
  CompletableFuture<String> done=new CompletableFuture<>();runOnMainSync(()->host.web.evaluateJavascript(script,done::complete));Thread.sleep(350);
  CompletableFuture<Void> clicked=new CompletableFuture<>();runOnMainSync(()->{try{
   Class<?> global=Class.forName("android.view.WindowManagerGlobal");Object manager=global.getMethod("getInstance").invoke(null);
   java.util.List<?> views=(java.util.List<?>)global.getMethod("getWindowViews").invoke(manager);
   for(Object item:views){android.view.View root=(android.view.View)item;android.widget.TextView message=root.findViewById(android.R.id.message);
    if(message!=null&&text.contentEquals(message.getText())){android.view.View action=root.findViewById(button==android.R.string.ok?android.R.id.button1:android.R.id.button2);if(action==null||!action.performClick())throw new AssertionError("Dialog button unavailable");clicked.complete(null);return;}
   }
   throw new AssertionError("Dialog text missing: "+text);
  }catch(Throwable t){clicked.completeExceptionally(t);}});clicked.get(10,TimeUnit.SECONDS);return done.get(10,TimeUnit.SECONDS);
 }
 public void onStart(){Bundle out=new Bundle();HostActivity[] host={null};boolean installed=false;try {
  Store store=Store.get(getTargetContext());String id="branding-acceptance";try{store.app(id);throw new IllegalStateException("Fixture already exists");}catch(IllegalArgumentException absent){}
  JSONObject manifest=new JSONObject().put("id",id).put("name","Codex CODEX test").put("version",1).put("entry","index.html").put("capabilities",new JSONArray());
  String html="<!doctype html><meta name='viewport' content='width=device-width'><title>Codex test</title><h1>Codex CODEX cOdEx</h1><p id='later'></p><a href='https://example.test/codex' aria-label='Codex link'>Codex link</a><script>window.original='codex';localStorage.setItem('original','codex');setTimeout(()=>document.getElementById('later').textContent='later CODEX',100);</script>";
  store.install(new JSONObject().put("id",id).put("expected_version",0).put("request_id","branding"+System.nanoTime()),new JSONObject().put("manifest",manifest).put("files",new JSONObject().put("index.html",android.util.Base64.encodeToString(html.getBytes(java.nio.charset.StandardCharsets.UTF_8),2))));installed=true;
  if(!LauncherProvider.entry(store.app(id)).getString("name").equals("AIOS AIOS test"))throw new AssertionError("Launcher label");
  host[0]=(HostActivity)startActivitySync(new Intent(getTargetContext(),HostActivity.class).putExtra("app_id",id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  Thread.sleep(1500);if(host[0].web==null)throw new AssertionError("WebView not opened");
  CompletableFuture<String> result=new CompletableFuture<>();runOnMainSync(()->host[0].web.evaluateJavascript("JSON.stringify({heading:document.querySelector('h1').textContent,later:document.getElementById('later').textContent,title:document.title,label:document.querySelector('a').getAttribute('aria-label'),href:document.querySelector('a').href,script:window.original,stored:localStorage.getItem('original')})",result::complete));
  String value=result.get(15,TimeUnit.SECONDS);String decoded=new JSONArray("["+value+"]").getString(0);JSONObject j=new JSONObject(decoded);
  if(!j.getString("heading").equals("AIOS AIOS AIOS")||!j.getString("later").equals("later AIOS")||!j.getString("title").equals("AIOS test")||!j.getString("label").equals("AIOS link"))throw new AssertionError("Visible text "+j);
  if(!j.getString("href").endsWith("/codex")||!j.getString("script").equals("codex")||!j.getString("stored").equals("codex"))throw new AssertionError("Functional content altered "+j);
  dialog(host[0],"alert('CODEX alert'); 'done'","AIOS alert",android.R.string.ok);
  if(!dialog(host[0],"confirm('CoDeX confirm')","AIOS confirm",android.R.string.cancel).equals("false"))throw new AssertionError("Confirm cancellation changed");
  if(!dialog(host[0],"confirm('CoDeX confirm')","AIOS confirm",android.R.string.ok).equals("true"))throw new AssertionError("Confirm approval changed");
  if(!dialog(host[0],"prompt('Codex prompt','codex')","AIOS prompt",android.R.string.ok).equals("\"codex\""))throw new AssertionError("Prompt original value changed");
  try(java.io.FileOutputStream image=new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(),"branding-acceptance.png"))){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);}
  out.putString("result","PASS generated page: static and dynamic text, title, accessibility, launcher label, script, link destination and stored data; alert/confirm/prompt callbacks");
 }catch(Throwable t){out.putString("error",t.toString());}finally{try{if(host[0]!=null)runOnMainSync(host[0]::finish);if(installed){CompletableFuture<?>[] done={null};runOnMainSync(()->done[0]=LibraryActivity.uninstall(getTargetContext(),"branding-acceptance"));done[0].get(30,TimeUnit.SECONDS);}}catch(Exception e){out.putString("cleanup_error",e.toString());}finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}}
}