package os.aios.agent;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads only the GSF registration identifier; authentication stays in the browser. */
public final class GoogleRegistrationActivity extends Activity {
    private static final String REGISTRATION_URL="https://www.google.com/android/uncertified/";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView copy,register,retry;
    private String gsfId;
    private int dp(int n){return AiosUi.dp(this,n);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=AiosUi.column(this);
        scroll.setFillViewport(true);scroll.addView(panel);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        scroll.requestApplyInsets();
        AiosUi.page(this,scroll);
        panel.addView(AiosUi.appBar(this,"Google 设备注册",this::finish));
        TextView intro=AiosUi.secondary(this,"为使用自定义系统的这台设备登记 Google 服务框架 ID（GSF ID）。这不是 ROM 认证，也不保证所有 Google 服务和应用都能使用。");intro.setPadding(dp(16),0,dp(16),dp(12));panel.addView(intro);
        LinearLayout card=AiosUi.card(this);panel.addView(card);
        TextView idLabel=AiosUi.label(this,"GSF ID");idLabel.setPadding(0,0,0,dp(4));card.addView(idLabel);
        status=AiosUi.text(this,"正在读取 GSF ID…",20,AiosTheme.text(this));status.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));status.setTextIsSelectable(true);card.addView(status);
        retry=AiosUi.textButton(this,"重新读取",this::readId);copy=AiosUi.tonalButton(this,"复制 ID",()->copyId());register=AiosUi.filledButton(this,"复制并打开注册页",()->{if(copyId())openPage();});
        card.addView(AiosUi.actions(this,retry,copy,register));
        LinearLayout steps=AiosUi.group(this);panel.addView(steps);
        String[] titles={"复制 ID 并打开官方注册页","在浏览器中自行登录 Google 账号","在 Android ID 输入框长按粘贴，完成人机验证并提交"};
        for(int i=0;i<titles.length;i++){TextView n=AiosUi.text(this,String.valueOf(i+1),14,AiosTheme.onTonal(this));n.setGravity(android.view.Gravity.CENTER);n.setBackground(AiosUi.shape(this,AiosTheme.tonal(this),12));LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(dp(24),dp(24));n.setLayoutParams(nl);LinearLayout row=AiosUi.settingRow(this,null,titles[i],null,null,i==0,i==titles.length-1,null);row.addView(n,0);nl.rightMargin=dp(16);steps.addView(row);}
        TextView note=AiosUi.meta(this,"返回本页可以重新复制。AIOS 不读取登录信息，不会自动提交注册；是否登记成功以 Google 页面结果为准。");note.setPadding(dp(32),0,dp(32),dp(16));panel.addView(note);
        LinearLayout footer=AiosUi.actions(this,AiosUi.textButton(this,"仅打开 Google 注册页",this::openPage));footer.setPadding(dp(16),0,dp(16),dp(16));panel.addView(footer);
        readId();
    }
    private void readId(){
        gsfId=null;copy.setEnabled(false);register.setEnabled(false);retry.setEnabled(false);copy.setAlpha(.5f);register.setAlpha(.5f);status.setText("正在读取 GSF ID…");
        worker.execute(()->{
            String id=null,message;
            try{
                getPackageManager().getPackageInfo("com.google.android.gsf",0);
                try(Cursor cursor=getContentResolver().query(Uri.parse("content://com.google.android.gsf.gservices"),null,null,new String[]{"android_id"},null)){
                    if(cursor!=null&&cursor.moveToFirst()&&cursor.getColumnCount()>=2)id=GoogleRegistrationId.normalize(cursor.getString(1));
                }
                message=id==null?"尚未取得 GSF ID。Google 服务框架可能尚未完成初始化，请联网打开 Google 应用后重试。":id;
            }catch(android.content.pm.PackageManager.NameNotFoundException e){message="未安装 Google 服务框架（GSF），无法读取注册 ID。安装与系统兼容的 Google 服务组件并完成初始化后，请重新读取。";}
            catch(SecurityException e){message="Google 服务框架不允许 AIOS 读取 GSF ID。此版本需要兼容的读取权限，不能用普通 Android ID 代替。";}
            catch(Exception e){message="暂时无法读取 GSF ID，请确认 Google 服务框架可用后重试。";}
            final String result=id,info=message;
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;gsfId=result;status.setText(info);status.setTextSize(result==null?14:20);retry.setEnabled(true);copy.setEnabled(result!=null);register.setEnabled(result!=null);copy.setAlpha(result!=null?1f:.5f);register.setAlpha(result!=null?1f:.5f);});
        });
    }
    private boolean copyId(){
        if(gsfId==null)return false;
        try{ClipboardManager clipboard=getSystemService(ClipboardManager.class);clipboard.setPrimaryClip(ClipData.newPlainText("Google 服务框架 ID",gsfId));Toast.makeText(this,os.aios.core.DisplayText.clean("GSF ID 已复制；登录后在 Android ID 输入框粘贴"),Toast.LENGTH_LONG).show();return true;}
        catch(RuntimeException e){Toast.makeText(this,os.aios.core.DisplayText.clean("复制失败，请长按上方 ID 手动复制"),Toast.LENGTH_LONG).show();return false;}
    }
    private void openPage(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(REGISTRATION_URL)).addCategory(Intent.CATEGORY_BROWSABLE));}
        catch(android.content.ActivityNotFoundException e){Toast.makeText(this,os.aios.core.DisplayText.clean("未找到浏览器，请先安装或启用浏览器"),Toast.LENGTH_LONG).show();}}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
