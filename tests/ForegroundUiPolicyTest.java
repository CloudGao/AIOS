package os.aios.agent;
public final class ForegroundUiPolicyTest {
 static int count;
 static void check(boolean condition){count++;if(!condition)throw new AssertionError("case "+count);}
 public static void main(String[] args){
  for(String state:new String[]{"closed","paused","failed","permission_required","not_supported"})
   check(!ForegroundUiPolicy.busy(state,"keyboard","map","old","old"));
  check(ForegroundUiPolicy.busy("active","keyboard","map","old","old"));
  check(ForegroundUiPolicy.busy("waiting_for_app","keyboard","map","old","old"));
  check(!ForegroundUiPolicy.busy("active","keyboard","map","old","new"));
  check(!ForegroundUiPolicy.busy("active","map","map","old","old"));
  check(ForegroundUiPolicy.blocked("old","old"));
  check(!ForegroundUiPolicy.blocked("old","new"));
  check(!ForegroundUiPolicy.blocked("","new"));
  check(!ForegroundUiPolicy.expired("waiting_for_app",100,15099));
  check(ForegroundUiPolicy.expired("waiting_for_app",100,15100));
  check(!ForegroundUiPolicy.expired("active",100,999999));
  System.out.println("Foreground lease regression passed: "+count);
 }
}
