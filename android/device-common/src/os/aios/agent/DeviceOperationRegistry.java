package os.aios.agent;

import java.util.*;
import java.util.function.Predicate;
import org.json.JSONObject;

/** Lazy, build-selected device handlers. Owns resources, not business or execution ledgers. */
final class DeviceOperationRegistry implements AutoCloseable {
    interface Operation { JSONObject execute(String capability,JSONObject arguments)throws Exception; }
    interface Factory { Bound create(); }
    static final class Bound {
        final Operation operation;final Runnable release;
        Bound(Operation operation,Runnable release){this.operation=operation;this.release=release;}
    }
    private static final class Entry {
        final Predicate<String> accepts;final Factory factory;Bound instance;
        Entry(Predicate<String> accepts,Factory factory){this.accepts=accepts;this.factory=factory;}
    }
    private final List<Entry> entries=new ArrayList<>();
    private boolean closed;
    synchronized void register(Predicate<String> accepts,Factory factory){
        if(closed)throw new IllegalStateException("backend_closed");
        entries.add(new Entry(accepts,factory));
    }
    JSONObject execute(String capability,JSONObject arguments)throws Exception {
        Bound selected=null;
        synchronized(this){
            if(closed)throw new IllegalStateException("backend_closed");
            for(Entry entry:entries)if(entry.accepts.test(capability)){
                if(entry.instance==null)entry.instance=entry.factory.create();
                selected=entry.instance;break;
            }
        }
        return selected==null?null:selected.operation.execute(capability,arguments);
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        for(Entry entry:entries)if(entry.instance!=null)try{entry.instance.release.run();}
        catch(Exception error){android.util.Log.w("AIOSDevice","Device handler release failed",error);}
    }
}
