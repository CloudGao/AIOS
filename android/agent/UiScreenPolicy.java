package os.aios.agent;
/** Pure screen decision table, shared by application-open and UI-create routes. */
final class UiScreenPolicy {
 static String mode(boolean existingVirtual,boolean onMain,boolean sameOrigin){
  return existingVirtual?"background":onMain?(sameOrigin?"foreground":"choice"):"background";
 }
 static String recovery(String reason){return switch(reason){
  case "observation_unavailable" -> "The virtual screen is unchanged. Its accessibility tree is unavailable; retry observation or use screenshot. Do not infer foreground ownership or move/recreate this session.";
  case "device_locked" -> "Unlock the phone, then observe this same session again.";
  case "permission_pending" -> "Handle the existing permission dialog, then resume the same session.";
  case "observation_required" -> "Input was not executed. Read tree or screenshot for this session and decide again with its observation_id; never replay stale coordinates.";
  case "waiting_for_app","creating","preparing","resuming" -> "This is a transient running state, not a terminal result and not a request for user input. Keep the same assigned session and continue observing until it becomes active or returns an explicit failure.";
  case "foreground_owned" -> "The target is actually visible on the physical screen. Respect user control and request a screen choice.";
  default -> "Inspect session_state and detail. Resume only after a new user request permits it. Do not infer a different screen from this error.";
 };}
}
