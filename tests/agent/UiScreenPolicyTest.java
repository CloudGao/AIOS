package os.aios.agent;
public class UiScreenPolicyTest {
 public static void main(String[] args){int checks=0;
  for(boolean existing:new boolean[]{false,true})for(boolean main:new boolean[]{false,true})for(boolean origin:new boolean[]{false,true}){
   String want=existing?"background":!main?"background":origin?"foreground":"choice";
   if(!want.equals(UiScreenPolicy.mode(existing,main,origin)))throw new AssertionError(want);checks++;
  }
  for(String reason:new String[]{"observation_unavailable","device_locked","permission_pending","observation_required","session_unavailable","session_busy"}){
   if(UiScreenPolicy.recovery(reason).contains("leave the foreground"))throw new AssertionError(reason);checks++;
  }
  if(!UiScreenPolicy.recovery("observation_unavailable").contains("screenshot"))throw new AssertionError();checks++;
  if(!UiScreenPolicy.recovery("foreground_owned").contains("actually visible"))throw new AssertionError();checks++;
  for(String state:new String[]{"waiting_for_app","creating","preparing","resuming"}){String next=UiScreenPolicy.recovery(state);if(!next.contains("transient running")||!next.contains("same assigned session"))throw new AssertionError(state);checks++;}
  System.out.println(checks+" screen policy assertions passed");
 }
}
