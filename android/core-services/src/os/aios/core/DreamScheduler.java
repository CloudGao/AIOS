package os.aios.core;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

final class DreamScheduler {
 private static final int VERSION=3;
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("dream_schedule",0);}
 static synchronized void restore(Context c)throws Exception{
  SharedPreferences p=prefs(c);
  // Do not enable reviews before Agent has supplied the existing user preference.
  if(p.contains("enabled"))configure(c,p.getBoolean("enabled",false),p.getBoolean("twice",false));
 }
 static JSONObject status(Context c)throws Exception{
  SharedPreferences p=prefs(c);
  return new JSONObject().put("configured",p.contains("enabled")).put("enabled",p.getBoolean("enabled",false))
   .put("registered_at",p.getLong("registered_at",0)).put("dispatched_at",p.getLong("dispatched_at",0));
 }
 static synchronized void configure(Context c,boolean enabled,boolean twice)throws Exception{
  SharedPreferences p=prefs(c);
  // Save intent before enqueue, so registration/boot can repair an interrupted operation.
  if(!p.edit().putBoolean("enabled",enabled).putBoolean("twice",twice).commit())throw new IllegalStateException("dream_schedule_persist_failed");
  WorkManager wm=WorkManager.getInstance(c);
  wm.cancelUniqueWork("aios-core-dream-periodic").getResult().get();
  if(!enabled){
   wm.cancelUniqueWork("aios-core-dream-night").getResult().get();
   wm.cancelUniqueWork("aios-core-dream-noon").getResult().get();return;
  }
  boolean migrate=p.getInt("version",0)<VERSION;
  scheduleDaily(wm,"aios-core-dream-night",3,migrate);
  if(twice)scheduleDaily(wm,"aios-core-dream-noon",12,migrate);
  else wm.cancelUniqueWork("aios-core-dream-noon").getResult().get();
  if(!p.edit().putInt("version",VERSION).putLong("registered_at",System.currentTimeMillis()).commit())throw new IllegalStateException("dream_schedule_persist_failed");
 }
 private static void scheduleDaily(WorkManager wm,String name,int hour,boolean migrate)throws Exception{
  PeriodicWorkRequest request=request(ZonedDateTime.now(),hour);
  // Upgrade old requests once without cancelling an in-flight trigger. Ordinary
  // process starts preserve the existing request, enqueue time and initial delay.
  wm.enqueueUniquePeriodicWork(name,migrate?ExistingPeriodicWorkPolicy.UPDATE:ExistingPeriodicWorkPolicy.KEEP,request).getResult().get();
 }
 static PeriodicWorkRequest request(ZonedDateTime now,int hour){
  ZonedDateTime next=now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
  if(!next.isAfter(now))next=next.plusDays(1);
  // Core does no network I/O and has no INTERNET permission. Network eligibility belongs to Agent.
  Constraints limits=new Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build();
  // Full-period flex: initialDelay alone controls the first eligible run. A one-hour
  // flex adds another 23 hours in WorkSpec.calculateNextRunTime() on the first run.
  return new PeriodicWorkRequest.Builder(DreamTriggerWorker.class,24,TimeUnit.HOURS)
   .setInitialDelay(Duration.between(now,next).toMillis(),TimeUnit.MILLISECONDS).setConstraints(limits)
   .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build();
 }
 static void manual(Context c)throws Exception{
  Constraints limits=new Constraints.Builder().setRequiresBatteryNotLow(true).build();
  WorkManager.getInstance(c).enqueueUniqueWork("aios-core-dream-manual",ExistingWorkPolicy.REPLACE,
   new OneTimeWorkRequest.Builder(DreamTriggerWorker.class).setConstraints(limits).setInputData(new Data.Builder().putBoolean("manual",true).build()).build());
 }
}
