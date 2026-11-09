package os.aios.speech;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MinutesActivity extends Activity {
 static volatile boolean visible;
 MinutesStore store;LinearLayout body,rows;ScrollView transcript;TextView status,live,summary,toggle;String selected="",kind="recording";final Handler handler=new Handler(Looper.getMainLooper());
 final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,1000);}};
 private void wakeSummary(){try{startForegroundService(new Intent().setClassName("os.aios.agent","os.aios.agent.AgentService"));}catch(Exception e){android.util.Log.w("AIOSMinutes","Summary service start deferred");}}
 public void onCreate(Bundle b){super.onCreate(b);wakeSummary();store=new MinutesStore(this);kind=getIntent().getStringExtra("kind");if(!"call".equals(kind)&&!"meeting".equals(kind))kind="recording";startForegroundService(new Intent(this,MeetingMonitorService.class));if(b!=null)selected=b.getString("selected","");if(getIntent().hasExtra("session_id"))selected=getIntent().getStringExtra("session_id");
  getWindow().setStatusBarColor(os.aios.core.AiosTheme.background(this));getWindow().setNavigationBarColor(os.aios.core.AiosTheme.background(this));os.aios.core.AiosTheme.window(this);
  body=new LinearLayout(this);body.setOrientation(1);body.setBackgroundColor(os.aios.core.AiosTheme.background(this));setContentView(body);
  body.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});
  TextView history=os.aios.core.AiosUi.textButton(this,"历史",this::history);
  body.addView(os.aios.core.AiosUi.toolbar(this,kind.equals("call")?"通话纪要":kind.equals("meeting")?"会议纪要":"录音纪要",this::finish,history));
  transcript=new ScrollView(this);transcript.setFillViewport(true);transcript.setClipToPadding(false);transcript.setPadding(dp(16),dp(8),dp(16),dp(24));body.addView(transcript,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout content=new LinearLayout(this);content.setOrientation(1);transcript.addView(content);
  LinearLayout overview=card(content);TextView eyebrow=text("设备端实时转写",12);eyebrow.setTextColor(os.aios.core.AiosTheme.accent(this));eyebrow.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));overview.addView(eyebrow);
  status=text("准备就绪",18);status.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));overview.addView(status);
  TextView note=text("录音结束后自动生成 AI 总结",14);note.setTextColor(os.aios.core.AiosTheme.muted(this));overview.addView(note);
  TextView info=os.aios.core.AiosUi.textButton(this,"了解转写与总结",()->new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("转写与总结")).setMessage(os.aios.core.DisplayText.clean("语音在设备上转写。总结由手机 AIOS 调用已配置的 AI 服务生成，仅发送转写文字；需要有效账号和网络。\n\n通话按上下行区分我与对方；录音按估计的声纹编号区分。会议播放可能包含多人，释放音频后约 15 秒结束，也可以手动停止。")).setPositiveButton(os.aios.core.DisplayText.clean("知道了"),null).show());info.setPadding(0,0,dp(12),0);LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-2,-2);il.gravity=Gravity.START;overview.addView(info,il);
  toggle=os.aios.core.AiosUi.filledButton(this,"",()->{if(MinutesService.instance!=null)startService(new Intent(this,MinutesService.class).setAction("stop"));else start();});toggle.setTextSize(15);LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(52));action.topMargin=dp(12);overview.addView(toggle,action);
  if(getIntent().getBooleanExtra("view_only",false)){toggle.setVisibility(View.GONE);history.setVisibility(View.GONE);}
  playback=new LinearLayout(this);playback.setOrientation(1);content.addView(playback);
  LinearLayout summaryCard=card(content);TextView heading=text("AI 总结",20);heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));summaryCard.addView(heading);summary=text("结束录音后，总结会显示在这里",16);summary.setLineSpacing(dp(5),1);summaryCard.addView(summary);TextView retry=os.aios.core.AiosUi.textButton(this,"重试或检查总结",()->{if(selected.isEmpty())return;try{store.retry(selected);wakeSummary();render();}catch(Exception e){Toast.makeText(this,os.aios.core.DisplayText.clean("暂时无法重试，转写已保留"),Toast.LENGTH_SHORT).show();}});summaryCard.addView(os.aios.core.AiosUi.actions(this,retry));
  LinearLayout transcriptCard=card(content);TextView transcriptHeading=text("文字记录",20);transcriptHeading.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));transcriptCard.addView(transcriptHeading);
  rows=new LinearLayout(this);rows.setOrientation(1);transcriptCard.addView(rows);live=text("开始录音后，这里会实时显示转写内容",14);live.setTextColor(os.aios.core.AiosTheme.muted(this));transcriptCard.addView(live);
 }
 int dp(int value){return os.aios.core.AiosUi.dp(this,value);}
 LinearLayout card(LinearLayout parent){LinearLayout card=os.aios.core.AiosUi.card(this);card.setPadding(dp(20),dp(16),dp(20),dp(20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(card,lp);return card;}
 TextView text(String s,int size){TextView t=os.aios.core.AiosUi.selectable(this,s,size,os.aios.core.AiosTheme.text(this));t.setPadding(0,dp(5),0,dp(5));t.setLineSpacing(dp(3),1);return t;}

 void start(){ArrayList<String> permissions=new ArrayList<>();for(String p:new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)permissions.add(p);if(kind.equals("call")&&checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.READ_PHONE_STATE);if(!permissions.isEmpty()){requestPermissions(permissions.toArray(new String[0]),42);return;}
  if(kind.equals("meeting")&&getIntent().getIntExtra("meeting_uid",-1)<0){chooseMeeting();return;}
  selected="";startForegroundService(new Intent(this,MinutesService.class).putExtra("kind",kind).putExtra("call_created",getIntent().getLongExtra("call_created",0)).putExtra("call_account",getIntent().getStringExtra("call_account")).putExtra("call_component",getIntent().getStringExtra("call_component")).putExtra("call_number",getIntent().getStringExtra("call_number")).putExtra("meeting_uid",getIntent().getIntExtra("meeting_uid",-1)).putExtra("meeting_package",getIntent().getStringExtra("meeting_package")));handler.postDelayed(()->{selected=MinutesService.currentId;render();},300);
 }
 void chooseMeeting(){LinkedHashMap<Integer,String> apps=new LinkedHashMap<>();for(android.media.AudioRecordingConfiguration r:MeetingAudio.externalRecordings(this))apps.put(r.getClientUid(),r.getClientPackageName());if(apps.isEmpty()){status.setText("没有其他应用正在使用麦克风");return;}Integer[] uids=apps.keySet().toArray(new Integer[0]);String[] labels=apps.values().toArray(new String[0]);new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("选择要记录的会议应用")).setItems(os.aios.core.DisplayText.clean(labels),(d,n)->{getIntent().putExtra("meeting_uid",uids[n]).putExtra("meeting_package",labels[n]);start();}).setNegativeButton(os.aios.core.DisplayText.clean("取消"),null).show();}
 public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==42){for(int value:g)if(value!=PackageManager.PERMISSION_GRANTED){status.setText("需要麦克风权限才能录音");return;}start();}}
 public void onResume(){super.onResume();visible=true;handler.post(refresh);}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);recreate();}
 public void onPause(){stopPlayback();visible=false;handler.removeCallbacks(refresh);super.onPause();}
 public void onSaveInstanceState(Bundle b){b.putString("selected",selected);super.onSaveInstanceState(b);}
 LinearLayout playback;android.media.MediaPlayer player;String audioShown="";
 void audio(JSONObject d){if(!getIntent().getBooleanExtra("view_only",false)||selected.equals(audioShown))return;audioShown=selected;playback.removeAllViews();
  for(String name:new String[]{"uplink.wav","downlink.wav"}){java.io.File file=new java.io.File(d.optString("audio"),name);if(file.length()<=44)continue;
   TextView button=os.aios.core.AiosUi.tonalButton(this,name.equals("uplink.wav")?"播放我的录音":"播放对方录音",()->{try{stopPlayback();player=new android.media.MediaPlayer();player.setDataSource(file.getPath());player.setOnCompletionListener(p->stopPlayback());player.prepare();player.start();}catch(Exception e){stopPlayback();status.setText("录音无法播放："+e.getMessage());}});LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.bottomMargin=dp(8);playback.addView(button,bl);
  }
  if(playback.getChildCount()>0){TextView stop=os.aios.core.AiosUi.outlinedButton(this,"停止播放",this::stopPlayback);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);sl.bottomMargin=dp(12);playback.addView(stop,sl);}
 }
 void stopPlayback(){if(player!=null){player.release();player=null;}}
 String previous="";
 void render(){try{
  boolean running=MinutesService.instance!=null;toggle.setText(running?"停止录音并自动总结":kind.equals("call")?"开启通话录音与转写":kind.equals("meeting")?"开启会议录音与转写":"开启录音与转写");
  if(!running&&MinutesService.currentId.isEmpty()&&!MinutesService.partial.isEmpty()){status.setText(MinutesService.partial);return;}
  if(selected.isEmpty()){if(!MinutesService.currentId.isEmpty())selected=MinutesService.currentId;else {JSONArray list=store.list();if(list.length()>0)selected=list.getJSONObject(0).getString("id");}}
  if(selected.isEmpty()){status.setText(MinutesService.partial.isEmpty()?"准备就绪":MinutesService.partial);return;}
  JSONObject d=store.detail(selected);if(!d.has("id"))return;audio(d);String st=d.getString("status");String label=switch(st){case "recording"->"正在录音 · 转写约每 4 秒刷新";case "importing"->"正在转写已保存的录音…";case "finishing"->"正在处理最后一段语音…";case "queued"->"录音已结束 · 等待手机 AIOS 总结";case "summarizing"->"AI 正在总结…";case "completed"->"纪要已完成";case "completed_partial"->"部分纪要已生成 · 录音完整性待核对";case "summary_failed"->"总结未完成，请检查手机 AIOS 账号与网络";case "empty"->"未识别到语音";default->"录音已中断";};status.setText(label+(running&&MinutesService.instance!=null?"\n"+MinutesService.instance.audioStatus():"")+(d.optString("error").isEmpty()?"":"\n"+d.optString("error")));
  JSONArray segments=d.getJSONArray("segments");String revision=selected+":"+segments.length();if(!previous.equals(revision)){previous=revision;rows.removeAllViews();for(int i=0;i<segments.length();i++){JSONObject s=segments.getJSONObject(i);long sec=s.getLong("start_ms")/1000;TextView row=text(String.format(Locale.ROOT,"%02d:%02d  %s\n%s",sec/60,sec%60,s.getString("speaker")+(d.getString("kind").equals("recording")?"（估计）":""),s.getString("text")),17);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(os.aios.core.AiosUi.shape(this,i%2==0?os.aios.core.AiosTheme.surfaceHigh(this):os.aios.core.AiosTheme.surface(this),12));LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2);rl.bottomMargin=dp(4);rows.addView(row,rl);}}
  live.setText(running&&selected.equals(MinutesService.currentId)&&!MinutesService.partial.isEmpty()?"正在识别（暂定）\n"+MinutesService.partial:"");String markdown=d.optString("summary");if(!markdown.equals(summary.getTag())){summary.setTag(markdown);summary.setText(markdown.isEmpty()?"录音结束后，总结会显示在这里":MinutesMarkdown.render(markdown));}
 }catch(Exception e){status.setText("读取纪要失败："+e.getMessage());}}
 void history(){try{JSONArray list=store.list();String[] labels=new String[list.length()];for(int i=0;i<labels.length;i++){JSONObject d=list.getJSONObject(i);labels[i]=(d.getString("kind").equals("call")?"通话 · ":d.getString("kind").equals("meeting")?"会议 · ":"录音 · ")+new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(d.getLong("started")));}new AlertDialog.Builder(this).setTitle(os.aios.core.DisplayText.clean("历史纪要")).setItems(os.aios.core.DisplayText.clean(labels),(dialog,index)->{try{selected=list.getJSONObject(index).getString("id");render();}catch(Exception ignored){}}).setNegativeButton(os.aios.core.DisplayText.clean("关闭"),null).show();}catch(Exception e){status.setText(e.getMessage());}}
}
