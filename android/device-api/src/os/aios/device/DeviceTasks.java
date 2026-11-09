package os.aios.device;

import android.content.Intent;
import java.util.List;

/** Current-user task facts and mechanisms; no task/session ownership policy. */
public interface DeviceTasks {
    List<Task> tasks();
    void move(int taskId, int displayId) throws Exception;
    void launch(Intent intent, int displayId);
    void forceStop(String packageName);
    String permissionControllerPackage();
    List<String> controlPackages(String target);
    final class Task {
        public final int id, displayId;
        public final String basePackage, topPackage, topClass;
        public final boolean visible;
        public Task(int id, int displayId, String basePackage, String topPackage, String topClass, boolean visible) {
            this.id = id; this.displayId = displayId; this.basePackage = basePackage;
            this.topPackage = topPackage; this.topClass = topClass; this.visible = visible;
        }
    }
}
