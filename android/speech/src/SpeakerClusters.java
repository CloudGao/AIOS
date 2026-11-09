package os.aios.speech;
import java.util.*;
/** Session-local acoustic identities, never names. Conservative ambiguous labels. */
final class SpeakerClusters {
 final android.content.Context context;final String unknown;
 SpeakerClusters(android.content.Context c){context=c;unknown=c.getString(c.getResources().getIdentifier("optional_model_unknown_speaker","string",c.getPackageName()));}
 String label(int n){return context.getString(context.getResources().getIdentifier("optional_model_speaker_number","string",context.getPackageName()),n);}
 final ArrayList<float[]> centers=new ArrayList<>();
 String identify(float[] vector){
  double norm=0;for(float x:vector){if(!Float.isFinite(x))return unknown;norm+=x*x;}if(norm<1e-9)return unknown;
  float[] v=vector.clone();for(int i=0;i<v.length;i++)v[i]/=(float)Math.sqrt(norm);
  int best=-1;double score=-1,second=-1;
  for(int j=0;j<centers.size();j++){float[] c=centers.get(j);double s=0;for(int i=0;i<v.length;i++)s+=v[i]*c[i];if(s>score){second=score;score=s;best=j;}else second=Math.max(second,s);}
  if(best>=0&&score>=.58){if(score-second<.08)return unknown;return label(best+1);}
  if(score>=.40||centers.size()>=8)return unknown;
  centers.add(v);return label(centers.size());
 }
}
