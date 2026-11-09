package os.aios.agent;
import android.graphics.Path;
import android.accessibilityservice.GestureDescription;
import org.json.*;
import java.util.*;

/** Strict, bounded gesture contract shared by foreground and virtual-display input. */
final class UiGestureSpec {
 static final int MAX_POINTERS=3,MAX_POINTS=32,MIN_DURATION=100,MAX_DURATION=5000,MAX_HOLD=3000;
 final int[] ids,counts,xs,ys;final int holdMs,durationMs;
 private UiGestureSpec(int[] i,int[] c,int[] x,int[] y,int h,int d){ids=i;counts=c;xs=x;ys=y;holdMs=h;durationMs=d;}
 static UiGestureSpec parse(JSONObject q,int width,int height)throws Exception{
  Set<String> allowed=Set.of("op","session_id","observation_id","observation_token","observation","pointers","hold_ms","duration_ms");
  for(Iterator<String> it=q.keys();it.hasNext();){String key=it.next();if(!allowed.contains(key))throw new IllegalArgumentException("Unknown gesture field: "+key);}
  JSONArray p=q.getJSONArray("pointers");if(p.length()<1||p.length()>MAX_POINTERS)throw new IllegalArgumentException("Gesture requires 1-3 pointers");
  int hold=q.optInt("hold_ms",0),duration=q.getInt("duration_ms");if(hold<0||hold>MAX_HOLD||duration<MIN_DURATION||duration>MAX_DURATION)throw new IllegalArgumentException("Gesture timing outside supported range");
  int total=0;Set<Integer> unique=new HashSet<>();for(int i=0;i<p.length();i++){JSONObject pointer=p.getJSONObject(i);for(Iterator<String> it=pointer.keys();it.hasNext();)if(!Set.of("id","path").contains(it.next()))throw new IllegalArgumentException("Unknown pointer field");int id=pointer.getInt("id");if(id<0||id>31||!unique.add(id))throw new IllegalArgumentException("Pointer ids must be unique 0-31");JSONArray path=pointer.getJSONArray("path");if(path.length()<2||path.length()>MAX_POINTS)throw new IllegalArgumentException("Each pointer path requires 2-32 points");total+=path.length();}
  int[] ids=new int[p.length()],counts=new int[p.length()],xs=new int[total],ys=new int[total];int at=0;
  for(int i=0;i<p.length();i++){JSONObject pointer=p.getJSONObject(i);ids[i]=pointer.getInt("id");JSONArray path=pointer.getJSONArray("path");counts[i]=path.length();for(int j=0;j<path.length();j++){JSONArray point=path.getJSONArray(j);if(point.length()!=2)throw new IllegalArgumentException("Gesture points are [x,y]");int x=point.getInt(0),y=point.getInt(1);if(x<0||y<0||x>=width||y>=height)throw new IllegalArgumentException("Gesture point outside display");xs[at]=x;ys[at++]=y;}}
  return new UiGestureSpec(ids,counts,xs,ys,hold,duration);
 }
 GestureDescription foregroundDirect(int displayId){GestureDescription.Builder b=new GestureDescription.Builder().setDisplayId(displayId);int at=0;for(int i=0;i<ids.length;i++){Path p=path(at,i);at+=counts[i];b.addStroke(new GestureDescription.StrokeDescription(p,0,durationMs));}return b.build();}
 GestureDescription.StrokeDescription[] foregroundHold(){GestureDescription.StrokeDescription[] out=new GestureDescription.StrokeDescription[ids.length];int at=0;for(int i=0;i<ids.length;i++){Path p=new Path();p.moveTo(xs[at],ys[at]);out[i]=new GestureDescription.StrokeDescription(p,0,holdMs,true);at+=counts[i];}return out;}
 GestureDescription foregroundContinue(int displayId,GestureDescription.StrokeDescription[] prior){GestureDescription.Builder b=new GestureDescription.Builder().setDisplayId(displayId);int at=0;for(int i=0;i<ids.length;i++){Path p=path(at,i);at+=counts[i];b.addStroke(prior[i].continueStroke(p,0,durationMs,false));}return b.build();}
 GestureDescription foregroundRelease(int displayId,GestureDescription.StrokeDescription[] prior){GestureDescription.Builder b=new GestureDescription.Builder().setDisplayId(displayId);int at=0;for(int i=0;i<ids.length;i++){Path p=new Path();p.moveTo(xs[at],ys[at]);b.addStroke(prior[i].continueStroke(p,0,1,false));at+=counts[i];}return b.build();}
 private Path path(int at,int pointer){Path p=new Path();p.moveTo(xs[at],ys[at]);for(int j=1;j<counts[pointer];j++)p.lineTo(xs[at+j],ys[at+j]);return p;}
}
