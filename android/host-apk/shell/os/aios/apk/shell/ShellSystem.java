package os.aios.apk.shell;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.bluetooth.*;
import android.provider.Settings;
import org.json.*;
/** Narrow system operations. The helper does not accept arbitrary shell commands. */
final class ShellSystem {
 static BluetoothAdapter bluetooth(Context c)throws Exception {
  Class<?> init=Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
  if(init.getMethod("getBluetoothServiceManager").invoke(null)==null){Class<?> manager=Class.forName("android.os.BluetoothServiceManager");init.getMethod("setBluetoothServiceManager",manager).invoke(null,manager.getConstructor().newInstance());}
  android.content.Context shell=new android.content.ContextWrapper(c){
   @Override public String getOpPackageName(){return "com.android.shell";}
   @Override public android.content.AttributionSource getAttributionSource(){return new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build();}
   @Override public Context createAttributionContext(String tag){return this;}
   @Override public Context createDeviceContext(int device){return this;}
  };
  return BluetoothManager.class.getConstructor(Context.class).newInstance(shell).getAdapter();
 }

 static JSONObject ok(JSONObject data)throws Exception{return new JSONObject().put("state","succeeded").put("data",data).put("backend","adb");}
 static JSONObject execute(Context c,String op,JSONObject p)throws Exception {
  switch(op){
   case "display.brightness.set":{int value=p.getInt("value");if(value<0||value>255)throw new IllegalArgumentException("brightness_out_of_range");if(!Settings.System.putInt(c.getContentResolver(),"screen_brightness",value))throw new IllegalStateException("brightness_write_rejected");int actual=Settings.System.getInt(c.getContentResolver(),"screen_brightness",-1);return ok(new JSONObject().put("brightness",actual)).put("state",value==actual?"succeeded":"outcome_unknown").put("completion_verified",value==actual);}
   case "wifi.read":case "wifi.set":{WifiManager manager=c.getSystemService(WifiManager.class);if(manager==null)return new JSONObject().put("state","not_supported");if(op.equals("wifi.read"))return ok(new JSONObject().put("enabled",manager.isWifiEnabled()).put("state",manager.getWifiState()));if(manager.isWifiEnabled()!=p.getBoolean("expected_enabled"))throw new IllegalStateException("wifi_state_changed");boolean accepted=manager.setWifiEnabled(p.getBoolean("enabled"));return new JSONObject().put("state",accepted?"handed_to_system":"failed").put("accepted",accepted).put("completion_verified",false).put("backend","adb");}
   case "bluetooth.read":case "bluetooth.set":{BluetoothAdapter adapter=bluetooth(c);if(adapter==null)return new JSONObject().put("state","not_supported");if(op.equals("bluetooth.read")){JSONArray peers=new JSONArray();for(BluetoothDevice device:adapter.getBondedDevices())peers.put(new JSONObject().put("name",device.getName()).put("address",device.getAddress()).put("bond_state",device.getBondState()));return ok(new JSONObject().put("enabled",adapter.isEnabled()).put("bonded_devices",peers));}if(adapter.isEnabled()!=p.getBoolean("expected_enabled"))throw new IllegalStateException("bluetooth_state_changed");boolean accepted=p.getBoolean("enabled")?adapter.enable():adapter.disable();return new JSONObject().put("state",accepted?"handed_to_system":"failed").put("accepted",accepted).put("completion_verified",false).put("backend","adb");}
   default:throw new IllegalArgumentException("unsupported_shell_system_operation");
  }
 }
}
