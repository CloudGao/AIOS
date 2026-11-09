package os.aios.speech;
import android.app.*;
import android.app.Service;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import android.telephony.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MinutesService extends Service {
 static volatile MinutesService instance;static volatile String partial="",currentId="",recorderToken="";
 boolean recorderTap;
 volatile boolean stop;volatile String error="";String kind;long began;int meetingUid=-1;String meetingPackage="";MeetingAudio meetingAudio;long absentSince;boolean meetingModeSeen;
 final Handler main=new Handler(Looper.getMainLooper());MinutesStore store;PhoneStateListener listener;TelephonyManager phone;Thread processing;
 final ArrayList<Channel> channels=new ArrayList<>();
 final Set<Integer> meetingPlayers=new HashSet<>();
 final class Channel {
  final int source;final String label;final File file;final ArrayBlockingQueue<float[]> frames=new ArrayBlockingQueue<>(1900);
  volatile AudioRecord recorder;volatile boolean finished;volatile String preview="";volatile long audibleSamples;long offset;Thread capture;
  Channel(int source,String label,File file){this.source=source;this.label=label;this.file=file;}
 }
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();instance=this;store=new MinutesStore(this);store.recover();currentId="";partial="";}
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if("recorder_stop".equals(intent.getAction())){if(recorderTap&&recorderToken.equals(intent.getStringExtra("recorder_token")))requestStop();else if(processing==null)stopSelf();return START_NOT_STICKY;}
  if("stop".equals(intent.getAction())){if(processing==null)stopSelf();else requestStop();return START_NOT_STICKY;}
  if(processing!=null)return START_NOT_STICKY;
  recorderTap="recorder_start".equals(intent.getAction());recorderToken=recorderTap?intent.getStringExtra("recorder_token"):"";
  kind=intent.getStringExtra("kind");if(!"call".equals(kind)&&!"meeting".equals(kind))kind="recording";meetingUid=intent.getIntExtra("meeting_uid",-1);meetingPackage=intent.getStringExtra("meeting_package");
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("minutes","录音转写",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MinutesActivity.class).putExtra("kind",kind).putExtra("meeting_uid",meetingUid).putExtra("meeting_package",meetingPackage),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent end=PendingIntent.getService(this,0,new Intent(this,MinutesService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
  startForeground(901,new Notification.Builder(this,"minutes").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(os.aios.core.DisplayText.clean("正在录音并本地转写")).setContentText(os.aios.core.DisplayText.clean("点此查看实时文字；结束后自动生成 AI 纪要")).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止并总结",end).build()).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
  try{startForegroundService(new Intent().setClassName("os.aios.agent","os.aios.agent.AgentService"));}catch(Exception unavailable){android.util.Log.w("AIOSMinutes","Summary service start deferred");}
  try{
   if(kind.equals("call")){
    Bundle capture=getContentResolver().call(android.net.Uri.parse("content://com.android.dialer.aios.capture"),"capture",null,null);
    if(capture==null||capture.getBoolean("recording"))throw new IllegalStateException("请先停止拨号器普通录音，再开启纪要");
   }
   if(kind.equals("call")){
    phone=getSystemService(TelephonyManager.class);if(phone.getCallState()!=TelephonyManager.CALL_STATE_OFFHOOK)throw new IOException("请先接通电话再开启通话纪要");
    listener=new PhoneStateListener(){public void onCallStateChanged(int state,String number){if(state==TelephonyManager.CALL_STATE_IDLE)requestStop();}};phone.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
   }
   if(kind.equals("meeting")){
    if(meetingPackage==null||getPackageManager().getApplicationInfo(meetingPackage,0).uid!=meetingUid||!MeetingAudio.recording(this,meetingUid))throw new IOException("目标应用已停止使用麦克风，请返回会议后再开启");
    meetingAudio=new MeetingAudio(this);meetingModeSeen=getSystemService(AudioManager.class).getMode()==AudioManager.MODE_IN_COMMUNICATION;MeetingAudio.retainedPlayer(this,meetingUid,meetingPlayers,true);
   }
   File dir=new File(getFilesDir(),"recordings/"+System.currentTimeMillis());if(!dir.mkdirs())throw new IOException("无法创建录音目录");
   if(kind.equals("call")){channels.add(new Channel(MediaRecorder.AudioSource.VOICE_UPLINK,"我",new File(dir,"uplink.wav")));channels.add(new Channel(MediaRecorder.AudioSource.VOICE_DOWNLINK,"对方",new File(dir,"downlink.wav")));}
   else if(kind.equals("meeting")){channels.add(new Channel(MediaRecorder.AudioSource.VOICE_RECOGNITION,"我",new File(dir,"microphone.wav")));channels.add(new Channel(MediaRecorder.AudioSource.REMOTE_SUBMIX,"会议播放",new File(dir,"meeting.wav")));}
   else channels.add(new Channel(MediaRecorder.AudioSource.MIC,null,new File(dir,"recording.wav")));
   currentId=store.create(kind,dir.getPath());if(kind.equals("call"))CallRecordings.link(store,currentId,intent);String id=currentId;began=SystemClock.elapsedRealtime();
   // Both sources must initialize. Never substitute a mixed source or microphone for a call direction.
   for(Channel c:channels){c.recorder=c.source==MediaRecorder.AudioSource.REMOTE_SUBMIX?meetingAudio.openPlayback(this,meetingUid):(kind.equals("meeting")||recorderTap)?MeetingAudio.openMicrophone(this):new AudioRecord.Builder().setContext(this).setAudioSource(c.source).setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(Math.max(32768,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)*4)).build();if(c.recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("设备无法提供 "+(c.label==null?"麦克风":c.label+"的独立通话音频"));}
   for(Channel c:channels){c.recorder.registerAudioRecordingCallback(getMainExecutor(),new AudioManager.AudioRecordingCallback(){public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs){AudioRecord r=c.recorder;AudioRecordingConfiguration config=r==null?null:r.getActiveRecordingConfiguration();if(config!=null&&config.isClientSilenced()){error="系统未提供"+(c.label==null?"麦克风":c.label)+"音频（被静音），已停止";requestStop();}}});c.recorder.startRecording();if(c.recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException((c.label==null?"麦克风":c.label)+"音频采集未启动");c.offset=SystemClock.elapsedRealtime()-began;}
   if(kind.equals("meeting"))main.post(meetingWatch);
   for(Channel c:channels){c.capture=new Thread(()->capture(c),"minutes-capture-"+c.source);c.capture.start();}
   processing=new Thread(()->process(id),"minutes-recognize");processing.start();
  }catch(Exception e){error=(kind.equals("call")?"无法分离通话双方音频：":"录音失败：")+e.getMessage();partial=error;for(Channel c:channels){if(c.recorder!=null){c.recorder.release();c.recorder=null;}}if(!currentId.isEmpty())try{store.finish(currentId,error);}catch(Exception failure){store.state(currentId,"interrupted",error);}if(meetingAudio!=null)meetingAudio.close();store.changed();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
  return START_NOT_STICKY;
 }
 synchronized void requestStop(){if(stop)return;stop=true;for(Channel c:channels){AudioRecord r=c.recorder;if(r!=null)try{r.stop();}catch(IllegalStateException ignored){}}if(!currentId.isEmpty())store.state(currentId,"finishing",error);}
 void capture(Channel c){long count=0;try(RandomAccessFile wav=new RandomAccessFile(c.file,"rw")){
  wav.setLength(0);wav.write(new byte[44]);short[] pcm=new short[1536];byte[] bytes=new byte[3072];Pcm16k resampler=new Pcm16k(c.recorder.getSampleRate());
  while(!stop){int n=c.recorder.read(pcm,0,pcm.length);if(n<0){if(stop)break;throw new IOException("音频读取失败 "+n);}if(n==0)continue;float[] frame=resampler.accept(pcm,n);n=frame.length;if(n==0)continue;for(int i=0;i<n;i++){short sample=(short)Math.round(frame[i]*32768f);if(Math.abs((int)sample)>16)c.audibleSamples++;bytes[2*i]=(byte)sample;bytes[2*i+1]=(byte)(sample>>8);}wav.write(bytes,0,n*2);count+=n;if(!c.frames.offer(frame))throw new IOException("设备转写速度不足，已停止；原音频已保留");if(count>=16000L*60*60*4)throw new IOException("已达到单次四小时上限");}
  header(wav,count*2);
 }catch(Exception e){if(error.isEmpty())error="录音中断："+e.getMessage();requestStop();try(RandomAccessFile wav=new RandomAccessFile(c.file,"rw")){if(wav.length()>=44)header(wav,wav.length()-44);}catch(Exception ignored){}
 }finally{c.finished=true;AudioRecord r=c.recorder;c.recorder=null;if(r!=null)r.release();}}
 void process(String id){ArrayList<MinutesEngine> engines=new ArrayList<>();try{
  for(Channel c:channels)engines.add(new MinutesEngine(this,new MinutesEngine.Output(){public void segment(long a,long b,String speaker,String text)throws Exception{store.segment(id,a+c.offset,b+c.offset,speaker,text);}public void partial(String text){c.preview=text;StringBuilder p=new StringBuilder();for(Channel ch:channels)if(!ch.preview.isEmpty())p.append(ch.label==null?"":ch.label+"：").append(ch.preview).append("\n");partial=p.toString();store.changed();}},c.label));
  boolean pending=true;while(pending){pending=false;for(int i=0;i<channels.size();i++){Channel c=channels.get(i);float[] f=c.frames.poll(30,TimeUnit.MILLISECONDS);if(f!=null)engines.get(i).accept(f);pending|=!c.finished||!c.frames.isEmpty();}}
  for(MinutesEngine e:engines)e.finish();
 }catch(Exception|LinkageError e){error="转写失败："+e.getMessage();requestStop();}finally{
  for(MinutesEngine e:engines)e.close();stop=true;try{for(Channel c:channels)if(c.capture!=null)c.capture.join(3000);if(kind.equals("meeting"))for(Channel c:channels)if(c.audibleSamples==0)error+=(error.isEmpty()?"":"；")+"未检测到“"+c.label+"”音轨声音，无法确认这一路发言是否完整";store.finish(id,error);}catch(Exception e){store.state(id,"interrupted","保存转写失败："+e.getMessage());}
  partial="";main.post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
 }}
 final Runnable meetingWatch=new Runnable(){public void run(){
  if(stop)return;
  boolean recording=MeetingAudio.recording(MinutesService.this,meetingUid),playing=MeetingAudio.playing(MinutesService.this,meetingUid);
  boolean other=false;for(AudioRecordingConfiguration r:MeetingAudio.externalRecordings(MinutesService.this)){
   if(r.getClientUid()==meetingUid&&r.isClientSilenced()){error="纪要影响了会议麦克风，已停止旁路录音";requestStop();return;}
   if(r.getClientUid()!=meetingUid)other=true;
  }
  boolean communicating=getSystemService(AudioManager.class).getMode()==AudioManager.MODE_IN_COMMUNICATION;
  if(recording&&communicating)meetingModeSeen=true;
  // A muted meeting can release AudioRecord while retaining its communication mode.
  if(recording||playing||MeetingAudio.retainedPlayer(MinutesService.this,meetingUid,meetingPlayers,recording)||(meetingModeSeen&&communicating&&!other))absentSince=0;
  else if(absentSince==0)absentSince=SystemClock.elapsedRealtime();
  else if(SystemClock.elapsedRealtime()-absentSince>=15000){requestStop();return;}
  main.postDelayed(this,1000);
 }};
 String audioStatus(){if(!"meeting".equals(kind))return "";StringBuilder s=new StringBuilder();for(Channel c:channels){if(s.length()>0)s.append(" · ");s.append(c.label).append(c.audibleSamples>0?"：已收到音频信号":"：尚未检测到声音");}return s.toString();}
 static void header(RandomAccessFile f,long bytes)throws IOException{f.seek(0);f.writeBytes("RIFF");le(f,bytes+36,4);f.writeBytes("WAVEfmt ");le(f,16,4);le(f,1,2);le(f,1,2);le(f,16000,4);le(f,32000,4);le(f,2,2);le(f,16,2);f.writeBytes("data");le(f,bytes,4);}
 static void le(RandomAccessFile f,long v,int n)throws IOException{for(int i=0;i<n;i++)f.write((int)(v>>(i*8))&255);}
 public void onDestroy(){stop=true;main.removeCallbacks(meetingWatch);if(meetingAudio!=null)meetingAudio.close();if(phone!=null&&listener!=null)phone.listen(listener,PhoneStateListener.LISTEN_NONE);for(Channel c:channels){AudioRecord r=c.recorder;if(r!=null)try{r.stop();}catch(Exception ignored){}}instance=null;super.onDestroy();}
}
