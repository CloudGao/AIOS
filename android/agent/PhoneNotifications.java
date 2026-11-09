package os.aios.agent;
import android.service.notification.*;
import android.content.*;
import org.json.*;
public class PhoneNotifications extends NotificationListenerService {
 static volatile PhoneNotifications instance;
 public void onListenerConnected(){instance=this;AgentCapabilities.refresh(this);EventStore.initialize(this);try{StatusBarNotification[] active=getActiveNotifications();if(active!=null)for(StatusBarNotification n:active)onNotificationPosted(n);}catch(Exception e){android.util.Log.w("AIOSEvents","Notification reconnect snapshot unavailable",e);}}
 public void onListenerDisconnected(){instance=null;AgentCapabilities.refresh(this);}
 public void onNotificationPosted(StatusBarNotification n){try{
 NotificationHooks hooks=AgentFeatureBindings.notifications();
 if(hooks!=null)try{hooks.posted(this,n);}catch(Exception error){android.util.Log.w("AIOSNotifications","Governance consumer unavailable",error);}
if(n.getNotification().extras.getBoolean("android.aios.filtered",false)||internalAutomationNotice(n))return;EventStore.add("notification.posted",notification(n));}catch(Exception ignored){}}
 @Override public void onNotificationRemoved(StatusBarNotification n,RankingMap ranking,int reason){
  if(reason==REASON_CANCEL||reason==REASON_CANCEL_ALL||reason==REASON_LISTENER_CANCEL||reason==REASON_LISTENER_CANCEL_ALL){NotificationHooks hooks=AgentFeatureBindings.notifications();if(hooks!=null)hooks.removed(this,n);return;}
  onNotificationRemoved(n);
 }
 public void onNotificationRemoved(StatusBarNotification n){try{NotificationHooks hooks=AgentFeatureBindings.notifications();if(hooks!=null)hooks.removed(this,n);if(n.getNotification().extras.getBoolean("android.aios.filtered",false)||internalAutomationNotice(n))return;EventStore.add("notification.removed",notification(n));}catch(Exception ignored){}}
 // A completion/confirmation notice must not recursively trigger the automation that emitted it.
 static boolean internalAutomationNotice(StatusBarNotification n){return n.getPackageName().equals("os.aios.agent")&&java.util.Set.of("automation_results","interventions").contains(n.getNotification().getChannelId());}
 static JSONObject notification(StatusBarNotification n)throws Exception{
  String title=String.valueOf(n.getNotification().extras.getCharSequence("android.title","")),text=String.valueOf(n.getNotification().extras.getCharSequence("android.text",""));
  return new JSONObject().put("key",n.getKey()).put("package",n.getPackageName()).put("posted_ms",n.getPostTime()).put("occurred_at",n.getPostTime()).put("title",title.substring(0,Math.min(title.length(),1000))).put("text",text.substring(0,Math.min(text.length(),6000))).put("content_truncated",text.length()>6000||title.length()>1000).put("_snapshot",new JSONObject().put("title",title.substring(0,Math.min(title.length(),16000))).put("text",text.substring(0,Math.min(text.length(),200000))).put("truncated",text.length()>200000||title.length()>16000)).put("source_id",n.getKey()).put("source_version",n.getPostTime()+":"+os.aios.runtime.CoreContracts.stableId("notification",title,text));
 }
 static JSONObject read(Context c)throws Exception{
  if(instance==null)return new JSONObject().put("state","permission_required").put("permission","notification_listener_access").put("next_step","Open settings.open page notifications and enable AIOS Control");
  JSONArray out=new JSONArray();for(StatusBarNotification n:instance.getActiveNotifications())if(!n.getNotification().extras.getBoolean("android.aios.filtered",false))out.put(new JSONObject().put("key",n.getKey()).put("package",n.getPackageName()).put("posted_ms",n.getPostTime()).put("title",String.valueOf(n.getNotification().extras.getCharSequence("android.title",""))).put("text",String.valueOf(n.getNotification().extras.getCharSequence("android.text",""))));
  return new JSONObject().put("state","succeeded").put("data",out);
 }
 static JSONObject dismiss(Context c,String key)throws Exception{if(instance==null)return read(c);instance.cancelNotification(key);return new JSONObject().put("state","handed_to_system").put("submitted",true).put("completion_verified",false);}
 static JSONObject reply(String key,String text)throws Exception{
  if(instance==null)return new JSONObject().put("state","permission_required");
  for(StatusBarNotification n:instance.getActiveNotifications())if(n.getKey().equals(key)){
   android.app.Notification.Action[] actions=n.getNotification().actions;
   if(actions!=null)for(android.app.Notification.Action action:actions){
    android.app.RemoteInput[] inputs=action.getRemoteInputs();if(inputs==null)continue;
    for(android.app.RemoteInput input:inputs)if(input.getAllowFreeFormInput()){
     android.os.Bundle result=new android.os.Bundle();result.putCharSequence(input.getResultKey(),text);Intent intent=new Intent();
     android.app.RemoteInput.addResultsToIntent(inputs,intent,result);action.actionIntent.send(instance,0,intent);
     return new JSONObject().put("state","handed_to_app").put("completion_verified",false);
    }
   }
   return new JSONObject().put("state","not_supported").put("reason","Notification has no free-form reply action");
  }
  return new JSONObject().put("state","failed").put("reason","Notification no longer exists");
 }
}
