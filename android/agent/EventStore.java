package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import os.aios.runtime.CoreRuntimeClient;

/** Durable publisher: acknowledge locally only after Core commits the stable event ID. */
public final class EventStore {
 private static CoreRuntimeClient runtime;
 private static SQLiteDatabase database;
 private static final java.util.concurrent.ScheduledExecutorService sender=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
 private static final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
 private static boolean scheduled;
 static synchronized void initialize(Context c){
  runtime=new CoreRuntimeClient(c);
  if(database==null){database=new SQLiteOpenHelper(c.getApplicationContext(),"device_event_outbox.db",null,1){
   public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,body TEXT NOT NULL,created_at INTEGER NOT NULL)");d.execSQL("CREATE TABLE receipts(id TEXT PRIMARY KEY,created_at INTEGER NOT NULL)");d.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
   public void onUpgrade(SQLiteDatabase d,int a,int b){throw new IllegalStateException("Unsupported publisher migration");}
  }.getWritableDatabase();}
  if(!scheduled){scheduled=true;sender.scheduleWithFixedDelay(EventStore::drain,0,30,java.util.concurrent.TimeUnit.SECONDS);}flush();
 }
 static synchronized void shutdown(){runtime=null;}
 private static synchronized CoreRuntimeClient get(){if(runtime==null)throw new IllegalStateException("Core event client not initialized");return runtime;}
 private static synchronized SQLiteDatabase db(){if(database==null)throw new IllegalStateException("Event database not initialized");return database;}
 private static void setMeta(String key,String value){db().execSQL("INSERT OR REPLACE INTO meta VALUES(?,?)",new Object[]{key,value});}
 private static String meta(String key,String fallback){try(Cursor c=db().rawQuery("SELECT value FROM meta WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):fallback;}}
 private static void flush(){if(queued.compareAndSet(false,true))sender.execute(()->{queued.set(false);drain();});}
 static void add(String topic,JSONObject data){try{
  synchronized(EventStore.class){if(database==null)return;}
  JSONObject copy=new JSONObject(data.toString());String source=copy.optString("source_id",topic),version=copy.optString("source_version",java.util.UUID.randomUUID().toString());String id=os.aios.runtime.CoreContracts.stableId("event",topic,source,version);
  SQLiteDatabase d=db();d.beginTransaction();try{
   try(Cursor c=d.rawQuery("SELECT id FROM receipts WHERE id=? UNION ALL SELECT id FROM pending WHERE id=? LIMIT 1",new String[]{id,id})){if(c.moveToFirst())return;}
   try(Cursor c=d.rawQuery("SELECT COUNT(*),COALESCE(SUM(length(body)),0) FROM pending",null)){c.moveToFirst();if(c.getLong(0)>=1000||c.getLong(1)>=16*1024*1024){setMeta("overflow",Long.toString(Long.parseLong(meta("overflow","0"))+1));d.setTransactionSuccessful();return;}}
   copy.put("occurred_at",copy.optLong("occurred_at",System.currentTimeMillis()));JSONObject event=new JSONObject().put("id",id).put("topic",topic).put("source_id",source).put("source_version",version).put("data",copy);
   d.execSQL("INSERT INTO pending VALUES(?,?,?)",new Object[]{id,event.toString(),System.currentTimeMillis()});d.setTransactionSuccessful();
  }finally{d.endTransaction();}flush();
 }catch(Exception e){android.util.Log.e("AIOSEvents","Unable to persist event",e);}}
 static void drain(){try{
  synchronized(EventStore.class){if(runtime==null||database==null)return;}
  java.util.List<String> batch=new java.util.ArrayList<>();try(Cursor c=db().rawQuery("SELECT body FROM pending ORDER BY created_at LIMIT 32",null)){while(c.moveToNext())batch.add(c.getString(0));}
  for(String body:batch){JSONObject event=new JSONObject(body),data=event.getJSONObject("data");String id=event.getString("id");
   if(data.has("_snapshot")){android.net.Uri uri=get().allocateDataChannel();try(java.io.OutputStream out=get().openOutput(uri)){out.write(data.getJSONObject("_snapshot").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}data.remove("_snapshot");data.put("content_uri",uri.toString());db().execSQL("UPDATE pending SET body=? WHERE id=?",new Object[]{event.toString(),id});}
   get().call("events","publish",event);SQLiteDatabase d=db();d.beginTransaction();try{d.execSQL("INSERT OR REPLACE INTO receipts VALUES(?,?)",new Object[]{id,System.currentTimeMillis()});d.delete("pending","id=?",new String[]{id});setMeta("last_success",Long.toString(System.currentTimeMillis()));setMeta("last_error","");d.setTransactionSuccessful();}finally{d.endTransaction();}
  }
  db().delete("receipts","created_at<?",new String[]{Long.toString(System.currentTimeMillis()-14L*86400000)});if(batch.size()==32)flush();
 }catch(Exception failure){try{setMeta("last_error",failure.getClass().getSimpleName());}catch(Exception ignored){}android.util.Log.w("AIOSEvents","Outbox retained for retry",failure);}}
 static JSONObject sourceStatus()throws Exception{JSONObject r=new JSONObject().put("notification_listener",!os.aios.host.HostProduct.includes("notification-events")?"not_packaged":NotificationSources.connected()?"connected":"permission_or_connection_required");if(database!=null){r.put("last_published_at",Long.parseLong(meta("last_success","0"))).put("last_error",meta("last_error","")).put("overflow_count",Long.parseLong(meta("overflow","0")));try(Cursor c=db().rawQuery("SELECT COUNT(*) FROM pending",null)){c.moveToFirst();r.put("pending_events",c.getInt(0));}}return r;}
 static JSONObject read(long after)throws Exception{return get().call("events","read",new JSONObject().put("after",after));}
 static JSONObject subscribe(JSONArray topics)throws Exception{return get().call("events","subscribe",new JSONObject().put("topics",topics));}
 static JSONObject poll(String id)throws Exception{return get().call("events","poll",new JSONObject().put("subscription",id));}
 static JSONObject ack(String id,JSONArray events)throws Exception{return get().call("events","ack",new JSONObject().put("subscription",id).put("events",events));}
 static JSONObject fail(String id,String event,String error)throws Exception{return get().call("events","fail",new JSONObject().put("subscription",id).put("event_id",event).put("error",error));}
 static JSONObject unsubscribe(String id)throws Exception{return get().call("events","unsubscribe",new JSONObject().put("subscription",id));}
}
