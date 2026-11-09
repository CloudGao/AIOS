package os.aios.apk.shell;

import android.content.Context;
import android.content.pm.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Fixed package-manager commands, never a command string supplied by the model. */
final class ShellPackages {
 private final Context context;
 private final int user;
 private final ConcurrentHashMap<String,JSONObject> jobs=new ConcurrentHashMap<>();
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 ShellPackages(Context c,int user){context=c;this.user=user;}
 synchronized JSONObject submit(JSONObject request,ParcelFileDescriptor[] files)throws Exception {
  String id=request.getString("id"),pkg=request.getString("package");
  if(!id.matches("[a-f0-9-]{36}")||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||pkg.equals("os.aios.app"))throw new IllegalArgumentException("invalid_install_identity");
  if(files==null||files.length<1||files.length>50)throw new IllegalArgumentException("invalid_apk_count");
  if(jobs.containsKey(id))throw new IllegalStateException("install_id_already_submitted_query_status");
  if(jobs.size()>=128)throw new IllegalStateException("install_history_capacity_reactivate_when_idle");
  for(ParcelFileDescriptor fd:files)if(fd==null||fd.getStatSize()<=0)throw new IllegalArgumentException("regular_apk_file_required");
  JSONObject job=new JSONObject().put("id",id).put("state","queued").put("completion_verified",false);jobs.put(id,job);
  worker.execute(()->install(id,request,files));return new JSONObject(job.toString());
 }
 JSONObject status(String id)throws Exception {JSONObject job=jobs.get(id);return job==null?new JSONObject().put("state","outcome_unknown").put("reason_code","install_job_not_found"):new JSONObject(job.toString());}
 private void state(String id,String state,String reason,boolean verified)throws Exception {jobs.put(id,new JSONObject().put("id",id).put("state",state).put("reason_code",reason).put("completion_verified",verified));}
 private static String command(List<String> args,ParcelFileDescriptor source,long length,int timeout)throws Exception {
  ArrayList<String> argv=new ArrayList<>(Arrays.asList("/system/bin/pm"));argv.addAll(args);
  java.lang.Process process=new ProcessBuilder(argv).redirectErrorStream(true).start();
  FutureTask<String> reader=new FutureTask<>(()->{ByteArrayOutputStream saved=new ByteArrayOutputStream();try(InputStream in=process.getInputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){int keep=Math.min(n,16384-saved.size());if(keep>0)saved.write(bytes,0,keep);}}return saved.toString(java.nio.charset.StandardCharsets.UTF_8);});
  Thread drain=new Thread(reader,"aios-package-output");drain.setDaemon(true);drain.start();
  FutureTask<Void> writer=new FutureTask<>(()->{try(OutputStream out=process.getOutputStream()){if(source!=null)try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(source)){byte[] bytes=new byte[65536];long copied=0;int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);copied+=n;}if(copied!=length)throw new IOException("apk_length_changed");}}return null;});
  Thread feed=new Thread(writer,"aios-package-input");feed.setDaemon(true);feed.start();
  try{if(!process.waitFor(timeout,TimeUnit.SECONDS))throw new IOException("package_command_timeout");writer.get(5,TimeUnit.SECONDS);String result=reader.get(5,TimeUnit.SECONDS);if(process.exitValue()!=0)throw new IOException("package_command_rejected");return result;}
  finally{process.destroy();try{process.getOutputStream().close();}catch(IOException ignored){}if(source!=null)try{source.close();}catch(IOException ignored){}}
 }
 private void install(String id,JSONObject request,ParcelFileDescriptor[] files){
  int session=-1;boolean committing=false;
  try{
   state(id,"installing","install_session_preparing",false);
   String pkg=request.getString("package");long expected=request.getLong("expected_installed_version");PackageInfo old=null;try{old=context.getPackageManager().getPackageInfo(pkg,0);}catch(PackageManager.NameNotFoundException ignored){}
   if(old!=null&&(!request.getBoolean("replace")||old.getLongVersionCode()!=expected))throw new IllegalStateException("installed_version_changed");
   long total=0;for(ParcelFileDescriptor fd:files)total=Math.addExact(total,fd.getStatSize());
   ArrayList<String> create=new ArrayList<>(Arrays.asList("install-create","--user",Integer.toString(user),"-S",Long.toString(total)));
   if(request.getBoolean("replace"))create.add("-r");
   String created=command(create,null,0,30);java.util.regex.Matcher m=java.util.regex.Pattern.compile("Success:.*\\[(\\d+)\\]").matcher(created);if(!m.find())throw new IOException("install_session_not_created");session=Integer.parseInt(m.group(1));
   for(int i=0;i<files.length;i++){
    long size=files[i].getStatSize();String written=command(Arrays.asList("install-write","-S",Long.toString(size),Integer.toString(session),i+".apk","-"),files[i],size,120);if(!written.startsWith("Success"))throw new IOException("install_write_failed");
   }
   committing=true;String result=command(Arrays.asList("install-commit",Integer.toString(session)),null,0,180);
   if(!result.startsWith("Success")){state(id,"failed","package_install_rejected",false);return;}
   PackageInfo installed=context.getPackageManager().getPackageInfo(pkg,0);boolean verified=installed.getLongVersionCode()==request.getLong("version_code");state(id,verified?"succeeded":"outcome_unknown",verified?"installed_version_verified":"installed_version_mismatch",verified);
  }catch(Exception error){try{state(id,committing?"outcome_unknown":"failed",committing?"install_submission_outcome_unknown":"install_preparation_failed",false);}catch(Exception ignored){}}
  finally{
   for(ParcelFileDescriptor fd:files)if(fd!=null)try{fd.close();}catch(IOException ignored){}
   if(session>=0&&!committing)try{command(Arrays.asList("install-abandon",Integer.toString(session)),null,0,15);}catch(Exception ignored){}
  }
 }
}
