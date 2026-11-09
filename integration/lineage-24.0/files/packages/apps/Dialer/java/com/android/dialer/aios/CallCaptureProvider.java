package com.android.dialer.aios;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import com.android.incallui.call.CallRecorder;
/** Read-only capture conflict status for the system speech service. */
public final class CallCaptureProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public Bundle call(String method,String arg,Bundle extras){
  try{ApplicationInfo app=getContext().getPackageManager().getApplicationInfo("os.aios.speech",0);
   if(app.uid!=Binder.getCallingUid()||(app.flags&ApplicationInfo.FLAG_SYSTEM)==0||!"capture".equals(method))throw new SecurityException("System speech service required");
   Bundle b=new Bundle();b.putBoolean("recording",CallRecorder.getInstance().isRecording());return b;
  }catch(android.content.pm.PackageManager.NameNotFoundException e){throw new SecurityException("Speech service unavailable",e);}
 }
 public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}public String getType(Uri u){return null;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
