package os.aios.speech;

import android.content.Context;
import android.media.*;
import android.media.audiopolicy.*;
import android.media.audiopolicy.AudioMix;
import android.os.Process;
import java.io.IOException;
import java.util.*;

/** Privileged, explicitly started capture. The loopback also renders to the original device. */
final class MeetingAudio implements AutoCloseable {
 final AudioManager manager; AudioPolicy policy;
 MeetingAudio(Context context){manager=context.getSystemService(AudioManager.class);}
 static AudioRecord openMicrophone(Context context){
  return new AudioRecord.Builder().setContext(context).setAudioAttributes(new AudioAttributes.Builder().setInternalCapturePreset(MediaRecorder.AudioSource.VOICE_RECOGNITION).addTag("aios_meeting_tap").build()).setPrivacySensitive(false).setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(32768).build();
 }
 static List<AudioRecordingConfiguration> externalRecordings(Context c){
  ArrayList<AudioRecordingConfiguration> out=new ArrayList<>();
  for(AudioRecordingConfiguration r:c.getSystemService(AudioManager.class).getActiveRecordingConfigurations())
   if(r.getClientUid()>=10000 && r.getClientUid()!=Process.myUid() && r.getClientAudioSource()!=MediaRecorder.AudioSource.REMOTE_SUBMIX)out.add(r);
  return out;
 }
 static boolean recording(Context c,int uid){for(AudioRecordingConfiguration r:externalRecordings(c))if(r.getClientUid()==uid)return true;return false;}
 static boolean playing(Context c,int uid){for(AudioPlaybackConfiguration p:c.getSystemService(AudioManager.class).getActivePlaybackConfigurations())if(p.getClientUid()==uid&&p.isActive())return true;return false;}
 static boolean retainedPlayer(Context c,int uid,Set<Integer> ids,boolean enroll){
  // With MODIFY_AUDIO_ROUTING, this API includes paused players, not just active ones.
  boolean retained=false;
  for(AudioPlaybackConfiguration p:c.getSystemService(AudioManager.class).getActivePlaybackConfigurations())if(p.getClientUid()==uid){
   if(enroll&&p.isActive())ids.add(p.getPlayerInterfaceId());
   if(ids.contains(p.getPlayerInterfaceId())&&(p.isActive()||p.getPlayerState()==AudioPlaybackConfiguration.PLAYER_STATE_PAUSED))retained=true;
  }return retained;
 }
 AudioRecord openPlayback(Context context,int uid)throws IOException{
  // Voice usage must be explicit: a UID-only rule deliberately excludes voice communication.
  AudioMixingRule.Builder rule=new AudioMixingRule.Builder().addMixRule(AudioMixingRule.RULE_MATCH_UID,Integer.valueOf(uid));
  for(int usage:new int[]{AudioAttributes.USAGE_VOICE_COMMUNICATION,AudioAttributes.USAGE_MEDIA,AudioAttributes.USAGE_UNKNOWN})
   rule.addRule(new AudioAttributes.Builder().setUsage(usage).build(),AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE);
  rule.allowPrivilegedPlaybackCapture(true);
  AudioFormat format=new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
  AudioMix mix=new AudioMix.Builder(rule.build()).setFormat(format).setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK|AudioMix.ROUTE_FLAG_RENDER).build();
  policy=new AudioPolicy.Builder(context).addMix(mix).build();
  if(manager.registerAudioPolicy(policy)!=AudioManager.SUCCESS){close();throw new IOException("系统拒绝会议播放音频旁路");}
  AudioRecord record=policy.createAudioRecordSink(mix);
  if(record==null){close();throw new IOException("设备没有提供会议播放音频");}
  return record;
 }
 public void close(){if(policy!=null){manager.unregisterAudioPolicy(policy);policy=null;}}
}
