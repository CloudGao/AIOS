package com.android.dialer.aios;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One async lookup per bind; recycled rows cannot inherit another call's badge. */
public final class CallRecordingUi {
 private static final ExecutorService io=Executors.newSingleThreadExecutor();
 private static final WeakHashMap<View,Object> requests=new WeakHashMap<>();
 private static final String TAG="aios.call.recordings";
 public static void bind(View root,long[] ids){root.post(()->{
  if(!(root instanceof LinearLayout))return;
  TextView badge=root.findViewWithTag(TAG);
  if(badge==null){
   badge=new TextView(root.getContext());badge.setTag(TAG);badge.setTextSize(13);badge.setTextColor(android.graphics.Color.rgb(35,79,169));
   float density=root.getResources().getDisplayMetrics().density;badge.setPadding((int)(72*density),(int)(6*density),(int)(16*density),(int)(12*density));badge.setMinHeight((int)(40*density));
   View card=root.findViewById(root.getResources().getIdentifier("call_log_row","id",root.getContext().getPackageName()));
   LinearLayout parent=(LinearLayout)root;if(card instanceof ViewGroup&&((ViewGroup)card).getChildCount()>0&&((ViewGroup)card).getChildAt(0) instanceof LinearLayout)parent=(LinearLayout)((ViewGroup)card).getChildAt(0);
   parent.addView(badge,new LinearLayout.LayoutParams(-1,-2));
  }
  bindView(badge,ids,null);
 });}
 public static void bindText(TextView view,long id){bindView(view,new long[]{id},view.getText().toString());}
 private static void bindView(TextView view,long[] ids,String original){
  Object ticket=new Object();requests.put(view,ticket);view.setOnClickListener(null);view.setClickable(false);
  if(original==null)view.setVisibility(View.GONE);
  long[] copy=ids==null?new long[0]:Arrays.copyOf(ids,Math.min(ids.length,100));Context context=view.getContext().getApplicationContext();
  io.execute(()->{JSONArray result;
   try{Bundle args=new Bundle();args.putLongArray("call_ids",copy);Bundle reply=context.getContentResolver().call(Uri.parse("content://os.aios.speech.minutes"),"call_recordings",null,args);result=new JSONObject(reply.getString("json")).getJSONArray("recordings");}catch(Exception e){result=new JSONArray();}
   JSONArray rows=result;view.post(()->{if(requests.get(view)!=ticket)return;if(rows.length()==0)return;
    int audio=0,text=0;for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row.optBoolean("recorded"))audio++;if(row.optBoolean("transcribed"))text++;}
    String label=audio>0?"已录音"+(rows.length()>1?" "+audio+" 份":""):(text>0?"录音文件缺失":"录音未成功");
    if(text>0)label+=" · 已转录"+(rows.length()>1?" "+text+" 份":"");
    view.setText(original==null?label+" · 查看":original+"\n"+label+" · 查看");view.setVisibility(View.VISIBLE);view.setClickable(true);view.setOnClickListener(v->open(view.getContext(),rows));
   });
  });
 }
 private static void open(Context context,JSONArray rows){
  if(rows.length()==1){show(context,rows.optJSONObject(0));return;}
  String[] choices=new String[rows.length()];for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);choices[i]=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(row.optLong("date")))+" · "+(row.optBoolean("recorded")?"已录音":"录音未成功")+(row.optBoolean("transcribed")?" · 已转录":"");}
  new AlertDialog.Builder(context).setTitle("本组通话的录音与转写").setItems(choices,(d,n)->show(context,rows.optJSONObject(n))).setNegativeButton("关闭",null).show();
 }
 private static void show(Context context,JSONObject row){
  try{context.startActivity(new Intent().setClassName("os.aios.speech","os.aios.speech.MinutesActivity").putExtra("kind","call").putExtra("session_id",row.optString("session_id")).putExtra("view_only",true));}catch(ActivityNotFoundException e){Toast.makeText(context,"转写服务暂不可用",Toast.LENGTH_SHORT).show();}
 }
}
