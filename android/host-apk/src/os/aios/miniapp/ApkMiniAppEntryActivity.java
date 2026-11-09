package os.aios.miniapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import org.json.JSONObject;

/** Public shortcut router: never forward caller extras or capability requests. */
public final class ApkMiniAppEntryActivity extends Activity {
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  try{
   Uri uri=getIntent().getData();
   if(!Intent.ACTION_VIEW.equals(getIntent().getAction())||uri==null||!"aios-miniapp".equals(uri.getScheme())||!"app".equals(uri.getAuthority())||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPathSegments().size()!=2)throw new IllegalArgumentException();
   String id=Store.id(uri.getPathSegments().get(0)),generation=uri.getPathSegments().get(1);
   JSONObject app=Store.get(this).app(id);
   if(!"installed".equals(app.getString("state"))||!generation.equals(app.getString("generation")))throw new IllegalArgumentException();
   startActivity(new Intent(this,HostActivity.class).setAction(Intent.ACTION_VIEW).setData(DesktopIntegration.uri(app)));
  }catch(Exception e){Toast.makeText(this,R.string.apk_mini_unavailable,Toast.LENGTH_LONG).show();}
  finish();
 }
}
