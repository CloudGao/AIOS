package os.aios.apk.shell;
public final class TouchMonitorProbe {
 public static void main(String[] args)throws Exception {
  java.util.concurrent.atomic.AtomicInteger downs=new java.util.concurrent.atomic.AtomicInteger();
  PhysicalTouchMonitor m=PhysicalTouchMonitor.start(args[0],()->{downs.incrementAndGet();System.out.println("PHYSICAL_DOWN");},()->{System.out.println("MONITOR_FAILED");System.exit(2);});
  if(m==null){System.out.println("MONITOR_UNAVAILABLE");System.exit(1);}
  System.out.println("MONITOR_READY");
  Thread.sleep(Long.parseLong(args[1]));m.stop();Thread.sleep(350);
  System.out.println("PHYSICAL_DOWN_COUNT="+downs.get());
 }
}
