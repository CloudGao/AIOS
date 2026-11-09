package os.aios.agent;

/** Lease decisions, independent of Android window APIs. */
final class ForegroundUiPolicy {
 static final long START_TIMEOUT_MS=15000;
 static boolean holds(String state){return state.equals("active")||state.equals("waiting_for_app");}
 static boolean blocked(String pausedRequest,String request){return !pausedRequest.isEmpty()&&pausedRequest.equals(request);}
 static boolean busy(String state,String target,String next,String leaseRequest,String request){
  return holds(state)&&!target.equals(next)&&leaseRequest.equals(request);
 }
 static boolean expired(String state,long since,long now){return state.equals("waiting_for_app")&&now-since>=START_TIMEOUT_MS;}
}
