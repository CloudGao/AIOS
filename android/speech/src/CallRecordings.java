package os.aios.speech;

import android.content.*;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import org.json.*;
import java.io.File;

/** Exact Telecom creation time and phone account association. Never guess a nearby call. */
final class CallRecordings {
 static void create(android.database.sqlite.SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS call_links(session TEXT PRIMARY KEY,created INTEGER,account TEXT,component TEXT,number TEXT)");}
 static String normalized(String value){return value==null?"":PhoneNumberUtils.isUriNumber(value)?value:PhoneNumberUtils.normalizeNumber(value);}
 static void link(MinutesStore store,String session,Intent intent){
  long created=intent.getLongExtra("call_created",0);if(created<=0)return;
  store.getWritableDatabase().execSQL("INSERT OR REPLACE INTO call_links VALUES(?,?,?,?,?)",new Object[]{session,created,intent.getStringExtra("call_account")==null?"":intent.getStringExtra("call_account"),intent.getStringExtra("call_component")==null?"":intent.getStringExtra("call_component"),normalized(intent.getStringExtra("call_number"))});
 }
 static JSONObject find(Context context,MinutesStore store,long[] ids)throws Exception{
  JSONArray recordings=new JSONArray();if(ids==null||ids.length>100)throw new IllegalArgumentException("At most 100 call IDs required");
  String[] columns={CallLog.Calls._ID,CallLog.Calls.DATE,CallLog.Calls.PHONE_ACCOUNT_ID,CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,CallLog.Calls.NUMBER};
  for(long id:ids){if(id<=0)continue;
   try(Cursor call=context.getContentResolver().query(android.content.ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI,id),columns,null,null,null)){
    if(call==null||!call.moveToFirst())continue;
    long date=call.getLong(1);String account=call.isNull(2)?"":call.getString(2),component=call.isNull(3)?"":call.getString(3),number=normalized(call.getString(4));
    int matches=0;
    try(Cursor duplicates=context.getContentResolver().query(CallLog.Calls.CONTENT_URI,columns,CallLog.Calls.DATE+"=?",new String[]{Long.toString(date)},null)){
     if(duplicates!=null)while(duplicates.moveToNext())if(account.equals(duplicates.isNull(2)?"":duplicates.getString(2))&&component.equals(duplicates.isNull(3)?"":duplicates.getString(3)))matches++;
    }
    if(matches!=1)continue;
    try(Cursor sessions=store.getReadableDatabase().rawQuery("SELECT s.id,s.status,s.audio,s.summary,(SELECT COUNT(*) FROM segments WHERE session=s.id) FROM sessions s JOIN call_links c ON s.id=c.session WHERE s.kind='call' AND c.created=? AND c.account=? AND c.component=? AND (c.number=? OR c.number='') ORDER BY s.started",new String[]{Long.toString(date),account,component,number})){
     while(sessions.moveToNext()){
      File folder=new File(sessions.getString(2));String prefix=new File(context.getFilesDir(),"recordings").getCanonicalPath()+File.separator;
      boolean audio=folder.getCanonicalPath().startsWith(prefix)&&(new File(folder,"uplink.wav").length()>44||new File(folder,"downlink.wav").length()>44);
      recordings.put(new JSONObject().put("call_id",id).put("date",date).put("session_id",sessions.getString(0)).put("status",sessions.getString(1)).put("recorded",audio).put("transcribed",sessions.getInt(4)>0).put("summarized",!sessions.isNull(3)&&!sessions.getString(3).isEmpty()));
     }
    }
   }
  }
  return new JSONObject().put("recordings",recordings);
 }
}
