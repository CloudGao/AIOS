package os.aios.agent;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import os.aios.core.AiosUi;

/** Official discovery links and Android-owned app/default selection. No silent installation. */
public final class OptionalAppsActivity extends Activity {
    private int id(String name){return getResources().getIdentifier("optional_apps_"+name,"string",getPackageName());}
    private String text(String name){return getString(id(name));}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);setTitle(id("title"));
        LinearLayout page=AiosUi.column(this);page.addView(AiosUi.appBar(this,text("title"),this::finish));
        page.addView(AiosUi.body(this,text("description")));page.addView(AiosUi.secondary(this,text("browser_limit")));
        link(page,"chromium","https://www.chromium.org/getting-involved/download-chromium/");
        link(page,"collabora","https://www.collaboraonline.com/collabora-office-android-ios/");
        link(page,"pdf","https://gitlab.com/mudlej_android/mj_pdf_reader");
        link(page,"editor","https://github.com/billthefarmer/editor/releases");
        link(page,"tts","https://k2-fsa.github.io/sherpa/onnx/tts/apk-engine.html");
        page.addView(AiosUi.textButton(this,text("installed"),()->open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))));
        page.addView(AiosUi.textButton(this,text("defaults"),()->open(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))));
        page.addView(AiosUi.textButton(this,text("tts_settings"),()->open(new Intent("com.android.settings.TTS_SETTINGS"))));
        ScrollView scroll=new ScrollView(this);scroll.addView(page);scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});AiosUi.page(this,scroll);setContentView(scroll);
    }
    private void link(LinearLayout page,String label,String url){page.addView(AiosUi.textButton(this,text(label),()->open(new Intent(Intent.ACTION_VIEW,Uri.parse(url)))));}
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException error){AiosUi.dialog(this).setMessage(id("unavailable")).setPositiveButton(android.R.string.ok,null).show();}}
}
