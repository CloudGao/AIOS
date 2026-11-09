package os.aios.agent;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pins a transport per operation; never retries a submitted write through another UID. */
final class ApkPackageBackend implements PackageServices.Backend {
    private final Context c;
    private final String epoch;
    private static final ExecutorService statusQueries=Executors.newFixedThreadPool(2);
    private static final ConcurrentHashMap<String,Future<JSONObject>> pendingStatus=new ConcurrentHashMap<>();
    private static final ExecutorService downloads=Executors.newFixedThreadPool(2);
    private static final ConcurrentHashMap<Long,AtomicBoolean> active=new ConcurrentHashMap<>();
    ApkPackageBackend(Context c){this.c=c;epoch=AdbBridge.instance();}
    static PackageServices.Backend select(Context c,String op,JSONObject p)throws Exception{
        if(op.equals("downloads.status")||op.equals("downloads.cancel"))return "adb".equals(PackageServices.read(c,"download-"+p.getLong("id")).optString("backend"))?new ApkPackageBackend(c):null;
        if(op.equals("apps.install.status"))return "adb".equals(PackageServices.read(c,p.getString("id")).optString("backend"))?new ApkPackageBackend(c):null;
        return AdbBridge.connected()?new ApkPackageBackend(c):null;
    }
    public InputStream read(String path)throws Exception{return new ParcelFileDescriptor.AutoCloseInputStream(AdbBridge.file(epoch,path,false));}
    public void install(String id,JSONObject job,JSONArray paths)throws Exception{
        job.put("shell_instance",epoch);PackageServices.save(c,id,job);
        ParcelFileDescriptor[] files=new ParcelFileDescriptor[paths.length()];
        try{for(int i=0;i<files.length;i++)files[i]=ParcelFileDescriptor.open(new File(paths.getString(i)),ParcelFileDescriptor.MODE_READ_ONLY);
            JSONObject result=AdbBridge.install(epoch,job,files);job.put("state",result.getString("state"));PackageServices.save(c,id,job);
        }finally{for(ParcelFileDescriptor fd:files)if(fd!=null)fd.close();}
    }
    public JSONObject status(JSONObject job)throws Exception{
        String state=job.optString("state");
        if(Set.of("succeeded","failed").contains(state))return job;
        if(!AdbBridge.connected()||!AdbBridge.instance().equals(job.optString("shell_instance")))return job.put("state","outcome_unknown").put("reason_code","installation_backend_lost_query_package");
        String id=job.getString("id");
        Future<JSONObject> pending=pendingStatus.computeIfAbsent(id,key->statusQueries.submit(()->AdbBridge.call("install_status",new JSONObject().put("instance",job.getString("shell_instance")).put("id",key))));
        JSONObject result;
        try{result=pending.get(2500,TimeUnit.MILLISECONDS);}
        catch(TimeoutException waiting){return new JSONObject(job.toString()).put("reason_code","status_pending_check_system_installer");}
        finally{if(pending.isDone())pendingStatus.remove(id,pending);}

        for(Iterator<String> keys=result.keys();keys.hasNext();){String key=keys.next();job.put(key,result.get(key));}
        PackageServices.save(c,job.getString("id"),job);return job;
    }
    private static URL https(String value)throws Exception{
        URL u=new URL(value);if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null)throw new IllegalArgumentException("https_url_without_credentials_required");return u;
    }
    public JSONObject download(String op,JSONObject p)throws Exception{
        if(!op.equals("downloads.start")){
            long id=p.getLong("id");JSONObject job=PackageServices.read(c,"download-"+id);AtomicBoolean cancel=active.get(id);
            if(op.equals("downloads.cancel")){if(cancel!=null){cancel.set(true);return PackageServices.ok(new JSONObject(job.toString()).put("state","cancelling").put("downloaded_file_removed",false));}return PackageServices.ok(job.put("downloaded_file_removed",false));}
            if(cancel==null&&Set.of("queued","running").contains(job.optString("state"))){job.put("state","interrupted").put("reason_code","download_process_ended_partial_file_retained");PackageServices.save(c,"download-"+id,job);}
            return PackageServices.ok(job);
        }
        URL url=https(p.getString("url"));String path=p.getString("path");long id;
        synchronized(ApkPackageBackend.class){do{id=-(1L+new java.security.SecureRandom().nextInt(Integer.MAX_VALUE-1));}while(PackageServices.db(c).contains("download-"+id));
            PackageServices.save(c,"download-"+id,new JSONObject().put("id",id).put("backend","adb").put("path",path).put("state","queued").put("bytes",0).put("total_bytes",-1));}
        long key=id;AtomicBoolean cancel=new AtomicBoolean();active.put(key,cancel);
        // Create exclusively. A pre-existing user file is never overwritten.
        ParcelFileDescriptor descriptor;
        try{descriptor=AdbBridge.file(epoch,path,true);}catch(Exception e){active.remove(key);JSONObject failed=PackageServices.read(c,"download-"+key).put("state","failed").put("reason_code","destination_not_opened");PackageServices.save(c,"download-"+key,failed);throw e;}
        downloads.execute(()->runDownload(key,url,descriptor,cancel));return PackageServices.ok(PackageServices.read(c,"download-"+key));
    }
    private void runDownload(long id,URL initial,ParcelFileDescriptor descriptor,AtomicBoolean cancelled){
        HttpURLConnection connection=null;JSONObject job=null;long count=0;
        try(ParcelFileDescriptor.AutoCloseOutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)){
            job=PackageServices.read(c,"download-"+id);job.put("state","running");PackageServices.save(c,"download-"+id,job);
            URL url=initial;
            for(int redirects=0;;redirects++){
                if(cancelled.get())throw new InterruptedIOException("download_cancelled");
                connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(30000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept-Encoding","identity");
                int status=connection.getResponseCode();
                if(Set.of(301,302,303,307,308).contains(status)){
                    if(redirects>=5)throw new IOException("redirect_limit");String location=connection.getHeaderField("Location");if(location==null)throw new IOException("redirect_missing_location");URL next=https(new URL(url,location).toString());connection.disconnect();url=next;continue;
                }
                if(status!=200)throw new IOException("http_status_"+status);break;
            }
            long expected=connection.getContentLengthLong();job.put("total_bytes",expected);long saved=0;
            try(InputStream input=connection.getInputStream()){
                byte[] bytes=new byte[65536];int n;
                while((n=input.read(bytes))!=-1){if(cancelled.get())throw new InterruptedIOException("download_cancelled");output.write(bytes,0,n);count+=n;
                    if(System.currentTimeMillis()-saved>500){job.put("bytes",count);PackageServices.save(c,"download-"+id,job);saved=System.currentTimeMillis();}}
            }
            if(cancelled.get())throw new InterruptedIOException("download_cancelled");
            if(expected>=0&&expected!=count)throw new IOException("download_length_mismatch");output.getFD().sync();job.put("state","succeeded").put("bytes",count);PackageServices.save(c,"download-"+id,job);
        }catch(Exception error){try{if(job==null)job=PackageServices.read(c,"download-"+id);job.put("state",cancelled.get()?"cancelled":"failed").put("bytes",count).put("reason_code",cancelled.get()?"download_cancelled_partial_file_retained":"download_failed_partial_file_retained");PackageServices.save(c,"download-"+id,job);}catch(Exception ignored){}}
        finally{if(connection!=null)connection.disconnect();active.remove(id);}
    }
}
