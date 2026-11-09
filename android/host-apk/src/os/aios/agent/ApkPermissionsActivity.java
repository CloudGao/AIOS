package os.aios.agent;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import os.aios.core.AiosUi;
import java.util.*;

/** Grants stay with Android; visiting this page is never proof of readiness. */
public final class ApkPermissionsActivity extends Activity {
    static final int VERSION=3;
    private LinearLayout page;
    private ScrollView scroll;
    private boolean automatic,requesting,showOptional;
    private int missing;
    private android.speech.SpeechRecognizer speech;
    private int speechState,microphoneState;
    private volatile int microphoneGeneration;
    private String checkedLanguage="";
    private int speechGeneration;
    private boolean accountReady,accountChecking;
    private CodexAccountClient accounts;
    private final java.util.concurrent.ExecutorService checks=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable speechTimeout=()->finishSpeechCheck(speechGeneration,3);

    private final ArrayDeque<String[]> queue=new ArrayDeque<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,3000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null){requesting=state.getBoolean("requesting");ArrayList<String> pending=state.getStringArrayList("pending");if(pending!=null)for(String group:pending)queue.add(group.split("\n"));}
        automatic=state==null&&getIntent().getBooleanExtra("automatic",false);
        LinearLayout root=AiosUi.column(this);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        LinearLayout bar=AiosUi.appBar(this,getString(R.string.apk_permissions_title),this::leave);
        bar.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1,AiosUi.dp(this,56)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        scroll=new ScrollView(this);page=AiosUi.column(this);page.setPadding(16,0,16,24);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);AiosUi.page(this,root);
        // This is only the once-per-version invitation marker, not a completion flag.
        getSharedPreferences("apk-permissions",0).edit().putInt("version",VERSION).apply();
    }
    @Override protected void onResume(){super.onResume();checkAccount();String language=AssistantLocale.systemLocale(this).toLanguageTag();if(!language.equals(checkedLanguage)){checkedLanguage=language;speechState=0;checkSpeech();}render();handler.postDelayed(refresh,1500);if(automatic){automatic=false;page.post(this::requestRuntime);}else if(!requesting&&!queue.isEmpty())handler.post(this::nextRequest);}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("requesting",requesting);ArrayList<String> pending=new ArrayList<>();for(String[] names:queue)pending.add(String.join("\n",names));state.putStringArrayList("pending",pending);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);microphoneGeneration++;speechGeneration++;if(speech!=null)speech.destroy();if(accounts!=null)accounts.close();checks.shutdownNow();super.onDestroy();}
    @Override protected void onPause(){handler.removeCallbacks(refresh);microphoneGeneration++;if(microphoneState==1)microphoneState=0;super.onPause();}
    @Override public void onBackPressed(){leave();}
    private List<PermissionInfo> runtime() {
        List<PermissionInfo> out=new ArrayList<>();
        try {String[] names=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_PERMISSIONS).requestedPermissions;
            if(names!=null)for(String name:names)try{PermissionInfo p=getPackageManager().getPermissionInfo(name,0);if((p.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)==PermissionInfo.PROTECTION_DANGEROUS)out.add(p);}catch(PackageManager.NameNotFoundException ignored){}
        }catch(PackageManager.NameNotFoundException ignored){}
        return out;
    }
    private boolean granted(String name){return checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED;}
    private boolean background(String name){return name.equals("android.permission.ACCESS_BACKGROUND_LOCATION")||name.equals("android.permission.BODY_SENSORS_BACKGROUND")||name.equals("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND");}
    private boolean restricted(PermissionInfo p){return (p.flags&PermissionInfo.FLAG_HARD_RESTRICTED)!=0;}
    private void requestRuntime(){
        if(requesting)return;
        Map<String,ArrayList<String>> groups=new LinkedHashMap<>();
        for(PermissionInfo p:runtime())if(!granted(p.name)&&!background(p.name)&&!restricted(p))groups.computeIfAbsent(p.group==null?p.name:p.group,k->new ArrayList<>()).add(p.name);
        queue.clear();for(ArrayList<String> names:groups.values())queue.add(names.toArray(new String[0]));nextRequest();
    }
    private void nextRequest(){
        if(requesting||isFinishing()||isDestroyed())return;
        if(queue.isEmpty()){requesting=false;render();return;}
        requesting=true;String[] names=queue.removeFirst();
        try{requestPermissions(names,1);}catch(SecurityException e){queue.clear();requesting=false;manual(R.string.apk_permission_runtime_help,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()));}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==1){requesting=false;render();handler.post(this::nextRequest);}else if(code==2){if(granted(android.Manifest.permission.RECORD_AUDIO))handler.postDelayed(this::checkMicrophone,300);else{microphoneState=3;render();if(!shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO))manual(R.string.apk_permission_runtime_help,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri()));}}}
    private Uri packageUri(){return Uri.parse("package:"+getPackageName());}
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){AiosUi.dialog(this).setMessage(R.string.apk_settings_unavailable).setPositiveButton(android.R.string.ok,null).show();}}
    private void manual(int help,Intent intent){AiosUi.dialog(this).setTitle(R.string.apk_permission_manual_title).setMessage(help).setPositiveButton(R.string.apk_permission_open,(d,w)->open(intent)).setNegativeButton(android.R.string.cancel,null).show();}
    private void row(CharSequence title,boolean ready,int help,Runnable action){row(title,ready,help,false,action);}
    private void row(CharSequence title,boolean ready,int help,boolean required,Runnable action){
        if(required&&!ready)missing++;
        LinearLayout item=AiosUi.settingRow(this,"shield",title.toString(),getString(ready?R.string.apk_permission_granted:R.string.apk_permission_needed)+"\n"+getString(help),null,true,true,action);
        TextView description=(TextView)((LinearLayout)item.getChildAt(1)).getChildAt(1);
        description.setSingleLine(false);description.setMaxLines(Integer.MAX_VALUE);description.setEllipsize(null);
        page.addView(item);
    }
    private void special(int title,boolean ready,int help,Intent intent){row(getString(title),ready,help,()->manual(help,intent));}
    private void required(int title,boolean ready,int help,Intent intent){row(getString(title),ready,help,true,()->manual(help,intent));}
    private void checkAccount(){
        if(accountChecking)return;accountChecking=true;if(accounts==null)accounts=new CodexAccountClient(this);
        checks.execute(()->{boolean ready=false;try{org.json.JSONObject status=accounts.call(new org.json.JSONObject().put("op","accounts/status"));ready=java.util.Set.of("configured","authenticated").contains(status.optString("state"));}catch(Exception ignored){}
            final boolean value=ready;handler.post(()->{if(isDestroyed())return;accountChecking=false;accountReady=value;render();});});
    }
    private void checkMicrophone(){
        if(microphoneState==1)return;
        if(!granted(android.Manifest.permission.RECORD_AUDIO)){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},2);return;}
        if(!hasWindowFocus())return;
        microphoneState=1;int generation=++microphoneGeneration;render();
        new Thread(()->{
            boolean ready=false;android.media.AudioRecord recorder=null;
            try{
                if(generation!=microphoneGeneration)return;
                int size=Math.max(4096,android.media.AudioRecord.getMinBufferSize(16000,android.media.AudioFormat.CHANNEL_IN_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT)*2);
                recorder=new android.media.AudioRecord.Builder().setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(new android.media.AudioFormat.Builder().setSampleRate(16000).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setChannelMask(android.media.AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(size).build();
                recorder.startRecording();short[] discarded=new short[512];int samples=0;
                long end=SystemClock.elapsedRealtime()+700;
                while(generation==microphoneGeneration&&SystemClock.elapsedRealtime()<end){int n=recorder.read(discarded,0,discarded.length,android.media.AudioRecord.READ_NON_BLOCKING);if(n<0)throw new IllegalStateException("microphone_read_failed");samples+=n;Thread.sleep(10);}
                android.media.AudioRecordingConfiguration configuration=recorder.getActiveRecordingConfiguration();
                ready=generation==microphoneGeneration&&samples>0&&configuration!=null&&!configuration.isClientSilenced();
                java.util.Arrays.fill(discarded,(short)0);
            }catch(Exception failure){android.util.Log.w("AIOSSetup","microphone_check_failed: "+failure.getClass().getSimpleName());}
            finally{if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();}}
            final boolean result=ready;handler.post(()->{if(isDestroyed()||generation!=microphoneGeneration)return;microphoneState=result?2:3;render();});
        },"aios-microphone-check").start();
    }
    private void checkSpeech(){
        if(speechState==1)return;
        if(!ApkSpeech.supports(AssistantLocale.systemLocale(this))){speechState=4;render();return;}
        speechState=1;final int generation=++speechGeneration;render();
        try{
            speech=android.speech.SpeechRecognizer.createSpeechRecognizer(this,ApkSpeech.local(this));
            handler.postDelayed(speechTimeout,45000);
            android.content.Intent request=new android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,AssistantLocale.systemLocale(this).toLanguageTag());
            speech.checkRecognitionSupport(request,getMainExecutor(),new android.speech.RecognitionSupportCallback(){
                public void onSupportResult(android.speech.RecognitionSupport support){finishSpeechCheck(generation,2);}
                public void onError(int error){finishSpeechCheck(generation,error==android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED?4:3);}
            });
        }catch(Exception failure){finishSpeechCheck(generation,3);}
    }
    private void finishSpeechCheck(int generation,int state){
        if(isDestroyed()||generation!=speechGeneration||speechState!=1)return;
        handler.removeCallbacks(speechTimeout);speechState=state;
        if(speech!=null){speech.destroy();speech=null;}render();
    }
    private boolean accessibilityEnabled(){String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(enabled==null)return false;ComponentName expected=new ComponentName(this,ForegroundUiService.class);for(String item:enabled.split(":"))if(expected.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    private void completeSetup(){
        if(getIntent().getBooleanExtra("finish_setup",false)){getSharedPreferences("apk-onboarding",0).edit().putBoolean("handled",true).apply();startActivity(new Intent(this,ApkHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
        finish();
    }
    private void leave(){render();if(missing==0){completeSetup();return;}AiosUi.dialog(this).setTitle(R.string.apk_permission_incomplete).setMessage(getString(R.string.apk_permission_missing,missing)).setPositiveButton(R.string.apk_permission_continue,(d,w)->completeSetup()).setNegativeButton(R.string.apk_permission_review,null).show();}
    private void render(){
        if(page==null)return;int y=scroll.getScrollY();missing=0;page.removeAllViews();
        page.addView(AiosUi.secondary(this,getString(R.string.apk_permissions_intro)));
        TextView summary=AiosUi.secondary(this,"");page.addView(summary);
        page.addView(AiosUi.filledButton(this,getString(R.string.apk_permissions_request),this::requestRuntime));
        page.addView(AiosUi.label(this,getString(R.string.apk_setup_basic)));
        row(getString(R.string.apk_accounts),accountReady,R.string.apk_setup_account_help,true,()->open(new Intent(this,CodexAccountsActivity.class)));
        int speechHelp=switch(speechState){case 1->R.string.apk_setup_speech_checking;case 2->R.string.apk_setup_speech_ready;case 4->R.string.assistant_speech_language_unsupported;default->R.string.apk_setup_speech_retry;};
        row(getString(R.string.apk_setup_speech),speechState==2,speechHelp,true,this::checkSpeech);
        int microphoneHelp=microphoneState==2?R.string.apk_setup_microphone_ready:microphoneState==1?R.string.apk_setup_microphone_checking:R.string.apk_setup_microphone_help;
        row(getString(R.string.apk_setup_microphone),microphoneState==2&&granted(android.Manifest.permission.RECORD_AUDIO),microphoneHelp,true,this::checkMicrophone);
        required(R.string.adb_activation_title,AdbBridge.monitorsInput(),R.string.apk_permission_adb_help,new Intent(this,AdbActivationActivity.class));
        required(R.string.apk_authorize,Authorization.enabled(this),R.string.apk_permission_authorize_help,new Intent(this,ControlActivity.class));
        boolean accessibility=accessibilityEnabled();
        required(R.string.apk_accessibility,accessibility&&ForegroundUiService.instance!=null,accessibility&&ForegroundUiService.instance==null?R.string.apk_permission_connecting_help:R.string.apk_permission_accessibility_help,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        page.addView(AiosUi.secondary(this,getString(AdbBridge.connected()&&ForegroundUiService.instance!=null?R.string.apk_permission_screen_check:R.string.apk_permission_screen_blocked)));
        required(R.string.apk_overlay,Settings.canDrawOverlays(this),R.string.apk_permission_overlay_help,new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,packageUri()));
        for(PermissionInfo p:runtime())if(p.name.equals(android.Manifest.permission.RECORD_AUDIO))row(p.loadLabel(getPackageManager()),granted(p.name),R.string.apk_permission_runtime_help,true,()->manual(R.string.apk_permission_runtime_help,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri())));
        page.addView(AiosUi.filledButton(this,getString(R.string.apk_setup_try_assistant),()->open(new Intent(this,ApkAssistantEntryActivity.class))));
        page.addView(AiosUi.filledButton(this,getString(showOptional?R.string.apk_setup_hide_optional:R.string.apk_setup_optional),()->{showOptional=!showOptional;render();}));
        if(showOptional){
        String assistant=Settings.Secure.getString(getContentResolver(),"assistant");
        special(R.string.apk_default_assistant,new ComponentName(this,ApkAssistantEntryActivity.class).equals(assistant==null?null:ComponentName.unflattenFromString(assistant)),R.string.apk_assistant_invocation_help,new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        for(PermissionInfo p:runtime()){
            if(p.name.equals(android.Manifest.permission.RECORD_AUDIO))continue;
            int help=restricted(p)?R.string.apk_permission_restricted_help:background(p.name)?R.string.apk_permission_background_help:R.string.apk_permission_runtime_help;
            row(p.loadLabel(getPackageManager()),granted(p.name),help,()->manual(help,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,packageUri())));
        }
        special(R.string.apk_permission_files,Environment.isExternalStorageManager(),R.string.apk_permission_files_help,new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,packageUri()));
        special(R.string.apk_permission_install,getPackageManager().canRequestPackageInstalls(),R.string.apk_permission_install_help,new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,packageUri()));
        special(R.string.apk_notifications,getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(this,PhoneNotifications.class)),R.string.apk_permission_notifications_help,new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        special(R.string.apk_permission_settings,Settings.System.canWrite(this),R.string.apk_permission_settings_help,new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,packageUri()));
        special(R.string.apk_permission_dnd,getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted(),R.string.apk_permission_dnd_help,new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        AppOpsManager ops=getSystemService(AppOpsManager.class);
        special(R.string.apk_permission_usage,ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED,R.string.apk_permission_usage_help,new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,packageUri()));
        special(R.string.apk_permission_battery,getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()),R.string.apk_permission_battery_help,new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
        summary.setText(missing==0?getString(R.string.apk_permission_complete):getString(R.string.apk_permission_missing,missing));
        page.addView(AiosUi.textButton(this,getString(R.string.apk_permissions_done),this::leave));
        scroll.post(()->scroll.scrollTo(0,y));
    }
}
