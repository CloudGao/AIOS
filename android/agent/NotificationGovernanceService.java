package os.aios.agent;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Framework-only pre-presentation policy endpoint. */
public final class NotificationGovernanceService extends Service {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64));
    private final ThreadPoolExecutor urgentWorker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64));
    private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message message){
            if(message.sendingUid!=android.os.Process.SYSTEM_UID||message.replyTo==null)return;
            Bundle input=new Bundle(message.getData());Messenger reply=message.replyTo;int id=message.arg1;
            try{
                boolean urgent=input.getBoolean("autogroup")||input.getBoolean("ongoing")||input.getBoolean("special");
                try{urgent|=NotificationClassifier.urgent(new JSONObject().put("title",input.getString("title","")).put("text",input.getString("text","")))!=null;}catch(JSONException ignored){}
                (urgent?urgentWorker:worker).execute(()->process(input,reply,id));}catch(RejectedExecutionException full){respond(reply,id,new Bundle());}
        }
    });
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    @Override public void onDestroy(){worker.shutdownNow();urgentWorker.shutdownNow();super.onDestroy();}
    private void process(Bundle input,Messenger reply,int id){
        Bundle result=new Bundle();
        if(android.os.SystemClock.elapsedRealtime()>input.getLong("deadline",Long.MAX_VALUE)){respond(reply,id,result);return;}
        try(NotificationLedger store=new NotificationLedger(this)){
            JSONObject notification=new JSONObject();
            for(String key:new String[]{"key","package","channel","title","text","conversation"})notification.put(key,input.getString(key,""));
            notification.put("posted_ms",input.getLong("posted_ms"));
            notification.put("event_time",input.getLong("event_time"));
            notification.put("message_time",input.getLong("message_time"));
            JSONObject rules=store.rules();
            android.content.SharedPreferences prefs=getSharedPreferences("notification_preferences",0);
            boolean filter=prefs.getBoolean("model_filter",true),smart=prefs.getBoolean("smart_importance",false);
            String config=NotificationClassifier.POLICY_VERSION+":"+rules.getLong("revision")+":"+filter+":"+smart+":"+SharedModelCatalog.resolve(this,"notification.filter").toString()+":"+SharedModelCatalog.resolve(this,"notification.importance").toString();
            String identity=hash(notification.toString()+config);notification.put("identity",identity);
            notification.put("fingerprint",hash(notification.optString("package")+"\n"+notification.optString("channel")+"\n"+notification.optString("title")+"\n"+notification.optString("text")+"\n"+notification.optLong("message_time")));
            JSONObject decision=store.decision(identity);
            if(decision==null){
                decision=NotificationRules.evaluate(rules.getJSONArray("items"),notification,(model,instruction,n)->SharedTextInference.judge(this,model,instruction,n));
                // Explicit rules are terminal. Never replace a user's allow/filter/importance.
                if(!decision.has("rule_id")&&!input.getBoolean("autogroup")&&!input.getBoolean("ongoing")&&!input.getBoolean("special")){
                    JSONObject urgent=(filter||smart)?NotificationClassifier.rules(notification):NotificationClassifier.urgent(notification);
                    if(urgent!=null){decision=urgent;if(!filter)decision.put("decision","allow");}
                    else if(filter||smart){
                        if(android.os.SystemClock.elapsedRealtime()+5000<input.getLong("deadline",Long.MAX_VALUE)){
                            try{
                                decision=SharedTextInference.classify(this,filter?"notification.filter":"notification.importance","inherit","",notification);
                                if(filter&&smart&&decision.optString("decision").equals("allow")
                                        &&!SharedModelCatalog.resolve(this,"notification.filter").toString().equals(SharedModelCatalog.resolve(this,"notification.importance").toString())){
                                    try{JSONObject ranked=SharedTextInference.classify(this,"notification.importance","inherit","",notification);decision.put("importance",ranked.getInt("importance"));}
                                    catch(Exception ignored){/* Preserve the successful filter decision. */}
                                }
                                if(!filter)decision.put("decision","allow");
                                if(!smart&&decision.optString("decision").equals("allow"))decision.put("importance",1);
                            }catch(Exception error){decision=new JSONObject().put("decision","allow").put("importance",1).put("reason_code","model_failed");}
                        }else decision.put("reason_code","model_deadline");
                    }
                }
                JSONObject previous=store.previous(notification.getString("package"),notification.getString("key"));
                if(previous==null&&(input.getBoolean("ongoing")||notification.optLong("message_time")>0))previous=store.duplicate(notification.getString("package"),notification.getString("fingerprint"));
                boolean fresh=!input.getBoolean("summary");
                if(previous!=null){JSONObject old=previous.getJSONObject("notification");
                    if(notification.optLong("message_time")>0)fresh=notification.optLong("message_time")>old.optLong("message_time");
                    else if(old.optString("title").equals(notification.optString("title"))&&old.optString("text").equals(notification.optString("text"))
                            &&(input.getBoolean("ongoing")||input.getBoolean("progress")
                                ||(notification.optLong("event_time")>0&&notification.optLong("event_time")==old.optLong("event_time"))))fresh=false;
                    if(input.getBoolean("progress"))fresh=false;
                }
                if(input.getBoolean("summary"))fresh=false;
                decision.put("fresh",fresh).put("received_ms",fresh||previous==null?System.currentTimeMillis():previous.getJSONObject("result").optLong("received_ms",previous.getLong("received")));
                store.record(notification,decision);
            }
            result.putBoolean("filtered",decision.optString("decision").equals("filter"));
            result.putInt("importance",decision.optInt("importance",1));
            String reason=decision.optString("reason_code");
            result.putBoolean("ranked",decision.has("rule_id")||reason.equals("time_sensitive_service")||reason.startsWith("builtin_")
                ||(smart&&java.util.Set.of("model_urgent","model_normal","model_advertising","model_suspected_fraud").contains(reason)));
            result.putBoolean("pinned",store.pinned(notification.getString("package")));
            result.putString("identity",identity);
            result.putBoolean("old",!decision.optBoolean("fresh",true));
            result.putLong("received",decision.optLong("received_ms",input.getLong("posted_ms")));
            result.putBoolean("summary",input.getBoolean("summary"));
        }catch(Exception error){android.util.Log.w("AIOSNotifications","Decision unavailable: "+error.getClass().getSimpleName());}
        respond(reply,id,result);
    }
    static String hash(String text)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static void respond(Messenger reply,int id,Bundle result){try{Message response=Message.obtain(null,1,id,0);response.setData(result);reply.send(response);}catch(RemoteException ignored){}}
}
