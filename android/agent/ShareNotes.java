package os.aios.agent;
import android.app.*;import android.content.*;import android.content.pm.*;import org.json.*;import java.util.*;
final class ShareNotes {
 static String target(Context c){String pkg=c.getSharedPreferences("share_notes",0).getString("package","");if(pkg.isEmpty())try{c.getPackageManager().getApplicationInfo("net.gsantner.markor",0);return "net.gsantner.markor";}catch(Exception ignored){}return pkg;}
 static String label(Context c){String pkg=target(c);if(pkg.isEmpty())return c.getString(R.string.share_internal);try{return String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)));}catch(Exception e){return c.getString(R.string.share_missing);}}
 static void choose(Activity activity,Runnable changed){
  ArrayList<String> labels=new ArrayList<>(),packages=new ArrayList<>();
  Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain");Set<String> seen=new HashSet<>();
  for(ResolveInfo info:activity.getPackageManager().queryIntentActivities(share,0)){String pkg=info.activityInfo.packageName;if(pkg.equals(activity.getPackageName())||!seen.add(pkg))continue;packages.add(pkg);labels.add(String.valueOf(info.loadLabel(activity.getPackageManager())));}
  os.aios.core.AiosUi.dialog(activity).setTitle(activity.getString(R.string.share_target)).setSingleChoiceItems(labels.toArray(new String[0]),packages.indexOf(target(activity)),(d,n)->{activity.getSharedPreferences("share_notes",0).edit().putString("package",packages.get(n)).apply();d.dismiss();changed.run();}).setNegativeButton(android.R.string.cancel,null).show();
 }
 static String sources(Context c,JSONArray attachments)throws Exception{
  StringBuilder out=new StringBuilder("\n\n<!-- AIOS sources -->\n\n## ").append(c.getString(R.string.share_sources)).append("\n\n");
  for(int i=0;i<attachments.length();i++){JSONObject a=attachments.getJSONObject(i);String name=a.getString("name").replace("[","\\[").replace("]","\\]"),path=android.net.Uri.fromFile(new java.io.File(a.getString("path"))).toString();out.append(a.optString("mime").startsWith("image/")?"![":"[").append(name).append("](").append(path).append(")\n\n");}return out.toString();
 }
 static JSONObject publishSource(Context c,JSONObject file)throws Exception{
  JSONObject published=AssistantAttachments.publish(c,file);android.net.Uri uri=android.net.Uri.parse(published.getString("uri"));
  String name=published.getString("name").replaceAll("[\\\\/\\p{Cntrl}]","_");if(name.length()>160)name=name.substring(name.length()-160);
  android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"AIOS-"+uri.getLastPathSegment()+"-"+name);
  if(c.getContentResolver().update(uri,values,null,null)!=1)throw new java.io.IOException(c.getString(R.string.share_document_failed));
  return AssistantAttachments.describe(c,uri,published.getString("name"));
 }
 static JSONObject create(Context c,String job,String body,JSONArray attachments)throws Exception{
  // A process death after publishing but before the job checkpoint must not duplicate a note.
  android.net.Uri collection=android.provider.MediaStore.Files.getContentUri("external_primary");
  try(android.database.Cursor existing=c.getContentResolver().query(collection,new String[]{"_id","is_pending"},"_display_name=? AND relative_path=? AND owner_package_name=?",new String[]{"AIOS-"+job+".md","Documents/AIOS Notes/",c.getPackageName()},null)){
   if(existing!=null&&existing.moveToFirst()){android.net.Uri found=android.content.ContentUris.withAppendedId(collection,existing.getLong(0));if(existing.getInt(1)!=0){write(c,found,"# "+c.getString(R.string.share_note)+"\n\n"+body+sources(c,attachments));AssistantAttachments.publish(c,found);}return AssistantAttachments.describe(c,found,null);}
  }
  android.content.ContentValues values=new android.content.ContentValues();values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"AIOS-"+job+".md");values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"text/markdown");values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Documents/AIOS Notes");values.put(android.provider.MediaStore.MediaColumns.IS_PENDING,1);
  android.net.Uri uri=c.getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external_primary"),values);if(uri==null)throw new java.io.IOException(c.getString(R.string.share_document_failed));
  try{write(c,uri,"# "+c.getString(R.string.share_note)+"\n\n"+body+sources(c,attachments));AssistantAttachments.publish(c,uri);return AssistantAttachments.describe(c,uri,null);}catch(Exception e){AssistantAttachments.delete(c,uri);throw e;}
 }
 private static void write(Context c,android.net.Uri uri,String text)throws Exception{try(java.io.OutputStream out=c.getContentResolver().openOutputStream(uri,"rwt")){if(out==null)throw new java.io.IOException(c.getString(R.string.share_document_failed));out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
 private static String read(Context c,android.net.Uri uri)throws Exception{try(java.io.InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException(c.getString(R.string.share_document_failed));byte[] bytes=in.readNBytes(1048577);if(bytes.length>1048576)throw new java.io.IOException(c.getString(R.string.share_limit));return new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}}
 private static String hash(String value)throws Exception{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
 static synchronized JSONObject call(Context c,String action,JSONObject args)throws Exception{
  String id=args.getString("id");JSONObject job=new JSONObject(c.getSharedPreferences("share_jobs",0).getString(id,"{}"));if(!job.has("body"))throw new IllegalArgumentException(c.getString(R.string.share_empty));
  if(action.equals("source"))return new JSONObject().put("id",id).put("body",job.optString("body")).put("instruction",job.optString("instruction")).put("attachments",job.optJSONArray("copied")).put("original",job.optJSONObject("original")).put("default_notes_package",job.optString("target")).put("content_role","untrusted_reference_data");
  if(action.equals("create")&&!job.has("document")){String content=args.getString("markdown");if(content.length()>100000)throw new IllegalArgumentException(c.getString(R.string.share_limit));JSONArray attachments=new JSONArray(job.getJSONArray("copied").toString());if(job.has("original"))attachments.put(job.getJSONObject("original"));job.put("document",create(c,id,content,attachments));if(!c.getSharedPreferences("share_jobs",0).edit().putString(id,job.toString()).commit())throw new java.io.IOException(c.getString(R.string.share_document_failed));}
  JSONObject document=job.optJSONObject("document");if(document==null)throw new IllegalArgumentException(c.getString(R.string.share_document_failed));android.net.Uri uri=android.net.Uri.parse(document.getString("uri"));String markdown=read(c,uri);
  if(action.equals("update")){if(!hash(markdown).equals(args.getString("expected_sha256")))throw new IllegalStateException(c.getString(R.string.share_document_changed));String content=args.getString("markdown");if(content.length()>100000)throw new IllegalArgumentException(c.getString(R.string.share_limit));int footer=content.indexOf("<!-- AIOS sources -->");if(footer>=0)content=content.substring(0,footer);JSONArray attachments=new JSONArray(job.getJSONArray("copied").toString());if(job.has("original"))attachments.put(job.getJSONObject("original"));write(c,uri,content.stripTrailing()+sources(c,attachments));markdown=read(c,uri);}
  else if(!action.equals("get")&&!action.equals("create"))throw new IllegalArgumentException(action);
  return new JSONObject().put("state","succeeded").put("id",id).put("document",document).put("markdown",markdown).put("sha256",hash(markdown)).put("destination_package",job.optString("target")).put("storage","document_file_not_task");
 }
}
