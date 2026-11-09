package os.aios.workspace;

import java.util.Set;

/** Single presentation contract between detailed runtime states and the three user states. */
public final class ExecutionState {
    public static final String RUNNING="running", PAUSED="paused", STOPPED="stopped";
    private static final Set<String> PAUSED_INTERNAL=Set.of("paused","needs_user_consent","confirming","waiting_for_user","awaiting_user");
    private static final Set<String> STOPPED_INTERNAL=Set.of("idle","ready","completed","failed","interrupted","closed");
    private ExecutionState() {}

    public static String fromInternal(String state) {
        if(PAUSED_INTERNAL.contains(state))return PAUSED;
        if(STOPPED_INTERNAL.contains(state))return STOPPED;
        return RUNNING;
    }

    public static String fromTask(String status) {
        if("doing".equals(status))return RUNNING;
        if("done".equals(status))return STOPPED;
        return PAUSED;
    }

    public static String label(String userState) {
        return switch(userState){case RUNNING->"运行中";case PAUSED->"暂停中";default->"已停止";};
    }

    public static String taskReason(String status) {
        return switch(status){case "done"->"已完成";case "todo"->"待办";case "note"->"待整理";default->"";};
    }

    public static String stoppedReason(String state) {
        return switch(state){case "completed"->"已完成";case "failed"->"失败";case "interrupted"->"已取消";case "idle","ready"->"尚未开始";case "closed"->"已关闭";default->"";};
    }

    public static String taskLabel(String status) {
        String user=label(fromTask(status)),reason=taskReason(status);
        return reason.isEmpty()?user:user+" · "+reason;
    }
}
