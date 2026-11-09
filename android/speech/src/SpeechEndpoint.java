package os.aios.speech;
/** Audio-time endpointing: no elapsed-time or utterance-length cutoff. */
final class SpeechEndpoint {
 final int rate;long total,voiced,silence;boolean heard;
 SpeechEndpoint(int sampleRate){rate=sampleRate;}
 boolean accept(boolean speech,int samples){
  total+=samples;
  if(speech){voiced+=samples;silence=0;if(voiced>=rate/5)heard=true;}
  else {silence+=samples;if(!heard)voiced=0;}
  return heard ? silence>=rate*3L/2 : total>=rate*7L;
 }
}
