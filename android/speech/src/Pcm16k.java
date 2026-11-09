package os.aios.speech;
import java.util.Arrays;
/** Streaming 48k -> 16k low-pass decimator; carries filter state across AudioRecord reads. */
final class Pcm16k {
 private final int rate;private final float[] history=new float[63],kernel=new float[63];private int cursor,phase;
 Pcm16k(int rate){if(rate!=16000&&rate!=48000)throw new IllegalArgumentException("Unsupported input rate "+rate);this.rate=rate;double sum=0;for(int i=0;i<63;i++){double x=i-31,fc=7000.0/48000;kernel[i]=(float)((x==0?2*fc:Math.sin(2*Math.PI*fc*x)/(Math.PI*x))*(.54-.46*Math.cos(2*Math.PI*i/62)));sum+=kernel[i];}for(int i=0;i<63;i++)kernel[i]/=sum;}
 float[] accept(short[] pcm,int n){float[] out=new float[rate==16000?n:(n+2)/3];int used=0;for(int i=0;i<n;i++){float v=pcm[i]/32768f;if(rate==16000){out[used++]=v;continue;}history[cursor]=v;cursor=(cursor+1)%63;if(++phase==3){phase=0;float sum=0;int j=cursor;for(int k=0;k<63;k++){j=(j+62)%63;sum+=kernel[k]*history[j];}out[used++]=Math.max(-1f,Math.min(32767f/32768f,sum));}}return used==out.length?out:Arrays.copyOf(out,used);}
}
