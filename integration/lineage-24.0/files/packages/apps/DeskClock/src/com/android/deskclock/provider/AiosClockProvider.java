package com.android.deskclock.provider;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import com.android.deskclock.data.*;
import com.android.deskclock.alarms.AlarmStateManager;
import org.json.*;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.*;

/** Fixed clock operations for the preinstalled AIOS adapter. */
public final class AiosClockProvider extends ContentProvider {
 private static final Set<String> OPS=Set.of("alarm.list","alarm.get","alarm.create","alarm.update","alarm.delete","timer.list","timer.get","timer.create","timer.update","timer.delete","timer.start","timer.pause","timer.reset");
 public boolean onCreate(){return true;}
 public Bundle call(String method,String arg,Bundle extras){
  Context c=getContext();c.enforceCallingOrSelfPermission("android.permission.SET_TIME","AIOS clock permission required");
  try{
   ApplicationInfo app=c.getPackageManager().getApplicationInfo("os.aios.agent",0);
   if(app.uid!=Binder.getCallingUid()||(app.flags&ApplicationInfo.FLAG_SYSTEM)==0)throw new SecurityException("System AIOS agent required");
   if(!OPS.contains(method))throw new IllegalArgumentException("Unknown clock operation");
   String raw=extras==null?"{}":extras.getString("params","{}");if(raw.length()>8192)throw new IllegalArgumentException("Clock request too large");
   JSONObject params=new JSONObject(raw);
   FutureTask<JSONObject> task=new FutureTask<>(()->execute(method,params));
   long identity=Binder.clearCallingIdentity();
   try{
    Handler main=new Handler(Looper.getMainLooper());
    if(Looper.myLooper()==Looper.getMainLooper())task.run();else if(!main.post(task))throw new IllegalStateException("Clock main thread unavailable");
    JSONObject result;
    try{result=new JSONObject().put("state","succeeded").put("data",task.get(15,TimeUnit.SECONDS));}
    catch(ExecutionException failure){result=new JSONObject().put("state","outcome_unknown").put("error","Clock operation did not finish cleanly; read current state before retrying");}
    catch(TimeoutException timeout){task.cancel(false);main.removeCallbacks(task);result=new JSONObject().put("state","outcome_unknown").put("error","Clock request timed out; read state before retrying");}
    Bundle reply=new Bundle();reply.putString("result",result.toString());return reply;
   }finally{Binder.restoreCallingIdentity(identity);}
  }catch(SecurityException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Clock operation failed",e);}
 }
 private static long integer(JSONObject p,String key,long min,long max)throws JSONException{
  Object value=p.get(key);if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException(key+" must be integer");
  long n=((Number)value).longValue();if(n<min||n>max)throw new IllegalArgumentException(key+" out of range");return n;
 }
 private static String label(JSONObject p)throws JSONException{Object v=p.opt("label");if(v==null)return "AIOS";if(!(v instanceof String)||((String)v).length()>200)throw new IllegalArgumentException("Invalid label");return (String)v;}
 private static boolean flag(JSONObject p,String name,boolean fallback)throws JSONException{if(!p.has(name))return fallback;Object v=p.get(name);if(!(v instanceof Boolean))throw new IllegalArgumentException("Invalid "+name);return (Boolean)v;}
 private JSONObject timer(Timer t)throws JSONException{if(t==null)throw new IllegalArgumentException("Timer not found");return new JSONObject().put("id",t.getId()).put("state",t.getState().name()).put("label",t.getLabel()).put("length_ms",t.getLength()).put("remaining_ms",t.getRemainingTime());}
 private JSONObject alarm(Alarm a)throws JSONException{if(a==null)throw new IllegalArgumentException("Alarm not found");return new JSONObject().put("id",a.id).put("hour",a.hour).put("minute",a.minutes).put("enabled",a.enabled).put("label",a.label).put("repeat_bits",a.daysOfWeek.getBits());}
 private JSONObject execute(String op,JSONObject p)throws Exception{
  Context c=getContext();ContentResolver cr=c.getContentResolver();DataModel dm=DataModel.getDataModel();
  if(op.startsWith("timer.")){
   if(op.equals("timer.list")){JSONArray items=new JSONArray();for(Timer t:dm.getTimers())items.put(timer(t));return new JSONObject().put("items",items);}
   if(op.equals("timer.create")){long length=integer(p,"seconds",1,86400)*1000;String text=label(p);boolean start=flag(p,"start",true);Timer t=dm.addTimer(length,text,false);if(start)dm.startTimer(t);return timer(dm.getTimer(t.getId()));}
   int id=(int)integer(p,"id",0,Integer.MAX_VALUE);Timer t=dm.getTimer(id);if(t==null)throw new IllegalArgumentException("Timer not found");
   switch(op){
    case "timer.get":return timer(t);
    case "timer.delete":dm.removeTimer(t);return new JSONObject().put("deleted",dm.getTimer(id)==null).put("verified_absent",dm.getTimer(id)==null);
    case "timer.start":dm.startTimer(t);break;
    case "timer.pause":dm.pauseTimer(t);break;
    case "timer.reset":dm.resetTimer(t);break;
    case "timer.update":dm.updateAiosTimer(t,integer(p,"seconds",1,86400)*1000,label(p));break;
    default:throw new IllegalArgumentException("Unknown timer operation");
   }return timer(dm.getTimer(id));
  }
  if(op.equals("alarm.list")){JSONArray items=new JSONArray();for(Alarm a:Alarm.getAlarms(cr,null))items.put(alarm(a));return new JSONObject().put("items",items);}
  boolean create=op.equals("alarm.create");Alarm a=create?new Alarm():Alarm.getAlarm(cr,integer(p,"id",1,Long.MAX_VALUE));if(a==null)throw new IllegalArgumentException("Alarm not found");
  if(op.equals("alarm.get"))return alarm(a);
  if(op.equals("alarm.delete")){AlarmStateManager.deleteAllInstances(c,a.id);boolean deleted=Alarm.deleteAlarm(cr,a.id);return new JSONObject().put("deleted",deleted).put("verified_absent",Alarm.getAlarm(cr,a.id)==null);}
  int hour=(int)integer(p,"hour",0,23),minute=(int)integer(p,"minute",0,59),bits=p.has("repeat_bits")?(int)integer(p,"repeat_bits",0,127):0;
  String text=label(p);boolean enabled=flag(p,"enabled",true);
  if(!create)AlarmStateManager.deleteAllInstances(c,a.id);
  a.hour=hour;a.minutes=minute;a.daysOfWeek=Weekdays.fromBits(bits);a.label=text;a.enabled=enabled;
  if(create)Alarm.addAlarm(cr,a);else Alarm.updateAlarm(cr,a);
  if(enabled){AlarmInstance instance=a.createInstanceAfter(Calendar.getInstance());AlarmInstance.addInstance(cr,instance);AlarmStateManager.registerInstance(c,instance,true);}
  return alarm(Alarm.getAlarm(cr,a.id));
 }
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
 public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
