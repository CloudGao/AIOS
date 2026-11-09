package os.aios.agent;
import android.app.*;
import android.content.*;
import org.json.*;
/** Physical display and invocation identity, never Android's globally focused display. */
final class UiScreenContext {
 static String mainPackage(Context c){
  try{for(os.aios.device.DeviceTasks.Task t:DeviceHost.tasks(c).tasks())
   if(t.displayId==0&&t.visible&&!t.topPackage.isEmpty()){
    String pkg=t.topPackage;
    if(pkg.equals(c.getPackageName())&&t.topClass.equals(AssistantActivity.class.getName()))continue;
    return pkg;
   }}catch(RuntimeException e){android.util.Log.w("AIOSUi","Cannot read physical surface",e);}return "";
 }
 static JSONObject capture(Context c)throws Exception{
  String screen=AssistantConversation.activeUiSession(c);
  String main=mainPackage(c),orb=AssistantOrbService.originPackage();
  return new JSONObject().put("virtual_session",screen).put("main_package",main)
   .put("assistant_package",screen.isEmpty()?(orb.isEmpty()?"":main):"");
 }
}
