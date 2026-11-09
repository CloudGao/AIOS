package os.aios.speech;

import android.app.*;
import android.app.Service;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Observes recording metadata only. No audio is opened until the user starts minutes. */
public final class MeetingMonitorService extends Service {
 final Handler handler=new Handler(Looper.getMainLooper()); WindowManager windows;Button chip;AudioManager audio;
 final LinkedHashMap<Integer,String> apps=new LinkedHashMap<>();
 final AudioManager.AudioRecordingCallback callback=new AudioManager.AudioRecordingCallback(){public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs){refresh();}};
 final Runnable poll=new Runnable(){public void run(){refresh();handler.postDelayed(this,2000);}};
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("meeting-entry","会议纪要入口",NotificationManager.IMPORTANCE_MIN));
  PendingIntent open=PendingIntent.getActivity(this,22,new Intent(this,MinutesActivity.class),PendingIntent.FLAG_IMMUTABLE);
  startForeground(902,new Notification.Builder(this,"meeting-entry").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(os.aios.core.DisplayText.clean("会议纪要入口已启用")).setContentText(os.aios.core.DisplayText.clean("应用使用麦克风时显示纪要按钮；点击开启后才录音")).setContentIntent(open).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
  windows=getSystemService(WindowManager.class);audio=getSystemService(AudioManager.class);audio.registerAudioRecordingCallback(callback,handler);handler.post(poll);
 }
 public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
 String appName(String pkg){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
 void refresh(){
  apps.clear();for(AudioRecordingConfiguration r:MeetingAudio.externalRecordings(this)){String pkg=r.getClientPackageName();if(!pkg.isEmpty()&&!pkg.equals("org.lineageos.recorder")&&!pkg.equals("com.android.dialer"))apps.put(r.getClientUid(),pkg);}
  boolean capturing=MinutesService.instance!=null&&"meeting".equals(MinutesService.instance.kind);
  boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked();
  if(locked||MinutesActivity.visible||(!capturing&&apps.isEmpty())||!Settings.canDrawOverlays(this)){remove();return;}
  if(chip==null){chip=os.aios.core.DisplayText.bind(new Button(this));chip.setTextSize(12);chip.setTextColor(Color.WHITE);chip.setAllCaps(false);chip.setPadding(dp(10),0,dp(10),0);chip.setMinHeight(0);chip.setMinimumHeight(0);
   GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(30,104,76));bg.setCornerRadius(dp(20));chip.setBackground(bg);
   WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,dp(40),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
   p.gravity=Gravity.TOP|Gravity.END;p.x=dp(8);p.y=dp(4);p.setTitle(os.aios.core.DisplayText.clean("AIOS microphone minutes"));windows.addView(chip,p);chip.setOnClickListener(v->open());
  }
  String name=apps.size()==1?appName(apps.values().iterator().next()):apps.size()+" 个应用";
  chip.setText(capturing?"● 转写中 · 查看 / 停止":"麦克风使用中 · "+name+" · 纪要");chip.setContentDescription(os.aios.core.DisplayText.clean(chip.getText()));
 }
 void open(){
  if(MinutesService.instance!=null&&"meeting".equals(MinutesService.instance.kind)){launch(MinutesService.instance.meetingUid,MinutesService.instance.meetingPackage);return;}
  if(apps.size()==1){Map.Entry<Integer,String> e=apps.entrySet().iterator().next();launch(e.getKey(),e.getValue());}
  else startActivity(new Intent(this,MinutesActivity.class).putExtra("kind","meeting").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
 }
 void launch(int uid,String pkg){startActivity(new Intent(this,MinutesActivity.class).putExtra("kind","meeting").putExtra("meeting_uid",uid).putExtra("meeting_package",pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 void remove(){if(chip!=null){windows.removeView(chip);chip=null;}}
 public void onDestroy(){handler.removeCallbacksAndMessages(null);if(audio!=null)audio.unregisterAudioRecordingCallback(callback);remove();super.onDestroy();}
}
