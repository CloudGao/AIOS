package os.aios.core.embedding;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.json.JSONObject;

/** Seed a fresh private model directory from verified immutable system inputs.
 * New paired profiles are committed by pointer; old vectors and profile files remain available.
 */
public final class BundledModels {
    private static boolean started;
    public static synchronized void initialize(Context context){
        if(started)return;started=true;Context app=context.getApplicationContext();
        new Thread(()->{try{install(app);app.startService(new android.content.Intent(app,DeviceModelValidation.class));}catch(Exception e){android.util.Log.w("AIOSModels","Bundled model initialization failed",e);}finally{synchronized(BundledModels.class){started=false;}}},"aios-model-init").start();
    }
    static void install(Context context)throws Exception{
        File source=os.aios.host.HostBindings.bundledSearchModels();
        if(!new File(source,"manifest.json").isFile())return;
        JSONObject manifest=new JSONObject(os.aios.core.CompatFiles.readString(new File(source,"manifest.json").toPath()));
        String space=manifest.getString("space_id");if(!space.matches("[a-f0-9]{64}"))throw new SecurityException("model_space_invalid");
        File base=new File(context.getFilesDir(),"search-models");
        File target=new File(base,"profiles/"+space);
        if(!target.mkdirs()&&!target.isDirectory())throw new java.io.IOException("model_directory_unavailable");
        for(String role:new String[]{"image","text","face","token_embeddings"}){
            if(role.equals("token_embeddings")&&!manifest.has(role))continue;JSONObject spec=manifest.getJSONObject(role);String name=spec.getString("file");
            if(!name.matches("[A-Za-z0-9_.-]+"))throw new SecurityException("model_path_invalid");
            if(role.equals("face")&&!new File(source,name).isFile())continue;
            copy(new File(source,name),new File(target,name),spec.getString("sha256"));
        }
        copy(new File(source,"tokenizer.json"),new File(target,"tokenizer.json"),manifest.getString("tokenizer_sha256"));
        copy(new File(source,"LICENSE"),new File(target,"LICENSE"),ModelRegistry.sha256(new File(source,"LICENSE")));
        // Commit last; interruption is recoverable by re-running initialization.
        copy(new File(source,"manifest.json"),new File(target,"manifest.json"),ModelRegistry.sha256(new File(source,"manifest.json")));
        File pointer=new File(base,"active-profile.json"),temp=new File(base,"active-profile.next");
        os.aios.core.CompatFiles.writeString(temp.toPath(),new JSONObject().put("space_id",space).toString());
        Files.move(temp.toPath(),pointer.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        // Never manufacture validated.json: its full-GPU evidence must be collected on this ROM.
    }
    static void copy(File source,File target,String expected)throws Exception{
        if(!ModelRegistry.sha256(source).equals(expected))throw new SecurityException("bundled_model_hash_mismatch");
        if(target.isFile()&&ModelRegistry.sha256(target).equals(expected))return;
        File temporary=new File(target.getPath()+".bundled.tmp");
        Files.copy(source.toPath(),temporary.toPath(),StandardCopyOption.REPLACE_EXISTING);
        if(!ModelRegistry.sha256(temporary).equals(expected))throw new SecurityException("model_copy_hash_mismatch");
        Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
}
