package os.aios.core.indexing;
import org.json.*;
import java.util.*;

/** BlazeFace full-range sparse 192x192 / 48x48 anchors, output 16 coords and one logit. */
public final class FaceDecoder {
    public static JSONArray decode(float[] boxes,float[] logits,int width,int height)throws Exception{
        if(boxes.length!=2304*16||logits.length!=2304||width<=0||height<=0)throw new IllegalArgumentException("Face model output contract mismatch");
        ArrayList<float[]> candidates=new ArrayList<>();float scale=Math.min(192f/width,192f/height),padX=(192-width*scale)/2,padY=(192-height*scale)/2;
        for(int i=0;i<2304;i++){
            float score=(float)(1/(1+Math.exp(-Math.max(-100,Math.min(100,logits[i])))));if(!Float.isFinite(score)||score<0.6f)continue;
            float cx=(i%48+.5f)*4+boxes[i*16],cy=(i/48+.5f)*4+boxes[i*16+1],w=boxes[i*16+2],h=boxes[i*16+3];
            float l=Math.max(0,(cx-w/2-padX)/scale),t=Math.max(0,(cy-h/2-padY)/scale),r=Math.min(width,(cx+w/2-padX)/scale),b=Math.min(height,(cy+h/2-padY)/scale);
            if(!Float.isFinite(l+t+r+b)||r<=l||b<=t)continue;candidates.add(new float[]{l,t,r,b,score,i});
        }
        candidates.sort((a,b)->Float.compare(b[4],a[4]));ArrayList<float[]> kept=new ArrayList<>();JSONArray result=new JSONArray();
        for(float[] box:candidates){boolean overlap=false;for(float[] old:kept)if(iou(box,old)>.3f){overlap=true;break;}if(overlap)continue;kept.add(box);
            JSONObject face=new JSONObject().put("left",box[0]).put("top",box[1]).put("right",box[2]).put("bottom",box[3]).put("score",box[4]);JSONArray points=new JSONArray();int index=(int)box[5];
            for(int k=0;k<6;k++){float x=((index%48+.5f)*4+boxes[index*16+4+k*2]-padX)/scale,y=((index/48+.5f)*4+boxes[index*16+5+k*2]-padY)/scale;points.put(new JSONObject().put("x",x).put("y",y));}face.put("keypoints",points);result.put(face);
        }return result;
    }
    private static float iou(float[] a,float[] b){float intersection=Math.max(0,Math.min(a[2],b[2])-Math.max(a[0],b[0]))*Math.max(0,Math.min(a[3],b[3])-Math.max(a[1],b[1]));return intersection/((a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-intersection);}
}
