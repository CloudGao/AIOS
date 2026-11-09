package os.aios.speech;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.nio.*;
import java.util.concurrent.*;

/** Transcribes the native recorder's finished file; never opens a second microphone. */
public final class RecordingImportService extends android.app.Service {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private MinutesStore store;private volatile boolean cancelled;private int pending;
 public IBinder onBind(Intent intent){return null;}
 public void onCreate(){super.onCreate();store=new MinutesStore(this);store.getWritableDatabase().execSQL("UPDATE sessions SET status='interrupted',error='转写进程中断；原录音保留在录音机' WHERE status='importing'");}
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null||intent.getData()==null){stopSelf(startId);return START_NOT_STICKY;}
  Uri uri=intent.getData();if(!"content".equals(uri.getScheme())){stopSelf(startId);return START_NOT_STICKY;}
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("recording-import","录音转写",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,20,new Intent(this,MinutesActivity.class).putExtra("kind","recording").putExtra("view_only",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  startForeground(903,new Notification.Builder(this,"recording-import").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(os.aios.core.DisplayText.clean("正在转写已保存的录音")).setContentText(os.aios.core.DisplayText.clean("原始录音已保存在录音机中")).setContentIntent(open).build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
  final String id=store.create("recording",uri.toString());store.state(id,"importing","");pending++;
  worker.execute(()->{String error="";try{decode(uri,id);}catch(Exception|LinkageError e){error="转写未完成，原始录音仍保留："+e.getMessage();}
   try{store.finish(id,error);}catch(Exception e){store.state(id,"interrupted","转写结果保存失败");}
   main.post(()->{
    PendingIntent result=PendingIntent.getActivity(this,id.hashCode(),new Intent(this,MinutesActivity.class).putExtra("kind","recording").putExtra("session_id",id).putExtra("view_only",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    getSystemService(NotificationManager.class).notify(id.hashCode(),new Notification.Builder(this,"recording-import").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(os.aios.core.DisplayText.clean("录音转写结果")).setContentText(os.aios.core.DisplayText.clean("点此查看文字、纪要与处理状态")).setContentIntent(result).setAutoCancel(true).build());
    if(--pending==0){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});
   try{startForegroundService(new Intent().setClassName("os.aios.agent","os.aios.agent.AgentService"));}catch(Exception ignored){}
  });return START_NOT_STICKY;
 }
 private void decode(Uri uri,String id)throws Exception{
  MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;MinutesEngine engine=null;
  PowerManager.WakeLock wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AIOS:RecordingImport");wake.acquire(4*60*60*1000L);
  try{
   extractor.setDataSource(this,uri,null);int track=-1;MediaFormat format=null;
   for(int i=0;i<extractor.getTrackCount();i++){MediaFormat candidate=extractor.getTrackFormat(i);if(candidate.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;format=candidate;break;}}
   if(track<0)throw new IllegalArgumentException("录音没有音轨");extractor.selectTrack(track);
   codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();
   engine=new MinutesEngine(this,new MinutesEngine.Output(){public void segment(long a,long b,String speaker,String text){store.segment(id,a,b,speaker,text);}public void partial(String text){}},null);
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnded=false,ended=false;int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding=AudioFormat.ENCODING_PCM_16BIT;
   long inputFrames=0,outputFrames=0,lastActivity=SystemClock.elapsedRealtime();float previous=0;
   while(!ended&&!cancelled){
    if(!inputEnded){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer input=codec.getInputBuffer(index);int count=extractor.readSampleData(input,0);codec.queueInputBuffer(index,0,Math.max(0,count),Math.max(0,extractor.getSampleTime()),count<0?MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);inputEnded=count<0;if(!inputEnded)extractor.advance();lastActivity=SystemClock.elapsedRealtime();}}
    int index=codec.dequeueOutputBuffer(info,10000);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat output=codec.getOutputFormat();rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);encoding=output.containsKey(MediaFormat.KEY_PCM_ENCODING)?output.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;}
    else if(index>=0){
     if(rate<=0||channels<1||channels>8)throw new IllegalArgumentException("不支持的录音格式");
     if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IllegalArgumentException("不支持的音频编码");
     ByteBuffer pcm=codec.getOutputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);pcm.position(info.offset);pcm.limit(info.offset+info.size);
     int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;float[] batch=new float[2048];int used=0;
     while(pcm.remaining()>=bytes*channels){float sample=0;for(int ch=0;ch<channels;ch++)sample+=encoding==AudioFormat.ENCODING_PCM_FLOAT?pcm.getFloat():pcm.getShort()/32768f;sample/=channels;
      while(outputFrames*(double)rate/16000<=inputFrames){double at=outputFrames*(double)rate/16000;double weight=Math.max(0,Math.min(1,at-(inputFrames-1)));batch[used++]=(float)(previous+(sample-previous)*weight);outputFrames++;if(used==batch.length){engine.accept(batch);batch=new float[2048];used=0;}}
      previous=sample;inputFrames++;if(inputFrames>(long)rate*60*60*4)throw new IllegalArgumentException("单次转写最多四小时，原录音保留");
     }
     if(used>0)engine.accept(java.util.Arrays.copyOf(batch,used));ended=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);lastActivity=SystemClock.elapsedRealtime();
    }
    if(SystemClock.elapsedRealtime()-lastActivity>30000)throw new IllegalStateException("音频解码超时");
   }
   if(cancelled)throw new InterruptedException("处理被停止");engine.finish();
  }finally{if(engine!=null)engine.close();if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();if(wake.isHeld())wake.release();}
 }
 public void onDestroy(){cancelled=true;worker.execute(()->store.close());worker.shutdown();super.onDestroy();}
}
