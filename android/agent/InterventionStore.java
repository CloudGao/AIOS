package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;

/** Private durable requests, events and result outbox; native UI is the only decision writer. */
final class InterventionStore extends SQLiteOpenHelper {
 InterventionStore(Context c){this(c,"interventions.db");}
 InterventionStore(Context c,String name){super(c,name,null,1);setWriteAheadLoggingEnabled(true);}
 public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE interventions(id TEXT PRIMARY KEY,thread TEXT NOT NULL,request TEXT NOT NULL,dedupe TEXT UNIQUE NOT NULL,body TEXT NOT NULL,state TEXT NOT NULL,revision INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE intervention_events(id INTEGER PRIMARY KEY AUTOINCREMENT,request_id TEXT NOT NULL,event TEXT NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE intervention_outbox(id TEXT PRIMARY KEY,payload TEXT NOT NULL,state TEXT NOT NULL)");
 }
 public void onUpgrade(SQLiteDatabase d,int a,int b){throw new IllegalStateException("Unsupported intervention schema");}
 synchronized JSONObject get(String id)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM interventions WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("Request missing");return new JSONObject(c.getString(0));}}
 synchronized JSONArray list(String thread)throws Exception{JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM interventions WHERE thread=? ORDER BY rowid DESC LIMIT 40",new String[]{thread})){while(c.moveToNext())out.put(new JSONObject(c.getString(0)));}return out;}
 synchronized JSONArray forTurn(String thread,String turn,String request)throws Exception{
  JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM interventions WHERE thread=? ORDER BY rowid",new String[]{thread})){
   while(c.moveToNext()){JSONObject row=new JSONObject(c.getString(0));if(!request.isEmpty()?request.equals(row.optString("assistant_request_id")):!turn.isEmpty()&&turn.equals(row.optString("turn_id")))out.put(row);}
  }return out;
 }
 synchronized JSONObject create(JSONObject v,String dedupe)throws Exception{
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   try(Cursor c=d.rawQuery("SELECT body FROM interventions WHERE dedupe=?",new String[]{dedupe})){if(c.moveToFirst()){JSONObject old=new JSONObject(c.getString(0));if(!old.getString("snapshot").equals(v.getString("snapshot")))throw new IllegalStateException("Confirmation content changed; use a new step");d.setTransactionSuccessful();return old;}}
   try(Cursor active=d.rawQuery("SELECT id FROM interventions WHERE thread=? AND state IN ('waiting_user','handoff_active') LIMIT 1",new String[]{v.getString("thread_id")})){if(active.moveToFirst())throw new IllegalStateException("Original thread already awaits user input");}
   ContentValues x=new ContentValues();x.put("id",v.getString("id"));x.put("thread",v.getString("thread_id"));x.put("request",v.getString("assistant_request_id"));x.put("dedupe",dedupe);x.put("body",v.toString());x.put("state",v.getString("state"));x.put("revision",v.getLong("revision"));d.insertOrThrow("interventions",null,x);event(d,v.getString("id"),"waiting_user");d.setTransactionSuccessful();return v;
  }finally{d.endTransaction();}
 }
 synchronized JSONObject transition(String id,long revision,String state,JSONObject result)throws Exception{
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{JSONObject v=get(id);if(v.getLong("revision")!=revision||!java.util.Set.of("waiting_user","handoff_active").contains(v.getString("state")))throw new IllegalStateException("Request already handled or changed");
   v.put("state",state).put("revision",revision+1).put("updated_at",System.currentTimeMillis());if(result!=null)v.put("result",result);
   ContentValues x=new ContentValues();x.put("body",v.toString());x.put("state",state);x.put("revision",revision+1);if(d.update("interventions",x,"id=? AND revision=?",new String[]{id,Long.toString(revision)})!=1)throw new IllegalStateException("Concurrent decision");event(d,id,state);
   if(result!=null){ContentValues o=new ContentValues();o.put("id",id);o.put("payload",result.toString());o.put("state","pending");d.insertOrThrow("intervention_outbox",null,o);}d.setTransactionSuccessful();return v;
  }finally{d.endTransaction();}
 }
 synchronized void annotateScreen(String id,String pkg)throws Exception{JSONObject row=get(id);row.put("host_screen_package",pkg);ContentValues x=new ContentValues();x.put("body",row.toString());getWritableDatabase().update("interventions",x,"id=? AND state='waiting_user'",new String[]{id});}
 synchronized void deleteThread(String thread){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.execSQL("DELETE FROM intervention_outbox WHERE id IN (SELECT id FROM interventions WHERE thread=?)",new Object[]{thread});d.execSQL("DELETE FROM intervention_events WHERE request_id IN (SELECT id FROM interventions WHERE thread=?)",new Object[]{thread});d.delete("interventions","thread=?",new String[]{thread});d.setTransactionSuccessful();}finally{d.endTransaction();}}
 synchronized boolean undelivered(String thread,String request){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM intervention_outbox o JOIN interventions i ON o.id=i.id WHERE i.thread=? AND i.request=? AND o.state='pending' LIMIT 1",new String[]{thread,request})){return c.moveToFirst();}}
 synchronized JSONArray pending(){JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM interventions WHERE state IN ('waiting_user','handoff_active') OR id IN (SELECT id FROM intervention_outbox WHERE state='pending')",null)){while(c.moveToNext())try{out.put(new JSONObject(c.getString(0)));}catch(JSONException e){throw new IllegalStateException(e);}}return out;}
 synchronized void delivered(String id){getWritableDatabase().execSQL("UPDATE intervention_outbox SET state='delivered' WHERE id=?",new Object[]{id});}
 private void event(SQLiteDatabase d,String id,String event){d.execSQL("INSERT INTO intervention_events(request_id,event,created_at) VALUES(?,?,?)",new Object[]{id,event,System.currentTimeMillis()});}
}
