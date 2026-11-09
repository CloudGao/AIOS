package os.aios.speech;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Durable transcript and summary jobs. No recording text is stored in logs. */
final class MinutesStore extends SQLiteOpenHelper {
 static final String CHANGED="os.aios.speech.MINUTES_CHANGED";
 final Context context;
 MinutesStore(Context c){super(c,"minutes.db",null,3);context=c.getApplicationContext();setWriteAheadLoggingEnabled(true);}
 public void onCreate(SQLiteDatabase d){
  CallRecordings.create(d);
  d.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,kind TEXT,started INTEGER,ended INTEGER,status TEXT,error TEXT DEFAULT '',summary TEXT DEFAULT '',audio TEXT)");
  d.execSQL("CREATE TABLE segments(session TEXT,seq INTEGER,start_ms INTEGER,end_ms INTEGER,speaker TEXT,text TEXT,PRIMARY KEY(session,seq))");
  d.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,session TEXT,part INTEGER,text TEXT,status TEXT,reply TEXT DEFAULT '',error TEXT DEFAULT '',thread_id TEXT DEFAULT '',turn_id TEXT DEFAULT '',retry_safe INTEGER DEFAULT 0,started_ms INTEGER DEFAULT 0,attempt INTEGER DEFAULT 0)");
 }
 public void onUpgrade(SQLiteDatabase d,int a,int b){if(a<2)CallRecordings.create(d);if(a<3){d.execSQL("ALTER TABLE jobs ADD COLUMN retry_safe INTEGER DEFAULT 0");d.execSQL("ALTER TABLE jobs ADD COLUMN started_ms INTEGER DEFAULT 0");d.execSQL("ALTER TABLE jobs ADD COLUMN attempt INTEGER DEFAULT 0");}}
 void changed(){context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));}
 synchronized String create(String kind,String audio){String id=UUID.randomUUID().toString();getWritableDatabase().execSQL("INSERT INTO sessions(id,kind,started,status,audio) VALUES(?,?,?,'recording',?)",new Object[]{id,kind,System.currentTimeMillis(),audio});changed();return id;}
 synchronized void segment(String id,long start,long end,String speaker,String text){if(text.isBlank())return;getWritableDatabase().execSQL("INSERT INTO segments SELECT ?,COALESCE(MAX(seq),0)+1,?,?,?,? FROM segments WHERE session=?",new Object[]{id,start,end,speaker,text,id});changed();}
 synchronized void state(String id,String status,String error){getWritableDatabase().execSQL("UPDATE sessions SET status=?,error=? WHERE id=?",new Object[]{status,error,id});changed();}
 synchronized void recover(){getWritableDatabase().execSQL("UPDATE sessions SET status='interrupted',error='录音进程中断；已保存的转写保留，音频可能不完整' WHERE status IN ('recording','finishing')");}
 synchronized JSONArray list() throws JSONException {JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,kind,started,status FROM sessions ORDER BY started DESC LIMIT 100",null)){while(c.moveToNext())out.put(new JSONObject().put("id",c.getString(0)).put("kind",c.getString(1)).put("started",c.getLong(2)).put("status",c.getString(3)));}return out;}
 synchronized JSONObject detail(String id)throws JSONException{
  JSONObject v=new JSONObject();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,kind,started,ended,status,error,summary,audio FROM sessions WHERE id=?",new String[]{id})){if(!c.moveToFirst())return v;for(int i=0;i<c.getColumnCount();i++)v.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getString(i));}
  JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT start_ms,end_ms,speaker,text FROM segments WHERE session=? ORDER BY start_ms,seq",new String[]{id})){while(c.moveToNext())rows.put(new JSONObject().put("start_ms",c.getLong(0)).put("end_ms",c.getLong(1)).put("speaker",c.getString(2)).put("text",c.getString(3)));}return v.put("segments",rows);
 }
 synchronized JSONObject detailPage(String id,JSONObject params)throws JSONException{
  int offset=params.optInt("offset",0),limit=params.optInt("limit",100),summaryOffset=params.optInt("summary_offset",0);
  if(offset<0||limit<1||limit>100||summaryOffset<0)throw new IllegalArgumentException("Invalid minutes page");
  JSONObject result=detail(id);if(result.length()==0)return result;
  JSONArray all=result.getJSONArray("segments"),page=new JSONArray();int chars=0,index=offset;
  for(;index<all.length()&&page.length()<limit;index++){
   JSONObject item=all.getJSONObject(index);int size=item.toString().length();
   if(chars+size>100000&&page.length()>0)break;
   if(size>100000)throw new IllegalStateException("Transcript segment exceeds page limit");
   page.put(item);chars+=size;
  }
  String summary=result.optString("summary","");int start=Math.min(summaryOffset,summary.length()),end=Math.min(start+40000,summary.length());
  return result.put("segments",page).put("total_segments",all.length()).put("offset",offset)
   .put("next_offset",index<all.length()?index:JSONObject.NULL).put("summary",summary.substring(start,end))
   .put("summary_offset",start).put("summary_chars",summary.length()).put("next_summary_offset",end<summary.length()?end:JSONObject.NULL);
 }
 synchronized void finish(String id,String error)throws JSONException{
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   d.execSQL("UPDATE sessions SET ended=?,status=?,error=? WHERE id=?",new Object[]{System.currentTimeMillis(),error.isEmpty()?"queued":"interrupted",error,id});
   JSONArray segments=detail(id).getJSONArray("segments");StringBuilder chunk=new StringBuilder();int part=0;
   for(int i=0;i<segments.length();i++){JSONObject s=segments.getJSONObject(i);String line="["+s.getLong("start_ms")/1000+"秒] "+s.getString("speaker")+"："+s.getString("text")+"\n";
    if(chunk.length()+line.length()>10000&&chunk.length()>0){job(id,++part,chunk.toString());chunk.setLength(0);}chunk.append(line);}
   if(chunk.length()>0)job(id,++part,chunk.toString());
   if(part>0)d.execSQL("UPDATE sessions SET status='queued' WHERE id=?",new Object[]{id});
   if(part==0)d.execSQL("UPDATE sessions SET status=?,error=? WHERE id=?",new Object[]{error.isEmpty()?"empty":"interrupted",error.isEmpty()?"未识别到语音，不生成总结":error,id});
   d.setTransactionSuccessful();
  }finally{d.endTransaction();}changed();
 }
 private void job(String session,int part,String text){getWritableDatabase().execSQL("INSERT OR IGNORE INTO jobs(id,session,part,text,status) VALUES(?,?,?,?,'queued')",new Object[]{session+":"+part,session,part,text});}
 synchronized JSONObject next()throws JSONException{
  try(Cursor c=getReadableDatabase().rawQuery("SELECT id,session,text,status,part,(SELECT COUNT(*) FROM jobs alljobs WHERE alljobs.session=jobs.session) ,thread_id,turn_id,started_ms,(SELECT reply FROM jobs previous WHERE previous.session=jobs.session AND previous.part<jobs.part AND previous.status='completed' ORDER BY part DESC LIMIT 1),attempt FROM jobs WHERE status IN ('queued','running') AND NOT EXISTS(SELECT 1 FROM jobs earlier WHERE earlier.session=jobs.session AND earlier.part<jobs.part AND earlier.status IN ('failed','interrupted')) ORDER BY rowid LIMIT 1",null)){
   if(!c.moveToFirst())return new JSONObject();String note=detail(c.getString(1)).optString("error","");return new JSONObject().put("request_id",c.getString(0)).put("conversation_id","minutes:"+c.getString(1)).put("text",(note.isEmpty()?"":"录音质量说明："+note+"。请在总结中明确记录这一完整性限制。\n")+"以下是待总结的录音转写，第 "+c.getInt(4)+" / "+c.getInt(5)+" 部分。请输出累计纪要。\n<transcript>\n"+c.getString(2)+"\n</transcript>").put("status",c.getString(3)).put("thread_id",c.getString(6)).put("turn_id",c.getString(7)).put("started_ms",c.getLong(8)).put("previous_summary",c.isNull(9)?"":c.getString(9)).put("attempt",c.getInt(10));}
 }
 synchronized JSONObject retry(String session)throws JSONException{
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
   d.execSQL("UPDATE jobs SET status='queued',reply='',error='',thread_id='',turn_id='',retry_safe=0,started_ms=0,attempt=attempt+1 WHERE session=? AND status='completed' AND part>(SELECT MIN(part) FROM jobs WHERE session=? AND status IN ('failed','interrupted'))",new Object[]{session,session});
   d.execSQL("UPDATE sessions SET summary=COALESCE((SELECT reply FROM jobs WHERE session=? AND status='completed' ORDER BY part DESC LIMIT 1),'') WHERE id=?",new Object[]{session,session});
   d.execSQL("UPDATE jobs SET attempt=attempt+CASE WHEN retry_safe=1 OR thread_id='' THEN 1 ELSE 0 END,status=CASE WHEN retry_safe=1 OR thread_id='' THEN 'queued' ELSE 'running' END,thread_id=CASE WHEN retry_safe=1 THEN '' ELSE thread_id END,turn_id=CASE WHEN retry_safe=1 THEN '' ELSE turn_id END,error='',retry_safe=0,started_ms=0 WHERE session=? AND status IN ('failed','interrupted')",new Object[]{session});
   d.execSQL("UPDATE sessions SET status='queued' WHERE id=? AND EXISTS(SELECT 1 FROM jobs WHERE session=? AND status IN ('queued','running'))",new Object[]{session,session});d.setTransactionSuccessful();
  }finally{d.endTransaction();}changed();return new JSONObject().put("ok",true);
 }
 synchronized JSONObject update(JSONObject p)throws JSONException{
  SQLiteDatabase transaction=getWritableDatabase();transaction.beginTransaction();try{
  String id=p.getString("request_id"),status=p.getString("status");if(!Set.of("running","completed","failed","interrupted").contains(status))throw new IllegalArgumentException("Invalid status");
  String session;try(Cursor c=getReadableDatabase().rawQuery("SELECT session,status,attempt FROM jobs WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new IllegalArgumentException("Unknown job");session=c.getString(0);if(c.getInt(2)!=p.optInt("attempt",0))return new JSONObject().put("ignored",true);if(!p.getString("conversation_id").equals("minutes:"+session))throw new SecurityException("Wrong session");if(Set.of("completed","failed","interrupted").contains(c.getString(1)))return new JSONObject().put("ignored",true);}
  String reply=p.optString("reply","");if(status.equals("completed")&&reply.isBlank())throw new IllegalArgumentException("Empty summary");
  getWritableDatabase().execSQL("UPDATE jobs SET status=?,reply=?,error=?,thread_id=?,turn_id=?,retry_safe=?,started_ms=CASE WHEN started_ms=0 THEN ? ELSE started_ms END WHERE id=?",new Object[]{status,reply,p.optString("error"),p.optString("thread_id"),p.optString("turn_id"),p.optBoolean("retry_safe")?1:0,System.currentTimeMillis(),id});
  boolean pending=false,failed=false;StringBuilder result=new StringBuilder();try(Cursor c=getReadableDatabase().rawQuery("SELECT part,status,reply,error FROM jobs WHERE session=? ORDER BY part",new String[]{session})){int count=c.getCount();while(c.moveToNext()){String st=c.getString(1);pending|=st.equals("queued")||st.equals("running");failed|=st.equals("failed")||st.equals("interrupted");if(st.equals("completed")){result.setLength(0);result.append(c.getString(2)).append("\n\n");}}}
  getWritableDatabase().execSQL("UPDATE sessions SET summary=?,status=? WHERE id=?",new Object[]{result.toString(),failed?"summary_failed":pending?"summarizing":detail(session).optString("error","").isEmpty()?"completed":"completed_partial",session});transaction.setTransactionSuccessful();changed();return new JSONObject().put("ok",true);
  }finally{transaction.endTransaction();}
 }
}
