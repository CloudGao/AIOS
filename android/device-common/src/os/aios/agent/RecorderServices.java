package os.aios.agent;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.*;
import java.io.*;
final class RecorderServices {
 final Context c;final os.aios.device.DeviceResources resources;os.aios.device.DeviceResources.Lease foregroundToken;MediaRecorder recorder;File file;long start;final Handler handler=new Handler(Looper.getMainLooper());
 RecorderServices(Context c,os.aios.device.DeviceResources resources){this.c=c;this.resources=resources;}
 synchronized JSONObject execute(String op,JSONObject p)throws Exception{
  if(!java.util.Arrays.asList("microphone.start","microphone.stop","microphone.status").contains(op))throw new IllegalArgumentException("Unknown operation");
  if(op.equals("microphone.start")){
   if(recorder!=null)throw new IllegalStateException("Recording already active");int seconds=p.optInt("seconds",60);if(seconds<1||seconds>600)throw new IllegalArgumentException("Duration 1..600 seconds");
   file=new SharedFiles(c).target(p.getString("path"));if(file.exists())throw new IllegalArgumentException("Output exists");
   foregroundToken=resources.acquireForeground(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
   try{recorder=new MediaRecorder(c);final MediaRecorder owned=recorder;recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setOutputFile(file);recorder.setMaxDuration(seconds*1000);recorder.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED){synchronized(RecorderServices.this){if(recorder!=r)return;try{stop();}catch(Exception ignored){}}}});recorder.prepare();recorder.start();start=System.currentTimeMillis();handler.postDelayed(()->{synchronized(RecorderServices.this){if(recorder==owned&&System.currentTimeMillis()-start>=seconds*1000L)try{stop();}catch(Exception ignored){}}},seconds*1000L);}catch(Exception e){if(recorder!=null)recorder.release();recorder=null;foregroundToken.close();foregroundToken=null;throw e;}
  }else if(op.equals("microphone.stop")){if(recorder==null)throw new IllegalStateException("No active recording");stop();}
  JSONObject value=new JSONObject().put("recording",recorder!=null).put("path",file==null?JSONObject.NULL:file.getPath()).put("bytes",file==null?0:file.length()).put("started_ms",start);return new JSONObject().put("state","succeeded").put("data",value);
 }
 void stop()throws Exception{MediaRecorder r=recorder;if(r==null)return;recorder=null;try{r.stop();}finally{r.release();if(foregroundToken!=null)foregroundToken.close();foregroundToken=null;}MediaScannerConnection.scanFile(c,new String[]{file.getPath()},new String[]{"audio/mp4"},null);}
 synchronized void close(){if(recorder!=null)try{stop();}catch(Exception ignored){}}
}
