package os.aios.agent;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
public final class PackageServices extends BroadcastReceiver {
 /** Host transport only; package validation stays in this shared implementation. */
 interface Backend {
  InputStream read(String path)throws Exception;
  JSONObject download(String op,JSONObject params)throws Exception;
  void install(String id,JSONObject job,JSONArray validatedFiles)throws Exception;
  JSONObject status(JSONObject job)throws Exception;
 }
 static final String generation=UUID.randomUUID().toString();
 static final ExecutorService worker=Executors.newSingleThreadExecutor();
 static android.content.SharedPreferences db(Context c){return c.getSharedPreferences("package_jobs",0);}
 static synchronized void save(Context c,String id,JSONObject value){if(!db(c).edit().putString(id,value.toString()).commit())throw new IllegalStateException("Cannot persist job");}
 static JSONObject read(Context c,String id)throws Exception{String s=db(c).getString(id,null);if(s==null)throw new IllegalArgumentException("Unknown job");return new JSONObject(s);}
 static JSONObject ok(Object d)throws Exception{return new JSONObject().put("state","succeeded").put("data",d);}
 public void onReceive(Context c,Intent i){try{
  String id=i.getStringExtra("job");JSONObject j=read(c,id);int status=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
  j.put("installer_status",status).put("message",i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
  if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){j.put("state","waiting_for_user");Intent consent=i.getParcelableExtra(Intent.EXTRA_INTENT,Intent.class);if(consent!=null)c.startActivity(consent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  else if(status==PackageInstaller.STATUS_SUCCESS){PackageInfo p=c.getPackageManager().getPackageInfo(j.getString("package"),0);boolean match=p.getLongVersionCode()==j.getLong("version_code");j.put("state",match?"succeeded":"failed").put("completion_verified",match).put("installed_version",p.getLongVersionCode());}
  else j.put("state","failed");
  save(c,id,j);
 }catch(Exception e){android.util.Log.e("AIOS_INSTALL","callback",e);}}
 static JSONObject inspect(Context c,File f)throws Exception{
  PackageInfo p=c.getPackageManager().getPackageArchiveInfo(f.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
  if(p==null)throw new IllegalArgumentException("Invalid APK");
  JSONArray signs=new JSONArray();if(p.signingInfo!=null)for(Signature sig:p.signingInfo.getApkContentsSigners()){byte[] d=java.security.MessageDigest.getInstance("SHA-256").digest(sig.toByteArray());StringBuilder h=new StringBuilder();for(byte b:d)h.append(String.format("%02x",b));signs.put(h.toString());}
  Set<String> abis=new TreeSet<>();try(ZipFile z=new ZipFile(f)){Enumeration<? extends ZipEntry> e=z.entries();while(e.hasMoreElements()){String n=e.nextElement().getName();String[] a=n.split("/");if(a.length>=3&&a[0].equals("lib")&&n.endsWith(".so"))abis.add(a[1]);}}
  boolean abi=abis.isEmpty();for(String a:Build.SUPPORTED_ABIS)abi|=abis.contains(a);
  return new JSONObject().put("package",p.packageName).put("version_code",p.getLongVersionCode()).put("version_name",p.versionName).put("min_sdk",p.applicationInfo.minSdkVersion).put("device_sdk",Build.VERSION.SDK_INT).put("sdk_compatible",p.applicationInfo.minSdkVersion<=Build.VERSION.SDK_INT).put("native_abis",new JSONArray(abis)).put("device_abis",new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS))).put("abi_compatible",abi).put("signer_sha256",signs).put("sha256",new SharedFiles(c).digest(f)).put("signature_note","Signer metadata only; Android PackageInstaller performs authoritative signature verification");
 }
 static JSONObject execute(Context c,String op,JSONObject p)throws Exception{return execute(c,op,p,null);}
 static JSONObject execute(Context c,String op,JSONObject p,Backend backend)throws Exception{
  if(backend!=null&&op.startsWith("downloads."))return backend.download(op,p);
  if(backend!=null&&op.equals("apps.install.status")){
   JSONObject job=read(c,p.getString("id"));
   if("adb".equals(job.optString("backend")))return ok(backend.status(job));
  }
  if(backend!=null&&op.equals("apps.apk.inspect")){
   File temp=File.createTempFile("inspect-",".apk",c.getCacheDir());
   try{try(InputStream in=backend.read(p.getString("path"));OutputStream out=new FileOutputStream(temp)){in.transferTo(out);}return ok(inspect(c,temp));}finally{temp.delete();}
  }
  if(op.equals("apps.apk.inspect"))return ok(inspect(c,new SharedFiles(c).target(p.getString("path"))));
  if(op.equals("apps.package.get")){
   try{PackageInfo info=c.getPackageManager().getPackageInfo(p.getString("package"),0);return ok(new JSONObject().put("installed",true).put("package",info.packageName).put("version_code",info.getLongVersionCode()).put("version_name",info.versionName).put("launchable",c.getPackageManager().getLaunchIntentForPackage(info.packageName)!=null));}
   catch(PackageManager.NameNotFoundException e){return ok(new JSONObject().put("installed",false));}
  }
  if(op.equals("apps.install.start")){
   JSONArray paths=p.getJSONArray("paths");if(paths.length()==0||paths.length()>50)throw new IllegalArgumentException("Provide base APK and optional split APKs");
   String id=UUID.randomUUID().toString();JSONObject j=new JSONObject().put("id",id).put("state","queued").put("generation",generation).put("completion_verified",false);save(c,id,j);
   worker.execute(()->{File dir=new File(c.getCacheDir(),"install-"+id);int sessionId=-1;try{
    dir.mkdirs();JSONObject base=null;JSONArray files=new JSONArray();
    for(int n=0;n<paths.length();n++){File dst=new File(dir,n+".apk");try(InputStream in=backend==null?new FileInputStream(new SharedFiles(c).target(paths.getString(n))):backend.read(paths.getString(n));OutputStream out=new FileOutputStream(dst)){in.transferTo(out);}JSONObject info=inspect(c,dst);if(!info.getBoolean("sdk_compatible")||!info.getBoolean("abi_compatible"))throw new IllegalArgumentException("APK SDK/ABI incompatible");if(base==null)base=info;else if(!base.getString("package").equals(info.getString("package"))||base.getLong("version_code")!=info.getLong("version_code")||!base.getJSONArray("signer_sha256").toString().equals(info.getJSONArray("signer_sha256").toString()))throw new IllegalArgumentException("Split package/version/signers differ");files.put(dst.getPath());}
    if(!base.getString("package").equals(p.getString("expected_package")))throw new IllegalArgumentException("Unexpected APK package");
    JSONArray hashes=p.getJSONArray("sha256");if(hashes.length()!=files.length())throw new IllegalArgumentException("Each APK requires SHA256");
    for(int n=0;n<files.length();n++)if(!new SharedFiles(c).digest(new File(files.getString(n))).equals(hashes.getString(n)))throw new IllegalArgumentException("APK SHA256 mismatch");
    String name=base.getString("package");if(name.equals(c.getPackageName()))throw new IllegalArgumentException("Agent self-update requires maintenance deployment");
    PackageInfo old=null;try{old=c.getPackageManager().getPackageInfo(name,0);}catch(PackageManager.NameNotFoundException ignored){}
    if(old!=null&&(!p.optBoolean("replace",false)||old.getLongVersionCode()!=p.optLong("expected_installed_version",-1)))throw new IllegalArgumentException("Existing package version changed or replacement not authorized");
    if(backend!=null){j.put("backend","adb").put("package",name).put("version_code",base.getLong("version_code")).put("replace",p.optBoolean("replace",false)).put("expected_installed_version",p.optLong("expected_installed_version",-1)).put("state","installing");save(c,id,j);backend.install(id,j,files);return;}
    PackageInstaller pi=c.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(name);params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
    sessionId=pi.createSession(params);j.put("session_id",sessionId).put("package",name).put("version_code",base.getLong("version_code")).put("state","installing");save(c,id,j);
    try(PackageInstaller.Session session=pi.openSession(sessionId)){
     for(int n=0;n<files.length();n++){File f=new File(files.getString(n));try(InputStream in=new FileInputStream(f);OutputStream out=session.openWrite(n+".apk",0,f.length())){in.transferTo(out);session.fsync(out);}}
     Intent intent=new Intent(c,PackageServices.class).setAction("os.aios.INSTALL_RESULT").setData(Uri.parse("aios-install:"+id)).putExtra("job",id);
     PendingIntent pending=PendingIntent.getBroadcast(c,sessionId,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
     session.commit(pending.getIntentSender());
    }
   }catch(Exception e){try{if(sessionId>=0)c.getPackageManager().getPackageInstaller().abandonSession(sessionId);j.put("state","adb".equals(j.optString("backend"))?"outcome_unknown":"failed").put("error",e.toString());save(c,id,j);}catch(Exception ignored){}}
   finally{File[] children=dir.listFiles();if(children!=null)for(File f:children)f.delete();dir.delete();}
   });return ok(j);
  }
  if(op.equals("apps.install.status")){JSONObject j=read(c,p.getString("id"));if(j.optString("state").equals("queued")&&!generation.equals(j.optString("generation"))){j.put("state","failed").put("reason","Agent restarted before install submission; start a new job");save(c,p.getString("id"),j);}if(j.optString("state").equals("installing")&&c.getPackageManager().getPackageInstaller().getSessionInfo(j.optInt("session_id"))==null){j.put("state","outcome_unknown").put("reason","Session ended without recorded callback; query installed package");}return ok(j);}
  if(op.equals("downloads.start")){
   Uri u=Uri.parse(p.getString("url"));if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException("HTTPS URL without credentials required");
   File f=new SharedFiles(c).target(p.getString("path"));if(f.exists())throw new IllegalArgumentException("Download destination exists");if(!f.getParentFile().isDirectory())throw new IllegalArgumentException("Download directory missing");
   DownloadManager.Request request=new DownloadManager.Request(u).setDestinationUri(Uri.fromFile(f)).setTitle(os.aios.core.DisplayText.clean(f.getName())).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
   long id=c.getSystemService(DownloadManager.class).enqueue(request);save(c,"download-"+id,new JSONObject().put("path",f.getPath()));return ok(new JSONObject().put("id",id).put("state","queued"));
  }
  if(op.equals("downloads.status")||op.equals("downloads.cancel")){
   long id=p.getLong("id");JSONObject own=read(c,"download-"+id);DownloadManager dm=c.getSystemService(DownloadManager.class);
   if(op.equals("downloads.cancel"))return ok(new JSONObject().put("removed",dm.remove(id)).put("state","cancelled").put("downloaded_file_removed",true));
   try(Cursor cur=dm.query(new DownloadManager.Query().setFilterById(id))){if(cur==null||!cur.moveToFirst())return ok(new JSONObject().put("state","missing"));int state=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));JSONObject j=new JSONObject().put("id",id).put("state",state==DownloadManager.STATUS_SUCCESSFUL?"succeeded":state==DownloadManager.STATUS_FAILED?"failed":state==DownloadManager.STATUS_PAUSED?"paused":"running").put("reason_code",cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))).put("bytes",cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))).put("total_bytes",cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))).put("path",own.getString("path"));int reason=cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));String reasonText=state==DownloadManager.STATUS_FAILED?(reason>=400&&reason<=599?"HTTP "+reason:switch(reason){case 1001->"File storage error";case 1002->"Unhandled HTTP response";case 1004->"HTTP data error";case 1005->"Too many redirects";case 1006->"Insufficient space";case 1007->"Storage device missing";case 1008->"Cannot resume";case 1009->"File already exists";default->"Unknown download error";}):state==DownloadManager.STATUS_PAUSED?switch(reason){case 1->"Waiting to retry";case 2->"Waiting for network";case 3->"Waiting for Wi-Fi";default->"Paused";}:"";j.put("reason",reasonText);return ok(j);}
  }
  throw new IllegalArgumentException("Unknown package operation");
 }
}
