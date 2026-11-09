package os.aios.miniapp;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;

/** Display-only directory. This provider cannot install, delete, or grant capabilities. */
public final class LauncherProvider extends ContentProvider {
 public static final Uri URI=Uri.parse("content://os.aios.miniapp.launcher");
 public boolean onCreate(){return true;}
 static JSONObject entry(JSONObject a)throws Exception {
  JSONObject m=a.getJSONObject("manifest");
  return new JSONObject().put("id",m.getString("id")).put("name",os.aios.core.DisplayText.clean(m.getString("name")))
   .put("version",m.getInt("version")).put("generation",a.getString("generation"))
   .put("state",a.getString("state")).put("desktop_request",a.optLong("desktop_request",0));
 }
 public Bundle call(String method,String arg,Bundle extras){
  if(!"list".equals(method))throw new IllegalArgumentException("Read-only directory");
  try{JSONArray out=new JSONArray(),apps=Store.get(getContext()).list();
   for(int i=0;i<apps.length();i++)out.put(entry(apps.getJSONObject(i)));
   Bundle b=new Bundle();b.putString("json",out.toString());return b;
  }catch(Exception e){throw new IllegalStateException("Directory unavailable",e);}
 }
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
 public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
