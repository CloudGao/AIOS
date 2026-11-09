package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;
public final class BackgroundExpiryTests extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  if(BackgroundExpiry.due(999999,0)||BackgroundExpiry.due(300099,100)||!BackgroundExpiry.due(300100,100)||BackgroundExpiry.due(50,100))throw new AssertionError("five minute boundary");
  if(BackgroundExpiry.due(300100,300000)||BackgroundExpiry.due(300100,0))throw new AssertionError("interaction/resume resets timer");
  if(!UiSessionController.targetPackages(getTargetContext(),"com.android.settings").contains("com.android.wallpaper"))throw new AssertionError("Settings system companion declared");
  if(UiSessionController.targetPackages(getTargetContext(),"com.android.calculator2").contains("com.android.wallpaper"))throw new AssertionError("companion scope leaked");
  android.content.Context isolated=new android.content.ContextWrapper(getTargetContext()){
   public android.content.SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("background_acceptance_"+name,mode);}
  };
  try {
   isolated.getSharedPreferences("ui_conversation_owners",0).edit().putString("screen",new org.json.JSONObject().put("conversation_id","fixture").toString()).commit();
   for(String status:new String[]{"running","queued","completed","failed","interrupted","paused"}){
    isolated.getSharedPreferences("assistant_conversation",0).edit().putString("state",new org.json.JSONObject().put("conversation_id","fixture").put("status",status).toString()).commit();
    boolean stopped=AssistantConversation.stoppedUiOwner(isolated,"screen");if(stopped==java.util.Set.of("running","queued").contains(status))throw new AssertionError("owner state "+status);
   }
   if(AssistantConversation.stoppedUiOwner(isolated,"missing"))throw new AssertionError("unknown ownership must not expire");
   isolated.getSharedPreferences("ui_conversation_owners",0).edit().putString("screen",new org.json.JSONObject().put("conversation_id","schedule:fixture").toString()).commit();
   if(AssistantConversation.stoppedUiOwner(isolated,"screen"))throw new AssertionError("scheduler owns scheduled lifecycle");
  }finally{for(String name:new String[]{"ui_conversation_owners","assistant_conversation","assistant_conversation_archive"})getTargetContext().deleteSharedPreferences("background_acceptance_"+name);}
  result.putString("stream","PASS 16 expiry boundary/reset assertions\n");finish(-1,result);
 }catch(Throwable e){result.putString("stream","FAIL "+e);finish(0,result);}}
}
