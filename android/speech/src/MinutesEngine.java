package os.aios.speech;

import android.content.Context;
import com.k2fsa.sherpa.onnx.*;
import java.io.*;
import java.util.*;

/** Bounded segment processing. Capture runs independently so inference never drops input. */
final class MinutesEngine implements AutoCloseable {
 static final int RATE=16000;
 interface Output {void segment(long start,long end,String speaker,String text)throws Exception;void partial(String text);}
 final WhisperRecognizer recognizer;final SpeakerEmbeddingExtractor speaker;final Vad vad;final SpeakerClusters clusters;final String unknownSpeaker;final Output output;final String fixedSpeaker;
 final float[] window=new float[RATE*16];int used=0;long base=0,total=0,lastPreview=0;boolean active=false;
 MinutesEngine(Context c,Output out)throws IOException{this(c,out,null);}
 MinutesEngine(Context c,Output out,String fixed)throws IOException{
  output=out;fixedSpeaker=fixed;clusters=new SpeakerClusters(c);unknownSpeaker=c.getString(c.getResources().getIdentifier("optional_model_unknown_speaker","string",c.getPackageName()));File dir=new File(c.getFilesDir(),"models");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create models directory");
  for(String name:new String[]{"silero_vad.onnx"}){File dest=new File(dir,name);if(!dest.isFile()){File tmp=new File(dir,name+".minutes-part");try(InputStream in=c.getAssets().open(name);OutputStream o=new FileOutputStream(tmp)){in.transferTo(o);}if(!tmp.renameTo(dest))throw new IOException("Model install failed");}}
  recognizer=new WhisperRecognizer(c);
  File speakerFile=os.aios.runtime.models.OptionalModelStore.file(c,"speaker");
  // Existing private legacy model remains usable only with the same pinned hash.
  if(!speakerFile.isFile())speakerFile=new File(dir,"speaker.onnx");
  if(!speakerFile.isFile())try(InputStream input=c.getAssets().open("speaker.onnx")){os.aios.runtime.models.OptionalModelStore.importFile(c,"speaker",input,()->false);speakerFile=os.aios.runtime.models.OptionalModelStore.file(c,"speaker");}catch(FileNotFoundException absent){}catch(Exception unavailable){android.util.Log.w("AIOSMinutes","Optional bundled speaker model unavailable",unavailable);}
  boolean speakerReady=false;try{speakerReady=speakerFile.isFile()&&speakerFile.length()==os.aios.runtime.models.OptionalModelSpec.get("speaker").bytes&&os.aios.runtime.models.VerifiedModelFile.hash(speakerFile).equals(os.aios.runtime.models.OptionalModelSpec.get("speaker").sha256);}catch(Exception ignored){}
  speaker=fixed==null&&speakerReady?new SpeakerEmbeddingExtractor(null,new SpeakerEmbeddingExtractorConfig(speakerFile.getPath(),1,false,"cpu")):null;
  VadModelConfig vc=new VadModelConfig();vc.setSampleRate(RATE);vc.setNumThreads(1);vc.getSileroVadModelConfig().setModel(new File(dir,"silero_vad.onnx").getPath());vc.getSileroVadModelConfig().setMinSilenceDuration(.6f);vc.getSileroVadModelConfig().setMinSpeechDuration(.2f);vc.getSileroVadModelConfig().setMaxSpeechDuration(12f);vad=new Vad(null,vc);
 }
 void accept(float[] samples)throws Exception{
  vad.acceptWaveform(samples);total+=samples.length;
  if(vad.isSpeechDetected())active=true;
  if(used+samples.length<=window.length){System.arraycopy(samples,0,window,used,samples.length);used+=samples.length;}
  while(!vad.empty()){SpeechSegment s=vad.front();emit(s.getSamples(),s.getStart());vad.pop();used=0;base=total;active=false;output.partial("");}
  if(active&&total-lastPreview>=RATE*4&&used>=RATE){lastPreview=total;output.partial(decode(Arrays.copyOf(window,used)));}
  if(!active&&used>RATE){System.arraycopy(window,used-RATE/2,window,0,RATE/2);used=RATE/2;base=total-used;}
 }
 String decode(float[] audio){return recognizer.decode(audio,"auto");}
 String identify(float[] audio){if(speaker==null||audio.length<RATE)return unknownSpeaker;OnlineStream s=speaker.createStream();try{s.acceptWaveform(audio,RATE);s.inputFinished();return speaker.isReady(s)?clusters.identify(speaker.compute(s)):unknownSpeaker;}finally{s.release();}}
 void emit(float[] audio,long start)throws Exception{
  if(fixedSpeaker!=null||speaker==null){commit(audio,0,audio.length,start,fixedSpeaker!=null?fixedSpeaker:unknownSpeaker);return;}
  // Compare two-second acoustic windows and split when a confident identity changes.
  int step=RATE*2,from=0;String label=null;
  for(int pos=0;pos<audio.length;pos+=step){int end=Math.min(audio.length,pos+step);String next=identify(Arrays.copyOfRange(audio,pos,end));
   if(label==null)label=next;
   else if(!next.equals(unknownSpeaker)&&!label.equals(unknownSpeaker)&&!next.equals(label)){commit(audio,from,pos,start,label);from=pos;label=next;}
   else if(label.equals(unknownSpeaker))label=next;
  }
  commit(audio,from,audio.length,start,label==null?unknownSpeaker:label);
 }
 void commit(float[] audio,int from,int end,long start,String label)throws Exception{if(end-from<RATE/5)return;String text=decode(Arrays.copyOfRange(audio,from,end));if(!text.isBlank())output.segment((start+from)*1000/RATE,(start+end)*1000/RATE,label,text);}
 void finish()throws Exception{vad.flush();while(!vad.empty()){SpeechSegment s=vad.front();emit(s.getSamples(),s.getStart());vad.pop();}output.partial("");}
 public void close(){vad.release();if(speaker!=null)speaker.release();recognizer.close();}
}
