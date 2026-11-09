package os.aios.agent;

import android.content.Context;
import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import java.util.Locale;
import org.json.JSONObject;

/** Locale-aware presentation only. Never translate protocol IDs or user/model content. */
final class AssistantLocale {
    static Locale systemLocale(Context c) {
        android.os.LocaleList locales=c.getSystemService(android.app.LocaleManager.class).getSystemLocales();
        return locales.isEmpty()?Locale.ENGLISH:locales.get(0);
    }
    static String duration(Context c,long millis) {
        long seconds=Math.max(0,millis)/1000;
        java.util.List<Measure> parts=new java.util.ArrayList<>();
        if(seconds>=3600)parts.add(new Measure(seconds/3600,MeasureUnit.HOUR));
        if(seconds>=60)parts.add(new Measure(seconds/60%60,MeasureUnit.MINUTE));
        parts.add(new Measure(seconds%60,MeasureUnit.SECOND));
        return MeasureFormat.getInstance(systemLocale(c),MeasureFormat.FormatWidth.SHORT).formatMeasures(parts.toArray(new Measure[0]));
    }
    static String label(Context c,String state) {return switch(state) { case "running" -> c.getString(R.string.assistant_running_59424); case "paused" -> c.getString(R.string.assistant_paused_86a83);  default -> c.getString(R.string.assistant_stopped_75ddd); };}
    static String reason(Context c,String state) {return switch(state) { case "completed" -> c.getString(R.string.assistant_complete_e99b4); case "failed" -> c.getString(R.string.assistant_failed_3e3c8); case "interrupted" -> c.getString(R.string.assistant_cancelled_a5ffd); case "idle" -> c.getString(R.string.assistant_not_started_43462); case "ready" -> c.getString(R.string.assistant_not_started_43462); case "closed" -> c.getString(R.string.assistant_closed_f6287);  default -> ""; };}
    static String goal(Context c,JSONObject goal) {if(goal==null)return c.getString(R.string.assistant_no_ongoing_goal_57809);return switch(goal.optString("status")) { case "active" -> c.getString(R.string.assistant_working_on_goal_c875d); case "complete" -> c.getString(R.string.assistant_goal_complete_69d76); case "paused" -> c.getString(R.string.assistant_goal_paused_f4724); case "blocked" -> c.getString(R.string.assistant_goal_blocked_93ae3); case "budgetLimited" -> c.getString(R.string.assistant_goal_budget_exhausted_56256); case "usageLimited" -> c.getString(R.string.assistant_usage_limit_reached_f72b6);  default -> c.getString(R.string.assistant_goal_status_unconfirmed_d67f7); };}
    static String summary(Context c,JSONObject goal) {return goal(c,goal)+(goal==null?"":"\n"+goal.optString("objective"));}
    static String progress(Context c,JSONObject state) {
        org.json.JSONArray args=state.optJSONArray("progress_args");String first=args==null?"":args.optString(0),second=args==null?"":args.optString(1);
        return switch(state.optString("progress_kind")) {
            case "tool" -> c.getString(R.string.assistant_progress_tool,first.isEmpty()?c.getString(R.string.assistant_progress_phone):first);
            case "search" -> c.getString(R.string.assistant_progress_search);
            case "command" -> c.getString(R.string.assistant_progress_command);
            case "retry" -> c.getString(R.string.assistant_progress_retry,first,second);
            case "recovery" -> c.getString(R.string.assistant_progress_recovery);
            case "failed" -> c.getString(R.string.assistant_progress_failed);
            case "reconnecting" -> c.getString(R.string.assistant_reconnecting);
            default -> state.optString("progress");
        };
    }
    static String notice(Context c,JSONObject state){String name=state.optString("notice_resource");int id=name.startsWith("assistant_")?c.getResources().getIdentifier(name,"string",c.getPackageName()):0;return id==0?state.optString("notice"):c.getString(id);}
}
