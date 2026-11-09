package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;

/** Private durable notification decisions; UI changes never publish automation events. */
final class NotificationLedger extends SQLiteOpenHelper {
    NotificationLedger(Context context){super(context,"notification_governance.db",null,1);setWriteAheadLoggingEnabled(true);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE rules (singleton INTEGER PRIMARY KEY CHECK(singleton=1), revision INTEGER NOT NULL, body TEXT NOT NULL)");
        db.execSQL("INSERT INTO rules VALUES(1,0,'[]')");
        db.execSQL("CREATE TABLE notices (identity TEXT PRIMARY KEY, package TEXT NOT NULL, notification_key TEXT NOT NULL, received INTEGER NOT NULL, body TEXT NOT NULL, decision TEXT NOT NULL, result TEXT NOT NULL, cleared INTEGER NOT NULL DEFAULT 0, restored INTEGER NOT NULL DEFAULT 0, applied INTEGER NOT NULL DEFAULT 0, display TEXT NOT NULL DEFAULT '', job_state TEXT NOT NULL DEFAULT '', generated_rule TEXT NOT NULL DEFAULT '', fingerprint TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX notices_recent ON notices(received DESC)");
        db.execSQL("CREATE INDEX notices_package ON notices(package,received DESC)");
        db.execSQL("CREATE TABLE app_state (package TEXT PRIMARY KEY, pinned INTEGER NOT NULL DEFAULT 0, summary TEXT NOT NULL DEFAULT '', summary_version TEXT NOT NULL DEFAULT '', summarized_at INTEGER NOT NULL DEFAULT 0, summary_count INTEGER NOT NULL DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("unsupported_notification_schema");}
    JSONObject rules()throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT revision,body FROM rules WHERE singleton=1",null)){
            c.moveToFirst();return new JSONObject().put("revision",c.getLong(0)).put("items",new JSONArray(c.getString(1)));
        }
    }
    void saveRules(JSONArray rules,long expectedRevision)throws JSONException{
        NotificationRules.validate(rules);ContentValues v=new ContentValues();v.put("revision",expectedRevision+1);v.put("body",rules.toString());
        if(getWritableDatabase().update("rules",v,"singleton=1 AND revision=?",new String[]{Long.toString(expectedRevision)})!=1)throw new IllegalStateException("rules_changed");
    }
    JSONObject decision(String identity)throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT result FROM notices WHERE identity=?",new String[]{identity})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}
    }
    JSONObject previous(String pkg,String key)throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body,result,received FROM notices WHERE applied=1 AND package=? AND notification_key=? ORDER BY received DESC LIMIT 1",new String[]{pkg,key})){
            return c.moveToFirst()?new JSONObject().put("notification",new JSONObject(c.getString(0))).put("result",new JSONObject(c.getString(1))).put("received",c.getLong(2)):null;
        }
    }
    JSONObject duplicate(String pkg,String fingerprint)throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body,result,received FROM notices WHERE applied=1 AND package=? AND fingerprint=? ORDER BY received DESC LIMIT 1",new String[]{pkg,fingerprint})){
            return c.moveToFirst()?new JSONObject().put("notification",new JSONObject(c.getString(0))).put("result",new JSONObject(c.getString(1))).put("received",c.getLong(2)):null;
        }
    }
    void record(JSONObject notification,JSONObject result)throws JSONException{
        ContentValues v=new ContentValues();v.put("identity",notification.getString("identity"));v.put("package",notification.getString("package"));
        v.put("notification_key",notification.getString("key"));v.put("received",System.currentTimeMillis());v.put("body",notification.toString());
        v.put("fingerprint",notification.optString("fingerprint"));v.put("decision",result.getString("decision"));v.put("result",result.toString());
        getWritableDatabase().insertWithOnConflict("notices",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    JSONArray list(boolean trash,int limit,long before)throws JSONException{
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT identity,body,result,received,restored,job_state FROM notices WHERE applied=1 AND decision=? AND cleared=0 AND restored=0 AND received<? ORDER BY received DESC LIMIT ?",new String[]{trash?"filter":"allow",Long.toString(before<=0?Long.MAX_VALUE:before),Integer.toString(Math.max(1,Math.min(limit,100)))})){
            while(c.moveToNext())out.put(new JSONObject().put("identity",c.getString(0)).put("notification",new JSONObject(c.getString(1))).put("result",new JSONObject(c.getString(2))).put("received",c.getLong(3)).put("restored",c.getInt(4)!=0).put("job_state",c.getString(5)));
        }return out;
    }
    void applied(String identity){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            ContentValues v=new ContentValues();v.put("applied",1);db.update("notices",v,"identity=?",new String[]{identity});
            String pkg=null,key=null;try(Cursor c=db.rawQuery("SELECT package,notification_key FROM notices WHERE identity=?",new String[]{identity})){if(c.moveToFirst()){pkg=c.getString(0);key=c.getString(1);}}
            if(pkg!=null){
                db.delete("notices","decision='allow' AND applied=1 AND package=? AND notification_key=? AND identity<>?",new String[]{pkg,key,identity});
                db.execSQL("DELETE FROM notices WHERE decision='allow' AND applied=1 AND package=? AND identity NOT IN (SELECT identity FROM notices WHERE package=? AND decision='allow' AND applied=1 ORDER BY received DESC LIMIT 200)",new Object[]{pkg,pkg});
            }
            db.delete("notices","applied=0 AND received<?",new String[]{Long.toString(System.currentTimeMillis()-120000)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void clear(String identity){ContentValues v=new ContentValues();v.put("cleared",1);getWritableDatabase().update("notices",v,"identity=?",new String[]{identity});}
    void restore(String identity){ContentValues v=new ContentValues();v.put("restored",1);getWritableDatabase().update("notices",v,"identity=? AND decision='filter'",new String[]{identity});}
    boolean pinned(String pkg){try(Cursor c=getReadableDatabase().rawQuery("SELECT pinned FROM app_state WHERE package=?",new String[]{pkg})){return c.moveToFirst()&&c.getInt(0)!=0;}}
    void pin(String pkg,boolean pinned){SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT OR IGNORE INTO app_state(package) VALUES(?)",new Object[]{pkg});ContentValues v=new ContentValues();v.put("pinned",pinned?1:0);db.update("app_state",v,"package=?",new String[]{pkg});}

    JSONObject notice(String identity)throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM notices WHERE identity=?",new String[]{identity})){if(!c.moveToFirst())throw new IllegalArgumentException("notification_missing");return new JSONObject(c.getString(0));}
    }
    void display(String identity,String display){ContentValues v=new ContentValues();v.put("display",display);v.put("restored",display.equals("restore")?1:0);getWritableDatabase().update("notices",v,"identity=?",new String[]{identity});}
    void block(String identity){ContentValues v=new ContentValues();v.put("decision","filter");v.put("display","block");v.put("job_state","queued");v.put("restored",0);v.put("cleared",0);getWritableDatabase().update("notices",v,"identity=?",new String[]{identity});}
    void blockApp(String pkg){ContentValues v=new ContentValues();v.put("decision","filter");v.put("display","block");v.put("restored",0);getWritableDatabase().update("notices",v,"package=?",new String[]{pkg});}
    void job(String identity,String state){ContentValues v=new ContentValues();v.put("job_state",state);getWritableDatabase().update("notices",v,"identity=? AND display='block'",new String[]{identity});}
    boolean blocked(String identity){try(Cursor c=getReadableDatabase().rawQuery("SELECT display FROM notices WHERE identity=?",new String[]{identity})){return c.moveToFirst()&&c.getString(0).equals("block");}}
    void generated(String identity,JSONObject rule)throws JSONException{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(!blocked(identity))return;
            JSONObject snapshot=rules();JSONArray previous=snapshot.getJSONArray("items"),next=new JSONArray().put(rule);
            for(int i=0;i<previous.length();i++)if(!previous.getJSONObject(i).getString("id").equals(rule.getString("id")))next.put(previous.get(i));
            saveRules(next,snapshot.getLong("revision"));ContentValues v=new ContentValues();v.put("generated_rule",rule.getString("id"));v.put("job_state","succeeded");db.update("notices",v,"identity=?",new String[]{identity});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void undo(String identity)throws JSONException{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            String generated="";try(Cursor c=db.rawQuery("SELECT generated_rule FROM notices WHERE identity=?",new String[]{identity})){if(c.moveToFirst())generated=c.getString(0);}
            if(!generated.isEmpty()){JSONObject snapshot=rules();JSONArray previous=snapshot.getJSONArray("items"),next=new JSONArray();for(int i=0;i<previous.length();i++)if(!previous.getJSONObject(i).getString("id").equals(generated))next.put(previous.get(i));saveRules(next,snapshot.getLong("revision"));}
            display(identity,"restore");ContentValues v=new ContentValues();v.put("job_state","cancelled");db.update("notices",v,"identity=?",new String[]{identity});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    JSONObject presentation()throws JSONException{
        JSONObject states=new JSONObject(),apps=new JSONObject();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT identity,display FROM notices WHERE applied=1 AND display<>'' ORDER BY received DESC LIMIT 2000",null)){while(c.moveToNext())states.put(c.getString(0),c.getString(1));}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT package,pinned,summary,summary_version,summarized_at,summary_count FROM app_state",null)){while(c.moveToNext())apps.put(c.getString(0),new JSONObject().put("pinned",c.getInt(1)!=0).put("summary",c.getString(2)).put("version",c.getString(3)).put("time",c.getLong(4)).put("count",c.getInt(5)));}
        return new JSONObject().put("notices",states).put("apps",apps);
    }
    void summary(String pkg,String text,String version,int count){SQLiteDatabase db=getWritableDatabase();db.execSQL("INSERT OR IGNORE INTO app_state(package) VALUES(?)",new Object[]{pkg});ContentValues v=new ContentValues();v.put("summary",text);v.put("summary_version",version);v.put("summary_count",count);v.put("summarized_at",System.currentTimeMillis());db.update("app_state",v,"package=?",new String[]{pkg});}
}
