package os.aios.agent;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.widget.*;
import org.json.*;
import java.util.Set;

/** Non-exported, immutable PendingIntent destination. Never a model-callable approval endpoint. */
public final class InterventionActionActivity extends Activity {
 static final Set<String> PERMISSIONS=Set.of("android.permission.RECORD_AUDIO","android.permission.POST_NOTIFICATIONS","android.permission.READ_CONTACTS","android.permission.WRITE_CONTACTS","android.permission.READ_CALENDAR","android.permission.WRITE_CALENDAR","android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_FINE_LOCATION","android.permission.CAMERA","android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_AUDIO","android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN");
 String id;long revision;
 public void onCreate(Bundle b){super.onCreate(b);id=getIntent().getStringExtra("id");revision=getIntent().getLongExtra("revision",0);if(getSystemService(KeyguardManager.class).isKeyguardLocked()){getSystemService(KeyguardManager.class).requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback(){public void onDismissSucceeded(){open(b);}public void onDismissCancelled(){finish();}public void onDismissError(){finish();}});}else open(b);}
 void open(Bundle saved){try{
  AgentService a=AgentService.instance;if(a==null)throw new IllegalStateException(getString(R.string.assistant_service_unavailable));JSONObject row=a.interventions.store.get(id);a.interventions.requireCurrent(row);
  if(row.getLong("revision")!=revision&&saved==null)throw new IllegalStateException(getString(R.string.assistant_request_changed));
  JSONObject args=row.getJSONObject("arguments");String choice=getIntent().getStringExtra("choice");
  if(choice!=null&&!choice.equals("open")){a.interventions.resolve(id,revision,choice);finish();return;}
  if(args.optString("handoff_reason").equals("system_permission")){
   stopService(new Intent(this,AssistantOrbService.class));
   if(row.getString("state").equals("waiting_user"))row=a.interventions.begin(id,revision);
   revision=row.getLong("revision");JSONArray values=args.getJSONArray("permissions");String[] permissions=new String[values.length()];for(int i=0;i<permissions.length;i++){permissions[i]=values.getString(i);if(!PERMISSIONS.contains(permissions[i]))throw new SecurityException("Unsupported permission");}
   boolean all=true;for(String permission:permissions)all&=checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;if(all)permissionReadback(permissions);else if(saved==null)requestPermissions(permissions,48);return;
  }
  if(choice==null||choice.equals("open")){startForegroundService(new Intent(this,AssistantOrbService.class).setAction("show").putExtra("intervention_id",id));finish();return;}
  LinearLayout layout=os.aios.core.AiosUi.column(this);int p=os.aios.core.AiosUi.dp(this,16);layout.setPadding(p,p*3,p,p);InterventionCards.add(this,layout,row);os.aios.core.AiosUi.page(this,layout);setContentView(layout);
 }catch(Exception e){Toast.makeText(this,os.aios.core.DisplayText.clean(e.getMessage()),Toast.LENGTH_LONG).show();finish();}}
 void permissionReadback(String[] permissions){try{JSONObject grants=new JSONObject();for(String p:permissions)grants.put(p,checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED);AgentService.instance.interventions.permissionResult(id,revision,grants);}catch(Exception e){Toast.makeText(this,os.aios.core.DisplayText.clean(e.getMessage()),Toast.LENGTH_LONG).show();}startForegroundService(new Intent(this,AssistantOrbService.class).setAction("show").putExtra("intervention_id",id));finish();}
 public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==48)permissionReadback(permissions);}
}
