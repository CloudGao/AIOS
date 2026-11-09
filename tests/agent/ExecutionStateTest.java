package os.aios.workspace;

public final class ExecutionStateTest {
 public static void main(String[] args){int checks=0;
  for(String state:new String[]{"queued","running","waiting_for_app","creating","preparing","resuming","observation_retry","unknown_future_state"}){if(!ExecutionState.RUNNING.equals(ExecutionState.fromInternal(state)))throw new AssertionError(state);checks++;}
  for(String state:new String[]{"paused","needs_user_consent","confirming","waiting_for_user","awaiting_user"}){if(!ExecutionState.PAUSED.equals(ExecutionState.fromInternal(state)))throw new AssertionError(state);checks++;}
  for(String state:new String[]{"idle","ready","completed","failed","interrupted","closed"}){if(!ExecutionState.STOPPED.equals(ExecutionState.fromInternal(state)))throw new AssertionError(state);checks++;}
  if(!"运行中".equals(ExecutionState.label(ExecutionState.fromTask("doing"))))throw new AssertionError();checks++;
  if(!"暂停中 · 待办".equals(ExecutionState.taskLabel("todo")))throw new AssertionError();checks++;
  if(!"已停止 · 已完成".equals(ExecutionState.taskLabel("done")))throw new AssertionError();checks++;
  System.out.println(checks+" execution-state assertions passed");
 }
}
