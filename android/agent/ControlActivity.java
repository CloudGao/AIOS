package os.aios.agent;
import android.app.*;
import android.app.role.RoleManager;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
/** Device control & authorization as a Settings-style grouped page. Follows system day/night via Activity recreation. */
public class ControlActivity extends Activity {
 final android.os.Handler refresh=new android.os.Handler(android.os.Looper.getMainLooper());
 TextView slots,serviceState;View serviceDot;
 final Runnable update=new Runnable(){public void run(){
  AgentService a=AgentService.instance;
  if(slots!=null)try{slots.setText(a==null?"控制服务未运行":"已用 "+a.ui.listing().optInt("slot_count")+" / 4 个槽位 · 点击查看");}catch(Exception ignored){}
  if(serviceState!=null){serviceState.setText(a==null?"已停止":"运行中");serviceDot.setBackground(AiosUi.shape(ControlActivity.this,a==null?AiosTheme.outlineStrong(ControlActivity.this):AiosTheme.accent(ControlActivity.this),4));}
  refresh.postDelayed(this,700);
 }};
 void sessionCommand(String op){try{AgentService a=AgentService.instance;if(a!=null)a.ui.call(new org.json.JSONObject().put("op",op));}catch(Exception e){android.widget.Toast.makeText(this,os.aios.core.DisplayText.clean(e.toString()),0).show();}}
 protected void onResume(){super.onResume();refresh.post(update);}
 protected void onPause(){refresh.removeCallbacks(update);super.onPause();}
 int dp(int n){return AiosUi.dp(this,n);}
 public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout panel=AiosUi.column(this);
  panel.addView(AiosUi.appBar(this,"设备控制与授权",this::finish));
  LinearLayout group=AiosUi.group(this);panel.addView(group);
  Switch access=AiosUi.m3switch(this,Authorization.enabled(this),enabled->{Authorization.set(this,enabled);stopService(new Intent(this,AgentService.class));startForegroundService(new Intent(this,AgentService.class));});
  group.addView(AiosUi.settingRow(this,"shield","允许 AIOS 控制设备","总授权；支持全局授权的系统上后台操作无需逐个确认",access,true,false,null));
  LinearLayout screens=AiosUi.settingRow(this,"screen","后台虚拟屏","正在读取…",null,false,true,()->startActivity(new Intent(this,UiSessionsActivity.class)));group.addView(screens);slots=summaryOf(screens);
  TextView note=AiosUi.secondary(this,"前台操作还需在设置 → 前台操作授权中开启 AIOS 前台控制；应用登录及其他系统权限仍按实际需要处理。");note.setPadding(dp(32),0,dp(32),dp(8));panel.addView(note);
  panel.addView(AiosUi.label(this,"本机控制服务"));
  LinearLayout service=AiosUi.group(this);panel.addView(service);
  LinearLayout state=AiosUi.row(this);serviceDot=AiosUi.dot(this,AiosTheme.outlineStrong(this));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(8),dp(8));dl.rightMargin=dp(8);state.addView(serviceDot,dl);serviceState=AiosUi.secondary(this,"…");state.addView(serviceState);
  service.addView(AiosUi.settingRow(this,"bolt","控制服务状态","后台执行、虚拟屏与事件助手依赖此服务",state,true,false,null));
  LinearLayout actions=AiosUi.row(this);actions.setPadding(dp(16),dp(12),dp(16),dp(16));actions.setBackground(AiosUi.groupShape(this,AiosTheme.surface(this),false,true));actions.setGravity(android.view.Gravity.END);
  LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=dp(8);
  actions.addView(AiosUi.textButton(this,"停止",()->stopService(new Intent(this,AgentService.class))));
  actions.addView(AiosUi.tonalButton(this,"启动",()->{
   if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},2);
   else startForegroundService(new Intent(this,AgentService.class));
  }),gap);
  service.addView(actions);
  panel.addView(AiosUi.label(this,"更多"));
  LinearLayout more=AiosUi.group(this);panel.addView(more);
  more.addView(AiosUi.settingRow(this,"assistant","设为默认助手","在系统语音输入设置中选择 AIOS",null,true,false,()->startActivity(new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))));
  more.addView(AiosUi.settingRow(this,"bell","待处理请求","继续被系统阻止的后台操作",null,false,true,()->startActivity(new Intent(this,UserRequest.class))));
  ScrollView scroll=new ScrollView(this);scroll.addView(panel);scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
  AiosUi.page(this,scroll);setContentView(scroll);
  startForegroundService(new Intent(this,AgentService.class));
 }
 static TextView summaryOf(LinearLayout row){for(int i=0;i<row.getChildCount();i++){if(row.getChildAt(i) instanceof LinearLayout){LinearLayout texts=(LinearLayout)row.getChildAt(i);if(texts.getChildCount()>1&&texts.getChildAt(1) instanceof TextView)return (TextView)texts.getChildAt(1);}}return null;}
}
