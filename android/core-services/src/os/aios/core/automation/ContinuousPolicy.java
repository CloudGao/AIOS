package os.aios.core.automation;
import android.content.*;
import android.os.*;
import org.json.*;
import java.time.*;

/** Continuous admission state; a past charging/topic event never substitutes for current state. */
public final class ContinuousPolicy {
    public static boolean inWindow(int minute,int start,int end){if(start==end)return false;return start<end?minute>=start&&minute<end:minute>=start||minute<end;}
    public static String reason(Context c,JSONObject policy){
        ZonedDateTime now=ZonedDateTime.now();if(!inWindow(now.getHour()*60+now.getMinute(),policy.optInt("start_minute",0),policy.optInt("end_minute",360)))return "outside_night_window";
        Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(battery==null)return "battery_unknown";
        int status=battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1);if(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)==0||(status!=BatteryManager.BATTERY_STATUS_CHARGING&&status!=BatteryManager.BATTERY_STATUS_FULL))return "not_charging";
        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(level<0||scale<=0||level*100/scale<20)return "battery_low_or_unknown";
        PowerManager power=c.getSystemService(PowerManager.class);if(power==null)return "power_state_unknown";if(power.isInteractive())return "device_interactive";if(power.isPowerSaveMode())return "power_save";
        int thermal=power.getCurrentThermalStatus();if(thermal<0||thermal>=PowerManager.THERMAL_STATUS_MODERATE)return "thermal_pause";
        if(c.getFilesDir().getUsableSpace()<1024L*1024*1024)return "storage_low";
        return "";
    }
    public static long nextWindow(JSONObject policy){ZonedDateTime now=ZonedDateTime.now();int minute=policy.optInt("start_minute",0);ZonedDateTime start=now.toLocalDate().atStartOfDay(now.getZone()).plusMinutes(minute);if(!start.isAfter(now))start=start.plusDays(1);return start.toInstant().toEpochMilli();}
}
