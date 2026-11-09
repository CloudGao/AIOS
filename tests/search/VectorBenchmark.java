package os.aios.core;
import android.app.Instrumentation;import android.os.*;import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;import os.aios.core.indexing.VectorIndex;
/** Synthetic vector scale benchmark, isolated from all photo index spaces. */
public final class VectorBenchmark extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){JSONObject result=new JSONObject();String space="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";File file=new File(getTargetContext().getFilesDir(),"photo-vectors/"+space+".db");try(VectorIndex index=new VectorIndex(getTargetContext(),space)){
  Random random=new Random(4817);float[] vector=new float[512],query=null;long start=System.nanoTime();JSONArray sizes=new JSONArray();
  for(int n=1;n<=50000;n++){double norm=0;for(int k=0;k<512;k++){vector[k]=random.nextFloat()-.5f;norm+=vector[k]*vector[k];}for(int k=0;k<512;k++)vector[k]/=(float)Math.sqrt(norm);index.put(n,vector);if(n==1)query=vector.clone();if(n==5000||n==50000){double[] times=new double[30];for(int k=0;k<30;k++){long before=System.nanoTime();JSONArray hits=index.nearest(query,20);if(hits.getJSONObject(0).getLong("id")!=1)throw new AssertionError("wrong nearest");times[k]=(System.nanoTime()-before)/1e6;}Arrays.sort(times);sizes.put(new JSONObject().put("vectors",n).put("database_bytes",file.length()).put("insert_elapsed_ms",(System.nanoTime()-start)/1e6).put("knn_p50_ms",times[15]).put("knn_p95_ms",times[28]).put("memory",Files.readString(Path.of("/proc/self/status"))));}}
  result.put("state","passed").put("scales",sizes);
 }catch(Throwable e){try{result.put("state","failed").put("error",android.util.Log.getStackTraceString(e));}catch(Exception ignored){}}
 try{Files.writeString(new File(getTargetContext().getFilesDir(),"vector-benchmark.json").toPath(),result.toString(2));Bundle out=new Bundle();out.putString("state",result.optString("state"));finish(result.optString("state").equals("passed")?-1:1,out);}catch(Exception e){finish(1,new Bundle());}}
}
