package os.aios.miniapp;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.webkit.*;
import org.json.*;
import java.util.*;

/** Individual app settings; never opens Android's host-wide uninstall/clear-data screen. */
public final class ManageActivity extends Activity {
 String id,generation;boolean busy;
 protected void onCreate(Bundle b){super.onCreate(b);try{String[] identity=HostActivity.launchIdentity(getIntent());id=identity[0];generation=identity[1];}catch(Exception e){error(e);return;}render();if(b==null&&getIntent().getBooleanExtra("uninstall",false))confirmUninstall();}
 JSONObject current()throws Exception {JSONObject a=Store.get(this).app(id);if(generation==null||!generation.equals(a.getString("generation")))throw new IllegalStateException(getString(R.string.unavailable));return a;}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void render(){try{
  JSONObject a=current(),m=a.getJSONObject("manifest");setTitle(os.aios.core.DisplayText.clean(m.getString("name")));
  LinearLayout list=os.aios.core.AiosUi.column(this);list.setPadding(dp(16),0,dp(16),dp(24));ScrollView scroll=new ScrollView(this);scroll.addView(list);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});setContentView(scroll);
  os.aios.core.AiosUi.page(this,scroll);
  list.addView(os.aios.core.AiosUi.appBar(this,m.getString("name"),this::finish));
  TextView meta=os.aios.core.AiosUi.secondary(this,getString(R.string.version,m.getInt("version"))+" · "+getString(R.string.mini_app));meta.setPadding(dp(12),0,dp(12),dp(16));list.addView(meta);
  if("deleting".equals(a.getString("state"))){list.addView(os.aios.core.AiosUi.banner(this,"info",getString(R.string.deleting),null,null));}
  else {
   LinearLayout open=os.aios.core.AiosUi.group(this);open.addView(os.aios.core.AiosUi.settingRow(this,"apps",getString(R.string.open),null,null,true,true,busy?null:()->{try{current();startActivity(new Intent(this,HostActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("aios-miniapp://app/"+id+"/"+generation)).putExtra("app_id",id).putExtra("generation",generation).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NEW_DOCUMENT));}catch(Exception e){error(e);}}));list.addView(open);
   list.addView(os.aios.core.AiosUi.label(this,getString(R.string.permissions)));
   JSONArray caps=m.getJSONArray("capabilities");JSONObject grants=a.getJSONObject("grants");
   if(caps.length()==0){TextView none=os.aios.core.AiosUi.secondary(this,getString(R.string.no_permissions));none.setPadding(dp(16),0,dp(16),dp(12));list.addView(none);}
   else{LinearLayout perms=os.aios.core.AiosUi.group(this);list.addView(perms);for(int i=0;i<caps.length();i++){String cap=caps.getString(i);Switch toggle=os.aios.core.AiosUi.m3switch(this,grants.optBoolean(cap),on->{try{current();Store.get(this).grant(id,generation,cap,on);HostActivity.closeApp(id);}catch(Exception e){error(e);render();}});LinearLayout row=os.aios.core.AiosUi.settingRow(this,"shield",capLabel(cap),null,toggle,i==0,i==caps.length()-1,null);os.aios.core.AiosUi.clickable(row,toggle::toggle);perms.addView(row);}}
   LinearLayout danger=os.aios.core.AiosUi.group(this);LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(-1,-2);dp2.topMargin=dp(16);list.addView(danger,dp2);
   danger.addView(os.aios.core.AiosUi.settingRow(this,"refresh",getString(R.string.clear_data),getString(R.string.clear_message),null,true,false,busy?null:()->new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(R.string.clear_data)).setMessage(os.aios.core.DisplayText.clean(R.string.clear_message)).setNegativeButton(os.aios.core.DisplayText.clean(android.R.string.cancel),null).setPositiveButton(os.aios.core.DisplayText.clean(R.string.clear_data),(d,n)->clearData()).show()));
   danger.addView(os.aios.core.AiosUi.settingRow(this,"delete",getString(R.string.uninstall),null,null,false,true,busy?null:this::confirmUninstall));
  }
  if("deleting".equals(a.getString("state"))){LinearLayout g=os.aios.core.AiosUi.group(this);g.addView(os.aios.core.AiosUi.settingRow(this,"delete",getString(R.string.uninstall),null,null,true,true,busy?null:this::confirmUninstall));list.addView(g);}
 }catch(Exception e){error(e);}}
 String capLabel(String cap){switch(cap){case "notifications.post":return getString(R.string.notifications);case "notifications.cancel":return getString(R.string.cancel_notifications);case "calendar.compose":return getString(R.string.calendar);case "sms.compose":return getString(R.string.sms);case "files.pickText":return getString(R.string.read_file);case "files.saveText":return getString(R.string.save_file);default:return cap;}}
 void error(Exception e){if(!isFinishing())new AlertDialog.Builder(this).setMessage(os.aios.core.DisplayText.clean(e.getMessage())).setPositiveButton(os.aios.core.DisplayText.clean(android.R.string.ok),(d,n)->{if(e instanceof IllegalArgumentException)finish();}).show();}
 void confirmUninstall(){if(busy)return;try{JSONObject a=current();new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean(getString(R.string.uninstall_title,a.getJSONObject("manifest").getString("name")))).setMessage(os.aios.core.DisplayText.clean(R.string.uninstall_message)).setNegativeButton(os.aios.core.DisplayText.clean(android.R.string.cancel),null).setPositiveButton(os.aios.core.DisplayText.clean(R.string.uninstall),(d,n)->{try{current();busy=true;render();LibraryActivity.uninstall(this,id).whenComplete((ignored,failure)->runOnUiThread(()->{busy=false;if(failure!=null){error(new Exception(failure));render();}else finish();}));}catch(Exception e){busy=false;error(e);}}).show();}catch(Exception e){error(e);}}
 void clearData(){if(busy)return;try{
  JSONObject a=current();if(!"installed".equals(a.getString("state")))throw new IllegalStateException(getString(R.string.unavailable));
  if(!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)||!WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))throw new IllegalStateException(getString(R.string.cleanup_unavailable));
  busy=true;HostActivity.closeApp(id);render();
  Runnable done=()->runOnUiThread(()->{try{current();deleteSharedPreferences("mini_receipts_"+generation);JSONObject grants=Store.get(this).app(id).getJSONObject("grants");ArrayList<String> keys=new ArrayList<>();grants.keys().forEachRemaining(keys::add);for(String k:keys)Store.get(this).grant(id,generation,k,false);Toast.makeText(this,os.aios.core.DisplayText.clean(R.string.data_cleared),Toast.LENGTH_SHORT).show();}catch(Exception e){error(e);}finally{busy=false;render();}});
  ProfileStore profiles=ProfileStore.getInstance();String profile=Store.profile(a);if(profiles.getAllProfileNames().contains(profile))WebStorageCompat.deleteBrowsingData(profiles.getProfile(profile).getWebStorage(),done);else done.run();
 }catch(Exception e){busy=false;error(e);render();}}
}
