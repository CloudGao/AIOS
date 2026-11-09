package os.aios.core.embedding;
import android.content.*;import android.os.*;import java.io.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicInteger;
/** Bounded FD requests; a batch may retain its binding, but never schedules work itself. */
public final class EmbeddingClient implements AutoCloseable {
 private final Context context;private final boolean persistent;private ServiceConnection connection;private CompletableFuture<Messenger> endpoint;
 private static final AtomicInteger interactive=new AtomicInteger();
 public static boolean interactivePending(){return interactive.get()>0;}
 public EmbeddingClient(Context c){this(c,false);}public EmbeddingClient(Context c,boolean keep){context=c.getApplicationContext();persistent=keep;}
 private Messenger connect()throws Exception{if(connection==null){endpoint=new CompletableFuture<>();connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){endpoint.complete(new Messenger(b));}public void onServiceDisconnected(ComponentName n){endpoint.completeExceptionally(new IllegalStateException("inference_process_died"));}};if(!context.bindService(new Intent().setClass(context,EmbeddingService.class),connection,Context.BIND_AUTO_CREATE)){connection=null;throw new IllegalStateException("inference_unavailable");}}return endpoint.get(10,TimeUnit.SECONDS);}
 public synchronized Bundle run(String role,float[] input,int[] tokens,boolean probe)throws Exception{
  return run(role,input,tokens,probe,null);
 }
 public synchronized Bundle run(String role,float[] input,int[] tokens,boolean probe,String space)throws Exception{
  if(role.equals("text"))interactive.incrementAndGet();ParcelFileDescriptor fd=null;HandlerThread replies=null;
  try{Bundle data=new Bundle();data.putString("role",role);data.putString("space_id",space);data.putBoolean("probe",probe);
   if(tokens!=null)data.putIntArray("tokens",tokens);else{File f=File.createTempFile("inference-",".bin",context.getCacheDir());try{try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f),65536))){for(float v:input)out.writeInt(Integer.reverseBytes(Float.floatToIntBits(v)));}fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}finally{f.delete();}data.putParcelable("input",fd);data.putInt("count",input.length);}
   replies=new HandlerThread("inference-reply");replies.start();CompletableFuture<Bundle> future=new CompletableFuture<>();Messenger receiver=new Messenger(new Handler(replies.getLooper()){public void handleMessage(Message message){Bundle b=message.getData();if(b.containsKey("error"))future.completeExceptionally(new IllegalStateException(b.getString("error")));else future.complete(b);}});
   Messenger target=connect();Message request=Message.obtain(null,1);request.setData(data);request.replyTo=receiver;target.send(request);return future.get(90,TimeUnit.SECONDS);
  }finally{if(!persistent)close();if(fd!=null)fd.close();if(replies!=null)replies.quitSafely();if(role.equals("text"))interactive.decrementAndGet();}
 }
 public synchronized void close(){if(connection!=null){context.unbindService(connection);connection=null;endpoint=null;}}
}
