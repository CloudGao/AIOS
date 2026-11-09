package os.aios.core.embedding;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.*;

/** Private worker process. No Workspace, scheduling, or index writes are permitted here. */
public final class EmbeddingService extends Service {
    private HandlerThread thread;private Handler worker;private final java.util.LinkedHashMap<String,LiteRtGpuBackend> models=new java.util.LinkedHashMap<>(4,.75f,true);private final java.util.concurrent.atomic.AtomicInteger queued=new java.util.concurrent.atomic.AtomicInteger();private final Runnable unload=()->{for(LiteRtGpuBackend model:models.values())model.close();models.clear();};
    public void onCreate(){super.onCreate();thread=new HandlerThread("gpu-inference");thread.start();worker=new Handler(thread.getLooper());}
    public IBinder onBind(Intent intent){return new Messenger(new Handler(android.os.Looper.getMainLooper()){
        public void handleMessage(Message message){
            if(message.sendingUid!=android.os.Process.myUid()||message.replyTo==null)return;
            Bundle data=new Bundle(message.getData());Messenger reply=message.replyTo;
            if(queued.incrementAndGet()>8){queued.decrementAndGet();ParcelFileDescriptor rejected=data.getParcelable("input",ParcelFileDescriptor.class);if(rejected!=null)try{rejected.close();}catch(IOException ignored){}Bundle error=new Bundle();error.putString("error","inference_busy");try{Message m=Message.obtain();m.setData(error);reply.send(m);}catch(RemoteException ignored){}return;}
            Runnable task=()->{
                ParcelFileDescriptor fd=data.getParcelable("input",ParcelFileDescriptor.class);Bundle response=new Bundle();
                try{
                    String role=data.getString("role","");boolean probe=data.getBoolean("probe",false);ModelRegistry registry=new ModelRegistry(EmbeddingService.this,data.getString("space_id"));
                    if(!probe&&!registry.validated(role))throw new IllegalStateException("backend_unverified");
                    File file=registry.model(role);String key=role+":"+file.getPath()+":"+file.lastModified();worker.removeCallbacks(unload);
                    long started=System.nanoTime();LiteRtGpuBackend backend=models.get(key);if(backend==null){while(models.size()>=2){String oldest=models.keySet().iterator().next();models.remove(oldest).close();}backend=new LiteRtGpuBackend(EmbeddingService.this,file.getPath());models.put(key,backend);}
                    int[] tokens=data.getIntArray("tokens");float[] values=null;
                    if(tokens!=null){if(!role.equals("text")||tokens.length!=77)throw new IllegalArgumentException("Expected 77 text tokens");}
                    else{
                        int count=data.getInt("count");if(count<1||count>3*512*512||fd==null||fd.getStatSize()!=count*4L)throw new IllegalArgumentException("Invalid bounded input FD");
                        values=new float[count];try(DataInputStream in=new DataInputStream(new BufferedInputStream(new ParcelFileDescriptor.AutoCloseInputStream(fd),65536))){fd=null;for(int i=0;i<count;i++)values[i]=Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));}
                    }
                    float[][] output=backend.run(values,tokens);int total=0;for(int i=0;i<output.length;i++){total+=output[i].length;if(total>65536)throw new IllegalStateException("Output budget exceeded");response.putFloatArray("output_"+i,output[i]);}
                    response.putInt("outputs",output.length);response.putLong("elapsed_us",(System.nanoTime()-started)/1000);response.putString("requested_backend","gpu_only");response.putString("actual_backend",registry.validated(role)?"gpu":"backend_unverified");
                }catch(Throwable e){response.putString("error",e.getClass().getSimpleName()+": "+e.getMessage());unload.run();}
                finally{queued.decrementAndGet();if(fd!=null)try{fd.close();}catch(IOException ignored){}worker.postDelayed(unload,30000);}
                try{Message m=Message.obtain();m.setData(response);reply.send(m);}catch(RemoteException ignored){}
            };if(data.getString("role","").equals("text"))worker.postAtFrontOfQueue(task);else worker.post(task);
        }
    }).getBinder();}
    public void onDestroy(){worker.post(()->{unload.run();thread.quitSafely();});super.onDestroy();}
}
