package os.aios.core;

import android.app.Instrumentation;
import android.os.Bundle;
import androidx.work.PeriodicWorkRequest;
import androidx.work.impl.model.WorkSpec;
import java.time.*;

/** Pure timing checks against production request construction and the bundled WorkManager.
 * Does not enqueue work, contact a model, or modify production preferences/databases.
 */
public final class DreamTimingAcceptance extends Instrumentation {
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){
  Bundle out=new Bundle();
  try{
   check("2026-11-02T02:00:00+08:00[Asia/Hong_Kong]",3);
   check("2026-11-02T04:00:00+08:00[Asia/Hong_Kong]",3);
   check("2026-11-02T11:00:00+08:00[Asia/Hong_Kong]",12);
   check("2026-11-02T13:00:00+08:00[Asia/Hong_Kong]",12);
   check("2026-11-01T00:00:00-04:00[America/New_York]",3);
   out.putString("stream","PASS: first review eligibility matches the next local window without extra flex delay\n");finish(-1,out);
  }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(1,out);}
 }
 private static void check(String value,int hour){
  ZonedDateTime now=ZonedDateTime.parse(value),expected=now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
  if(!expected.isAfter(now))expected=expected.plusDays(1);
  PeriodicWorkRequest request=DreamScheduler.request(now,hour);
  if(request.getWorkSpec().constraints.getRequiredNetworkType()!=androidx.work.NetworkType.NOT_REQUIRED)throw new AssertionError("Local Core trigger must not depend on its UID network access");
  WorkSpec spec=request.getWorkSpec();spec.lastEnqueueTime=now.toInstant().toEpochMilli();
  if(spec.calculateNextRunTime()!=expected.toInstant().toEpochMilli())throw new AssertionError("Wrong first eligibility for "+value);
  spec.setPeriodCount(1);
  if(spec.calculateNextRunTime()!=spec.lastEnqueueTime+24*3600000L)throw new AssertionError("Recurring interval changed");
 }
}
