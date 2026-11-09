package os.aios.agent;

import android.app.Service;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.*;
import os.aios.runtime.*;

/** System Data Adapter: only Core can open authorized sources or commit a new output. */
public final class DocumentStorageService extends Service {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()){
        public void handleMessage(Message m){final Messenger reply=m.replyTo;final Bundle b=m.getData();try{
            if(reply==null)return;
            if(m.sendingUid!=getPackageManager().getApplicationInfo("os.aios.core",0).uid||getPackageManager().checkSignatures(m.sendingUid,android.os.Process.myUid())!=0)throw new SecurityException("core_identity_required");
            worker.execute(()->{try(ParcelFileDescriptor input=b.getParcelable("input",ParcelFileDescriptor.class)){
                JSONObject p=new JSONObject(b.getString("json","{}"));String op=b.getString("operation");
                // Recheck the global switch in the adapter as well as in Core.
                if(!new CoreRuntimeClient(DocumentStorageService.this).call("policy","get",new JSONObject()).getBoolean("enabled"))throw new SecurityException("authorization_revoked");
                if(op.equals("open")){try(ParcelFileDescriptor fd=open(p.getString("source"))){DocumentRpc.reply(reply,new JSONObject(),fd,null);}}
                else if(op.equals("commit"))DocumentRpc.reply(reply,commit(p,input),null,null);
                else if(op.equals("verify")){try(ParcelFileDescriptor fd=open(p.getString("source"))){DocumentRpc.reply(reply,new JSONObject().put("sha256",hash(new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor())))),null,null);}}
                else throw new IllegalArgumentException("operation_unsupported");
            }catch(Exception e){DocumentRpc.reply(reply,null,null,e instanceof SecurityException?"permission_required":e.getMessage()==null?"storage_failed":e.getMessage());}});
        }catch(Exception e){if(reply!=null)DocumentRpc.reply(reply,null,null,"permission_required");}}
    });
    private File shared(String path)throws Exception{
        if(!Environment.isExternalStorageManager())throw new SecurityException("permission_required");
        File root=Environment.getExternalStorageDirectory().getCanonicalFile(),f=new File(path).getCanonicalFile();
        if(!f.getPath().startsWith(root.getPath()+"/")||f.getPath().startsWith(root+"/Android/"))throw new SecurityException("scope_denied");return f;
    }
    private ParcelFileDescriptor open(String source)throws Exception{
        if(source.startsWith("/"))return seekable(ParcelFileDescriptor.open(shared(source),ParcelFileDescriptor.MODE_READ_ONLY));
        Uri u=Uri.parse(source);if(!u.getScheme().equals("content")||u.getAuthority().equals("os.aios.core.runtime"))throw new SecurityException("scope_denied");
        // Framework enforces current URI grants and media permissions; no grant is synthesized.
        ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(u,"r");if(fd==null)throw new IOException("source_unavailable");return seekable(fd);
    }
    private ParcelFileDescriptor seekable(ParcelFileDescriptor fd)throws Exception{try{android.system.Os.lseek(fd.getFileDescriptor(),0,android.system.OsConstants.SEEK_CUR);long size=fd.getStatSize();if(size<0||size>DocumentContract.MAX_BYTES)throw new IOException("source_size_unsupported");return fd;}catch(Exception e){fd.close();throw new IOException("source_not_seekable_or_too_large",e);}}
    private JSONObject commit(JSONObject p,ParcelFileDescriptor input)throws Exception{
        if(input==null)throw new IllegalArgumentException("missing_output");File target=shared(p.getString("target"));
        if(!target.getParentFile().isDirectory())throw new IOException("directory_missing");
        if(target.exists())throw new IOException("target_exists");
        long size=p.getLong("bytes");if(size<0||size>DocumentContract.MAX_BYTES||target.getParentFile().getUsableSpace()<size+1048576)throw new IOException("space_or_size_limit");
        // CREATE_NEW never truncates a concurrently created file. Publish only after complete copy and verification.
        File temporary=new File(target.getParentFile(),".aios-document-"+java.util.UUID.randomUUID());
        try{
            try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(input.getFileDescriptor()));OutputStream out=Files.newOutputStream(temporary.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){copy(in,out);}
            String digest=hash(new FileInputStream(temporary));if(!digest.equals(p.getString("sha256")))throw new IOException("verification_failed");
            // Hard-link publication is atomic and fails if the target exists; no rename-replace race.
            try{android.system.Os.link(temporary.getPath(),target.getPath());}
            catch(android.system.ErrnoException unsupported){
                // Emulated storage may forbid links. CREATE_NEW preserves the no-overwrite guarantee,
                // but the new file is visible during copy. Report this explicitly.
                if(target.exists())throw new IOException("target_exists");
                try(InputStream in=new FileInputStream(temporary);OutputStream out=Files.newOutputStream(target.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){copy(in,out);}
                if(!hash(new FileInputStream(target)).equals(digest))throw new IOException("outcome_unknown");
                android.media.MediaScannerConnection.scanFile(this,new String[]{target.getPath()},null,null);
                return new JSONObject().put("source",target.getPath()).put("sha256",digest).put("atomic_publication",false).put("readback_verified",true);
            }
            if(!hash(new FileInputStream(target)).equals(digest))throw new IOException("outcome_unknown");
            android.media.MediaScannerConnection.scanFile(this,new String[]{target.getPath()},null,null);
            return new JSONObject().put("source",target.getPath()).put("sha256",digest).put("atomic_publication",true).put("readback_verified",true);
        }finally{temporary.delete();}
    }
    private static void copy(InputStream in,OutputStream out)throws Exception{byte[] b=new byte[32768];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>DocumentContract.MAX_BYTES)throw new IOException("source_too_large");out.write(b,0,n);}out.flush();}
    private static String hash(InputStream source)throws Exception{try(InputStream in=source){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[32768];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>DocumentContract.MAX_BYTES)throw new IOException("source_too_large");d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte v:d.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}}
    public IBinder onBind(Intent intent){return endpoint.getBinder();}
    public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
