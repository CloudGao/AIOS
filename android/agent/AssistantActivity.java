package os.aios.agent;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.speech.*;
import android.view.*;
import java.util.*;
/** Transparent ASSIST entry and activity-bound native speech recognition. */
public final class AssistantActivity extends Activity implements RecognitionListener {
 private android.content.ComponentName speechComponent;private boolean speechReady,retried;
 private final Runnable readyTimeout=()->{if(!this.finished&&!speechReady)onError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED);};
 private String gesture="",pendingWords="";
 private SpeechRecognizer recognizer;private final Handler handler=new Handler(Looper.getMainLooper());private boolean finished,started,inputReceiverRegistered;
 static final String CANCEL_INPUT="os.aios.agent.CANCEL_VOICE_INPUT",STOP_INPUT="os.aios.agent.STOP_VOICE_INPUT";
 static void cancelInput(Context context){context.sendBroadcast(new Intent(CANCEL_INPUT).setPackage(context.getPackageName()));}
 static void stopInput(Context context){context.sendBroadcast(new Intent(STOP_INPUT).setPackage(context.getPackageName()));}
 private final BroadcastReceiver cancelInputReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(STOP_INPUT.equals(i.getAction())){if(!pendingWords.isEmpty())submitWords(pendingWords);else if(recognizer!=null&&!finished)recognizer.stopListening();}else if(!finished){finished=true;finish();}}};
 @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setBackgroundDrawableResource(android.R.color.transparent);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);getWindow().setLayout(1,1);
  if(getSystemService(KeyguardManager.class).isKeyguardLocked()){finish();return;}
  if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));finish();return;}
  gesture=getIntent().getStringExtra("voice_gesture");if(gesture==null)gesture="";if(!gesture.isEmpty()&&(!AssistantVoiceGesture.valid(gesture)||AssistantVoiceGesture.released(gesture))){finish();return;}
  String screen=getIntent().getStringExtra("ui_session_id");if(screen==null||screen.isEmpty())screen=UiViewerActivity.visibleSession();
  startForegroundService(new Intent(this,AgentService.class));startForegroundService(new Intent(this,AssistantOrbService.class).putExtra("ui_session_id",screen).setAction("listen".equals(getIntent().getStringExtra("mode"))?"continue":"new"));
  if(!"listen".equals(getIntent().getStringExtra("mode"))){finish();return;}
  IntentFilter inputFilter=new IntentFilter(CANCEL_INPUT);inputFilter.addAction(STOP_INPUT);registerReceiver(cancelInputReceiver,inputFilter,Context.RECEIVER_NOT_EXPORTED);inputReceiverRegistered=true;
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},71);else handler.postDelayed(this::listen,150);
 }
 private void listen(){if(isFinishing()||started)return;if(!gesture.isEmpty()&&(!AssistantVoiceGesture.valid(gesture)||AssistantVoiceGesture.released(gesture))){finished=true;finish();return;}started=true;try{
  speechComponent=SpeechModelSelection.selected(this,DeviceHost.ui().speechRecognizerComponent(this));
  if(speechComponent==null&&!SpeechRecognizer.isRecognitionAvailable(this)){fail(R.string.assistant_speech_recognition_is_unavailable_switch_to_the_keyboard_b16bc);return;}
  startRecognizer(speechComponent);
 }catch(Exception e){fail(R.string.assistant_speech_recognition_is_unavailable_please_try_later_928b5);}}
 private void startRecognizer(android.content.ComponentName component) throws Exception{
  speechComponent=component;speechReady=false;AssistantConversation.voiceState(this,"listening");
  SpeechRecognizer current=component==null?SpeechRecognizer.createSpeechRecognizer(this):SpeechRecognizer.createSpeechRecognizer(this,component);recognizer=current;
  // Ignore late callbacks from a destroyed provider after host fallback.
  current.setRecognitionListener(new RecognitionListener(){
   private boolean active(){return !finished&&recognizer==current;}
   public void onReadyForSpeech(Bundle b){if(active())AssistantActivity.this.onReadyForSpeech(b);}
   public void onBeginningOfSpeech(){if(active())AssistantActivity.this.onBeginningOfSpeech();}
   public void onRmsChanged(float r){if(active())AssistantActivity.this.onRmsChanged(r);}
   public void onBufferReceived(byte[] b){if(active())AssistantActivity.this.onBufferReceived(b);}
   public void onEndOfSpeech(){if(active())AssistantActivity.this.onEndOfSpeech();}
   public void onError(int e){if(active())AssistantActivity.this.onError(e);}
   public void onResults(Bundle b){if(active())AssistantActivity.this.onResults(b);}
   public void onPartialResults(Bundle b){if(active())AssistantActivity.this.onPartialResults(b);}
   public void onEvent(int t,Bundle b){if(active())AssistantActivity.this.onEvent(t,b);}
  });
  Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,AssistantLocale.systemLocale(this).toLanguageTag()).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
  android.util.Log.i("AIOSVoice","Provider="+(component==null?"system":component.flattenToShortString()));
  handler.removeCallbacks(readyTimeout);handler.postDelayed(readyTimeout,5000);current.startListening(i);
 }
 private void fail(int resource){if(finished)return;finished=true;AssistantConversation.noticeResource(this,resource);finish();}
 private void fail(String message){if(finished)return;finished=true;AssistantConversation.notice(this,message);finish();}
 @Override public void onResults(Bundle b){if(finished)return;try{ArrayList<String> words=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(words==null||words.isEmpty()||words.get(0).isBlank()){fail(R.string.assistant_could_not_hear_you_please_try_again_30d5b);return;}if(!gesture.isEmpty()&&!AssistantVoiceGesture.released(gesture)){pendingWords=words.get(0);return;}submitWords(words.get(0));}catch(Exception e){fail(e.getMessage());}}
 private void submitWords(String words){if(finished)return;try{if(!gesture.isEmpty()&&(!AssistantVoiceGesture.valid(gesture)||!AssistantVoiceGesture.released(gesture))){finished=true;finish();return;}synchronized(AssistantConversation.class){if(!gesture.isEmpty()&&!AssistantVoiceGesture.conversation(gesture).equals(AssistantConversation.state(this).optString("conversation_id")))throw new IllegalStateException(AssistantActivity.this.getString(R.string.assistant_conversation_changed_voice_message_was_not_sent_8e8dd));AssistantConversation.enqueue(this,words);}finished=true;finish();}catch(Exception e){fail(e.getMessage());}}
 @Override public void onError(int e){
  handler.removeCallbacks(readyTimeout);
  android.content.ComponentName fallback=DeviceHost.ui().speechRecognizerFallback(this,speechComponent,e);
  if(!finished&&!retried&&!speechReady&&fallback!=null&&(gesture.isEmpty()||(AssistantVoiceGesture.valid(gesture)&&!AssistantVoiceGesture.released(gesture)))){
   retried=true;SpeechRecognizer previous=recognizer;recognizer=null;if(previous!=null)previous.destroy();
   try{startRecognizer(fallback);return;}catch(Exception ignored){}
  }
  android.util.Log.w("AIOSVoice","Recognition error="+e+", started="+started+", gestureReleased="+(!gesture.isEmpty()&&AssistantVoiceGesture.released(gesture)));fail(switch(e){case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED->R.string.assistant_speech_language_unsupported;case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE->R.string.assistant_speech_language_unavailable;case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->R.string.assistant_please_allow_microphone_access_2f5ee;case SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->R.string.assistant_speech_recognition_network_is_unavailable_1760e;case SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->R.string.assistant_could_not_hear_you_please_try_again_30d5b;case SpeechRecognizer.ERROR_RECOGNIZER_BUSY->R.string.assistant_speech_recognition_is_busy_please_try_later_b4f80;default->R.string.assistant_speech_recognition_did_not_finish_please_try_again_78a52;});}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==71&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)listen();else fail(R.string.assistant_allow_microphone_access_before_speaking_2cd94);}
 @Override protected void onDestroy(){try{AssistantConversation.voiceState(this,"");}catch(Exception ignored){}if(inputReceiverRegistered)unregisterReceiver(cancelInputReceiver);handler.removeCallbacksAndMessages(null);if(recognizer!=null){recognizer.cancel();recognizer.destroy();}super.onDestroy();}
 @Override protected void onStop(){super.onStop();if(started&&!finished)fail(R.string.assistant_voice_input_ended_8e8f9);}
 public void onReadyForSpeech(Bundle b){speechReady=true;handler.removeCallbacks(readyTimeout);android.util.Log.i("AIOSVoice","Recognizer ready");} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){if(!gesture.isEmpty())sendBroadcast(new Intent("os.aios.agent.VOICE_LEVEL").setPackage(getPackageName()).putExtra("gesture",gesture).putExtra("rms",r));} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){if(!finished)try{AssistantConversation.voiceState(this,"recognizing");}catch(Exception ignored){}} public void onPartialResults(Bundle b){} public void onEvent(int t,Bundle b){}
}
