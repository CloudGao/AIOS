package os.aios.core.indexing;
import android.content.Context;
import java.io.File;
import org.json.JSONArray;

/** Dedicated app-owned SQLite connection with statically registered sqlite-vec. */
public final class VectorIndex implements AutoCloseable {
    static {System.loadLibrary("aios_search");}
    private long handle;
    public VectorIndex(Context c,String space){this(c,space,0);}
    public VectorIndex(Context c,String space,long generation){if(generation<0)throw new IllegalArgumentException("Invalid generation");if(!space.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Space must be SHA256");File dir=new File(c.getFilesDir(),"photo-vectors");if(!dir.mkdirs()&&!dir.isDirectory())throw new IllegalStateException("Vector directory unavailable");handle=open(new File(dir,space+(generation==0?"":"-"+generation)+".db").getPath());}
    public synchronized void put(long id,float[] vector){check();if(id<=0||vector.length!=512)throw new IllegalArgumentException("Invalid photo vector");double norm=0;for(float f:vector){if(!Float.isFinite(f))throw new IllegalArgumentException("Non-finite vector");norm+=f*f;}if(Math.abs(norm-1)>0.02)throw new IllegalArgumentException("Vector is not L2 normalized");put(handle,id,vector);}
    public synchronized void remove(long id){check();remove(handle,id);}
    public synchronized JSONArray nearest(float[] vector,int count)throws Exception{check();if(vector.length!=512||count<1||count>4096)throw new IllegalArgumentException("Invalid KNN budget");for(float f:vector)if(!Float.isFinite(f))throw new IllegalArgumentException("Non-finite vector");return new JSONArray(nearest(handle,vector,count));}
    public synchronized long size(){check();return size(handle);}
    private static native long size(long handle);
    private void check(){if(handle==0)throw new IllegalStateException("Index closed");}
    public synchronized void close(){if(handle!=0){close(handle);handle=0;}}
    private static native long open(String path);private static native void close(long handle);private static native void put(long handle,long id,float[] vector);private static native void remove(long handle,long id);private static native String nearest(long handle,float[] vector,int count);
}
