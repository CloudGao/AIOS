package os.aios.agent;
import android.content.*;
import android.hardware.*;
import android.location.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
final class Streams {
 final Context c;final Handler main=new Handler(Looper.getMainLooper());final Map<String,Runnable> stops=new HashMap<>();final Map<String,JSONObject> info=new HashMap<>();
 final os.aios.device.DeviceEventSink events;final os.aios.device.DeviceResources resources;
 Streams(Context c,os.aios.device.DeviceEventSink events,os.aios.device.DeviceResources resources){this.c=c;this.events=events;this.resources=resources;}
 JSONObject ok(Object data)throws Exception{return new JSONObject().put("state","succeeded").put("data",data);}
 JSONObject location(Location l)throws Exception{return new JSONObject().put("provider",l.getProvider()).put("latitude",l.getLatitude()).put("longitude",l.getLongitude()).put("accuracy_m",l.getAccuracy()).put("time_ms",l.getTime()).put("age_ms",Math.max(0,(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000)).put("wall_clock_age_ms",System.currentTimeMillis()-l.getTime());}
 os.aios.device.DeviceResources.Lease locationForeground(){return resources.acquireForeground(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);}
 void releaseLocation(os.aios.device.DeviceResources.Lease token){token.close();}
 synchronized JSONObject execute(String op,JSONObject p)throws Exception{
  if(!java.util.Set.of("streams.list","streams.stop","location.current","location.stream","sensors.stream").contains(op))throw new IllegalArgumentException("Unknown stream operation");
  if(op.equals("streams.list"))return ok(new JSONArray(info.values()));
  if(op.equals("streams.stop")){String id=p.getString("id");Runnable stop=stops.remove(id);if(stop!=null)stop.run();info.remove(id);return ok(new JSONObject().put("stopped",stop!=null));}
  if(op.equals("location.current")){
   LocationManager lm=c.getSystemService(LocationManager.class);String provider=p.optString("provider","gps");if(!lm.isProviderEnabled(provider))return new JSONObject().put("state","not_supported").put("reason","Requested location provider disabled or absent");
   os.aios.device.DeviceResources.Lease token=locationForeground();CancellationSignal signal=new CancellationSignal();try{CountDownLatch ready=new CountDownLatch(1);Location[] result=new Location[1];lm.getCurrentLocation(provider,signal,c.getMainExecutor(),l->{result[0]=l;ready.countDown();});boolean received=ready.await(10,TimeUnit.SECONDS);signal.cancel();if(!received||result[0]==null)return new JSONObject().put("state","failed").put("reason","No fresh location fix within 10 seconds");long age=(SystemClock.elapsedRealtimeNanos()-result[0].getElapsedRealtimeNanos())/1000000;long wallAge=System.currentTimeMillis()-result[0].getTime();if(age<0||age>15000||Math.abs(wallAge)>15000)return new JSONObject().put("state","failed").put("reason","Location provider returned stale or invalid timestamp").put("age_ms",age).put("wall_clock_age_ms",wallAge);return ok(location(result[0]));}finally{signal.cancel();releaseLocation(token);}
  }
  int seconds=p.optInt("seconds",60),interval=p.optInt("interval_ms",1000);if(seconds<1||seconds>3600||interval<100||interval>60000)throw new IllegalArgumentException("Invalid duration/rate");if(stops.size()>=8)throw new IllegalStateException("At most 8 active streams");String id=UUID.randomUUID().toString();
  if(op.equals("sensors.stream")){
   SensorManager sm=c.getSystemService(SensorManager.class);Sensor sensor=sm.getDefaultSensor(p.getInt("type"));if(sensor==null)return new JSONObject().put("state","not_supported").put("reason","Sensor absent");
   SensorEventListener listener=new SensorEventListener(){long last;public void onAccuracyChanged(Sensor s,int a){}public void onSensorChanged(SensorEvent e){long now=SystemClock.elapsedRealtime();if(now-last<interval)return;last=now;try{JSONArray values=new JSONArray();for(float v:e.values)values.put(v);events.publish("sensor.sample",id,Long.toString(e.timestamp),new JSONObject().put("stream",id).put("type",e.sensor.getType()).put("timestamp_ns",e.timestamp).put("values",values));}catch(Exception ignored){}}};
   if(!sm.registerListener(listener,sensor,interval*1000,main))return new JSONObject().put("state","failed").put("reason","Sensor registration rejected");stops.put(id,()->sm.unregisterListener(listener));
  }else{
   LocationManager lm=c.getSystemService(LocationManager.class);String provider=p.optString("provider","gps");if(!lm.isProviderEnabled(provider))return new JSONObject().put("state","not_supported").put("reason","Location provider disabled or absent");LocationListener listener=l->{try{events.publish("location.sample",id,Long.toString(l.getElapsedRealtimeNanos()),location(l).put("stream",id));}catch(Exception ignored){}};
   os.aios.device.DeviceResources.Lease token=locationForeground();try{lm.requestLocationUpdates(provider,interval,0,listener,Looper.getMainLooper());stops.put(id,()->{try{lm.removeUpdates(listener);}finally{releaseLocation(token);}});}catch(Exception failure){releaseLocation(token);throw failure;}
  }
  JSONObject row=new JSONObject().put("id",id).put("kind",op).put("expires_ms",System.currentTimeMillis()+seconds*1000).put("process_lifetime_only",true);info.put(id,row);
  main.postDelayed(()->{synchronized(Streams.this){Runnable stop=stops.remove(id);if(stop!=null)stop.run();info.remove(id);}},seconds*1000L);return ok(row);
 }
 synchronized void close(){for(Runnable stop:stops.values())stop.run();stops.clear();info.clear();}
}
