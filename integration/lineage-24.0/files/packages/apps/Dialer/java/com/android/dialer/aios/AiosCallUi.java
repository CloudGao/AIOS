package com.android.dialer.aios;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import org.json.JSONObject;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallRecorder;
public final class AiosCallUi {
 public static boolean minutesActive(Context c){
  try{Bundle b=c.getContentResolver().call(Uri.parse("content://os.aios.speech.minutes"),"call_capture",null,null);return b!=null&&new JSONObject(b.getString("json","{}")).optBoolean("active");}catch(Exception e){return false;}
 }
 public static void open(Context c){
  if(CallList.getInstance().getActiveCall()==null){Toast.makeText(c,"接通电话后可开启通话纪要",Toast.LENGTH_SHORT).show();return;}
  if(CallRecorder.getInstance().isRecording()){Toast.makeText(c,"请先停止普通通话录音，再开启纪要",Toast.LENGTH_LONG).show();return;}
  try{c.startActivity(new Intent().setClassName("os.aios.speech","os.aios.speech.MinutesActivity").putExtra("kind","call"));}
  catch(ActivityNotFoundException e){Toast.makeText(c,"纪要服务暂不可用",Toast.LENGTH_SHORT).show();}
 }
 private AiosCallUi(){}
}
