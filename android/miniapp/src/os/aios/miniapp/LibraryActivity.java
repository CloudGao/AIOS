package os.aios.miniapp;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;

public final class LibraryActivity extends Activity {
 protected void onCreate(Bundle b){super.onCreate(b);}
 protected void onResume(){super.onResume();try{cleanRetired(this);}catch(Exception e){android.util.Log.w("MiniApps","Profile metadata cleanup deferred",e);}render();}
 void render(){
  MiniAppGrid grid=new MiniAppGrid(this);grid.setId(7201);
  grid.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return WindowInsets.CONSUMED;});
  setContentView(grid);os.aios.core.AiosUi.page(this,grid);
 }
 void error(Exception e){new AlertDialog.Builder(this).setMessage(os.aios.core.DisplayText.clean(e.getMessage())).setPositiveButton(os.aios.core.DisplayText.clean("知道了"),null).show();}
 static java.util.concurrent.CompletableFuture<Void> uninstall(Context c,String id) {
  java.util.concurrent.CompletableFuture<Void> result=new java.util.concurrent.CompletableFuture<>();
  try{
  Store s=Store.get(c);JSONObject a=s.app(id);s.deleting(id);HostActivity.closeApp(id);
  android.app.NotificationManager nm=c.getSystemService(android.app.NotificationManager.class);String tag=a.getString("generation");for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())if(n.getTag()!=null&&n.getTag().startsWith(tag+":"))nm.cancel(n.getTag(),n.getId());nm.deleteNotificationChannel("mini_"+tag);
  if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))throw new IllegalStateException("WebView profile cleanup unavailable; uninstall remains pending");
  ProfileStore ps=ProfileStore.getInstance();String profile=Store.profile(a);
  Runnable finish=()->{try{c.deleteSharedPreferences("mini_receipts_"+tag);s.retire(profile);s.removed(id);result.complete(null);}catch(Exception e){result.completeExceptionally(e);}};
  if(!ps.getAllProfileNames().contains(profile)){finish.run();return result;}
  if(!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))throw new IllegalStateException("Complete browsing-data deletion unavailable; uninstall remains pending");
  // Loaded Chromium profiles cannot be deleted during this process lifetime.
  // Clear all website data with a completion callback; retire the empty metadata on cold start.
  WebStorageCompat.deleteBrowsingData(ps.getProfile(profile).getWebStorage(),finish);
  }catch(Exception e){result.completeExceptionally(e);}return result;
 }
 static void cleanRetired(Context c)throws Exception {
  if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))return;
  Store s=Store.get(c);for(String name:s.retired())try{ProfileStore.getInstance().deleteProfile(name);s.retiredDone(name);}catch(IllegalStateException loaded){/* Empty metadata can be removed on the next process start. */}
 }
}
