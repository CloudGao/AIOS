package os.aios.miniapp;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class HostActivity extends Activity {
 static final Set<HostActivity> active=new HashSet<>();
 WebView web;Store store;JSONObject app;String appId,generation,origin;boolean resumed;
 SharedPreferences ledger;JSONObject pendingFile;JavaScriptReplyProxy pendingReply;
 final Set<String> pending=new HashSet<>();
 static void closeApp(String id){for(HostActivity a:new ArrayList<>(active))if(id.equals(a.appId)){a.destroyWeb();a.finish();}}
 protected void onCreate(Bundle b){super.onCreate(b);active.add(this);
  try{
   store=Store.get(this);String[] identity=launchIdentity(getIntent());appId=identity[0];app=store.app(appId);
   String expected=identity[1];if(expected!=null&&!expected.equals(app.getString("generation")))throw new IllegalStateException(getString(R.string.unavailable));
   if(!app.getString("state").equals("installed"))throw new IllegalStateException("小程序正在卸载，请返回管理页重试卸载。");
   if(!Capabilities.supported())throw new IllegalStateException("当前 WebView 不支持隔离运行，请更新系统 WebView。");
   LibraryActivity.cleanRetired(this);generation=app.getString("generation");origin=Store.origin(app);ledger=getSharedPreferences("mini_receipts_"+generation,0);
   LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
   web=new WebView(this);WebViewCompat.setProfile(web,Store.profile(app));
   WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setGeolocationEnabled(false);
   WebViewAssetLoader loader=new WebViewAssetLoader.Builder().setDomain(Uri.parse(origin).getHost()).addPathHandler("/app/",new WebViewAssetLoader.InternalStoragePathHandler(this,store.bundle(app))).addPathHandler("/_ling/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
   String presentation;
   try(InputStream in=getAssets().open("aios-presentation.js")){presentation=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
   if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,presentation,Set.of(origin));
   web.setWebViewClient(new WebViewClient(){
    public void onPageFinished(WebView w,String url){if(url.startsWith(origin+"/app/"))w.evaluateJavascript(presentation,null);}

    public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){
     Uri u=request.getUrl();String host=u.getHost();
     if(host!=null&&host.endsWith(".miniapp.invalid")){
      WebResourceResponse r=origin.equals(u.getScheme()+"://"+host)?loader.shouldInterceptRequest(u):null;
      if(r==null||r.getData()==null)r=new WebResourceResponse("text/plain","UTF-8",404,"Not Found",new HashMap<>(),new ByteArrayInputStream(new byte[0]));
      Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Content-Security-Policy","default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https:; font-src 'self' data:; object-src 'none'; frame-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'");headers.put("X-Content-Type-Options","nosniff");r.setResponseHeaders(headers);return r;
     }return null;
    }
    public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){Uri u=r.getUrl();if(origin.equals(u.getScheme()+"://"+u.getHost())&&u.getPath().startsWith("/app/"))return false;
     if(r.isForMainFrame()&&"https".equals(u.getScheme()))new AlertDialog.Builder(HostActivity.this).setTitle(os.aios.core.DisplayText.clean("在浏览器打开链接？")).setMessage(os.aios.core.DisplayText.clean(u.toString())).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).setPositiveButton(os.aios.core.DisplayText.clean("打开"),(d,n)->{try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){showError(e.getMessage());}}).show();return true;
    }
    public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){destroyWeb();showError("页面进程已退出，请返回后重新打开。已保存的本地数据会保留。");return true;}
   });
   web.setWebChromeClient(new WebChromeClient(){
    public boolean onJsAlert(WebView view,String url,String message,JsResult result){
     new AlertDialog.Builder(HostActivity.this).setMessage(os.aios.core.DisplayText.clean(message)).setPositiveButton(android.R.string.ok,(d,n)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;
    }
    public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
     new AlertDialog.Builder(HostActivity.this).setMessage(os.aios.core.DisplayText.clean(message)).setPositiveButton(android.R.string.ok,(d,n)->result.confirm()).setNegativeButton(android.R.string.cancel,(d,n)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
    }
    public boolean onJsBeforeUnload(WebView view,String url,String message,JsResult result){return onJsConfirm(view,url,message,result);}
    public boolean onJsPrompt(WebView view,String url,String message,String defaultValue,JsPromptResult result){
     EditText input=os.aios.core.AiosUi.field(HostActivity.this,null,defaultValue);
     new AlertDialog.Builder(HostActivity.this).setMessage(os.aios.core.DisplayText.clean(message)).setView(os.aios.core.AiosUi.dialogContent(HostActivity.this,input)).setPositiveButton(android.R.string.ok,(d,n)->result.confirm(input.getText().toString())).setNegativeButton(android.R.string.cancel,(d,n)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;
    }

    public void onPermissionRequest(PermissionRequest r){r.deny();}
    public boolean onConsoleMessage(ConsoleMessage m){android.util.Log.i("MiniAppConsole",appId+":"+m.lineNumber()+" "+m.messageLevel()+" "+m.message().substring(0,Math.min(500,m.message().length())));return true;}
   });
   WebViewCompat.addWebMessageListener(web,"LingBridge",Set.of(origin),(view,message,source,isMain,reply)->{
    if(!isMain||!origin.equals(source.getScheme()+"://"+source.getHost())||(source.getPort()!=-1&&source.getPort()!=443)||!resumed||web==null){android.util.Log.w("MiniAppBridge","Rejected source: main="+isMain+" port="+source.getPort()+" resumed="+resumed);return;}
    try{String raw=message.getData();if(raw==null||raw.length()>90000)throw new IllegalArgumentException("Message too large");JSONObject q=new JSONObject(raw);handle(q,reply);}catch(Exception e){android.util.Log.w("MiniAppBridge","Rejected message: "+e.getMessage());}
   });
   layout.setBackgroundColor(android.graphics.Color.WHITE);layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);setTitle(os.aios.core.DisplayText.clean(app.getJSONObject("manifest").getString("name")));web.loadUrl(origin+"/app/"+app.getJSONObject("manifest").getString("entry"));
  }catch(Exception e){showError(e.getMessage());}
 }
 // Android restores document tasks from the URI after package replacement; extras may be absent.
 // The persisted URI is authoritative, but never accept contradictory extra identities.
 static String[] launchIdentity(Intent intent) {
  String id=intent.getStringExtra("app_id"), generation=intent.getStringExtra("generation");
  Uri uri=intent.getData();
  if(uri!=null) {
   if(!"aios-miniapp".equals(uri.getScheme())||!"app".equals(uri.getHost())||uri.getPathSegments().size()!=2)
    throw new IllegalArgumentException("Invalid mini app identity");
   String uriId=uri.getPathSegments().get(0), uriGeneration=uri.getPathSegments().get(1);
   if((id!=null&&!id.equals(uriId))||(generation!=null&&!generation.equals(uriGeneration)))
    throw new IllegalArgumentException("Conflicting mini app identity");
   id=uriId;generation=uriGeneration;
  }
  if(id==null||id.isEmpty())throw new IllegalArgumentException("Missing mini app identity");
  return new String[]{id,generation};
 }
 protected void onResume(){super.onResume();resumed=true;}
 protected void onPause(){resumed=false;super.onPause();}
 protected void onDestroy(){active.remove(this);destroyWeb();super.onDestroy();}
 void destroyWeb(){if(web!=null){if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);web.stopLoading();web.destroy();web=null;}}
 void showError(String text){new AlertDialog.Builder(this).setMessage(os.aios.core.DisplayText.clean(text)).setPositiveButton(os.aios.core.DisplayText.clean("返回"),(d,n)->finish()).show();}
 void reply(JavaScriptReplyProxy proxy,String id,JSONObject result){try{proxy.postMessage(new JSONObject().put("id",id).put("result",result).toString());}catch(Exception ignored){/* Receipt remains queryable after navigation or process death. */}}
 JSONObject receipt(String id)throws Exception {String value=ledger.getString(Store.key(id),null);return value==null?Capabilities.error("not_found","No saved request"):new JSONObject(value).getJSONObject("result");}
 void save(String id,String fingerprint,JSONObject result)throws Exception {if(!ledger.edit().putString(id,new JSONObject().put("fingerprint",fingerprint).put("result",result).toString()).commit())throw new IOException("Cannot save receipt");}
 void handle(JSONObject q,JavaScriptReplyProxy proxy)throws Exception {
  String id=Store.key(q.getString("id")),cap=q.getString("capability");JSONObject p=q.getJSONObject("arguments");
  boolean started=false;
  try{
   JSONObject now=store.app(appId);if(!now.getString("generation").equals(generation)||!now.getString("state").equals("installed")||now.getJSONObject("manifest").getInt("version")!=app.getJSONObject("manifest").getInt("version"))throw new SecurityException("Application changed; reopen");
   if(cap.equals("capabilities")){JSONObject cat=Capabilities.catalog(this);cat.put("declared",app.getJSONObject("manifest").getJSONArray("capabilities")).put("grants",now.getJSONObject("grants"));reply(proxy,id,Capabilities.result("succeeded",cat));return;}
   if(cap.equals("requests.get")){reply(proxy,id,receipt(p.getString("request_id")));return;}
   if(!Capabilities.names().contains(cap)){reply(proxy,id,Capabilities.error("not_supported","No MiniApp adapter for "+cap));return;}
   boolean declared=false;JSONArray caps=app.getJSONObject("manifest").getJSONArray("capabilities");for(int i=0;i<caps.length();i++)declared|=cap.equals(caps.getString(i));if(!declared)throw new SecurityException("Capability absent from manifest");
   String fingerprint=cap+":"+p.toString(),previous=ledger.getString(id,null);
   if(previous!=null){JSONObject old=new JSONObject(previous);if(!old.getString("fingerprint").equals(fingerprint))throw new IllegalArgumentException("request_id conflict");reply(proxy,id,old.getJSONObject("result"));return;}
   if(pending.contains(id)){reply(proxy,id,Capabilities.error("waiting_user","Request already waiting for approval"));return;}
   if(!store.permitted(appId,generation,cap)){
    pending.add(id);new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("允许“"+app.getJSONObject("manifest").getString("name")+"”使用此能力？")).setMessage(os.aios.core.DisplayText.clean(label(cap)+"\n可在小程序管理中撤销。文件仍由你每次选择；日历和短信仍需在系统应用中确认。")).setNegativeButton(os.aios.core.DisplayText.clean("不允许"),(d,n)->{pending.remove(id);reply(proxy,id,Capabilities.error("permission_required","User denied"));}).setPositiveButton(os.aios.core.DisplayText.clean("允许"),(d,n)->{pending.remove(id);try{store.grant(appId,generation,cap,true);handle(q,proxy);}catch(Exception e){reply(proxy,id,Capabilities.error("failed",e.getMessage()));}}).setOnCancelListener(d->{pending.remove(id);reply(proxy,id,Capabilities.error("cancelled","User dismissed"));}).show();return;
   }
   if(ledger.getAll().size()>=512)throw new IllegalStateException("Request history full; no action performed");
   // Persist before any external mutation. A lost response is never replayed implicitly.
   save(id,fingerprint,Capabilities.error("outcome_unknown","Inspect system state before attempting another write"));started=true;
   JSONObject result=execute(cap,p,q,proxy);
   if(result!=null){save(id,fingerprint,result);reply(proxy,id,result);}
  }catch(SecurityException e){reply(proxy,id,Capabilities.error("permission_required",e.getMessage()));}
  catch(IllegalArgumentException|JSONException e){JSONObject r=Capabilities.error("failed",e.getMessage());if(started)save(id,cap+":"+p.toString(),r);reply(proxy,id,r);}
  catch(Exception e){reply(proxy,id,Capabilities.error(ledger.contains(id)?"outcome_unknown":"failed",e.getMessage()));}
 }
 String label(String cap){switch(cap){case "notifications.post":return "发送通知";case "notifications.cancel":return "移除本小程序的通知";case "calendar.compose":return "打开系统日历编辑器";case "sms.compose":return "打开短信编辑器";case "files.pickText":return "读取你选择的文本文件";default:return "将文本导出到你选择的位置";}}
 JSONObject execute(String cap,JSONObject p,JSONObject q,JavaScriptReplyProxy proxy)throws Exception {
  if(!resumed||!store.permitted(appId,generation,cap))throw new SecurityException("App not active or authorization revoked");
  switch(cap){
   case "notifications.post":case "notifications.cancel":{
    String tag=generation+":"+Capabilities.str(p,"id",80,false);NotificationManager nm=getSystemService(NotificationManager.class);
    if(cap.endsWith("cancel")){nm.cancel(tag,1);return Capabilities.result("succeeded",true);}
    String title=Capabilities.str(p,"title",100,false),body=Capabilities.str(p,"body",2000,true);
    if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);return Capabilities.error("permission_required","Approve Android notification permission, then use a new request id");}
    String channel="mini_"+generation;nm.createNotificationChannel(new NotificationChannel(channel,app.getJSONObject("manifest").getString("name"),NotificationManager.IMPORTANCE_DEFAULT));
    if(!nm.areNotificationsEnabled()||nm.getNotificationChannel(channel).getImportance()==NotificationManager.IMPORTANCE_NONE)return Capabilities.error("permission_required","Notifications disabled in Android settings");
    PendingIntent open=PendingIntent.getActivity(this,generation.hashCode(),new Intent(this,HostActivity.class).setData(Uri.parse(origin)).putExtra("app_id",appId),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    nm.notify(tag,1,new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(os.aios.core.DisplayText.clean(title)).setContentText(os.aios.core.DisplayText.clean(body)).setContentIntent(open).setAutoCancel(true).build());return Capabilities.result("succeeded",new JSONObject().put("id",p.getString("id")));
   }
   case "calendar.compose":{
    String title=Capabilities.str(p,"title",200,false);long begin=p.getLong("begin_ms"),end=p.getLong("end_ms");if(begin<0||end<=begin)throw new IllegalArgumentException("Invalid event interval");
    return launch(new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE,title).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,end));
   }
   case "sms.compose":{
    String number=Capabilities.str(p,"number",40,false);if(!number.matches("[+0-9 ()-]+"))throw new IllegalArgumentException("Invalid phone number");return launch(new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",number,null)).putExtra("sms_body",Capabilities.str(p,"text",2000,true)));
   }
   case "files.pickText":case "files.saveText":{
    if(pendingFile!=null)return Capabilities.error("busy","A file picker is already open");
    Intent intent;
    if(cap.equals("files.pickText"))intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE);
    else {String name=Capabilities.str(p,"name",80,false);if(name.contains("/")||name.contains("\\"))throw new IllegalArgumentException("Filename only");if(p.getString("text").getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("64 KiB export limit");intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name);}
    pendingFile=q;pendingReply=proxy;try{startActivityForResult(intent,9);}catch(Exception e){pendingFile=null;pendingReply=null;throw e;}return null;
   }
   default:return Capabilities.error("not_supported",cap);
  }
 }
 JSONObject launch(Intent i)throws Exception {if(i.resolveActivity(getPackageManager())==null)return Capabilities.error("not_supported","No installed editor");startActivity(i);return Capabilities.result("handed_to_system",new JSONObject().put("completion_verified",false));}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=9||pendingFile==null)return;
  JSONObject q=pendingFile;JavaScriptReplyProxy proxy=pendingReply;pendingFile=null;pendingReply=null;
  try{
   String id=q.getString("id"),cap=q.getString("capability");JSONObject r;
   if(result!=RESULT_OK||data==null||data.getData()==null)r=Capabilities.error("cancelled","No file selected");
   else if(!store.permitted(appId,generation,cap))r=Capabilities.error("permission_required","Authorization revoked");
   else if(cap.equals("files.pickText")){
    try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
     if(in==null)throw new IOException("Cannot open selected file");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>65536)throw new IllegalArgumentException("64 KiB file limit");out.write(buffer,0,n);}
     String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();r=Capabilities.result("succeeded",new JSONObject().put("text",text));
    }
   }else{try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(out==null)throw new IOException("Cannot open destination");out.write(q.getJSONObject("arguments").getString("text").getBytes(StandardCharsets.UTF_8));}r=Capabilities.result("succeeded",true);}
   save(id,cap+":"+q.getJSONObject("arguments").toString(),r);reply(proxy,id,r);
  }catch(Exception e){try{reply(proxy,q.getString("id"),Capabilities.error("outcome_unknown",e.getMessage()));}catch(Exception ignored){}}
 }
}
