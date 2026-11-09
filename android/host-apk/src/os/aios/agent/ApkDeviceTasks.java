package os.aios.agent;
import android.content.*;import java.util.*;import org.json.*;import os.aios.device.DeviceTasks;
final class ApkDeviceTasks implements DeviceTasks {
    private final Context context;ApkDeviceTasks(Context c){context=c;}
    public List<Task> tasks(){try{JSONArray rows=AdbBridge.call("tasks",new JSONObject()).getJSONArray("tasks");List<Task> tasks=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject t=rows.getJSONObject(i);tasks.add(new Task(t.getInt("id"),t.getInt("display"),t.getString("base"),t.getString("top"),t.getString("class"),t.getBoolean("visible")));}return tasks;
    }catch(Exception e){throw new IllegalStateException("task_observation_unavailable",e);}}
    public void move(int task,int display)throws Exception{AdbBridge.call("move",new JSONObject().put("task",task).put("display",display));}
    public void launch(Intent intent,int display){try{AdbBridge.launch(intent,display);}catch(Exception e){throw new IllegalStateException("launch_unconfirmed",e);}}
    public void forceStop(String pkg){try{AdbBridge.call("force_stop",new JSONObject().put("package",pkg));}catch(Exception e){throw new IllegalStateException("force_stop_unconfirmed",e);}}
    public String permissionControllerPackage(){android.content.pm.ResolveInfo handler=context.getPackageManager().resolveActivity(new Intent("android.intent.action.MANAGE_APP_PERMISSIONS"),android.content.pm.PackageManager.MATCH_SYSTEM_ONLY);return handler==null||handler.activityInfo==null?"":handler.activityInfo.packageName;}
    public List<String> controlPackages(String target){return List.of(target,context.getPackageName());}
}
