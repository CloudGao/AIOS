package os.aios.agent;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.net.Uri;
import android.provider.*;
import org.json.*;
import java.util.*;
public class UserRequest extends Activity {
 static final String PROCESS_EPOCH=UUID.randomUUID().toString();
 String ticket;JSONObject request;
 static android.content.SharedPreferences store(Context c){return c.getSharedPreferences("requests",0);}
 static synchronized JSONObject enqueue(Context c,String kind,JSONObject params)throws Exception{
  if(!Authorization.enabled(c))return Authorization.denied();
  String id=UUID.randomUUID().toString();JSONObject r=new JSONObject().put("ticket",id).put("kind",kind).put("params",params).put("state","queued").put("created_ms",System.currentTimeMillis()).put("next_step","AIOS dispatches automatically under unified authorization; poll request status.");
  store(c).edit().putString(id,r.toString()).commit();
  Intent i=new Intent(c,UserRequest.class).putExtra("ticket",id);
  PendingIntent pi=PendingIntent.getActivity(c,id.hashCode(),i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  NotificationManager nm=c.getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("requests","AIOS requests",NotificationManager.IMPORTANCE_DEFAULT));
  nm.notify(id.hashCode(),new Notification.Builder(c,"requests").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(os.aios.core.DisplayText.clean("AIOS: "+kind)).setContentText(os.aios.core.DisplayText.clean("AIOS action in progress")).setContentIntent(pi).setAutoCancel(true).build());
  if(Authorization.enabled(c)){
   try{c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));r.put("next_step","Read request status; AIOS dispatches under unified authorization. A selector or app-owned interaction may remain.");}
   catch(Exception e){r.put("next_step","Open AIOS Control Pending requests to continue; Android blocked background activity launch.");}
  }
  return r;
 }
 static synchronized JSONObject get(Context c,String id)throws Exception{String v=store(c).getString(id,null);if(v==null)throw new IllegalArgumentException("Unknown request");JSONObject r=new JSONObject(v);if(r.optString("state").equals("pending")&&!r.optString("process_epoch").equals(PROCESS_EPOCH)){r.put("state","outcome_unknown").put("reason","Agent process restarted after dispatch; inspect result before retrying");store(c).edit().putString(id,r.toString()).commit();}return r;}
 static synchronized boolean claim(Context c,String id)throws Exception{
  JSONObject r=get(c,id);if(!r.getString("state").equals("queued"))return false;
  r.put("state","pending").put("process_epoch",PROCESS_EPOCH);store(c).edit().putString(id,r.toString()).commit();return true;
 }
 static synchronized JSONObject cancel(Context c,String id)throws Exception{
  JSONObject r=get(c,id);if(!r.getString("state").equals("queued"))return new JSONObject().put("state",r.getString("state")).put("cancelled",false).put("reason","Already dispatched; cannot retract external action");
  r.put("state","cancelled");store(c).edit().putString(id,r.toString()).commit();c.getSystemService(NotificationManager.class).cancel(id.hashCode());return r.put("cancelled",true);
 }
 static String next(Context c){for(Map.Entry<String,?> e:store(c).getAll().entrySet()){try{if(new JSONObject((String)e.getValue()).optString("state").equals("queued"))return e.getKey();}catch(Exception ignored){}}return null;}
 void save(String state,JSONObject result){try{request.put("process_epoch",PROCESS_EPOCH);request.put("state",state).put("result",result);store(this).edit().putString(ticket,request.toString()).commit();}catch(Exception ignored){}}
 public void onCreate(Bundle b){
  super.onCreate(b);ticket=getIntent().getStringExtra("ticket");
  try{
   if(ticket==null)ticket=next(this);if(ticket==null){finish();return;}
   request=get(this,ticket);if(request.getString("state").equals("pending")&&b!=null)return;if(!request.getString("state").equals("queued")){finish();return;}
   String kind=request.getString("kind");JSONObject p=request.getJSONObject("params");
   if(!Authorization.enabled(this)){save("denied",new JSONObject());finish();return;}
   try{runRequest(kind,p);}catch(Exception e){save("failed",error(e));finish();}
  }catch(Exception e){finish();}
 }
 JSONObject error(Exception e){JSONObject o=new JSONObject();try{o.put("error",e.toString());}catch(Exception ignored){}return o;}
 void runRequest(String kind,JSONObject p)throws Exception{
  if(!Authorization.enabled(this)){save("denied",new JSONObject());finish();return;}
  if(!claim(this,ticket)){finish();return;}
  save("pending",new JSONObject());
  if(kind.equals("permissions")){
   Set<String> allowed=new HashSet<>(Arrays.asList("android.permission.READ_CONTACTS","android.permission.WRITE_CONTACTS","android.permission.WRITE_CALENDAR","android.permission.READ_CALENDAR","android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_VISUAL_USER_SELECTED","android.permission.CALL_PHONE","android.permission.READ_PHONE_STATE","android.permission.READ_SMS","android.permission.READ_CALL_LOG","android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_FINE_LOCATION","android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN","android.permission.RECORD_AUDIO","android.permission.POST_NOTIFICATIONS","android.permission.READ_MEDIA_AUDIO","android.permission.ACCESS_MEDIA_LOCATION"));
   JSONArray names=p.getJSONArray("permissions");String[] perms=new String[names.length()];
   for(int x=0;x<perms.length;x++){perms[x]=names.getString(x);if(!allowed.contains(perms[x]))throw new IllegalArgumentException("Not a supported runtime permission");}
   requestPermissions(perms,20);return;
  }
  if(kind.equals("photos")){startActivityForResult(new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"),21);return;}
  if(kind.equals("document")){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(p.optString("mime","*/*")).addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),21);return;}
  if(kind.equals("media_delete")){
   ArrayList<Uri> uris=new ArrayList<>();JSONArray ids=p.getJSONArray("ids");for(int i=0;i<ids.length();i++)uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ids.getLong(i)));
   startIntentSenderForResult(MediaStore.createDeleteRequest(getContentResolver(),uris).getIntentSender(),22,null,0,0,0);return;
  }
  if(kind.equals("provider_delete")){
   String table=p.getString("table");Uri base;
   if(table.equals("contacts"))base=ContactsContract.Contacts.CONTENT_URI;else if(table.equals("calendar"))base=CalendarContract.Events.CONTENT_URI;else throw new IllegalArgumentException("Invalid table");
   long id=p.getLong("id");if(id<0)throw new IllegalArgumentException("Invalid id");
   int count=getContentResolver().delete(ContentUris.withAppendedId(base,id),null,null);
   save("succeeded",new JSONObject().put("deleted_rows",count));finish();return;
  }
  if(kind.equals("managed_delete")){
   String name=p.getString("name");if(!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))throw new IllegalArgumentException("Invalid filename");
   java.io.File f=new java.io.File(new java.io.File(getFilesDir(),"managed"),name);boolean existed=f.exists();if(existed&&!f.delete())throw new java.io.IOException("Delete failed");
   save("succeeded",new JSONObject().put("existed",existed).put("deleted",existed));finish();return;
  }
  if(kind.equals("notification_reply")){
   JSONObject result=NotificationSources.reply(p.getString("key"),p.getString("text"));save(result.getString("state"),result);finish();return;
  }
  if(kind.equals("force_stop")){
   String pkg=p.getString("package");if(pkg.equals(getPackageName())||pkg.equals("android")||pkg.equals("com.android.systemui")||pkg.equals("com.android.virtualdevicemanager"))throw new IllegalArgumentException("Core control packages cannot be stopped");
   JSONObject unavailable=AgentCapabilities.guard(this,"apps.force_stop");if(unavailable!=null){save(unavailable.getString("state"),unavailable);finish();return;}
   DeviceHost.tasks(this).forceStop(pkg);save("handed_to_system",new JSONObject().put("completion_verified",false));finish();return;
  }
  if(kind.equals("reboot")){save("handed_to_system",new JSONObject().put("completion_verified",false));getSystemService(android.os.PowerManager.class).reboot(null);finish();return;}
  if(kind.equals("intent")){
   Intent i=Intent.parseUri(p.getString("uri"),Intent.URI_INTENT_SCHEME);
   AgentService current=AgentService.instance;if(current==null)throw new IllegalStateException("AIOS control unavailable");
   JSONObject result=current.ui.open(i);save(result.getString("state"),result);finish();return;
  }
  throw new IllegalArgumentException("Unsupported request");
 }
 public void onRequestPermissionsResult(int code,String[] perms,int[] results){
  super.onRequestPermissionsResult(code,perms,results);
  try{JSONObject grants=new JSONObject();boolean all=perms.length>0;for(int i=0;i<perms.length;i++){boolean granted=i<results.length&&results[i]==PackageManager.PERMISSION_GRANTED;grants.put(perms[i],granted);all&=granted;}save(all?"succeeded":"denied",grants);}catch(Exception ignored){}finish();
 }
 protected void onActivityResult(int code,int result,Intent data){
  super.onActivityResult(code,result,data);
  try{JSONObject value=new JSONObject();if(result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();value.put("uri",uri.toString());try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);value.put("persisted",true);}catch(SecurityException e){value.put("persisted",false);}}
   save(result==RESULT_OK?"succeeded":"cancelled",value);
  }catch(Exception e){save("failed",error(e));}finish();
 }
}
