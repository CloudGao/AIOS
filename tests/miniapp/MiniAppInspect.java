package os.aios.miniapp;
import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Acceptance-only inspection of the user-requested calendar; never part of the product APK. */
public final class MiniAppInspect extends Instrumentation {
 Bundle args;
 public void onCreate(Bundle b){super.onCreate(b);args=b;start();}
 public void onStart(){Bundle result=new Bundle();try{
  Store store=Store.get(getTargetContext());JSONObject app=store.app("ling-calendar");String action=args.getString("action","source");
  if(action.equals("source")){
   JSONObject files=new JSONObject();java.io.File root=store.bundle(app);try(java.util.stream.Stream<Path> all=Files.walk(root.toPath())){for(java.util.Iterator<Path> i=all.iterator();i.hasNext();){Path p=i.next();if(Files.isRegularFile(p)&&Files.size(p)<=49152)files.put(root.toPath().relativize(p).toString(),Files.readString(p));}}
   result.putString("stream",new JSONObject().put("manifest",app.getJSONObject("manifest")).put("files",files).toString());
  }else{
   HostActivity a=(HostActivity)startActivitySync(new Intent(getTargetContext(),HostActivity.class).putExtra("app_id","ling-calendar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   runOnMainSync(()->a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));waitForIdleSync();
   String ready=null;long end=System.currentTimeMillis()+15000;do{Thread.sleep(200);ready=evaluate(a,"document.readyState");}while(!"\"complete\"".equals(ready)&&System.currentTimeMillis()<end);
   if(action.equals("preview")){
    Thread.sleep(800);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();runOnMainSync(()->{android.view.View v=a.getWindow().getDecorView();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(v.getWidth(),v.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);v.draw(new android.graphics.Canvas(image));image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);image.recycle();});result.putString("stream",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));finish(Activity.RESULT_OK,result);return;
   }
   String script=args.getString("script","document.body.innerText");evaluate(a,"window.__testResult=undefined;Promise.resolve().then(async()=>{"+script+"}).then(value=>window.__testResult=JSON.stringify({ok:true,value})).catch(error=>window.__testResult=JSON.stringify({ok:false,error:String(error)}));true");
   String response="null";end=System.currentTimeMillis()+25000;do{Thread.sleep(150);response=evaluate(a,"window.__testResult||null");}while("null".equals(response)&&System.currentTimeMillis()<end);
   result.putString("stream",response);
  }
  finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}}
 String evaluate(HostActivity a,String script)throws Exception {String[] result={null};CountDownLatch done=new CountDownLatch(1);runOnMainSync(()->a.web.evaluateJavascript(script,r->{result[0]=r;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Page evaluation timed out");return result[0];}
}
