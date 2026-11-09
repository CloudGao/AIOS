package os.aios.agent;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** User-driven local pairing. No pairing code is written to preferences, saved state or logs. */
public final class AdbActivationActivity extends Activity {
 private EditText pairingPort,code,connectPort;
 private TextView state,pair,activate;
 private View stateDot;
 private boolean busy;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable poll=new Runnable(){public void run(){
  busy=LocalAdbActivation.BUSY.get();enable(!busy);
  int message=LocalAdbActivation.message;
  if(!busy&&AdbBridge.connected())message=AdbBridge.monitorsInput()?R.string.apk_connected:R.string.apk_connected_without_monitor;
  else if(!busy&&(message==R.string.apk_connected||message==R.string.apk_connected_without_monitor))message=R.string.apk_disconnected;
  show(message);handler.postDelayed(this,1000);
 }};
 public void onCreate(Bundle saved){super.onCreate(saved);setTitle(R.string.adb_activation_title);
  LinearLayout shell=AiosUi.column(this);
  shell.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
  LinearLayout appBar=AiosUi.appBar(this,getString(R.string.adb_activation_title),this::finish);
        appBar.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1,AiosUi.dp(this,56)));
        shell.addView(appBar,new LinearLayout.LayoutParams(-1,-2));
  ScrollView scroll=new ScrollView(this);scroll.setId(7002);LinearLayout page=AiosUi.column(this);page.setPadding(0,0,0,AiosUi.dp(this,24));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(shell);AiosUi.page(this,shell);
  int side=AiosUi.dp(this,16);
  // Status card: dot + live message.
  LinearLayout status=AiosUi.card(this);page.addView(status);
  LinearLayout line=AiosUi.row(this);stateDot=AiosUi.dot(this,AiosTheme.outlineStrong(this));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(AiosUi.dp(this,8),AiosUi.dp(this,8));dl.setMarginEnd(AiosUi.dp(this,12));line.addView(stateDot,dl);
  state=AiosUi.body(this,getString(R.string.apk_disconnected));state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);line.addView(state,new LinearLayout.LayoutParams(0,-2,1));status.addView(line);
  TextView guide=AiosUi.secondary(this,getString(R.string.adb_activation_guide));guide.setPadding(0,AiosUi.dp(this,12),0,0);status.addView(guide);
  status.addView(AiosUi.actions(this,AiosUi.tonalButton(this,getString(R.string.adb_open_settings),()->{try{startActivity(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"));}catch(ActivityNotFoundException e){startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}})));
  Switch recovery=new Switch(this);recovery.setText(R.string.adb_auto_recovery);recovery.setChecked(LocalAdbActivation.automatic(this));
  recovery.setOnCheckedChangeListener((button,checked)->LocalAdbActivation.automatic(this,checked));status.addView(recovery);
  status.addView(AiosUi.secondary(this,getString(R.string.adb_auto_recovery_help)));
  LocalAdbActivation.startRecovery(this);
  // Step 1: pair.
  page.addView(AiosUi.label(this,getString(R.string.adb_pair)));
  LinearLayout pairing=AiosUi.card(this);page.addView(pairing);
  pairingPort=field(pairing,R.string.adb_pair_port,101,false);code=field(pairing,R.string.adb_pair_code,102,true);
  pair=AiosUi.filledButton(this,getString(R.string.adb_pair),this::pair);pairing.addView(AiosUi.actions(this,pair));
  // Step 2: activate.
  page.addView(AiosUi.label(this,getString(R.string.adb_activate)));
  LinearLayout connect=AiosUi.card(this);page.addView(connect);
  connectPort=field(connect,R.string.adb_connect_port,103,false);
  connect.addView(AiosUi.secondary(this,getString(R.string.adb_port_help)));
  activate=AiosUi.filledButton(this,getString(R.string.adb_activate),this::activate);connect.addView(AiosUi.actions(this,activate));
 }
 private EditText field(LinearLayout parent,int label,int id,boolean secret){
  TextView text=AiosUi.fieldLabel(this,getString(label));text.setPadding(AiosUi.dp(this,4),0,AiosUi.dp(this,4),AiosUi.dp(this,6));text.setLabelFor(id);parent.addView(text);
  EditText input=AiosUi.field(this,null,null);input.setId(id);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER|(secret?InputType.TYPE_NUMBER_VARIATION_PASSWORD:0));input.setSaveEnabled(!secret);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);parent.addView(input);return input;
 }
 private void enable(boolean value){for(TextView b:new TextView[]{pair,activate}){b.setEnabled(value);b.setAlpha(value?1f:0.38f);}}
 private void show(int message){
  state.setText(message);
  int color=message==R.string.apk_connected||message==R.string.adb_paired?AiosTheme.accent(this)
   :message==R.string.adb_pair_failed||message==R.string.adb_connect_failed||message==R.string.adb_invalid_input?AiosTheme.error(this)
   :message==R.string.apk_connected_without_monitor||message==R.string.adb_pairing||message==R.string.adb_connecting?AiosTheme.warn(this):AiosTheme.outlineStrong(this);
  stateDot.setBackground(AiosUi.shape(this,color,4));
 }
 private void busy(boolean value,int message){busy=value;LocalAdbActivation.message=message;LocalAdbActivation.BUSY.set(value);enable(!value);show(message);}
 private void pair(){
  final int port;final String password=code.getText().toString();
  try{port=LocalAdbActivation.port(pairingPort.getText().toString(),false);if(!password.matches("[0-9]{6}"))throw new IllegalArgumentException();}catch(Exception e){show(R.string.adb_invalid_input);return;}
  if(!LocalAdbActivation.BUSY.compareAndSet(false,true))return;
  code.setText("");busy(true,R.string.adb_pairing);
  LocalAdbActivation.WORK.execute(()->{int result;try{LocalAdbActivation.get(this).pairLocal(port,password);result=R.string.adb_paired;}catch(Exception e){android.util.Log.w("AIOSLocalAdb","Pairing failed: "+e.getClass().getSimpleName());result=R.string.adb_pair_failed;}final int message=result;LocalAdbActivation.message=message;LocalAdbActivation.BUSY.set(false);runOnUiThread(()->{if(!isDestroyed())busy(false,message);});});
 }
 private void activate(){
  final int port;try{port=LocalAdbActivation.port(connectPort.getText().toString(),true);}catch(Exception e){show(R.string.adb_invalid_input);return;}
  if(!LocalAdbActivation.BUSY.compareAndSet(false,true))return;
  busy(true,R.string.adb_connecting);
  LocalAdbActivation.WORK.execute(()->{int result;try{LocalAdbActivation.get(this).activate(port);result=AdbBridge.monitorsInput()?R.string.apk_connected:R.string.apk_connected_without_monitor;}catch(Exception e){android.util.Log.w("AIOSLocalAdb","Activation failed: "+e.getClass().getSimpleName()+" "+e.getMessage());result=R.string.adb_connect_failed;}final int message=result;LocalAdbActivation.message=message;LocalAdbActivation.BUSY.set(false);runOnUiThread(()->{if(!isDestroyed())busy(false,message);});});
 }
 protected void onResume(){super.onResume();handler.post(poll);}
 protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
}
