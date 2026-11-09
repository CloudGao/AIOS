package os.aios.device.rom;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import java.util.ArrayList;
import java.util.List;
import os.aios.device.DeviceTasks;

public final class RomDeviceTasks implements DeviceTasks {
    private final Context context;
    public RomDeviceTasks(Context context) { this.context = context; }
    @Override public List<Task> tasks() {
        List<Task> result = new ArrayList<>();
        int user = android.os.Process.myUserHandle().getIdentifier();
        for (ActivityManager.RunningTaskInfo task : context.getSystemService(ActivityManager.class).getRunningTasks(100)) {
            if (task.userId != user) continue;
            result.add(new Task(task.taskId, task.displayId,
                task.baseActivity == null ? "" : task.baseActivity.getPackageName(),
                task.topActivity == null ? "" : task.topActivity.getPackageName(),
                task.topActivity == null ? "" : task.topActivity.getClassName(), task.isVisible));
        }
        return result;
    }
    @Override public void move(int taskId, int displayId) throws Exception {
        ActivityTaskManager.getService().moveRootTaskToDisplay(taskId, displayId);
    }
    @Override public void launch(Intent intent, int displayId) {
        context.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle());
    }
    @Override public String permissionControllerPackage() { return context.getPackageManager().getPermissionControllerPackageName(); }
    @Override public void forceStop(String packageName){context.getSystemService(ActivityManager.class).forceStopPackage(packageName);}
    @Override public List<String> controlPackages(String target) {
        List<String> result = new ArrayList<>(List.of(target, "os.aios.permissionanchor"));
        if (!target.equals("com.android.settings")) return result;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.res.Resources resources = pm.getResourcesForApplication(target);
            int id = resources.getIdentifier("config_wallpaper_picker_package", "string", target);
            String companion = id == 0 ? "" : resources.getString(id);
            if (!companion.isEmpty()) {
                ApplicationInfo app = pm.getApplicationInfo(companion, 0);
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && !result.contains(companion)) result.add(companion);
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException | android.content.res.Resources.NotFoundException error) {
            android.util.Log.w("AIOS_UI_SESSION", "Settings companion unavailable", error);
        }
        return result;
    }
}
