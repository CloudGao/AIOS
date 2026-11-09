package os.aios.core.documents;

import android.content.Context;
import android.os.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import os.aios.runtime.*;

/** Core owns document references, bounded snapshots/jobs and durable commit receipts. */
public final class DocumentCoordinator extends SQLiteOpenHelper implements os.aios.core.CoreFeatures.Documents {
    private static DocumentCoordinator instance;
    public static synchronized DocumentCoordinator get(Context c){if(instance==null)instance=new DocumentCoordinator(c);return instance;}
    public static synchronized void revokeIfPresent(){if(instance!=null)instance.revoke();}
    private static final String STORAGE="os.aios.agent/os.aios.agent.DocumentStorageService",WORKER="os.aios.core/os.aios.core.documents.DocumentWorkerService";
    private final Context context;private final File directory;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
    private final Map<String,Handle> handles=new ConcurrentHashMap<>();private final Map<String,Job> jobs=new ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore processingSlot=new java.util.concurrent.Semaphore(1,true);
    private static final class Handle {String id,owner,source,version,sourceVersion,format,name;File file;boolean artifact;long expires=System.currentTimeMillis()+900000;}
    private static final class Job {String id,owner,handle;volatile String state="queued";volatile JSONObject result;volatile Future<?> future;long expires=System.currentTimeMillis()+900000;}
    public DocumentCoordinator(Context c){super(c,"aios_document_receipts.db",null,1);context=c.getApplicationContext();directory=new File(c.getCacheDir(),"document-snapshots");directory.mkdirs();File[] stale=directory.listFiles();if(stale!=null)for(File f:stale)if(f.isFile())f.delete();setWriteAheadLoggingEnabled(true);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE receipts(id TEXT PRIMARY KEY,owner TEXT NOT NULL,fingerprint TEXT NOT NULL,state TEXT NOT NULL,target TEXT NOT NULL,sha256 TEXT NOT NULL,result TEXT NOT NULL)");}
    public void onUpgrade(SQLiteDatabase db,int old,int next){}
    private boolean authorized(){return context.getSharedPreferences("policy",0).getBoolean("enabled",true);}
    public JSONObject call(String action,JSONObject p,String owner)throws Exception{
        if(!authorized())throw new SecurityException("authorization_revoked");DocumentContract.validate(action,p);
        if(action.equals("capabilities"))return DocumentContract.success(DocumentContract.capabilities());
        if(action.equals("status"))return status(p,owner);
        if(action.equals("cancel")){Job j=job(p.getString("job_id"),owner);synchronized(j){if(!Set.of("queued","running").contains(j.state))return new JSONObject().put("state",j.state).put("cancellable",false);if(j.future!=null)j.future.cancel(true);j.state="cancelled";}return new JSONObject().put("state","cancelled").put("job_id",j.id);}
        if(action.equals("close")){Handle h=handle(p.getString("document_id"),owner);handles.remove(h.id);h.file.delete();return DocumentContract.success(new JSONObject().put("closed",true));}
        if(jobs.size()>=64){jobs.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis()&&Set.of("succeeded","failed","cancelled").contains(e.getValue().state));DocumentContract.require(jobs.size()<64,"job_limit");}
        JSONObject args=new JSONObject(p.toString());args.remove("_caller_thread");String operationId=args.optString("request_id","");
        if(action.equals("save")){DocumentContract.require(operationId.matches("[A-Za-z0-9_.:-]{1,180}"),"request_id_required");JSONObject prior=receipt(operationId,owner);if(prior!=null){DocumentContract.require(prior.getString("fingerprint").equals(DocumentContract.canonical(args)),"request_id_conflict");return receiptResult(prior);}}
        Job j=new Job();j.id=UUID.randomUUID().toString();j.owner=owner;j.handle=args.optString("document_id","");jobs.put(j.id,j);
        try{j.future=executor.submit(()->{synchronized(j){if(j.state.equals("cancelled"))return;j.state="running";}try{cleanup();if(!authorized())throw new SecurityException("authorization_revoked");JSONObject result=run(action,args,owner,j);if(!j.state.equals("cancelled")){j.result=result;j.state=result.optString("state","succeeded");}}catch(Throwable e){try{j.result=failure(e);j.state=j.state.equals("cancelled")?"cancelled":j.result.optString("state","failed");}catch(Exception ignored){j.state="failed";}}});}
        catch(RejectedExecutionException e){jobs.remove(j.id);throw new IllegalStateException("document_queue_full");}
        JSONObject started=new JSONObject().put("state","running").put("job_id",j.id).put("poll_action","documents.status");if(action.equals("save"))started.put("request_id",operationId);return started;
    }
    private JSONObject run(String action,JSONObject p,String owner,Job job)throws Exception{
        if(action.equals("open")){
            String source=p.getString("source"),name=p.optString("name",source.substring(source.lastIndexOf('/')+1)),format=DocumentContract.format(p.optString("format",name));reserve();File snapshot=temp();
            try(ParcelFileDescriptor fd=storageOpen(source);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()));OutputStream out=new FileOutputStream(snapshot)){copy(in,out);}catch(Exception e){snapshot.delete();throw e;}
            String version=hash(snapshot);try{verify(source,version);}catch(Exception e){snapshot.delete();throw e;}
            Handle h=make(owner,source,version,format,name,snapshot,false);return DocumentContract.success(describe(h));
        }
        if(action.equals("create")){reserve();String format=DocumentContract.format(p.getString("format"));File out=temp();try{JSONObject verified=process("create",p.put("format",format),null,out);Handle h=make(owner,"",hash(out),format,"document."+format,out,true);return DocumentContract.success(describe(h).put("verification",verified));}catch(Exception e){out.delete();throw e;}}
        Handle h=handle(p.getString("document_id"),owner);access(h);
        if(action.equals("save"))return save(p,h,owner,job);
        p.put("format",h.format);
        if(action.equals("edit")){
            DocumentContract.require(p.getString("source_version").equals(h.version),"source_changed");if(!h.source.isEmpty())verify(h.source,h.sourceVersion);reserve();File out=temp();try{JSONObject verified=process(action,p,h.file,out);if(!h.source.isEmpty())verify(h.source,h.sourceVersion);Handle artifact=make(owner,h.source,h.sourceVersion,h.format,h.name,out,true);return DocumentContract.success(describe(artifact).put("output_version",hash(out)).put("verification",verified));}catch(Exception e){out.delete();throw e;}
        }
        if(action.equals("render")){reserve();File out=temp();try{JSONObject rendered=process(action,p,h.file,out);Handle image=make(owner,h.source,h.sourceVersion,"png",h.name+".png",out,true);return DocumentContract.success(describe(image).put("render",rendered));}catch(Exception e){out.delete();throw e;}}
        JSONObject result=process(action,p,h.file,null);access(h);return DocumentContract.success(result.put("document_id",h.id).put("source_version",h.version).put("snapshot",true));
    }
    private JSONObject process(String op,JSONObject p,File source,File target)throws Exception{
        if(!processingSlot.tryAcquire(35,TimeUnit.SECONDS))throw new IllegalStateException("document_queue_full");
        try(ParcelFileDescriptor in=source==null?null:ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);ParcelFileDescriptor out=target==null?null:ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE)){
            return new JSONObject(DocumentRpc.request(context,WORKER,op,p,in,out,30).getString("json"));
        }finally{processingSlot.release();}
    }
    public JSONObject readSource(String source,String name,JSONObject p,String owner)throws Exception{
        android.net.Uri uri=android.net.Uri.parse(source);
        if("content".equals(uri.getScheme())&&"os.aios.core.runtime".equals(uri.getAuthority())){
            if(!authorized())throw new SecurityException("authorization_revoked");
            DocumentContract.require(uri.getPathSegments().size()==2&&uri.getPathSegments().get(0).equals("data")&&uri.getLastPathSegment().matches("[a-f0-9-]{36}"),"scope_denied");reserve();File snapshot=temp();
            try{try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(snapshot)){if(in==null)throw new IOException("source_unavailable");copy(in,out);}return process("read",new JSONObject(p.toString()).put("format",DocumentContract.format(name)),snapshot,null).put("source_version",hash(snapshot)).put("snapshot",true);}finally{snapshot.delete();}
        }
        Job j=new Job();JSONObject opened=run("open",new JSONObject().put("source",source).put("name",name),owner,j).getJSONObject("data");String id=opened.getString("document_id");
        try{return run("read",new JSONObject(p.toString()).put("document_id",id),owner,j).getJSONObject("data");}
        finally{Handle h=handles.remove(id);if(h!=null)h.file.delete();}
    }
    /** One explicitly selected search candidate; no retained body or persistent scan ledger. */
    public JSONObject findSource(String source,String name,JSONObject p,String owner)throws Exception{
        Job j=new Job();j.owner=owner;JSONObject opened=run("open",new JSONObject().put("source",source).put("name",name),owner,j).getJSONObject("data");String id=opened.getString("document_id");
        try{JSONObject request=new JSONObject(p.toString()).put("document_id",id);return run("find",request,owner,j).getJSONObject("data").put("bytes_read",opened.getLong("bytes"));}
        finally{Handle h=handles.remove(id);if(h!=null)h.file.delete();}
    }
    private JSONObject save(JSONObject p,Handle h,String owner,Job job)throws Exception{
        DocumentContract.require(h.artifact,"edit_or_create_required");DocumentContract.require(p.getString("source_version").equals(h.version),"source_changed");DocumentContract.require(!p.optBoolean("overwrite"),"overwrite_unsupported");String target=p.getString("target"),id=p.getString("request_id");DocumentContract.require(target.startsWith("/")&&target.toLowerCase(Locale.ROOT).endsWith("."+h.format),"invalid_target");
        String fingerprint=DocumentContract.canonical(p);
        JSONObject prior=receipt(id,owner);if(prior!=null){DocumentContract.require(prior.getString("fingerprint").equals(fingerprint),"request_id_conflict");return receiptResult(prior);}
        if(!h.source.isEmpty())verify(h.source,h.sourceVersion);String digest=hash(h.file);
        try(Cursor count=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM receipts",null)){count.moveToFirst();DocumentContract.require(count.getInt(0)<1000,"receipt_limit");}
        synchronized(job){if(job.state.equals("cancelled")||Thread.currentThread().isInterrupted())throw new IllegalStateException("cancelled");getWritableDatabase().execSQL("INSERT INTO receipts VALUES(?,?,?,'outcome_unknown',?,?, '{}')",new Object[]{id,owner,fingerprint,target,digest});job.state="committing";}
        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(h.file,ParcelFileDescriptor.MODE_READ_ONLY)){
            JSONObject result=new JSONObject(DocumentRpc.request(context,STORAGE,"commit",new JSONObject().put("target",target).put("sha256",digest).put("bytes",h.file.length()),fd,null,30).getString("json"));
            result.put("event_state",publishSave(id,target,digest));
            getWritableDatabase().execSQL("UPDATE receipts SET state='succeeded',result=? WHERE id=?",new Object[]{result.toString(),id});
            return DocumentContract.success(result.put("request_id",id));
        }catch(Exception e){return new JSONObject().put("state","outcome_unknown").put("request_id",id).put("message_key","document_save_unknown");}
    }
    private String publishSave(String id,String target,String digest){try{os.aios.core.events.EventHub.publish("document:"+id,"context.document.saved",target,digest,new JSONObject().put("request_id",id).put("source",target).put("source_version",digest),true);return "published";}catch(Exception e){return "unavailable";}}
    private JSONObject status(JSONObject p,String owner)throws Exception{
        if(p.has("request_id")){JSONObject r=receipt(p.getString("request_id"),owner);DocumentContract.require(r!=null,"receipt_missing");if(r.getString("state").equals("outcome_unknown")){try{verify(r.getString("target"),r.getString("sha256"));JSONObject result=new JSONObject().put("source",r.getString("target")).put("sha256",r.getString("sha256")).put("readback_verified",true).put("recovered",true).put("event_state",publishSave(r.getString("id"),r.getString("target"),r.getString("sha256")));getWritableDatabase().execSQL("UPDATE receipts SET state='succeeded',result=? WHERE id=?",new Object[]{result.toString(),r.getString("id")});r=receipt(r.getString("id"),owner);}catch(Exception ignored){}}return receiptResult(r);}
        Job j=job(p.getString("job_id"),owner);if(j.expires<System.currentTimeMillis())throw new IllegalArgumentException("job_expired");if(!j.handle.isEmpty()&&j.result!=null&&j.state.equals("succeeded"))access(handle(j.handle,owner));JSONObject out=new JSONObject().put("state",j.state).put("job_id",j.id);if(j.result!=null)out.put("result",j.result);return out;
    }
    private JSONObject receiptResult(JSONObject r)throws Exception{return new JSONObject().put("state",r.getString("state")).put("request_id",r.getString("id")).put("data",new JSONObject(r.getString("result")));}
    private JSONObject receipt(String id,String owner)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM receipts WHERE id=?",new String[]{id})){if(!c.moveToFirst())return null;DocumentContract.require(c.getString(c.getColumnIndexOrThrow("owner")).equals(owner),"owner_mismatch");JSONObject r=new JSONObject();for(int i=0;i<c.getColumnCount();i++)r.put(c.getColumnName(i),c.getString(i));return r;}}
    private Job job(String id,String owner){Job j=jobs.get(id);DocumentContract.require(j!=null&&j.owner.equals(owner),"job_unavailable");return j;}
    private Handle handle(String id,String owner){Handle h=handles.get(id);DocumentContract.require(h!=null&&h.owner.equals(owner)&&h.expires>System.currentTimeMillis(),"document_expired_or_denied");return h;}
    private Handle make(String owner,String source,String version,String format,String name,File file,boolean artifact)throws Exception{Handle h=new Handle();h.id=UUID.randomUUID().toString();h.owner=owner;h.source=source;h.sourceVersion=version;h.version=hash(file);h.format=format;h.name=name;h.file=file;h.artifact=artifact;handles.put(h.id,h);return h;}
    private JSONObject describe(Handle h)throws Exception{return new JSONObject().put("document_id",h.id).put("source_version",h.version).put("name",h.name).put("format",h.format).put("bytes",h.file.length()).put("artifact",h.artifact).put("expires_at",h.expires).put("snapshot",true);}
    private void access(Handle h)throws Exception{if(!authorized())throw new SecurityException("authorization_revoked");if(!h.source.isEmpty())try(ParcelFileDescriptor fd=storageOpen(h.source)){} }
    private ParcelFileDescriptor storageOpen(String source)throws Exception{return DocumentRpc.request(context,STORAGE,"open",new JSONObject().put("source",source),null,null,10).getParcelable("fd",ParcelFileDescriptor.class);}
    private void verify(String source,String version)throws Exception{JSONObject result=new JSONObject(DocumentRpc.request(context,STORAGE,"verify",new JSONObject().put("source",source),null,null,15).getString("json"));DocumentContract.require(result.getString("sha256").equals(version),"source_changed");}
    private void cleanup(){long now=System.currentTimeMillis();handles.entrySet().removeIf(e->{if(e.getValue().expires<now){e.getValue().file.delete();return true;}return false;});}
    private void reserve(){cleanup();long bytes=0;File[] files=directory.listFiles();if(files!=null)for(File f:files)bytes+=f.length();DocumentContract.require(bytes+DocumentContract.MAX_BYTES<=128L*1024*1024&&directory.getUsableSpace()>DocumentContract.MAX_BYTES+1048576,"cache_or_space_limit");}
    private synchronized File temp()throws IOException{File[] files=directory.listFiles();DocumentContract.require(files!=null&&files.length<4,"cache_or_space_limit");return File.createTempFile("document-",".tmp",directory);}
    private static void copy(InputStream in,OutputStream out)throws Exception{byte[] b=new byte[32768];int n,total=0;while((n=in.read(b))!=-1){total+=n;DocumentContract.require(total<=DocumentContract.MAX_BYTES,"source_too_large");out.write(b,0,n);}out.flush();}
    private static String hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte v:d.digest())out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}
    public void revoke(){
        for(Job j:jobs.values())synchronized(j){if(Set.of("queued","running").contains(j.state)){j.state="cancelled";if(j.future!=null)j.future.cancel(true);}j.result=null;}
        for(Handle h:handles.values())h.file.delete();handles.clear();
    }
    private JSONObject failure(Throwable e)throws Exception{Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();String raw=cause.getMessage(),code=raw!=null&&raw.matches("[a-z_]{3,80}")?raw:"document_processing_failed";return new JSONObject().put("state",e instanceof SecurityException?"permission_required":"failed").put("error_code",code).put("message_key","document_operation_failed");}
}
