package os.aios.core.embedding;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Hash-pinned paired model registry. Validation is tied to the installed ROM and model space. */
public final class ModelRegistry {
    private final File root,base;
    private final File optionalFace;private final File disabled;
    private static final java.util.concurrent.ConcurrentHashMap<String,String> hashes=new java.util.concurrent.ConcurrentHashMap<>();
    public ModelRegistry(Context c){base=new File(c.getFilesDir(),"search-models");disabled=new File(c.getFilesDir(),"photo-model.disabled");optionalFace=os.aios.runtime.models.OptionalModelStore.file(c,"face");root=current(base);}
    private static File current(File base){
        File pointer=new File(base,"active-profile.json");if(!pointer.isFile())return base;
        try{if(pointer.length()>256)throw new IOException("profile_pointer_size");String id=new JSONObject(os.aios.core.CompatFiles.readString(pointer.toPath())).getString("space_id");if(!id.matches("[a-f0-9]{64}"))throw new IOException("profile_pointer_id");return new File(base,"profiles/"+id);}catch(Exception error){throw new IllegalStateException("model_profile_pointer_invalid",error);}
    }
    public ModelRegistry(Context c,String space)throws Exception{base=new File(c.getFilesDir(),"search-models");disabled=new File(c.getFilesDir(),"photo-model.disabled");optionalFace=os.aios.runtime.models.OptionalModelStore.file(c,"face");if(space==null){root=current(base);return;}if(!space.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid model space");File pinned=new File(base,"profiles/"+space);root=new File(pinned,"manifest.json").isFile()?pinned:base;if(!manifest().getString("space_id").equals(space))throw new IllegalStateException("model_space_unavailable");}
    public File directory(){return root;}
    /** Copy exact validated inputs before a replacement can be staged at the mutable root. */
    public synchronized void pinCurrent()throws Exception{JSONObject m=manifest();String space=m.getString("space_id");if(!space.matches("[a-f0-9]{64}"))throw new IllegalStateException("Invalid model space");File target=new File(base,"profiles/"+space);if(!target.mkdirs()&&!target.isDirectory())throw new IOException("Profile directory unavailable");for(String role:List.of("image","text","face","token_embeddings")){if(role.equals("token_embeddings")&&!m.has(role))continue;if(role.equals("face")&&!faceAvailable())continue;File source=model(role),dest=new File(target,source.getName());if(!dest.isFile()||!verifiedHash(dest).equals(m.getJSONObject(role).getString("sha256")))Files.copy(source.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
        File tokenizer=new File(root,"tokenizer.json");if(!verifiedHash(tokenizer).equals(m.getString("tokenizer_sha256")))throw new IllegalStateException("tokenizer_hash_mismatch");File pinnedTokenizer=new File(target,"tokenizer.json");if(!pinnedTokenizer.isFile()||!verifiedHash(pinnedTokenizer).equals(m.getString("tokenizer_sha256")))Files.copy(tokenizer.toPath(),pinnedTokenizer.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        File evidence=new File(root,"validated.json"),saved=new File(target,"validated.json");if(!saved.isFile()||!verifiedHash(saved).equals(verifiedHash(evidence))){File temp=new File(target,"validated.tmp");Files.copy(evidence.toPath(),temp.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);Files.move(temp.toPath(),saved.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}File manifestFile=new File(target,"manifest.json");if(!manifestFile.isFile()){File temp=new File(target,"manifest.tmp");os.aios.core.CompatFiles.writeString(temp.toPath(),m.toString());Files.move(temp.toPath(),manifestFile.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE);}}
    public JSONObject manifest()throws Exception {if(disabled.exists())throw new IllegalStateException("model_disabled");File f=new File(root,"manifest.json");if(!f.isFile()||f.length()>65536)throw new IllegalStateException("model_missing");return new JSONObject(os.aios.core.CompatFiles.readString(f.toPath()));}
    public File model(String role)throws Exception{
        if(!Set.of("image","text","face","probe","token_embeddings").contains(role))throw new IllegalArgumentException("Unknown model role");
        JSONObject manifest=manifest();if(role.equals("text")&&manifest.has("token_embeddings"))model("token_embeddings");JSONObject spec=manifest.getJSONObject(role);String filename=spec.getString("file");if(!filename.matches("[A-Za-z0-9_.-]+"))throw new SecurityException("Unsafe model path");
        File f=new File(root,filename);if(role.equals("face")&&!f.isFile())f=optionalFace;if(!f.isFile()||!verifiedHash(f).equals(spec.getString("sha256")))throw new IllegalStateException("model_hash_mismatch");return f;
    }
    public boolean faceAvailable(){try{return model("face").isFile();}catch(Exception error){return false;}}
    public static boolean matchesRom(JSONObject evidence){return evidence.optString("fingerprint").equals(Build.FINGERPRINT)&&!evidence.optString("system_incremental").isEmpty()&&evidence.optString("system_incremental").equals(os.aios.host.HostIdentity.systemIncremental())&&evidence.optString("vendor_fingerprint").equals(os.aios.host.HostIdentity.vendorFingerprint());}
    public boolean validated(String role)throws Exception{
        if(role.equals("face")&&!faceAvailable())return false;JSONObject manifest=manifest();File f=new File(root,"validated.json");if(!f.isFile()||f.length()>65536)return false;
        JSONObject evidence=new JSONObject(os.aios.core.CompatFiles.readString(f.toPath()));JSONObject entry=evidence.optJSONObject(role);
        return matchesRom(evidence)&&evidence.optString("space_id").equals(manifest.getString("space_id"))&&entry!=null&&entry.optBoolean("full_gpu")&&entry.optString("model_sha256").equals(manifest.getJSONObject(role).getString("sha256"))&&!entry.optString("evidence_sha256").isBlank()&&model(role).isFile();
    }
    public JSONObject status(){try{JSONObject m=manifest();boolean ready=validated("image")&&validated("text");return new JSONObject().put("state",ready?"ready":"backend_unverified").put("space_id",m.getString("space_id")).put("image_verified",validated("image")).put("text_verified",validated("text")).put("face_verified",validated("face")).put("face_state",!faceAvailable()?"model_missing":validated("face")?"ready":"backend_unverified");}catch(Exception e){try{return new JSONObject().put("state",disabled.exists()?"model_disabled":"model_missing").put("reason",e.getMessage());}catch(Exception impossible){throw new IllegalStateException(impossible);}}}
    private static String verifiedHash(File f)throws Exception{String key=f.getPath()+":"+f.length()+":"+f.lastModified();String cached=hashes.get(key);if(cached!=null)return cached;String hash=sha256(f);if(hashes.size()>12)hashes.clear();hashes.put(key,hash);return hash;}
    public static String sha256(File file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)d.update(bytes,0,n);}StringBuilder out=new StringBuilder();for(byte b:d.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
