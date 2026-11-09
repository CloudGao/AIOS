package os.aios.agent;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.*;
import android.media.*;
import android.hardware.*;
import android.hardware.camera2.*;
import android.location.*;
import android.telephony.*;
import android.bluetooth.*;
import android.view.*;
import android.view.inputmethod.*;
import org.json.*;
import java.io.*;
import java.util.*;

abstract class SharedPlatformServices implements os.aios.device.DeviceBackend {
 final Context c;
 final os.aios.device.DeviceBackend.ApplicationLauncher launcher;
 final DeviceOperationRegistry contributions;
 SharedPlatformServices(Context c,os.aios.device.DeviceBackend.ApplicationLauncher launcher,DeviceOperationRegistry contributions){this.c=c;this.launcher=launcher;this.contributions=contributions;}
 PackageServices.Backend packageBackend(String op,JSONObject args)throws Exception{return null;}
 abstract JSONObject appFunctions(String op,JSONObject arguments)throws Exception;
 abstract JSONObject radio(String op,JSONObject arguments)throws Exception;
 abstract JSONObject clock(String op,JSONObject arguments)throws Exception;
 abstract JSONObject diagnostic(String service)throws Exception;
 abstract JSONObject reboot()throws Exception;
 abstract JSONObject recordMicrophone()throws Exception;
 abstract void appendHostCapabilities(JSONArray result)throws Exception;
 public void close(){contributions.close();}
 public JSONObject query(String capability,JSONObject arguments)throws Exception{
  if(capability.equals("apps.functions.discover"))return appFunctions(capability,arguments);
  throw new IllegalArgumentException("unsupported_device_query");
 }
 JSONObject ok(Object value)throws Exception{return new JSONObject().put("state","succeeded").put("data",value);}
 JSONObject blocked(String permission)throws Exception{
  return new JSONObject().put("state","permission_required").put("permission",permission)
    .put("next_step","Call permissions.request then approve in AIOS Control; retry with a new request_id after reading the result.");
 }
 boolean granted(String p){return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
 JSONObject require(String...ps)throws Exception{for(String p:ps)if(!granted(p))return blocked(p);return null;}
 JSONObject open(Intent i)throws Exception{return open(i,new JSONObject());}
 JSONObject open(Intent i,JSONObject options)throws Exception{
  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(i.resolveActivity(c.getPackageManager())==null)return new JSONObject().put("state","not_supported").put("reason","No installed activity handles this request").put("executed",false);
  return launcher.open(i,options);
 }
 JSONArray query(Uri uri,String[] projection,int limit)throws Exception{
  JSONArray out=new JSONArray();
  try(Cursor cur=c.getContentResolver().query(uri,projection,null,null,null)){
   if(cur==null)return out;
   while(cur.moveToNext()&&out.length()<limit){JSONObject row=new JSONObject();
    for(int i=0;i<cur.getColumnCount();i++){
     if(cur.getType(i)==Cursor.FIELD_TYPE_BLOB)continue;
     row.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));
    }out.put(row);
   }
  }return out;
 }
 JSONArray page(Uri uri,String[] columns,JSONObject p)throws Exception{
  int limit=p.optInt("limit",50);long after=p.optLong("after_id",0);if(limit<1||limit>200||after<0)throw new IllegalArgumentException("Invalid page");
  JSONArray out=new JSONArray();try(Cursor cur=c.getContentResolver().query(uri,columns,"_id>?",new String[]{String.valueOf(after)},"_id ASC")){if(cur!=null)while(cur.moveToNext()&&out.length()<limit){JSONObject row=new JSONObject();for(int i=0;i<cur.getColumnCount();i++)row.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));out.put(row);}}return out;
 }
 public JSONObject execute(String op,JSONObject p)throws Exception{
  JSONObject contributed=contributions.execute(op,p);if(contributed!=null)return contributed;
  JSONObject denied;
  if(op.startsWith("downloads.")||op.startsWith("apps.install.")||op.equals("apps.apk.inspect")||op.equals("apps.package.get"))return PackageServices.execute(c,op,p,packageBackend(op,p));
  if(op.equals("contacts.raw.get"))return ok(new ContactServices(c).record(p.getLong("raw_contact_id")));
  if(op.equals("contacts.create")||op.equals("contacts.update"))return new ContactServices(c).execute(op,p);
  if(op.startsWith("media.library."))return new MediaServices(c).execute(op,p);
  if(op.startsWith("hotspot.")||op.startsWith("mobile_data.")||op.startsWith("audio.route")||Arrays.asList("bluetooth.device","bluetooth.pair","bluetooth.unpair","bluetooth.connect","bluetooth.disconnect","bluetooth.active.set").contains(op))return radio(op,p);
  if(op.startsWith("alarm.")||op.startsWith("timer.")){
   return clock(op,p);
  }
  switch(op){
   case "system.context.read":{
    AudioManager am=c.getSystemService(AudioManager.class);String foreground=null;List<ActivityManager.RunningTaskInfo> tasks=c.getSystemService(ActivityManager.class).getRunningTasks(1);if(!tasks.isEmpty()&&tasks.get(0).topActivity!=null)foreground=tasks.get(0).topActivity.getPackageName();
    return ok(new JSONObject().put("android",Build.VERSION.RELEASE).put("build_id",Build.ID).put("foreground_package",foreground==null?JSONObject.NULL:foreground).put("media_volume",am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("media_volume_max",am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)));
   }
   case "apps.functions.discover":case "apps.functions.execute":return appFunctions(op,p);
   case "device.info":return ok(new JSONObject().put("android",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("build_id",Build.ID).put("model",Build.MODEL).put("manufacturer",Build.MANUFACTURER).put("features",features()));
   case "battery.read":{
    Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return ok(new JSONObject().put("level",b.getIntExtra("level",-1)).put("scale",b.getIntExtra("scale",100)).put("status",b.getIntExtra("status",-1)).put("plugged",b.getIntExtra("plugged",0)).put("temperature_tenths_c",b.getIntExtra("temperature",0)).put("voltage_mv",b.getIntExtra("voltage",0)).put("power_save",c.getSystemService(PowerManager.class).isPowerSaveMode()));
   }
   case "display.read":return ok(new JSONObject().put("brightness",Settings.System.getInt(c.getContentResolver(),"screen_brightness",-1)).put("automatic",Settings.System.getInt(c.getContentResolver(),"screen_brightness_mode",0)==1).put("rotation",Settings.System.getInt(c.getContentResolver(),"user_rotation",0)).put("font_scale",c.getResources().getConfiguration().fontScale).put("interactive",c.getSystemService(PowerManager.class).isInteractive()));
   case "display.brightness.set":{
    if(!Settings.System.canWrite(c))return blocked("android.settings.action.MANAGE_WRITE_SETTINGS");
    int value=p.getInt("value");if(value<0||value>255)throw new IllegalArgumentException("brightness 0..255");
    if(!Settings.System.putInt(c.getContentResolver(),"screen_brightness",value))throw new IllegalStateException("Write rejected");
    return execute("display.read",new JSONObject());
   }
   case "system.audio.media.get":{
    AudioManager am=c.getSystemService(AudioManager.class);
    return ok(new JSONObject().put("stream","media").put("current",am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("min",am.getStreamMinVolume(AudioManager.STREAM_MUSIC)).put("max",am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)));
   }
   case "system.audio.media.adjust":{
    Object deltaValue=p.get("delta"),expectedValue=p.get("expected_current");
    if(!(deltaValue instanceof Integer||deltaValue instanceof Long)||!(expectedValue instanceof Integer||expectedValue instanceof Long))throw new IllegalArgumentException("Volume values must be integers");
    long delta=((Number)deltaValue).longValue(),expected=((Number)expectedValue).longValue();
    if(delta < -15||delta > 15||expected < 0)throw new IllegalArgumentException("Invalid volume adjustment");
    AudioManager am=c.getSystemService(AudioManager.class);int stream=AudioManager.STREAM_MUSIC;
    int before=am.getStreamVolume(stream),min=am.getStreamMinVolume(stream),max=am.getStreamMaxVolume(stream);
    if(before!=expected)throw new IllegalArgumentException("Volume changed; read again");
    int target=(int)Math.max(min,Math.min(max,before+delta));am.setStreamVolume(stream,target,0);
    long deadline=SystemClock.elapsedRealtime()+2000;int after=am.getStreamVolume(stream);
    while(after!=target&&SystemClock.elapsedRealtime()<deadline){Thread.sleep(100);after=am.getStreamVolume(stream);}
    JSONObject data=new JSONObject().put("before",before).put("requested_delta",delta).put("target",target).put("after",after).put("min",min).put("max",max).put("clamped",target!=before+delta).put("verified",after==target);
    if(after!=target)return new JSONObject().put("state","outcome_unknown").put("data",data).put("error","Volume readback mismatch; read before retrying");
    return ok(data);
   }
   case "audio.read":{
    AudioManager am=c.getSystemService(AudioManager.class);JSONArray streams=new JSONArray();
    for(int stream:new int[]{0,2,3,4,5})streams.put(new JSONObject().put("stream",stream).put("current",am.getStreamVolume(stream)).put("min",am.getStreamMinVolume(stream)).put("max",am.getStreamMaxVolume(stream)));
    JSONArray devices=new JSONArray();for(AudioDeviceInfo dev:am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))devices.put(new JSONObject().put("id",dev.getId()).put("type",dev.getType()).put("name",dev.getProductName()));
    return ok(new JSONObject().put("streams",streams).put("outputs",devices).put("ringer_mode",am.getRingerMode()).put("microphone_muted",am.isMicrophoneMute()));
   }
   case "audio.set":{
    AudioManager am=c.getSystemService(AudioManager.class);int stream=p.getInt("stream"),value=p.getInt("value");
    if(!Arrays.asList(0,2,3,4,5).contains(stream))throw new IllegalArgumentException("Unsupported stream");
    if(am.getStreamVolume(stream)!=p.getInt("expected"))throw new IllegalStateException("Volume changed; read again");
    if(value<am.getStreamMinVolume(stream)||value>am.getStreamMaxVolume(stream))throw new IllegalArgumentException("Volume out of range");
    am.setStreamVolume(stream,value,0);JSONObject result=execute("audio.read",new JSONObject());int actual=am.getStreamVolume(stream);if(actual!=value)result.put("state","outcome_unknown").put("requested",value).put("actual",actual).put("reason","Volume change not confirmed; read state before retrying");return result;
   }
   case "network.read":{
    ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network n=cm.getActiveNetwork();NetworkCapabilities nc=cm.getNetworkCapabilities(n);LinkProperties lp=cm.getLinkProperties(n);
    return ok(new JSONObject().put("connected",n!=null).put("validated",nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).put("metered",cm.isActiveNetworkMetered()).put("wifi",nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).put("cellular",nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).put("vpn",nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).put("interface",lp==null?JSONObject.NULL:lp.getInterfaceName()));
   }
   case "bluetooth.read":{
    denied=require(Manifest.permission.BLUETOOTH_CONNECT);if(denied!=null)return denied;
    BluetoothManager bm=c.getSystemService(BluetoothManager.class);BluetoothAdapter ba=bm==null?null:bm.getAdapter();
    if(ba==null)return new JSONObject().put("state","not_supported").put("reason","No Bluetooth adapter");
    JSONArray peers=new JSONArray();for(BluetoothDevice dev:ba.getBondedDevices())peers.put(new JSONObject().put("name",dev.getName()).put("address",dev.getAddress()).put("bond_state",dev.getBondState()));
    return ok(new JSONObject().put("enabled",ba.isEnabled()).put("bonded_devices",peers));
   }
   case "wifi.read":{WifiManager wm=c.getSystemService(WifiManager.class);return ok(new JSONObject().put("enabled",wm.isWifiEnabled()).put("state",wm.getWifiState()));}
   case "wifi.set":{
    WifiManager wm=c.getSystemService(WifiManager.class);if(wm==null)return new JSONObject().put("state","not_supported");
    if(wm.isWifiEnabled()!=p.getBoolean("expected_enabled"))throw new IllegalStateException("Wi-Fi state changed; read again");
    boolean accepted=wm.setWifiEnabled(p.getBoolean("enabled"));return new JSONObject().put("state",accepted?"handed_to_system":"failed").put("accepted",accepted).put("completion_verified",false);
   }
   case "bluetooth.set":{
    denied=require(Manifest.permission.BLUETOOTH_CONNECT);if(denied!=null)return denied;
    BluetoothAdapter ba=c.getSystemService(BluetoothManager.class).getAdapter();if(ba==null)return new JSONObject().put("state","not_supported");
    if(ba.isEnabled()!=p.getBoolean("expected_enabled"))throw new IllegalStateException("Bluetooth state changed; read again");
    boolean accepted=p.getBoolean("enabled")?ba.enable():ba.disable();return new JSONObject().put("state",accepted?"handed_to_system":"failed").put("accepted",accepted).put("completion_verified",false);
   }
   case "apps.uninstall":return open(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+p.getString("package"))));
   case "apps.handlers":{
    Map<String,Intent> probes=new LinkedHashMap<>();
    
    probes.put("contacts.edit",new Intent(Intent.ACTION_EDIT,ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,1)));
    probes.put("calendar.editor.open",new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI));
    probes.put("calendar.edit",new Intent(Intent.ACTION_EDIT,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,1)));
    
    probes.put("phone.dial",new Intent(Intent.ACTION_DIAL,Uri.parse("tel:0")));probes.put("phone.call",new Intent(Intent.ACTION_CALL,Uri.parse("tel:0")));
    probes.put("sms.compose",new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:0")));probes.put("maps.open",new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=test")));
    probes.put("browser.open",new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")));probes.put("camera.capture",new Intent(MediaStore.ACTION_IMAGE_CAPTURE));probes.put("camera.video",new Intent(MediaStore.ACTION_VIDEO_CAPTURE));
    probes.put("photos.select",new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*"));probes.put("files.select",new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE));
    JSONArray out=new JSONArray();for(Map.Entry<String,Intent> entry:probes.entrySet()){ComponentName component=entry.getValue().resolveActivity(c.getPackageManager());if(component==null&&entry.getKey().equals("maps.open"))component=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/search?query=test")).resolveActivity(c.getPackageManager());out.put(new JSONObject().put("capability",entry.getKey()).put("available",component!=null).put("component",component==null?JSONObject.NULL:component.flattenToString()).put("launch_tested",false));}out.put(new JSONObject().put("capability","contacts.create").put("available",true).put("backend","native-service"));appendHostCapabilities(out);return ok(out);
   }
   case "apps.list":{
    JSONArray out=new JSONArray();int limit=p.optInt("limit",100),offset=p.optInt("offset",0);if(limit<1||limit>200||offset<0)throw new IllegalArgumentException("Invalid page");
    String query=p.optString("query","");if(query.length()>200)throw new IllegalArgumentException("Query too long");
    java.util.List<ApplicationInfo> apps=c.getPackageManager().getInstalledApplications(0);apps.sort(Comparator.comparing(ai->ai.packageName));int total=0;
    for(ApplicationInfo ai:apps){String label=String.valueOf(ai.loadLabel(c.getPackageManager()));if(!AppDiscovery.matches(query,ai.packageName,label))continue;
     if(total++<offset||out.length()>=limit)continue;
     out.put(new JSONObject().put("package",ai.packageName).put("label",label).put("system",(ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0).put("enabled",ai.enabled).put("launchable",c.getPackageManager().getLaunchIntentForPackage(ai.packageName)!=null));
    }
    int end=AppDiscovery.pageEnd(offset,limit,total);boolean more=end<total;
    return ok(out).put("query",query).put("total",total).put("offset",offset).put("limit",limit).put("has_more",more).put("next_offset",more?end:JSONObject.NULL);
   }
   case "apps.launch":{
    Intent i=c.getPackageManager().getLaunchIntentForPackage(p.getString("package"));if(i==null)return new JSONObject().put("state","not_supported").put("reason","No launcher activity");return open(i,p);
   }
   case "apps.details":return open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+p.getString("package"))));
   case "storage.read":{
    StatFs fs=new StatFs(c.getFilesDir().getAbsolutePath());return ok(new JSONObject().put("total_bytes",fs.getTotalBytes()).put("available_bytes",fs.getAvailableBytes()).put("scope","internal data partition"));
   }
   case "contacts.query":{
    denied=require(Manifest.permission.READ_CONTACTS);if(denied!=null)return denied;
    return ok(page(ContactsContract.Contacts.CONTENT_URI,new String[]{"_id","display_name","has_phone_number","lookup"},p));
   }
   case "contacts.get":{
    denied=require(Manifest.permission.READ_CONTACTS);if(denied!=null)return denied;long id=p.getLong("id");
    JSONArray info=query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,id),new String[]{"_id","display_name","has_phone_number","lookup"},1);
    if(info.length()==0)return new JSONObject().put("state","failed").put("reason","Contact not found");
    JSONArray details=new JSONArray();try(Cursor cur=c.getContentResolver().query(ContactsContract.Data.CONTENT_URI,new String[]{"mimetype","data1","data2","data3"},"contact_id=?",new String[]{String.valueOf(id)},null)){if(cur!=null)while(cur.moveToNext()&&details.length()<200){JSONObject row=new JSONObject();for(int i=0;i<cur.getColumnCount();i++)row.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));details.put(row);}}
    return ok(info.getJSONObject(0).put("details",details));
   }
   case "contacts.edit":return open(new Intent(Intent.ACTION_EDIT,ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,p.getLong("id"))));
   case "contacts.delete":
    denied=require(Manifest.permission.WRITE_CONTACTS);if(denied!=null)return denied;
    return UserRequest.enqueue(c,"provider_delete",new JSONObject().put("table","contacts").put("id",p.getLong("id")));
   case "calendar.edit":return open(new Intent(Intent.ACTION_EDIT,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,p.getLong("id"))));
   case "calendar.delete":
    denied=require(Manifest.permission.WRITE_CALENDAR);if(denied!=null)return denied;
    return UserRequest.enqueue(c,"provider_delete",new JSONObject().put("table","calendar").put("id",p.getLong("id")));
   case "power.reboot":return reboot();
   case "calendar.query":case "calendar.create":case "calendar.update":case "calendar.get":case "calendar.calendars":case "calendar.instances":return new CalendarServices(c).execute(op,p);
   case "calendar.editor.open":return open(new Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE,p.getString("title")).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,p.getLong("begin_ms")).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,p.getLong("end_ms")));
   case "phone.read":{
    TelephonyManager tm=c.getSystemService(TelephonyManager.class);
    return ok(new JSONObject().put("telephony_supported",c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).put("sim_state",tm==null?-1:tm.getSimState()).put("network_operator",tm==null?"":tm.getNetworkOperatorName()));
   }
   case "phone.dial":return open(new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",p.getString("number"),null)));
   case "phone.call":{
    denied=require(Manifest.permission.CALL_PHONE);if(denied!=null)return denied;
    return open(new Intent(Intent.ACTION_CALL,Uri.fromParts("tel",p.getString("number"),null)));
   }
   case "sms.compose":return open(new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",p.getString("number"),null)).putExtra("sms_body",p.getString("text")));
   case "sms.query":{
    denied=require(Manifest.permission.READ_SMS);if(denied!=null)return denied;
    return ok(page(Uri.parse("content://sms"),new String[]{"_id","address","date","type","body"},p));
   }
   case "calllog.query":{
    denied=require(Manifest.permission.READ_CALL_LOG);if(denied!=null)return denied;
    return ok(page(CallLog.Calls.CONTENT_URI,new String[]{"_id","number","date","duration","type"},p));
   }
   case "location.read":{
    denied=require(Manifest.permission.ACCESS_COARSE_LOCATION);if(denied!=null)return denied;
    LocationManager lm=c.getSystemService(LocationManager.class);JSONArray out=new JSONArray();
    for(String provider:lm.getProviders(true)){try{Location loc=lm.getLastKnownLocation(provider);if(loc!=null)out.put(new JSONObject().put("provider",provider).put("latitude",loc.getLatitude()).put("longitude",loc.getLongitude()).put("accuracy_m",loc.getAccuracy()).put("time_ms",loc.getTime()));}catch(SecurityException ignored){}}
    return ok(new JSONObject().put("enabled",lm.isLocationEnabled()).put("last_known_locations",out).put("fresh_fix_requested",false));
   }
   case "maps.open":{
    String q=Uri.encode(p.getString("query"));Intent map=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+q));
    String pkg=p.optString("package","");if(!pkg.isEmpty())map.setPackage(pkg);
    if(pkg.isEmpty()&&map.resolveActivity(c.getPackageManager())==null)map=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/search?query="+q));
    return open(map,p);
   }
   case "browser.open":{
    Uri uri=Uri.parse(p.getString("url"));if(!Arrays.asList("https","http").contains(uri.getScheme()))throw new IllegalArgumentException("http(s) only");Intent intent=new Intent(Intent.ACTION_VIEW,uri);String pkg=p.optString("package","");if(!pkg.isEmpty())intent.setPackage(pkg);return open(intent,p);
   }
   case "camera.list":{
    CameraManager cm=c.getSystemService(CameraManager.class);JSONArray out=new JSONArray();for(String id:cm.getCameraIdList()){CameraCharacteristics cc=cm.getCameraCharacteristics(id);out.put(new JSONObject().put("id",id).put("facing",cc.get(CameraCharacteristics.LENS_FACING)));}return ok(out);
   }
   case "camera.capture":return open(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
   case "camera.video":return open(new Intent(MediaStore.ACTION_VIDEO_CAPTURE));
   case "microphone.record":return recordMicrophone();
   case "sensors.sample":{
    SensorManager sm=c.getSystemService(SensorManager.class);Sensor sensor=sm.getDefaultSensor(p.getInt("type"));
    if(sensor==null)return new JSONObject().put("state","not_supported").put("reason","Sensor absent");
    java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);final JSONObject[] value=new JSONObject[1];
    SensorEventListener listener=new SensorEventListener(){public void onAccuracyChanged(Sensor sensor,int accuracy){}public void onSensorChanged(SensorEvent e){try{JSONArray v=new JSONArray();for(float f:e.values)v.put(f);value[0]=new JSONObject().put("values",v).put("accuracy",e.accuracy).put("elapsed_timestamp_ns",e.timestamp);ready.countDown();}catch(Exception ignored){}}};
    sm.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_NORMAL,new Handler(Looper.getMainLooper()));
    try{if(!ready.await(3,java.util.concurrent.TimeUnit.SECONDS))return new JSONObject().put("state","not_supported").put("reason","No sample within 3 seconds");return ok(value[0]);}finally{sm.unregisterListener(listener);}
   }
   case "dnd.read":{
    NotificationManager nm=c.getSystemService(NotificationManager.class);
    return ok(new JSONObject().put("filter",nm.getCurrentInterruptionFilter()).put("policy_access",nm.isNotificationPolicyAccessGranted()));
   }
   case "dnd.set":{
    NotificationManager nm=c.getSystemService(NotificationManager.class);if(!nm.isNotificationPolicyAccessGranted())return blocked("notification_policy_access");
    int filter=p.getInt("filter");if(filter<1||filter>4)throw new IllegalArgumentException("Invalid filter");nm.setInterruptionFilter(filter);return execute("dnd.read",new JSONObject());
   }
   case "sensors.list":{
    JSONArray out=new JSONArray();for(Sensor sensor:c.getSystemService(SensorManager.class).getSensorList(Sensor.TYPE_ALL))out.put(new JSONObject().put("name",sensor.getName()).put("type",sensor.getType()).put("vendor",sensor.getVendor()).put("max_range",sensor.getMaximumRange()));return ok(out);
   }
   case "input.read":{
    JSONArray out=new JSONArray();for(InputMethodInfo im:c.getSystemService(InputMethodManager.class).getEnabledInputMethodList())out.put(new JSONObject().put("id",im.getId()).put("package",im.getPackageName()));
    return ok(new JSONObject().put("enabled_methods",out).put("current",Settings.Secure.getString(c.getContentResolver(),"default_input_method")));
   }
   case "clipboard.read":{
    ClipboardManager cm=c.getSystemService(ClipboardManager.class);ClipData clip=cm.getPrimaryClip();
    if(clip==null)return ok(new JSONObject().put("available",false).put("reason","Empty or access denied by foreground/clipboard policy"));
    JSONArray out=new JSONArray();for(int i=0;i<Math.min(clip.getItemCount(),10);i++)out.put(String.valueOf(clip.getItemAt(i).coerceToText(c)));return ok(new JSONObject().put("available",true).put("items",out));
   }
   case "clipboard.write":c.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("AIOS",p.getString("text")));return new JSONObject().put("state","handed_to_system").put("completion_verified",false);
   case "security.read":return ok(new JSONObject().put("locked",c.getSystemService(KeyguardManager.class).isDeviceLocked()).put("secure_lock",c.getSystemService(KeyguardManager.class).isDeviceSecure()));
   case "settings.read":return ok(new JSONObject().put("locale",c.getResources().getConfiguration().getLocales().toLanguageTags()).put("timezone",TimeZone.getDefault().getID()).put("time_ms",System.currentTimeMillis()));
   case "media.control":{
    String key=p.getString("key");Map<String,Integer> keys=new HashMap<>();keys.put("play",KeyEvent.KEYCODE_MEDIA_PLAY);keys.put("pause",KeyEvent.KEYCODE_MEDIA_PAUSE);keys.put("play-pause",KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);keys.put("stop",KeyEvent.KEYCODE_MEDIA_STOP);keys.put("next",KeyEvent.KEYCODE_MEDIA_NEXT);keys.put("previous",KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    Integer code=keys.get(key);if(code==null)throw new IllegalArgumentException("Unsupported media key");AudioManager am=c.getSystemService(AudioManager.class);long now=SystemClock.uptimeMillis();am.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0));am.dispatchMediaKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0));return new JSONObject().put("state","handed_to_system").put("key",key).put("playback_verified",false);
   }
   case "media.sessions":return diagnostic("media_session");
   case "usb.read":return diagnostic("usb");
   case "windows.read":return diagnostic("window");
   case "network.wifi.read":return diagnostic("wifi");
   case "power.stats":return diagnostic("power");
   case "diagnostics.read":{
    String service=p.getString("service");
    if(!Set.of("meminfo","cpuinfo","diskstats","thermalservice","battery","activity").contains(service))throw new IllegalArgumentException("Unsupported diagnostic service");
    return diagnostic(service);
   }
   case "context.snapshot":return ok(snapshot());
   case "settings.open":{
    Map<String,String> actions=new HashMap<>();
    actions.put("main",Settings.ACTION_SETTINGS);actions.put("write_settings",Settings.ACTION_MANAGE_WRITE_SETTINGS);actions.put("usage",Settings.ACTION_USAGE_ACCESS_SETTINGS);actions.put("wifi",Settings.ACTION_WIFI_SETTINGS);actions.put("bluetooth",Settings.ACTION_BLUETOOTH_SETTINGS);actions.put("display",Settings.ACTION_DISPLAY_SETTINGS);actions.put("sound",Settings.ACTION_SOUND_SETTINGS);actions.put("location",Settings.ACTION_LOCATION_SOURCE_SETTINGS);actions.put("accessibility",Settings.ACTION_ACCESSIBILITY_SETTINGS);actions.put("default_apps",Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);actions.put("notifications",Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);actions.put("dnd",Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);actions.put("battery",Settings.ACTION_BATTERY_SAVER_SETTINGS);actions.put("date",Settings.ACTION_DATE_SETTINGS);actions.put("language",Settings.ACTION_LOCALE_SETTINGS);actions.put("input",Settings.ACTION_INPUT_METHOD_SETTINGS);actions.put("storage",Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
    String action=actions.get(p.getString("page"));if(action==null)throw new IllegalArgumentException("Unsupported settings page");return open(action.equals(Settings.ACTION_MANAGE_WRITE_SETTINGS)?new Intent(action,Uri.parse("package:"+c.getPackageName())):new Intent(action),p);
   }
   case "permissions.special":return ok(new JSONObject().put("write_settings_access",Settings.System.canWrite(c)).put("notification_policy_access",c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted()).put("notification_listener_access",NotificationSources.connected()).put("all_files_access",Environment.isExternalStorageManager()).put("media_management",MediaStore.canManageMedia(c)));
   case "permissions.read":{
    JSONArray out=new JSONArray();PackageInfo pi=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS);
    for(String perm:pi.requestedPermissions)out.put(new JSONObject().put("permission",perm).put("granted",granted(perm)));return ok(out);
   }
   case "permissions.request":return UserRequest.enqueue(c,"permissions",p);
   case "requests.get":return UserRequest.get(c,p.getString("ticket"));
   case "requests.cancel":return UserRequest.cancel(c,p.getString("ticket"));
   case "notifications.read":return NotificationSources.read(c);
   case "notifications.reply":return UserRequest.enqueue(c,"notification_reply",p);
   case "apps.force_stop":return UserRequest.enqueue(c,"force_stop",p);
   case "notifications.dismiss":return NotificationSources.dismiss(c,p.getString("key"));
   default:throw new IllegalArgumentException("Unknown platform service: "+op);
  }
 }
 JSONArray features()throws Exception{JSONArray out=new JSONArray();for(FeatureInfo f:c.getPackageManager().getSystemAvailableFeatures())if(f.name!=null)out.put(f.name);return out;}
 JSONObject snapshot()throws Exception{
  JSONObject out=new JSONObject(),errors=new JSONObject();String[] ops={"device.info","battery.read","display.read","storage.read","network.read","audio.read","security.read","settings.read","input.read"};
  for(String op:ops)try{out.put(op,execute(op,new JSONObject()).opt("data"));}catch(Exception e){errors.put(op,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
  return out.put("partial",errors.length()>0).put("errors",errors);
 }
 JSONObject diagnostics(String service)throws Exception{
  JSONObject out=new JSONObject().put("service",service).put("bounded",true);Runtime rt=Runtime.getRuntime();
  switch(service){
   case "meminfo":return out.put("runtime_max_bytes",rt.maxMemory()).put("runtime_total_bytes",rt.totalMemory()).put("runtime_free_bytes",rt.freeMemory()).put("low_memory",memoryInfo().lowMemory);
   case "cpuinfo":return out.put("available_processors",rt.availableProcessors()).put("elapsed_realtime_ms",SystemClock.elapsedRealtime()).put("process_cpu_ms",android.os.Process.getElapsedCpuTime());
   case "diskstats":{StatFs fs=new StatFs(c.getFilesDir().getAbsolutePath());return out.put("total_bytes",fs.getTotalBytes()).put("available_bytes",fs.getAvailableBytes());}
   case "thermalservice":return out.put("thermal_status",c.getSystemService(PowerManager.class).getCurrentThermalStatus()).put("power_save",c.getSystemService(PowerManager.class).isPowerSaveMode());
   case "battery":return out.put("battery",execute("battery.read",new JSONObject()).get("data"));
   case "activity":return out.put("agent_pid",android.os.Process.myPid()).put("importance",ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE);
   default:throw new IllegalArgumentException("Unsupported diagnostic service");
  }
 }
 ActivityManager.MemoryInfo memoryInfo(){ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();c.getSystemService(ActivityManager.class).getMemoryInfo(info);return info;}
}
