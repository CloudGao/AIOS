package os.aios.search.probe;
import android.app.Instrumentation;import android.os.Bundle;import java.io.*;import java.nio.*;import java.nio.file.*;import org.json.*;import os.aios.core.embedding.LiteRtGpuBackend;
public final class GpuProbe extends Instrumentation {
 private String[] roles; public void onCreate(Bundle b){super.onCreate(b);roles=b.getString("roles","image,text,face").split(",");start();}
 public void onStart(){JSONArray results=new JSONArray();File root=new File(getTargetContext().getFilesDir(),"search-models");int failed=0;
  for(String role:roles){JSONObject row=new JSONObject();try{row.put("role",role);float[] input=null;int[] tokens=null;
   if(role.equals("text")){ByteBuffer b=ByteBuffer.wrap(Files.readAllBytes(new File(root,"text-input.bin").toPath())).order(ByteOrder.LITTLE_ENDIAN);tokens=new int[b.remaining()/4];for(int i=0;i<tokens.length;i++)tokens[i]=b.getInt();}
   else{FloatBuffer b=ByteBuffer.wrap(Files.readAllBytes(new File(root,role+"-input.bin").toPath())).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();input=new float[b.remaining()];b.get(input);}
   long start=System.nanoTime();try(LiteRtGpuBackend backend=new LiteRtGpuBackend(getTargetContext(),new File(root,role+".tflite").getPath())){
    row.put("load_ms",(System.nanoTime()-start)/1e6);start=System.nanoTime();float[][] output=backend.run(input,tokens);row.put("first_ms",(System.nanoTime()-start)/1e6);JSONArray shape=new JSONArray();for(float[] o:output)shape.put(o.length);row.put("outputs",shape);
    if(!role.equals("face")){FloatBuffer b=ByteBuffer.wrap(Files.readAllBytes(new File(root,role+"-reference.bin").toPath())).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();double dot=0,norm=0,ref=0;for(float f:output[0]){float g=b.get();dot+=f*g;norm+=f*f;ref+=g*g;}double cosine=dot/Math.sqrt(norm*ref);row.put("cosine",cosine);if(cosine<.99)throw new AssertionError("GPU numerical mismatch");}
    double[] times=new double[10];for(int i=0;i<times.length;i++){start=System.nanoTime();backend.run(input,tokens);times[i]=(System.nanoTime()-start)/1e6;}java.util.Arrays.sort(times);row.put("warm_p50_ms",times[5]).put("warm_p95_ms",times[9]);
   }row.put("state","request_succeeded_backend_evidence_required");
  }catch(Throwable e){failed++;try{row.put("state","failed").put("error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}results.put(row);android.util.Log.i("AIOSGpuProbe",row.toString());}
  try{JSONObject report=new JSONObject().put("failed",failed).put("results",results);Files.writeString(new File(getTargetContext().getFilesDir(),"probe.json").toPath(),report.toString(2));Bundle result=new Bundle();result.putString("report",report.toString());finish(failed==0?-1:1,result);}catch(Exception e){finish(1,new Bundle());}
 }
}
