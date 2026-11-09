package os.aios.agent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import org.json.*;

/** ROM mechanisms; common business logic lives in SharedPlatformServices. */
final class RomPlatformServices extends SharedPlatformServices {
 RomPlatformServices(Context context,os.aios.device.DeviceBackend.ApplicationLauncher launcher){super(context,launcher,DeviceContributions.create(context));}
 JSONObject appFunctions(String op,JSONObject args)throws Exception{return AppFunctionServices.execute(c,op,args);}
 JSONObject radio(String op,JSONObject args)throws Exception{return new RadioServices(c).execute(op,args);}
 JSONObject clock(String op,JSONObject args)throws Exception{
  Bundle request=new Bundle();request.putString("params",args.toString());
  Bundle response=c.getContentResolver().call(Uri.parse("content://com.android.deskclock.aios"),op,null,request);
  if(response==null)throw new IllegalStateException("Clock provider unavailable");
  return new JSONObject(response.getString("result"));
 }
 JSONObject diagnostic(String service)throws Exception{return SystemDiagnostics.read(c,service);}
 JSONObject reboot()throws Exception{return UserRequest.enqueue(c,"reboot",new JSONObject());}
 JSONObject recordMicrophone()throws Exception{return open(new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION));}
 void appendHostCapabilities(JSONArray out)throws Exception{
  out.put(new JSONObject().put("capability","microphone.record").put("available",true).put("backend","native-service"));
  boolean clock=c.getPackageManager().resolveContentProvider("com.android.deskclock.aios",0)!=null;
  for(String cap:new String[]{"alarm.create","timer.create"})out.put(new JSONObject().put("capability",cap).put("available",clock).put("backend","deskclock-provider"));
 }
}
