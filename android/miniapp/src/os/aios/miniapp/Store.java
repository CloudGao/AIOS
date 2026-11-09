package os.aios.miniapp;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** All mutations serialize on one store; publication changes one durable pointer. */
final class Store {
 static Store instance;
 static synchronized Store get(Context c)throws Exception {if(instance==null)instance=new Store(c);return instance;}
 final Context context; final File root; final AtomicFile state; JSONObject db;
 Store(Context c)throws Exception {
  context=c.getApplicationContext();
  root=new File(c.getFilesDir(),"miniapps");if(!root.exists()&&!root.mkdirs())throw new IOException("Storage unavailable");
  state=new AtomicFile(new File(root,"registry.json"));
  if(state.getBaseFile().exists())db=new JSONObject(new String(state.readFully(),StandardCharsets.UTF_8));
  else db=new JSONObject().put("apps",new JSONObject()).put("stages",new JSONObject());
 }
 static String id(String s){if(!s.matches("[a-z][a-z0-9-]{2,47}"))throw new IllegalArgumentException("id: 3..48 lowercase letters, digits, hyphens");return s;}
 static String path(String s){
  if(s.length()>200||!s.matches("[A-Za-z0-9_./-]+")||s.startsWith("/")||s.startsWith("_ling/")||Arrays.asList(s.split("/",-1)).stream().anyMatch(x->x.isEmpty()||x.equals(".")||x.equals("..")))throw new IllegalArgumentException("Invalid relative file path");return s;
 }
 static String key(String s){if(!s.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException("Invalid operation key");return s;}
 static String digest(byte[] b)throws Exception {byte[] hash=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte v:hash)s.append(String.format("%02x",v&255));return s.toString();}
 static JSONObject copy(JSONObject j)throws Exception{return new JSONObject(j.toString());}
 void save()throws Exception {
  FileOutputStream out=null;try{out=state.startWrite();out.write(db.toString().getBytes(StandardCharsets.UTF_8));state.finishWrite(out);context.getContentResolver().notifyChange(LauncherProvider.URI,null);}catch(Exception e){state.failWrite(out);db=new JSONObject(new String(state.readFully(),StandardCharsets.UTF_8));throw e;}
 }
 synchronized JSONObject app(String appId)throws Exception {JSONObject a=db.getJSONObject("apps").optJSONObject(id(appId));if(a==null)throw new IllegalArgumentException("App not installed");return copy(a);}
 synchronized JSONArray list()throws Exception {JSONArray a=new JSONArray();JSONObject apps=db.getJSONObject("apps");for(Iterator<String> i=apps.keys();i.hasNext();)a.put(copy(apps.getJSONObject(i.next())));return a;}
 static JSONObject manifest(JSONObject m)throws Exception {
  id(m.getString("id"));String name=m.getString("name").trim();if(name.isEmpty()||name.length()>60)throw new IllegalArgumentException("name: 1..60 characters");
  if(m.getInt("version")<1)throw new IllegalArgumentException("version must be positive");path(m.getString("entry"));
  JSONArray caps=m.getJSONArray("capabilities");if(caps.length()>20)throw new IllegalArgumentException("Too many capabilities");
  Set<String> seen=new HashSet<>();for(int i=0;i<caps.length();i++){String cap=caps.getString(i);if(!Capabilities.names().contains(cap)||!seen.add(cap))throw new IllegalArgumentException("Unknown or duplicate capability: "+cap);}
  return new JSONObject().put("id",m.getString("id")).put("name",name).put("version",m.getInt("version")).put("entry",m.getString("entry")).put("capabilities",new JSONArray(caps.toString()));
 }
 synchronized JSONObject install(JSONObject p,JSONObject bundle)throws Exception {
  JSONObject m=manifest(bundle.getJSONObject("manifest"));if(!m.getString("id").equals(p.getString("id")))throw new IllegalArgumentException("Project id mismatch");
  JSONObject files=bundle.getJSONObject("files");if(files.length()>128)throw new IllegalArgumentException("Too many files");
  TreeSet<String> names=new TreeSet<>();files.keys().forEachRemaining(names::add);StringBuilder hash=new StringBuilder(m.toString());long total=0;
  for(String name:names){path(name);byte[] bytes=android.util.Base64.decode(files.getString(name),android.util.Base64.DEFAULT);total+=bytes.length;if(bytes.length>49152||total>2097152)throw new IllegalArgumentException("Bundle limit exceeded");hash.append(name).append(':').append(digest(bytes)).append(';');}
  String fingerprint=digest(hash.toString().getBytes(StandardCharsets.UTF_8));String stage=key(p.getString("request_id"));
  begin(new JSONObject().put("manifest",m).put("request_id",stage).put("expected_version",p.getInt("expected_version")));
  JSONObject receipt=db.getJSONObject("stages").getJSONObject(stage);
  if(receipt.has("install_digest")&&!receipt.getString("install_digest").equals(fingerprint))throw new IllegalArgumentException("request_id content conflict");
  if(!receipt.has("install_digest")&&receipt.getJSONObject("files").length()>0)throw new IllegalArgumentException("Stage belongs to another upload");
  receipt.put("install_digest",fingerprint);save();
  if(receipt.getString("state").equals("published"))return copy(receipt.getJSONObject("receipt"));
  for(String name:names)write(new JSONObject().put("stage_id",stage).put("path",name).put("base64",files.getString(name)));
  return publish(stage);
 }
 synchronized JSONObject begin(JSONObject p)throws Exception {
  JSONObject m=manifest(p.getJSONObject("manifest"));String stageId=key(p.getString("request_id"));JSONObject stages=db.getJSONObject("stages"),old=stages.optJSONObject(stageId);
  if(old!=null){if(!old.getJSONObject("manifest").toString().equals(m.toString())||old.getInt("expected_version")!=p.getInt("expected_version"))throw new IllegalArgumentException("request_id conflict");return copy(old);}
  if(stages.length()>=64)throw new IllegalStateException("Stage limit reached; discard unused stages");
  JSONObject current=db.getJSONObject("apps").optJSONObject(m.getString("id"));int expected=p.getInt("expected_version");
  if(current==null?expected!=0:!current.getString("state").equals("installed")||current.getJSONObject("manifest").getInt("version")!=expected)throw new IllegalStateException("Version conflict");
  if(m.getInt("version")<=expected)throw new IllegalArgumentException("New version must increase");
  JSONObject s=new JSONObject().put("stage_id",stageId).put("manifest",m).put("expected_version",expected).put("generation",current==null?UUID.randomUUID().toString().replace("-",""):current.getString("generation")).put("files",new JSONObject()).put("state","staging");
  stages.put(stageId,s);save();return copy(s);
 }
 File stageDir(String stage){return new File(root,"staging/"+key(stage));}
 synchronized JSONObject write(JSONObject p)throws Exception {
  String sid=key(p.getString("stage_id")),name=path(p.getString("path"));JSONObject s=db.getJSONObject("stages").getJSONObject(sid);
  if(!s.getString("state").equals("staging"))throw new IllegalStateException("Stage already published");
  byte[] bytes=android.util.Base64.decode(p.getString("base64"),android.util.Base64.DEFAULT);if(bytes.length>49152)throw new IllegalArgumentException("File limit: 48 KiB; split JS/CSS modules");
  JSONObject files=s.getJSONObject("files");long total=bytes.length;for(Iterator<String> i=files.keys();i.hasNext();){String k=i.next();if(!k.equals(name))total+=files.getJSONObject(k).getLong("bytes");}
  if(total>2097152||!files.has(name)&&files.length()>=128)throw new IllegalArgumentException("Bundle limit: 2 MiB / 128 files");
  File file=new File(stageDir(sid),name);if(!file.getParentFile().exists()&&!file.getParentFile().mkdirs())throw new IOException("Cannot create folder");AtomicFile af=new AtomicFile(file);FileOutputStream out=null;try{out=af.startWrite();out.write(bytes);af.finishWrite(out);}catch(Exception e){af.failWrite(out);throw e;}
  files.put(name,new JSONObject().put("sha256",digest(bytes)).put("bytes",bytes.length));save();return copy(files.getJSONObject(name));
 }
 synchronized JSONObject publish(String stage)throws Exception {
  JSONObject s=db.getJSONObject("stages").getJSONObject(key(stage));if(s.getString("state").equals("published"))return copy(s.getJSONObject("receipt"));
  JSONObject m=s.getJSONObject("manifest"),files=s.getJSONObject("files"),apps=db.getJSONObject("apps"),a=apps.optJSONObject(m.getString("id"));
  if(a==null?s.getInt("expected_version")!=0:!a.getString("state").equals("installed")||a.getJSONObject("manifest").getInt("version")!=s.getInt("expected_version")||!a.getString("generation").equals(s.getString("generation")))throw new IllegalStateException("Version conflict");
  if(!files.has(m.getString("entry")))throw new IllegalArgumentException("Entry file missing");
  File dest=new File(root,"apps/"+s.getString("generation")+"/versions/"+m.getInt("version"));File src=stageDir(stage);
  // A crash after rename is recoverable: revalidate the immutable destination.
  File verify=dest.exists()?dest:src;
  for(Iterator<String> i=files.keys();i.hasNext();){String name=i.next();File f=new File(verify,path(name));if(!f.isFile()||!digest(Files.readAllBytes(f.toPath())).equals(files.getJSONObject(name).getString("sha256")))throw new IOException("Missing or changed file: "+name);}
  if(!dest.exists()){dest.getParentFile().mkdirs();if(!src.renameTo(dest))throw new IOException("Cannot publish bundle");}
  JSONObject versions=a==null?new JSONObject():a.getJSONObject("versions");versions.put(String.valueOf(m.getInt("version")),new JSONObject().put("manifest",copy(m)).put("files",copy(files)));
  JSONObject next=new JSONObject().put("manifest",copy(m)).put("generation",s.getString("generation")).put("state","installed").put("desktop_request",a==null?1:a.optLong("desktop_request",0)).put("versions",versions).put("grants",a==null?new JSONObject():a.getJSONObject("grants"));
  // Removed permissions do not silently return after a later update.
  Set<String> declared=new HashSet<>();JSONArray ca=m.getJSONArray("capabilities");for(int i=0;i<ca.length();i++)declared.add(ca.getString(i));JSONObject grants=next.getJSONObject("grants");ArrayList<String> oldGrants=new ArrayList<>();grants.keys().forEachRemaining(oldGrants::add);for(String k:oldGrants)if(!declared.contains(k))grants.remove(k);
  apps.put(m.getString("id"),next);JSONObject receipt=new JSONObject().put("first_install",a==null).put("id",m.getString("id")).put("version",m.getInt("version")).put("generation",s.getString("generation"));s.put("state","published").put("receipt",receipt);save();return copy(receipt);
 }
 synchronized void requestDesktop(String id,String generation)throws Exception {
  JSONObject a=db.getJSONObject("apps").getJSONObject(id(id));
  if(!generation.equals(a.getString("generation"))||!"installed".equals(a.getString("state")))throw new IllegalStateException("Unavailable");
  a.put("desktop_request",Math.max(System.currentTimeMillis(),a.optLong("desktop_request",0)+1));save();
 }
 synchronized JSONObject rollback(String appId,int expected,int version)throws Exception {
  JSONObject a=db.getJSONObject("apps").getJSONObject(id(appId));if(!a.getString("state").equals("installed")||a.getJSONObject("manifest").getInt("version")!=expected)throw new IllegalStateException("Version conflict");
  a.put("manifest",copy(a.getJSONObject("versions").getJSONObject(String.valueOf(version)).getJSONObject("manifest"))).put("grants",new JSONObject());save();return app(appId);
 }
 synchronized void discard(String stage)throws Exception {JSONObject s=db.getJSONObject("stages").optJSONObject(key(stage));if(s==null)return;removeTree(stageDir(stage));db.getJSONObject("stages").remove(stage);save();}
 synchronized boolean permitted(String appId,String generation,String cap)throws Exception {JSONObject a=app(appId);if(!a.getString("state").equals("installed")||!a.getString("generation").equals(generation))return false;JSONArray caps=a.getJSONObject("manifest").getJSONArray("capabilities");for(int i=0;i<caps.length();i++)if(caps.getString(i).equals(cap))return a.getJSONObject("grants").optBoolean(cap);return false;}
 synchronized void grant(String appId,String generation,String cap,boolean value)throws Exception {JSONObject a=db.getJSONObject("apps").getJSONObject(id(appId));if(!a.getString("state").equals("installed")||!a.getString("generation").equals(generation))throw new IllegalStateException("App changed");boolean declared=false;JSONArray caps=a.getJSONObject("manifest").getJSONArray("capabilities");for(int i=0;i<caps.length();i++)declared|=caps.getString(i).equals(cap);if(!declared)throw new SecurityException("Capability no longer declared");a.getJSONObject("grants").put(cap,value);save();}
 synchronized void deleting(String appId)throws Exception {JSONObject a=db.getJSONObject("apps").getJSONObject(id(appId));a.put("state","deleting").put("grants",new JSONObject());save();}
 synchronized void retire(String profile)throws Exception {if(!db.has("retired"))db.put("retired",new JSONObject());db.getJSONObject("retired").put(profile,true);save();}
 synchronized java.util.List<String> retired()throws Exception {ArrayList<String> names=new ArrayList<>();JSONObject r=db.optJSONObject("retired");if(r!=null)r.keys().forEachRemaining(names::add);return names;}
 synchronized void retiredDone(String name)throws Exception {if(db.has("retired")){db.getJSONObject("retired").remove(name);save();}}
 synchronized void removed(String appId)throws Exception {JSONObject a=app(appId);String gen=a.getString("generation");removeTree(new File(root,"apps/"+gen));JSONObject stages=db.getJSONObject("stages");ArrayList<String> remove=new ArrayList<>();for(Iterator<String> i=stages.keys();i.hasNext();){String k=i.next();if(gen.equals(stages.getJSONObject(k).getString("generation")))remove.add(k);}for(String k:remove){removeTree(stageDir(k));stages.remove(k);}db.getJSONObject("apps").remove(appId);save();}
 void removeTree(File f)throws IOException {String base=root.getCanonicalPath()+File.separator;if(!f.getCanonicalPath().startsWith(base))throw new IOException("Outside storage root");if(f.isDirectory()){File[] children=f.listFiles();if(children==null)throw new IOException("Cannot list directory");for(File child:children)removeTree(child);}if(f.exists()&&!f.delete())throw new IOException("Cannot remove "+f.getName());}
 File bundle(JSONObject a)throws Exception{return new File(root,"apps/"+a.getString("generation")+"/versions/"+a.getJSONObject("manifest").getInt("version"));}
 static String profile(JSONObject a)throws Exception{return "mini_"+a.getString("generation");}
 static String origin(JSONObject a)throws Exception{return "https://"+a.getString("generation")+".miniapp.invalid";}
}
