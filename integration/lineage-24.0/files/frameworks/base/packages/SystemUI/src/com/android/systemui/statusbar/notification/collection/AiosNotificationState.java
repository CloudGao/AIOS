package com.android.systemui.statusbar.notification.collection;

import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import android.service.notification.StatusBarNotification;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Presentation-only state; never cancels or republishes source notifications. */
public final class AiosNotificationState {
    private static final Uri URI=Uri.parse("content://os.aios.agent.notifications");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<Integer,JSONObject> STATES=new HashMap<>();
    private static final Set<Integer> ABSENT_PROVIDERS=new HashSet<>();
    private static final List<Runnable> LISTENERS=new ArrayList<>();
    private static Context context;
    private static boolean queued,dirty;
    private static final AiosAlertHistory ALERTS=new AiosAlertHistory();
    public static boolean governed(StatusBarNotification sbn){return !ABSENT_PROVIDERS.contains(sbn.getNormalizedUserId())&&!sbn.getNotification().extras.getString("android.aios.identity", "").isEmpty();}
    public static void arriving(StatusBarNotification sbn){
        if(!governed(sbn)||hidden(sbn)||sbn.getNotification().extras.getBoolean("android.aios.old"))return;
        ALERTS.arriving(sbn.getNormalizedUserId(),sbn.getNotification().extras.getString("android.aios.identity"),sbn.getNotification().extras.getLong("android.aios.received",sbn.getPostTime()));
    }
    public static boolean claimHeadsUp(StatusBarNotification sbn){
        if(!governed(sbn))return true;
        Bundle extras=sbn.getNotification().extras;String id=extras.getString("android.aios.identity");
        if(hidden(sbn)||extras.getBoolean("android.aios.old"))return false;
        int user=sbn.getNormalizedUserId();long time=extras.getLong("android.aios.received",sbn.getPostTime());
        android.content.SharedPreferences prefs=context==null?null:context.getSharedPreferences("aios_heads_up",0);
        if(!ALERTS.claim(user,id,time,System.currentTimeMillis(),prefs==null?0:prefs.getLong("time:"+user,0),prefs==null?"":prefs.getString("id:"+user,"")))return false;
        if(prefs!=null)prefs.edit().putLong("time:"+user,time).putString("id:"+user,id).apply();return true;
    }
    public static void initialize(Context c,Runnable changed){
        LISTENERS.add(changed);if(context!=null)return;
        // UserTracker creates a user context whose application context can be null.
        Context application=c.getApplicationContext();context=application!=null?application:c;
        context.getContentResolver().registerContentObserver(URI,true,new ContentObserver(MAIN){@Override public void onChange(boolean self){refresh();}},UserHandle.USER_ALL);
        IntentFilter users=new IntentFilter(Intent.ACTION_USER_SWITCHED);users.addAction(Intent.ACTION_USER_UNLOCKED);
        context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){STATES.clear();ABSENT_PROVIDERS.clear();refresh();}},users);
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addAction(Intent.ACTION_PACKAGE_REPLACED);packages.addDataScheme("package");
        context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(i.getData()!=null&&"os.aios.agent".equals(i.getData().getSchemeSpecificPart()))refresh();}},packages);
        refresh();
    }
    public static void refresh(){
        if(context==null)return;if(queued){dirty=true;return;}queued=true;dirty=false;IO.execute(()->{
            Map<Integer,JSONObject> updates=new HashMap<>();
            Set<Integer> absent=new HashSet<>(),present=new HashSet<>();
            try{for(UserHandle user:context.createContextAsUser(UserHandle.of(android.app.ActivityManager.getCurrentUser()),0).getSystemService(UserManager.class).getUserProfiles()){
                try{Context owner=context.createContextAsUser(user,0);
                    if(owner.getPackageManager().resolveContentProvider(URI.getAuthority(),0)==null){absent.add(user.getIdentifier());continue;}
                    present.add(user.getIdentifier());
                    Bundle result=owner.getContentResolver().call(URI,"snapshot",null,null);
                    if(result!=null&&!result.containsKey("error"))updates.put(user.getIdentifier(),new JSONObject(result.getString("json","{}")));
                }catch(Exception ignored){}
            }}catch(Exception ignored){}
            MAIN.post(()->{queued=false;ABSENT_PROVIDERS.removeAll(present);ABSENT_PROVIDERS.addAll(absent);for(int user:absent)STATES.remove(user);STATES.putAll(updates);for(Runnable listener:LISTENERS)listener.run();if(dirty)refresh();});
        });
    }
    private static JSONObject state(int user){JSONObject value=STATES.get(user);return value==null?new JSONObject():value;}
    private static String display(StatusBarNotification sbn){JSONObject notices=state(sbn.getNormalizedUserId()).optJSONObject("notices");return notices==null?"":notices.optString(sbn.getNotification().extras.getString("android.aios.identity",""));}
    public static boolean hidden(StatusBarNotification sbn){if(ABSENT_PROVIDERS.contains(sbn.getNormalizedUserId()))return false;String display=display(sbn);if(display.equals("restore"))return false;if(display.equals("clear")||display.equals("block"))return true;return sbn.getNotification().extras.getBoolean("android.aios.filtered",false);}
    private static JSONObject app(String key){String[] parts=key.split(":",3);if(parts.length!=3)return new JSONObject();try{JSONObject apps=state(Integer.parseInt(parts[1])).optJSONObject("apps");JSONObject app=apps==null?null:apps.optJSONObject(parts[2]);return app==null?new JSONObject():app;}catch(Exception e){return new JSONObject();}}
    public static boolean pinned(String key){return app(key).optBoolean("pinned");}
    public static String summary(String key,List<NotificationEntry> entries){
        if(context==null||context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked())return "";
        JSONObject app=app(key);String text=app.optString("summary");if(text.isEmpty())return "";
        for(NotificationEntry entry:entries)if(entry.isSensitive().getValue())return "";
        Set<String> active=new HashSet<>();for(NotificationEntry entry:entries)active.add(entry.getSbn().getNotification().extras.getString("android.aios.identity",""));
        try{JSONArray covered=new JSONArray(app.optString("version","[]"));for(int i=0;i<covered.length();i++)if(!active.contains(covered.getString(i)))return "";
            int remaining=active.size()-covered.length();if(remaining>0)text+=" · "+context.getString(com.android.systemui.res.R.string.aios_summary_uncovered,remaining);
        }catch(Exception e){android.util.Log.w("AiosNotificationSummary","Cannot render cached summary",e);return "";}return text;
    }
    public static boolean canPreview(){return context!=null&&!context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked();}
    public static void action(Context c,int user,String method,String identity,String pkg,boolean pinned){
        Bundle request=new Bundle();request.putString("identity",identity);request.putString("package",pkg);request.putBoolean("pinned",pinned);
        IO.execute(()->{boolean failed=false;try{Bundle result=c.createContextAsUser(UserHandle.of(user),0).getContentResolver().call(URI,method,null,request);failed=result==null||result.containsKey("error");}catch(Exception error){failed=true;}
            boolean showFailure=failed;MAIN.post(()->{if(showFailure)android.widget.Toast.makeText(c,com.android.systemui.res.R.string.aios_notification_action_failed,android.widget.Toast.LENGTH_LONG).show();refresh();});});
    }
    private static long[] score(PipelineEntry entry){
        if(entry instanceof BundleEntry bundle){long importance=0,time=0;for(ListEntry child:bundle.getChildren()){long[] score=score(child);importance=Math.max(importance,score[1]);time=Math.max(time,score[2]);}return new long[]{pinned(entry.getKey())?1:0,importance,time};}
        if(entry instanceof GroupEntry group){long importance=0,time=0;for(NotificationEntry child:group.getChildren()){long[] score=score(child);importance=Math.max(importance,score[1]);time=Math.max(time,score[2]);}return new long[]{0,importance,time};}
        if(entry instanceof NotificationEntry notification){Bundle extras=notification.getSbn().getNotification().extras;return new long[]{0,extras.getInt("android.aios.importance",1),extras.getLong("android.aios.received",notification.getSbn().getPostTime())};}
        return new long[]{0,0,0};
    }
    public static int compare(PipelineEntry left,PipelineEntry right){long[] a=score(left),b=score(right);for(int i=0;i<a.length;i++){int compare=Long.compare(b[i],a[i]);if(compare!=0)return compare;}return left.getKey().compareTo(right.getKey());}
}
