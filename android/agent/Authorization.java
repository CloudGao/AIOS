package os.aios.agent;
import android.content.*;
import org.json.*;
import os.aios.runtime.CoreRuntimeClient;
/** Single local owner policy. Remote callers cannot enable it. */
public final class Authorization {
 static final String UI_CONTROL_SETTING="aios_assistant_ui_control_enabled";
 static void syncUiControl(Context c,boolean enabled){
  DeviceHost.synchronizeAuthorization(c,enabled);
 }
 static boolean enabled(Context c){boolean value;try{value=new CoreRuntimeClient(c).call("policy","get",new JSONObject()).getBoolean("enabled");c.getSharedPreferences("authorization-cache",0).edit().putBoolean("enabled",value).apply();}catch(Exception e){value=c.getSharedPreferences("authorization-cache",0).getBoolean("enabled",false);}return value;}
 static void set(Context c,boolean enabled){try{new CoreRuntimeClient(c).call("policy","set",new JSONObject().put("enabled",enabled));c.getSharedPreferences("authorization-cache",0).edit().putBoolean("enabled",enabled).commit();syncUiControl(c,enabled);}catch(Exception e){throw new IllegalStateException("Core authorization service unavailable",e);}}
 static JSONObject status(Context c)throws Exception{return new JSONObject().put("state","succeeded").put("enabled",enabled(c)).put("default_allow",true).put("scope","all_registered_capabilities");}
 static JSONObject denied()throws Exception{return new JSONObject().put("state","denied").put("error","AIOS unified authorization is disabled on the device");}
}
