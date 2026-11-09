package os.aios.core.automation;

import java.time.*;
import java.util.*;
import org.json.*;

/** Calendar schedules use a named zone; missed occurrences coalesce into one run. */
public final class Schedule {
    public static JSONObject validate(JSONObject s) throws Exception {
        String kind=s.getString("kind");
        Set<String> allowed=kind.equals("once")?Set.of("kind","at"):kind.equals("interval")?Set.of("kind","minutes"):kind.equals("daily")?Set.of("kind","time","zone"):Set.of("kind","time","zone","day");
        for(Iterator<String> keys=s.keys();keys.hasNext();){String key=keys.next();if(!allowed.contains(key))throw new IllegalArgumentException("Unsupported schedule field: "+key);}
        JSONObject r=new JSONObject().put("kind",kind);
        switch(kind) {
            case "once":
                long at=integer(s,"at");
                if(at<=0)throw new IllegalArgumentException("at must be UTC epoch milliseconds");
                r.put("at",at);break;
            case "interval":
                long minutes=integer(s,"minutes");
                if(minutes<15||minutes>525600)throw new IllegalArgumentException("Interval must be 15..525600 minutes");
                r.put("minutes",minutes);break;
            case "daily": case "weekly":
                LocalTime time=LocalTime.parse(s.getString("time"));
                if(time.getSecond()!=0||time.getNano()!=0)throw new IllegalArgumentException("Use HH:mm");
                r.put("time",time.toString()).put("zone",ZoneId.of(s.getString("zone")).getId());
                if(kind.equals("weekly")){
                    long day=integer(s,"day");
                    if(day<1||day>7)throw new IllegalArgumentException("day is ISO Monday=1..Sunday=7");
                    r.put("day",day);
                }break;
            default:throw new IllegalArgumentException("Unknown schedule kind");
        }
        return r;
    }
    private static long integer(JSONObject s,String key)throws Exception{Object v=s.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new IllegalArgumentException(key+" must be an integer");return ((Number)v).longValue();}
    public static long next(JSONObject s,long after) throws Exception {
        switch(s.getString("kind")){
            case "once":return s.getLong("at");
            case "interval":return after+s.getLong("minutes")*60000L;
            default:
                ZoneId zone=ZoneId.of(s.getString("zone"));
                ZonedDateTime now=Instant.ofEpochMilli(after).atZone(zone);
                LocalDate date=now.toLocalDate();LocalTime time=LocalTime.parse(s.getString("time"));
                for(int i=0;i<9;i++,date=date.plusDays(1)){
                    if(s.getString("kind").equals("weekly")&&date.getDayOfWeek().getValue()!=s.getInt("day"))continue;
                    long candidate=ZonedDateTime.of(date,time,zone).toInstant().toEpochMilli();
                    if(candidate>after)return candidate;
                }
                throw new IllegalStateException("No calendar occurrence");
        }
    }
}
