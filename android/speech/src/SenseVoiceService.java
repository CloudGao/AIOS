package os.aios.speech;

import android.content.*;
import android.app.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import android.speech.*;
import android.util.Log;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Whisper system recognizer. Legacy component name preserves saved Android service bindings. */
public final class SenseVoiceService extends RecognitionService {
 private static final String TAG="AIOS-Speech";
 private static final int RATE=16000;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ExecutorService capture=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private Session active;
 private WhisperRecognizer model;
 private Vad vad;
 private static final class Session {
  final Callback callback;
  String language="auto";
  Context attributed;
  volatile boolean cancelled,stop,captureFinished;
  volatile Exception captureError;
  final BlockingQueue<short[]> chunks=new ArrayBlockingQueue<>(1875); // At most 60 seconds while models load.
  volatile AudioRecord recorder;
  volatile ParcelFileDescriptor input;
  PowerManager.WakeLock wake;
  Session(Callback c){callback=c;}
 }
 @Override protected void onStartListening(Intent intent,Callback callback){
  if(active!=null){try{callback.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);}catch(RemoteException ignored){}return;}
  String language=intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
  if(language!=null&&!language.matches("(?i)(zh|cmn|yue|en|ja|ko)([-_].*)?")){try{callback.error(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED);}catch(RemoteException ignored){}return;}
  Session s=new Session(callback);s.language=whisperLanguage(language);active=s;Log.i(TAG,"Session started "+System.identityHashCode(s));
  s.attributed=createContext(new ContextParams.Builder().setNextAttributionSource(callback.getCallingAttributionSource()).build());
  s.input=intent.getParcelableExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,ParcelFileDescriptor.class);
  if(s.input!=null&&(intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,RATE)!=RATE
    ||intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1)!=1
    ||intent.getIntExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT)!=AudioFormat.ENCODING_PCM_16BIT)){
   complete(s,null,SpeechRecognizer.ERROR_AUDIO);return;
  }
  try {
   NotificationManager nm=getSystemService(NotificationManager.class);
   nm.createNotificationChannel(new NotificationChannel("speech-recognition",getString(os.aios.runtime.R.string.speech_processing_channel),NotificationManager.IMPORTANCE_LOW));
   startForeground(905,new Notification.Builder(this,"speech-recognition")
    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
    .setContentTitle(getString(os.aios.runtime.R.string.speech_processing_title))
    .setContentText(getString(os.aios.runtime.R.string.speech_processing_text))
    .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true).build(),
    s.input==null?ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE:ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
  } catch(RuntimeException failure){Log.e(TAG,"Foreground recognition unavailable",failure);complete(s,null,SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);return;}
  s.wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"aios:speech-recognition");
  s.wake.acquire(240000);
  capture.execute(()->capture(s));
  worker.execute(()->recognize(s));
 }
 private static String whisperLanguage(String requested){
  if(requested==null)return "auto";String language=requested.toLowerCase(java.util.Locale.ROOT).split("[-_]")[0];
  return language.equals("cmn")||language.equals("yue")?"zh":language;
 }
 private boolean verified;
 @Override public void onCheckRecognitionSupport(Intent intent,SupportCallback callback){
  String language=intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
  if(language!=null&&!language.matches("(?i)(zh|cmn|yue|en|ja|ko)([-_].*)?")){callback.onError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED);return;}
  if(active!=null){callback.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);return;}
  worker.execute(()->{
   try{
    prepare();
    if(!verified){model.decode(new float[RATE],"auto");verified=true;}
    callback.onSupportResult(new RecognitionSupport.Builder().setInstalledOnDeviceLanguages(List.of("zh-CN","zh-TW","yue-HK","en-US","ja-JP","ko-KR")).build());
   }catch(Exception|LinkageError failure){Log.e(TAG,"Readiness check failed: "+failure.getClass().getSimpleName());callback.onError(SpeechRecognizer.ERROR_SERVER);}
  });
 }
 private static String digest(File file)throws IOException{
  try{java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
 }
 private void installAsset(File dir,String name,String expected)throws IOException{
  File dest=new File(dir,name);
  if(dest.isFile()&&expected.equals(digest(dest)))return;
  android.util.AtomicFile atomic=new android.util.AtomicFile(dest);FileOutputStream out=atomic.startWrite();
  try{try(InputStream in=getAssets().open(name)){in.transferTo(out);}out.getFD().sync();atomic.finishWrite(out);}
  catch(IOException|RuntimeException error){atomic.failWrite(out);throw error;}
  if(!expected.equals(digest(dest)))throw new IOException("Bundled speech asset checksum mismatch");
 }
 private void prepare() throws IOException {
  if(model!=null&&vad!=null)return;
  File dir=new File(getFilesDir(),"models");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Model directory unavailable");
  installAsset(dir,"silero_vad.onnx","9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6");
  WhisperRecognizer created=new WhisperRecognizer(this);
  try {
  VadModelConfig vc=new VadModelConfig();vc.setNumThreads(1);vc.setSampleRate(RATE);
  vc.getSileroVadModelConfig().setModel(new File(dir,"silero_vad.onnx").getPath());
  vc.getSileroVadModelConfig().setMinSilenceDuration(0.9f);
  vc.getSileroVadModelConfig().setMinSpeechDuration(0.2f);
  Vad createdVad=new Vad(null,vc);
  model=created;vad=createdVad;
  } catch(RuntimeException|LinkageError failure){created.close();throw failure;}
 }
 private void capture(Session s){
  try {
   if(s.cancelled||s.stop)return;
   InputStream input=null;
   if(s.input!=null)input=new ParcelFileDescriptor.AutoCloseInputStream(s.input);
   else {
    AudioFormat format=new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    s.recorder=SpeechCapture.create(s.attributed,format,RATE);
    if(s.cancelled||s.stop)return;
    s.recorder.startRecording();
    if(s.recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("Microphone unavailable");
   }
   if(!s.cancelled)s.callback.readyForSpeech(new Bundle());
   short[] pcm=new short[512];byte[] bytes=new byte[1024];
   try {
    while(!s.cancelled&&!s.stop){
     int n;
     if(input!=null){int count=input.readNBytes(bytes,0,bytes.length);if(count==0)break;if((count&1)!=0)throw new IOException("Incomplete PCM sample");n=count/2;for(int i=0;i<n;i++)pcm[i]=(short)((bytes[2*i]&255)|(bytes[2*i+1]<<8));}
     else n=s.recorder.read(pcm,0,pcm.length);
     if(n<0){if(s.cancelled||s.stop)break;throw new IOException("AudioRecord "+n);}if(n==0)continue;
     if(!s.chunks.offer(Arrays.copyOf(pcm,n),500,TimeUnit.MILLISECONDS))throw new IOException("Speech preparation exceeded audio buffer");
    }
   } finally {if(input!=null)input.close();}
  } catch(Exception e){if(!s.cancelled&&!s.stop)s.captureError=e;}
  finally {closeRecorder(s);s.captureFinished=true;Log.i(TAG,"Capture finished "+System.identityHashCode(s));}
 }
 private void recognize(Session s){
  long began=SystemClock.elapsedRealtime();Log.i(TAG,"Decoder queued "+System.identityHashCode(s));
  try{
   prepare();model.resetCancellation();if(s.cancelled)return;Log.i(TAG,"Decoder ready "+System.identityHashCode(s));
   vad.reset();Log.i(TAG,"VAD reset "+System.identityHashCode(s));
   File audio=File.createTempFile("assistant-", ".pcm",getCacheDir());
   try {
    SpeechEndpoint endpoint=new SpeechEndpoint(RATE);
    try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(audio)))){
     while(!s.cancelled){
      short[] pcm=s.chunks.poll(100,TimeUnit.MILLISECONDS);
      if(pcm==null){if(s.captureFinished){if(s.captureError!=null)throw s.captureError;break;}continue;}
      int n=pcm.length;float[] chunk=new float[512];double power=0;
      for(int i=0;i<n;i++){chunk[i]=pcm[i]/32768f;power+=chunk[i]*chunk[i];out.writeFloat(chunk[i]);}
      boolean previouslyHeard=endpoint.heard;
      boolean ended=endpoint.accept(vad.compute(chunk)>=0.5f,n);
      if(!previouslyHeard&&endpoint.heard)s.callback.beginningOfSpeech();
      s.callback.rmsChanged((float)(20*Math.log10(Math.sqrt(power/n)+1e-6)));
      if(ended)break;
     }
     s.stop=true;

    }
    if(s.cancelled)return;
    if(!endpoint.heard){complete(s,null,SpeechRecognizer.ERROR_SPEECH_TIMEOUT);return;}
    s.callback.endOfSpeech();
    StringBuilder transcript=new StringBuilder();
    // Bounded inference windows do not end the user's recording.
    try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(audio)))){
     long remaining=audio.length()/4;
     while(remaining>0&&!s.cancelled){
      int count=(int)Math.min(remaining,RATE*20);float[] samples=new float[count];
      for(int i=0;i<count;i++)samples[i]=in.readFloat();remaining-=count;
      Log.i(TAG,"Decode window "+System.identityHashCode(s)+" samples="+count);String part=model.decode(samples,s.language);if(!part.isEmpty()){if(transcript.length()>0)transcript.append(" ");transcript.append(part);}
     }
    }
    if(s.cancelled)return;
    String text=transcript.toString();
    Log.i(TAG,"Recognition finished: audio_ms="+endpoint.total*1000L/RATE+" elapsed_ms="+(SystemClock.elapsedRealtime()-began));
   complete(s,text,text.isEmpty()?SpeechRecognizer.ERROR_NO_MATCH:0);
   }finally{audio.delete();}
  }catch(SecurityException e){Log.e(TAG,"Microphone permission",e);complete(s,null,SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS);
  }catch(Exception|LinkageError e){Log.e(TAG,"Recognition failed",e);complete(s,null,SpeechRecognizer.ERROR_SERVER);
  }finally{s.stop=true;closeInput(s);releaseWake(s);main.post(()->{if(s.cancelled&&active==s){active=null;stopForeground(STOP_FOREGROUND_REMOVE);}});}
 }
 private void complete(Session s,String text,int error){main.post(()->{
  closeInput(s);if(active!=s)return;active=null;stopForeground(STOP_FOREGROUND_REMOVE);if(s.cancelled)return;
  try{if(error!=0)s.callback.error(error);else{Bundle result=new Bundle();result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(List.of(text)));s.callback.results(result);}}catch(RemoteException ignored){}
 });}
 private static synchronized void releaseWake(Session s){if(s.wake!=null&&s.wake.isHeld())s.wake.release();}
 private static void closeRecorder(Session s){AudioRecord r=s.recorder;s.recorder=null;if(r!=null){try{r.stop();}catch(IllegalStateException ignored){}r.release();}}
 private static void closeInput(Session s){ParcelFileDescriptor fd=s.input;s.input=null;if(fd!=null)try{fd.close();}catch(IOException ignored){}}
 @Override protected void onStopListening(Callback callback){Session s=active;if(s!=null){s.stop=true;closeInput(s);AudioRecord r=s.recorder;if(r!=null)try{r.stop();}catch(IllegalStateException ignored){}}}
 @Override protected void onCancel(Callback callback){Session s=active;if(s!=null){s.cancelled=true;s.stop=true;releaseWake(s);if(model!=null)model.cancel();closeInput(s);AudioRecord r=s.recorder;if(r!=null)try{r.stop();}catch(IllegalStateException ignored){}}}
 @Override public void onDestroy(){if(active!=null)onCancel(active.callback);worker.execute(()->{if(vad!=null)vad.release();if(model!=null)model.close();});worker.shutdown();capture.shutdown();super.onDestroy();}
}
