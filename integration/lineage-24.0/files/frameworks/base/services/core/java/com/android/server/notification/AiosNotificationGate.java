package com.android.server.notification;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.service.notification.StatusBarNotification;
import java.util.*;
import java.util.function.Consumer;

/** Asynchronous trusted decision, always outside the notification lock. */
final class AiosNotificationGate {
    static final String PREFIX="android.aios.";
    private final Context context;
    private final Handler handler;
    private final Map<Integer,Connection> connections=new HashMap<>();
    private final Map<Integer,Pending> pending=new HashMap<>();
    private int sequence;
    AiosNotificationGate(Context context,Handler handler){this.context=context;this.handler=handler;}
    static void clearUntrusted(android.app.Notification notification){
        for(String key:new ArrayList<>(notification.extras.keySet()))if(key.startsWith(PREFIX))notification.extras.remove(key);
    }
    void evaluate(StatusBarNotification sbn,Consumer<Bundle> complete){
        handler.post(()->{
            if(pending.size()>=64){complete.accept(new Bundle());return;}
            int user=sbn.getUserId();
            if(user<0||android.os.UserHandle.getAppId(sbn.getUid())<10000){complete.accept(new Bundle());return;}
            int requestId=0;
            try{
                if(context.getPackageManager().checkSignatures("android","os.aios.agent")!=PackageManager.SIGNATURE_MATCH){complete.accept(new Bundle());return;}
                int uid=context.getPackageManager().getPackageUidAsUser("os.aios.agent",user);
                int id=++sequence;requestId=id;Pending item=new Pending(id,user,uid,sbn,complete);pending.put(id,item);
                handler.postDelayed(()->finish(id,new Bundle()),45000);
                Connection connection=connections.get(user);
                if(connection==null){connection=new Connection(user);connections.put(user,connection);
                    boolean bound=context.bindServiceAsUser(new Intent().setComponent(new ComponentName("os.aios.agent","os.aios.agent.NotificationGovernanceService")),connection,Context.BIND_AUTO_CREATE,UserHandle.of(user));
                    if(!bound){connections.remove(user);finish(id,new Bundle());return;}
                }
                if(connection.remote!=null)send(item,connection.remote);
            }catch(Exception error){if(requestId!=0)finish(requestId,new Bundle());else complete.accept(new Bundle());}
        });
    }
    private final Messenger replies=new Messenger(new Handler(android.os.Looper.getMainLooper()){
        @Override public void handleMessage(Message message){int id=message.arg1,uid=message.sendingUid;Bundle result=new Bundle(message.getData());handler.post(()->{Pending item=pending.get(id);if(item!=null&&uid==item.uid)finish(item.id,result);});}
    });
    private void send(Pending item,Messenger remote){
        if(item.sent)return;item.sent=true;
        try{Bundle data=new Bundle();StatusBarNotification n=item.notification;
            data.putLong("deadline",item.deadline);data.putBoolean("ongoing",n.isOngoing());
            data.putString("key",n.getKey());data.putString("package",n.getPackageName());data.putString("channel",n.getNotification().getChannelId());
            data.putLong("event_time",n.getNotification().when);data.putLong("posted_ms",n.getPostTime());data.putString("conversation",n.getNotification().getShortcutId());
            data.putString("title",bounded(n.getNotification().extras.getCharSequence("android.title"),2000));
            data.putString("text",bounded(n.getNotification().extras.getCharSequence("android.text"),16000));
            data.putBoolean("progress",n.getNotification().extras.getInt("android.progressMax",0)>0);
            android.os.Parcelable[] messages=n.getNotification().extras.getParcelableArray("android.messages");
            if(messages!=null&&messages.length>0&&messages[messages.length-1] instanceof Bundle last){
                data.putLong("message_time",last.getLong("time"));
                data.putString("text",bounded(last.getCharSequence("text"),16000));
            }
            data.putBoolean("summary",n.getNotification().isGroupSummary());
            data.putBoolean("autogroup",(n.getNotification().flags & android.app.Notification.FLAG_AUTOGROUP_SUMMARY)!=0);
            String category=n.getNotification().category;
            data.putBoolean("special",n.getNotification().isMediaNotification()||android.app.Notification.CATEGORY_CALL.equals(category)||android.app.Notification.CATEGORY_ALARM.equals(category)||android.app.Notification.CATEGORY_NAVIGATION.equals(category));
            Message request=Message.obtain(null,1,item.id,0);request.replyTo=replies;request.setData(data);remote.send(request);
        }catch(Exception error){finish(item.id,new Bundle());}
    }
    private static String bounded(CharSequence input,int limit){String text=input==null?"":input.toString();return text.substring(0,Math.min(limit,text.length()));}
    private void finish(int id,Bundle result){Pending item=pending.remove(id);if(item!=null)item.complete.accept(result);}
    private final class Connection implements ServiceConnection {
        final int user;Messenger remote;
        Connection(int user){this.user=user;}
        public void onServiceConnected(ComponentName name,IBinder binder){handler.post(()->{remote=new Messenger(binder);for(Pending item:new ArrayList<>(pending.values()))if(item.user==user)send(item,remote);});}
        public void onServiceDisconnected(ComponentName name){handler.post(()->{remote=null;fail();});}
        public void onBindingDied(ComponentName name){handler.post(()->{remote=null;connections.remove(user);try{context.unbindService(this);}catch(Exception ignored){}fail();});}
        public void onNullBinding(ComponentName name){onBindingDied(name);}
        private void fail(){for(Pending item:new ArrayList<>(pending.values()))if(item.user==user)finish(item.id,new Bundle());}
    }
    private static final class Pending {
        final long deadline=SystemClock.elapsedRealtime()+45000;final int id,user,uid;final StatusBarNotification notification;final Consumer<Bundle> complete;boolean sent;
        Pending(int id,int user,int uid,StatusBarNotification notification,Consumer<Bundle> complete){this.id=id;this.user=user;this.uid=uid;this.notification=notification;this.complete=complete;}
    }
}
