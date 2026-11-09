package os.aios.agent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.*;
import org.json.*;
import java.util.*;

/** Direct provider operations; UI editors remain separate capabilities. */
final class CalendarServices {
 final Context c;
 CalendarServices(Context c){this.c=c;}
 JSONObject ok(Object data)throws Exception{return new JSONObject().put("state","succeeded").put("data",data).put("completion_verified",true);}
 JSONArray rows(Uri uri,String[] columns,String where,String[] args,String order,int limit)throws Exception{
  JSONArray out=new JSONArray();try(Cursor cur=c.getContentResolver().query(uri,columns,where,args,order)){if(cur!=null)while(cur.moveToNext()&&out.length()<limit){JSONObject row=new JSONObject();for(int i=0;i<cur.getColumnCount();i++)row.put(cur.getColumnName(i),cur.isNull(i)?JSONObject.NULL:cur.getString(i));out.put(row);}}return out;
 }
 String[] eventColumns={"_id","calendar_id","title","dtstart","dtend","eventTimezone","eventLocation","description","allDay","rrule","duration","deleted"};
 JSONObject get(long id)throws Exception{
  JSONArray events=rows(ContentUris.withAppendedId(Events.CONTENT_URI,id),eventColumns,null,null,null,1);
  if(events.length()==0||events.getJSONObject(0).optString("deleted").equals("1"))throw new IllegalArgumentException("Event not found");
  JSONObject event=events.getJSONObject(0);if(event.isNull("dtend")&&!event.isNull("duration"))event.put("first_end_ms",event.getLong("dtstart")+java.time.Duration.parse(event.getString("duration")).toMillis());event.put("reminders",rows(Reminders.CONTENT_URI,new String[]{"minutes","method"},"event_id=?",new String[]{String.valueOf(id)},"minutes ASC",100));return event;
 }
 long calendar(long requested,String timezone)throws Exception{
  if(requested>0){JSONArray found=rows(Calendars.CONTENT_URI,new String[]{"_id"},"_id=? AND calendar_access_level>=?",new String[]{String.valueOf(requested),String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)},null,1);if(found.length()==0)throw new IllegalArgumentException("Calendar absent or read-only");return requested;}
  JSONArray local=rows(Calendars.CONTENT_URI,new String[]{"_id"},"account_name=? AND account_type=? AND name=?",new String[]{"AIOS",CalendarContract.ACCOUNT_TYPE_LOCAL,"AIOS-local"},null,1);if(local.length()>0)return local.getJSONObject(0).getLong("_id");
  ContentValues v=new ContentValues();v.put(Calendars.ACCOUNT_NAME,"AIOS");v.put(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL);v.put(Calendars.NAME,"AIOS-local");v.put(Calendars.CALENDAR_DISPLAY_NAME,"AIOS local calendar");v.put(Calendars.CALENDAR_COLOR,0xff4285f4);v.put(Calendars.CALENDAR_ACCESS_LEVEL,Calendars.CAL_ACCESS_OWNER);v.put(Calendars.OWNER_ACCOUNT,"AIOS");v.put(Calendars.CALENDAR_TIME_ZONE,timezone);v.put(Calendars.VISIBLE,1);v.put(Calendars.SYNC_EVENTS,1);
  Uri uri=Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(Calendars.ACCOUNT_NAME,"AIOS").appendQueryParameter(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build();Uri result=c.getContentResolver().insert(uri,v);if(result==null)throw new IllegalStateException("Calendar creation failed");return ContentUris.parseId(result);
 }
 void validateRule(String rule){
  if(rule.isEmpty())return;if(rule.length()>500)throw new IllegalArgumentException("RRULE too long");HashSet<String> keys=new HashSet<>();
  for(String part:rule.split(";")){String[] kv=part.split("=",-1);if(kv.length!=2||!keys.add(kv[0]))throw new IllegalArgumentException("Malformed RRULE");String key=kv[0],value=kv[1];
   switch(key){
    case "FREQ":if(!Arrays.asList("DAILY","WEEKLY","MONTHLY","YEARLY").contains(value))throw new IllegalArgumentException("FREQ DAILY/WEEKLY/MONTHLY/YEARLY required");break;
    case "INTERVAL":case "COUNT":int n=Integer.parseInt(value);if(n<1||n>1000)throw new IllegalArgumentException("RRULE value 1..1000");break;
    case "BYDAY":for(String d:value.split(","))if(!d.matches("([+-]?[1-5])?(MO|TU|WE|TH|FR|SA|SU)"))throw new IllegalArgumentException("Invalid BYDAY");break;
    case "WKST":if(!value.matches("MO|TU|WE|TH|FR|SA|SU"))throw new IllegalArgumentException("Invalid WKST");break;
    case "BYMONTH":case "BYMONTHDAY":for(String v:value.split(",")){int x=Integer.parseInt(v);if(key.equals("BYMONTH")?(x<1||x>12):(x==0||x< -31||x>31))throw new IllegalArgumentException("Invalid recurrence date");}break;
    case "UNTIL":if(value.matches("[0-9]{8}"))java.time.LocalDate.parse(value,java.time.format.DateTimeFormatter.BASIC_ISO_DATE);else java.time.LocalDateTime.parse(value,java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withResolverStyle(java.time.format.ResolverStyle.STRICT));break;
    default:throw new IllegalArgumentException("Unsupported RRULE component: "+key);
   }
  }if(!keys.contains("FREQ")||keys.contains("COUNT")&&keys.contains("UNTIL"))throw new IllegalArgumentException("Invalid recurrence bounds");
 }
 JSONObject execute(String op,JSONObject p)throws Exception{
  if(c.checkSelfPermission("android.permission.READ_CALENDAR")!=0)return new JSONObject().put("state","permission_required").put("permission","android.permission.READ_CALENDAR");
  int limit=p.optInt("limit",50);if(limit<1||limit>200)throw new IllegalArgumentException("limit 1..200");
  if(op.equals("calendar.calendars"))return ok(rows(Calendars.CONTENT_URI,new String[]{"_id","calendar_displayName","account_name","account_type","calendar_access_level","visible"},"_id>?",new String[]{String.valueOf(p.optLong("after_id",0))},"_id ASC",limit));
  if(op.equals("calendar.get"))return ok(get(p.getLong("id")));
  if(op.equals("calendar.query"))return ok(rows(Events.CONTENT_URI,eventColumns,"_id>? AND deleted=0",new String[]{String.valueOf(p.optLong("after_id",0))},"_id ASC",limit));
  if(op.equals("calendar.instances")){
   long start=p.getLong("begin_ms"),end=p.getLong("end_ms");if(start<0||end<=start||end-start>366L*86400000)throw new IllegalArgumentException("Instance interval must be within 366 days");
   Uri.Builder uri=Instances.CONTENT_URI.buildUpon();ContentUris.appendId(uri,start);ContentUris.appendId(uri,end);
   return ok(rows(uri.build(),new String[]{"event_id","title","begin","end","allDay","calendar_id"},"begin>? OR (begin=? AND event_id>?)",new String[]{String.valueOf(p.optLong("after_begin_ms",-1)),String.valueOf(p.optLong("after_begin_ms",-1)),String.valueOf(p.optLong("after_event_id",0))},"begin ASC,event_id ASC",limit));
  }
  if(c.checkSelfPermission("android.permission.WRITE_CALENDAR")!=0)return new JSONObject().put("state","permission_required").put("permission","android.permission.WRITE_CALENDAR");
  long begin=p.getLong("begin_ms"),end=p.getLong("end_ms");String title=p.getString("title").trim();boolean allDay=p.optBoolean("all_day",false);
  if(title.isEmpty()||title.length()>200||begin<0||end<=begin)throw new IllegalArgumentException("Title and valid start/end required");
  String timezone=p.optString("timezone",TimeZone.getDefault().getID());if(!Arrays.asList(TimeZone.getAvailableIDs()).contains(timezone))throw new IllegalArgumentException("Unknown timezone");
  if(allDay){if(begin%86400000!=0||end%86400000!=0)throw new IllegalArgumentException("All-day timestamps must be UTC midnight; end is exclusive");timezone="UTC";}
  JSONArray reminders=p.optJSONArray("reminder_minutes");if(reminders==null)reminders=new JSONArray();if(reminders.length()>5)throw new IllegalArgumentException("At most 5 reminders");
  HashSet<Integer> unique=new HashSet<>();for(int i=0;i<reminders.length();i++){int min=reminders.getInt(i);if(min<0||min>40320||!unique.add(min))throw new IllegalArgumentException("Reminder values must be unique, 0..40320");}
  long id=op.equals("calendar.update")?p.getLong("id"):-1;JSONObject previous=id>0?get(id):null;
  String rule=p.optString("rrule","");validateRule(rule);
  if(previous!=null&&previous.getLong("dtstart")!=p.getLong("expected_begin_ms"))throw new IllegalStateException("Event changed; read again");
  long calendarId=calendar(previous!=null?previous.getLong("calendar_id"):p.optLong("calendar_id",0),timezone);
  ContentValues v=new ContentValues();v.put(Events.CALENDAR_ID,calendarId);v.put(Events.TITLE,title);v.put(Events.DTSTART,begin);v.put(Events.DTEND,end);v.put(Events.EVENT_TIMEZONE,timezone);v.put(Events.EVENT_LOCATION,p.optString("location",""));v.put(Events.DESCRIPTION,p.optString("description",""));v.put(Events.ALL_DAY,allDay?1:0);v.put(Events.HAS_ALARM,unique.isEmpty()?0:1);
  if(rule.isEmpty()){v.putNull(Events.RRULE);v.putNull(Events.DURATION);}else{v.put(Events.RRULE,rule);v.putNull(Events.DTEND);v.put(Events.DURATION,allDay?"P"+((end-begin)/86400000)+"D":"PT"+((end-begin)/1000)+"S");if((end-begin)%1000!=0)throw new IllegalArgumentException("Recurring duration must be whole seconds");}
  ArrayList<ContentProviderOperation> ops=new ArrayList<>();
  if(id<0)ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(v).build());else{
   ops.add(ContentProviderOperation.newUpdate(Events.CONTENT_URI).withSelection("_id=? AND dtstart=?",new String[]{String.valueOf(id),String.valueOf(p.getLong("expected_begin_ms"))}).withValues(v).withExpectedCount(1).build());
   ops.add(ContentProviderOperation.newDelete(Reminders.CONTENT_URI).withSelection("event_id=?",new String[]{String.valueOf(id)}).build());
  }
  for(int min:unique){ContentProviderOperation.Builder b=ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValue(Reminders.MINUTES,min).withValue(Reminders.METHOD,Reminders.METHOD_ALERT);if(id<0)b.withValueBackReference(Reminders.EVENT_ID,0);else b.withValue(Reminders.EVENT_ID,id);ops.add(b.build());}
  ContentProviderResult[] result=c.getContentResolver().applyBatch(CalendarContract.AUTHORITY,ops);if(id<0)id=ContentUris.parseId(result[0].uri);
  JSONObject saved=get(id);if(!saved.getString("title").equals(title)||saved.getLong("dtstart")!=begin||saved.optLong("dtend",saved.optLong("first_end_ms",-1))!=end)throw new IllegalStateException("Write readback mismatch; inspect event "+id);
  return ok(saved);
 }
}
