package os.aios.agent;
import android.content.*;
import android.bluetooth.*;
import android.media.*;
import android.net.*;
import android.net.wifi.*;
import android.telephony.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
/** Privileged ROM radio/control implementation, outside shared business sources. */
final class RadioServices {
 final Context c;RadioServices(Context c){this.c=c;}
 JSONObject ok(Object data)throws Exception{return new JSONObject().put("state","succeeded").put("data",data);}
 JSONObject submit(boolean accepted)throws Exception{return new JSONObject().put("state",accepted?"handed_to_system":"failed").put("accepted",accepted).put("completion_verified",false);}
 JSONObject execute(String op,JSONObject p)throws Exception{
  if(!java.util.Arrays.asList("hotspot.read","hotspot.set","mobile_data.read","mobile_data.set","audio.routes","audio.route.set","bluetooth.device","bluetooth.pair","bluetooth.unpair","bluetooth.connect","bluetooth.disconnect","bluetooth.active.set").contains(op))throw new IllegalArgumentException("Unknown operation");
  if(op.startsWith("mobile_data.")){
   TelephonyManager tm=c.getSystemService(TelephonyManager.class);if(!c.getPackageManager().hasSystemFeature("android.hardware.telephony"))return new JSONObject().put("state","not_supported").put("reason","Telephony hardware absent");
   int sub=p.optInt("subscription_id",SubscriptionManager.getDefaultDataSubscriptionId());if(sub==-1)sub=SubscriptionManager.getDefaultDataSubscriptionId();if(!SubscriptionManager.isValidSubscriptionId(sub))return new JSONObject().put("state","not_supported").put("reason","No valid data SIM subscription");tm=tm.createForSubscriptionId(sub);
   if(op.endsWith("set")){if(tm.isDataEnabled()!=p.getBoolean("expected_enabled"))throw new IllegalArgumentException("Data state changed");tm.setDataEnabledForReason(TelephonyManager.DATA_ENABLED_REASON_USER,p.getBoolean("enabled"));}
   boolean enabled=tm.isDataEnabled();JSONObject data=new JSONObject().put("subscription_id",sub).put("enabled",enabled);if(op.endsWith("set")&&enabled!=p.getBoolean("enabled"))return new JSONObject().put("state","handed_to_system").put("data",data);return ok(data);
  }
  if(op.startsWith("hotspot.")){
   WifiManager wm=c.getSystemService(WifiManager.class);int state=wm.getWifiApState();if(op.endsWith("read"))return ok(new JSONObject().put("state",state).put("enabled",state==WifiManager.WIFI_AP_STATE_ENABLED));
   if(state==WifiManager.WIFI_AP_STATE_ENABLING||state==WifiManager.WIFI_AP_STATE_DISABLING)return new JSONObject().put("state","outcome_unknown").put("reason","Hotspot state is changing; read again before writing");
   if((state==WifiManager.WIFI_AP_STATE_ENABLED)!=p.getBoolean("expected_enabled"))throw new IllegalArgumentException("Hotspot state changed");
   TetheringManager manager=c.getSystemService(TetheringManager.class);if(!p.getBoolean("enabled")){manager.stopTethering(TetheringManager.TETHERING_WIFI);return submit(true);}
   CountDownLatch ready=new CountDownLatch(1);int[] error={-1};manager.startTethering(new TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build(),c.getMainExecutor(),new TetheringManager.StartTetheringCallback(){public void onTetheringStarted(){error[0]=0;ready.countDown();}public void onTetheringFailed(int e){error[0]=e;ready.countDown();}});
   if(!ready.await(10,TimeUnit.SECONDS))return new JSONObject().put("state","outcome_unknown").put("reason","Tethering callback timed out; read hotspot state");boolean enabled=wm.getWifiApState()==WifiManager.WIFI_AP_STATE_ENABLED;return new JSONObject().put("state",error[0]!=0?"failed":enabled?"succeeded":"handed_to_system").put("completion_verified",error[0]==0&&enabled).put("error_code",error[0]).put("enabled",enabled);
  }
  if(op.startsWith("audio.route")){
   AudioManager am=c.getSystemService(AudioManager.class);
   if(op.equals("audio.route.set")){int id=p.getInt("id");if(id==0)am.clearCommunicationDevice();else{AudioDeviceInfo selected=null;for(AudioDeviceInfo d:am.getAvailableCommunicationDevices())if(d.getId()==id)selected=d;if(selected==null)throw new IllegalArgumentException("Audio route unavailable");if(!am.setCommunicationDevice(selected))return submit(false);}}
   JSONArray available=new JSONArray();for(AudioDeviceInfo d:am.getAvailableCommunicationDevices())available.put(new JSONObject().put("id",d.getId()).put("name",d.getProductName()).put("type",d.getType()));AudioDeviceInfo active=am.getCommunicationDevice();JSONObject data=new JSONObject().put("available",available).put("active_id",active==null?0:active.getId()).put("scope","communication audio");if(op.equals("audio.route.set")&&p.getInt("id")!=0&&(active==null||active.getId()!=p.getInt("id")))return new JSONObject().put("state","handed_to_system").put("completion_verified",false).put("data",data);return ok(data);
  }
  BluetoothAdapter adapter=c.getSystemService(BluetoothManager.class).getAdapter();if(adapter==null)return new JSONObject().put("state","not_supported").put("reason","Bluetooth adapter absent");
  String address=p.getString("address");if(!BluetoothAdapter.checkBluetoothAddress(address))throw new IllegalArgumentException("Invalid Bluetooth address");BluetoothDevice device=adapter.getRemoteDevice(address);
  switch(op){
   case "bluetooth.device":return ok(new JSONObject().put("address",address).put("name",device.getName()).put("bond_state",device.getBondState()).put("connected",device.isConnected()));
   case "bluetooth.pair":return submit(device.createBond());
   case "bluetooth.unpair":return submit(device.removeBond());
  }
  String profile=p.optString("profile","a2dp");if(!profile.equals("a2dp")&&!profile.equals("headset"))throw new IllegalArgumentException("Unsupported Bluetooth profile");int which=profile.equals("headset")?BluetoothProfile.HEADSET:BluetoothProfile.A2DP;CountDownLatch ready=new CountDownLatch(1);BluetoothProfile[] proxy=new BluetoothProfile[1];boolean[] expired={false};
  adapter.getProfileProxy(c,new BluetoothProfile.ServiceListener(){public void onServiceConnected(int type,BluetoothProfile value){synchronized(proxy){if(expired[0]){adapter.closeProfileProxy(type,value);return;}proxy[0]=value;ready.countDown();}}public void onServiceDisconnected(int type){}},which);
  if(!ready.await(5,TimeUnit.SECONDS)){synchronized(proxy){expired[0]=true;if(proxy[0]!=null)adapter.closeProfileProxy(which,proxy[0]);}return new JSONObject().put("state","not_supported").put("reason","Bluetooth profile service unavailable");}
  try{
   String method=op.equals("bluetooth.connect")?"connect":op.equals("bluetooth.disconnect")?"disconnect":"setActiveDevice";
   Object accepted=proxy[0].getClass().getMethod(method,BluetoothDevice.class).invoke(proxy[0],device);return submit(Boolean.TRUE.equals(accepted));
  }finally{adapter.closeProfileProxy(which,proxy[0]);}
 }
}
