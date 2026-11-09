package os.aios.core.automation;
import android.content.Context;import androidx.work.*;
public final class AssistantScheduleWorker extends Worker {
 public AssistantScheduleWorker(Context c,WorkerParameters p){super(c,p);}
 public Result doWork(){try{AutomationService.get().reconcile();return Result.success();}catch(Exception e){return Result.retry();}}
}
