package os.aios.speech;
public class SpeechEndpointTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  SpeechEndpoint e=new SpeechEndpoint(16000);
  for(int i=0;i<18750;i++)check(!e.accept(true,512)); // Ten minutes continuous speech.
  for(int i=0;i<30;i++)check(!e.accept(false,512)); // 960 ms pause.
  check(!e.accept(true,512));
  for(int i=0;i<46;i++)check(!e.accept(false,512));
  check(e.accept(false,512));
  SpeechEndpoint quiet=new SpeechEndpoint(16000);
  for(int i=0;i<218;i++)check(!quiet.accept(false,512));check(quiet.accept(false,512));
  SpeechEndpoint noise=new SpeechEndpoint(16000);
  for(int i=0;i<5;i++)check(!noise.accept(true,512));check(!noise.heard);
  noise.accept(false,512);check(noise.voiced==0);
  System.out.println("PASS: 10 minute speech, short pause, resumed speech, end silence, no speech, noise burst");
 }
}
