package os.aios.apk.shell;

/** Shell-only, read-only evdev fallback. No coordinates or input contents leave native code. */
final class PhysicalTouchMonitor {
    private volatile boolean running=true;
    private volatile long lastPoll=System.nanoTime();
    private final long handle;
    private PhysicalTouchMonitor(long handle){this.handle=handle;}
    static PhysicalTouchMonitor start(String directory,Runnable down,Runnable failed) {
        try {
            System.load(directory+"/libaios_touch.so");
            long handle=open();if(handle==0)return null;
            PhysicalTouchMonitor monitor=new PhysicalTouchMonitor(handle);
            Thread worker=new Thread(()->{
                try {
                    while(monitor.running){int count=poll(handle);monitor.lastPoll=System.nanoTime();if(count<0)throw new IllegalStateException("touch_monitor_lost");if(count>0&&monitor.running)down.run();}
                }catch(Throwable error){if(monitor.running){monitor.running=false;failed.run();}}
                finally{close(handle);}
            },"aios-physical-touch");
            worker.setDaemon(true);worker.start();return monitor;
        }catch(LinkageError|RuntimeException unavailable){return null;}
    }
    boolean alive(){return running&&System.nanoTime()-lastPoll<2_000_000_000L;}
    void stop(){running=false;}
    private static native long open();
    private static native int poll(long handle);
    private static native void close(long handle);
}
