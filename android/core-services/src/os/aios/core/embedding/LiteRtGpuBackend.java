package os.aios.core.embedding;

import android.content.Context;
import com.google.ai.edge.litert.*;
import java.util.*;

/** Created/run/closed on a single inference thread. No CPU fallback configuration. */
public final class LiteRtGpuBackend implements AutoCloseable {
    private java.nio.ShortBuffer embeddingTable;
    private final Environment environment;private final CompiledModel model;
    private final List<TensorBuffer> inputs,outputs;private final long thread;
    public LiteRtGpuBackend(Context context,String path)throws Exception{
        thread=Thread.currentThread().getId();
        java.io.File table=new java.io.File(new java.io.File(path).getParentFile(),"token-embeddings.fp16");
        if(path.endsWith("/text.tflite")&&table.isFile()){
            if(table.length()!=49408L*512*2)throw new IllegalStateException("embedding_table_size");
            org.json.JSONObject manifest=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(new java.io.File(table.getParentFile(),"manifest.json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
            String expected=manifest.getJSONObject("token_embeddings").getString("sha256");if(!expected.equals("735c4bb3d104ff84416a33b494905f1364caf9f7118b0b06f4097ac7f3686b40"))throw new SecurityException("embedding_table_not_approved");java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            try(java.io.FileInputStream in=new java.io.FileInputStream(table)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!expected.contentEquals(hash))throw new SecurityException("embedding_table_hash");embeddingTable=in.getChannel().map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,table.length()).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer();}
        }
        environment=Environment.create(context);
        if(!environment.getAvailableAccelerators().contains(Accelerator.GPU)){environment.close();throw new IllegalStateException("gpu_unavailable");}
        CompiledModel.Options options=new CompiledModel.Options(Accelerator.GPU);
        if(path.endsWith("/text.tflite"))options.setGpuOptions(new CompiledModel.GpuOptions(null,null,null,CompiledModel.GpuOptions.Precision.FP32,null,null,null,null,null,null,null,null,null,null,null));
        CompiledModel created=null;try{created=CompiledModel.create(path,options,environment);model=created;inputs=model.createInputBuffers();outputs=model.createOutputBuffers();}
        catch(Exception e){if(created!=null)created.close();environment.close();throw e;}
    }
    public float[][] run(float[] image,int[] tokens)throws Exception{check();if(tokens!=null){
 if(inputs.size()!=1&&inputs.size()!=2)throw new IllegalStateException("text_input_count");if(embeddingTable==null)inputs.get(0).writeInt(tokens);else{if(tokens.length!=77)throw new IllegalArgumentException("text_token_count");float[] embedded=new float[77*512];for(int i=0;i<tokens.length;i++){int token=tokens[i];if(token<0||token>=49408)throw new IllegalArgumentException("text_token_range");for(int d=0;d<512;d++)embedded[i*512+d]=android.util.Half.toFloat(embeddingTable.get(token*512+d));}inputs.get(0).writeFloat(embedded);}
 if(inputs.size()==2){float[] mask=new float[tokens.length];int count=0;for(int i=0;i<tokens.length;i++)if(tokens[i]==49407){mask[i]=1;count++;}if(count!=1)throw new IllegalArgumentException("text_eot_count");inputs.get(1).writeFloat(mask);}
 }else{if(inputs.size()!=1)throw new IllegalStateException("image_input_count");inputs.get(0).writeFloat(image);}model.run(inputs,outputs);float[][] result=new float[outputs.size()][];for(int i=0;i<result.length;i++)result[i]=outputs.get(i).readFloat();return result;}
    private void check(){if(thread!=Thread.currentThread().getId())throw new IllegalStateException("GPU thread affinity violated");}
    public void close(){check();for(TensorBuffer b:inputs)b.close();for(TensorBuffer b:outputs)b.close();model.close();environment.close();embeddingTable=null;}
}
