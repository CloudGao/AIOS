package os.aios.firsttest;
import android.app.*;import android.os.*;import android.content.*;import android.speech.*;import java.util.*;import java.util.concurrent.*;import java.io.*;
public final class SpeechProbe extends Instrumentation {
 private SpeechRecognizer recognizer; private String languages="zh,en"; private boolean direct;
 public void onCreate(Bundle b){super.onCreate(b);languages=b.getString("languages","zh,en");direct=b.getString("direct","false").equals("true");start();}
 private Intent request(String language){return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,language);}
 private void create(){runOnMainSync(()->recognizer=SpeechRecognizer.createSpeechRecognizer(getTargetContext(),new ComponentName("os.aios.app","os.aios.speech.SenseVoiceService")));}
 private void destroy(){runOnMainSync(()->{if(recognizer!=null){recognizer.destroy();recognizer=null;}});}
 private int support(String language)throws Exception{
  CountDownLatch done=new CountDownLatch(1);int[] code={-1};
  runOnMainSync(()->recognizer.checkRecognitionSupport(request(language),getTargetContext().getMainExecutor(),new RecognitionSupportCallback(){public void onSupportResult(RecognitionSupport s){code[0]=0;done.countDown();}public void onError(int e){code[0]=e;done.countDown();}}));
  if(!done.await(60,TimeUnit.SECONDS))throw new IOException("support_timeout");return code[0];
 }
 private String recognize(String asset,String language)throws Exception{
  byte[] audio;try(InputStream in=getContext().getAssets().open(asset)){audio=in.readAllBytes();}
  ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();CountDownLatch done=new CountDownLatch(1);String[] text={null};int[] error={0};
  runOnMainSync(()->{
   recognizer.setRecognitionListener(new RecognitionListener(){
    public void onReadyForSpeech(Bundle b){}public void onBeginningOfSpeech(){}public void onRmsChanged(float r){}public void onBufferReceived(byte[] b){}public void onEndOfSpeech(){}public void onPartialResults(Bundle b){}public void onEvent(int e,Bundle b){}
    public void onError(int e){error[0]=e;done.countDown();}
    public void onResults(Bundle b){ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);text[0]=words==null||words.isEmpty()?"":words.get(0);done.countDown();}
   });
   recognizer.startListening(request(language).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pipe[0]).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,16000).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,android.media.AudioFormat.ENCODING_PCM_16BIT));
  });
  Thread writer=new Thread(()->{try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){out.write(audio);}catch(IOException ignored){}});writer.start();
  try{if(!done.await(60,TimeUnit.SECONDS))throw new IOException("recognition_timeout");if(error[0]!=0)throw new IOException("recognition_error_"+error[0]);return text[0];}finally{pipe[0].close();pipe[1].close();writer.join(3000);}
 }
 public void onStart(){Bundle result=new Bundle();int code=0;PowerManager.WakeLock wake=getTargetContext().getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"aios:speech-acceptance");wake.acquire(180000);
  try{
   if(direct){Class<?> cls=Class.forName("os.aios.speech.WhisperRecognizer");Object decoder=cls.getConstructor(Context.class).newInstance(getTargetContext());try{for(String lang:languages.split(",")){byte[] pcm;try(InputStream in=getContext().getAssets().open(lang+".pcm")){pcm=in.readAllBytes();}float[] audio=new float[pcm.length/2];for(int i=0;i<audio.length;i++)audio[i]=(short)((pcm[i*2]&255)|(pcm[i*2+1]<<8))/32768f;String text=(String)cls.getMethod("decode",float[].class,String.class).invoke(decoder,audio,lang);result.putString(lang+"_text",text);}}finally{((AutoCloseable)decoder).close();}return;}
   Class<?> policy=Class.forName("os.aios.agent.ApkSpeech");java.lang.reflect.Method select=policy.getDeclaredMethod("select",Context.class);select.setAccessible(true);
   ComponentName chosen=(ComponentName)select.invoke(null,getTargetContext());if(!chosen.getClassName().equals("os.aios.speech.SenseVoiceService"))throw new IOException("unexpected_default_recognizer");result.putBoolean("bundled_default",true);
   create();int readiness=support("zh-CN");result.putInt("support_zh",readiness);if(readiness!=0)throw new IOException("readiness_failed");
   File model=new File(getTargetContext().getFilesDir(),"whisper-models/ggml-small-q5_1.bin");if(model.length()!=190085487L)throw new IOException("whisper_model_missing");result.putBoolean("whisper_installed",true);
   int unsupported=support("fr-FR");result.putInt("support_fr",unsupported);if(unsupported!=SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)throw new IOException("unsupported_language_not_reported");
   for(String lang:languages.split(",")){Thread.sleep(300);String text=recognize(lang+".pcm",lang.equals("zh")?"zh-CN":"en-US");result.putString(lang+"_text",text);if(text==null||!(lang.equals("zh")?text.contains("九点")&&text.contains("五点"):text.toLowerCase(Locale.ROOT).contains("tribal chief")&&text.toLowerCase(Locale.ROOT).contains("gold")))throw new IOException("transcript_mismatch_"+lang);}
  }catch(Throwable error){code=1;result.putString("error",error.getClass().getSimpleName()+":"+error.getMessage());}finally{destroy();if(wake.isHeld())wake.release();finish(code,result);}
 }
}
