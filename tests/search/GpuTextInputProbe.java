package os.aios.core.embedding;
import android.app.Instrumentation;import android.os.Bundle;
public final class GpuTextInputProbe extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  ModelRegistry registry=new ModelRegistry(getTargetContext());int[] tokens=new ClipTokenizer(getTargetContext()).encode("a photo");
  int count=0;try(LiteRtGpuBackend backend=new LiteRtGpuBackend(getTargetContext(),registry.model("text").getPath())){for(float[] tensor:backend.run(null,tokens))for(float v:tensor){if(!Float.isFinite(v))throw new AssertionError("nonfinite with valid tokenizer input");count++;}}
  if(count==0)throw new AssertionError("empty output");out.putString("result","PASS finite text GPU output: "+count);finish(-1,out);
 }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));finish(0,out);}}
}
