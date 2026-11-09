package os.aios.agent;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.widget.*;
import java.util.*;
import os.aios.core.AiosUi;

public final class SpeechModelSelectionActivity extends Activity {
    private int id(String name){return getResources().getIdentifier("model_choice_"+name,"string",getPackageName());}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);setTitle(id("speech"));
        LinearLayout page=AiosUi.column(this);page.addView(AiosUi.appBar(this,getString(id("speech")),this::finish));page.addView(AiosUi.body(this,getString(id("speech_description"))));
        ArrayList<String> values=new ArrayList<>(),labels=new ArrayList<>();values.add(SpeechModelSelection.DEFAULT);labels.add(getString(id("host_default")));values.add(SpeechModelSelection.SYSTEM);labels.add(getString(id("system_default")));
        ComponentName builtin=SpeechModelSelection.builtin(this);
        try{getPackageManager().getServiceInfo(builtin,0);values.add(builtin.flattenToString());labels.add(getString(id("sensevoice")));}catch(PackageManager.NameNotFoundException ignored){}
        for(ResolveInfo resolved:getPackageManager().queryIntentServices(new Intent("android.speech.RecognitionService"),0)){
            ServiceInfo service=resolved.serviceInfo;if(service==null||!service.enabled||!service.applicationInfo.enabled||!service.exported||!"android.permission.BIND_SPEECH_RECOGNITION_SERVICE".equals(service.permission))continue;
            String value=new ComponentName(service.packageName,service.name).flattenToString();if(values.contains(value))continue;
            values.add(value);labels.add(service.loadLabel(getPackageManager())+" · "+service.packageName);
        }
        String current=SpeechModelSelection.current(this);if(!values.contains(current))page.addView(AiosUi.secondary(this,getString(id("missing"))));
        for(int n=0;n<values.size();n++){String value=values.get(n),label=labels.get(n);RadioButton button=new RadioButton(this);button.setText(label);button.setChecked(value.equals(current));button.setOnClickListener(v->{
            AiosUi.dialog(this).setMessage(id("speech_confirm")).setPositiveButton(android.R.string.ok,(dialog,which)->{SpeechModelSelection.save(this,value);finish();}).setNegativeButton(android.R.string.cancel,(dialog,which)->button.setChecked(value.equals(current))).setOnCancelListener(dialog->button.setChecked(value.equals(current))).show();
        });page.addView(button);}
        ScrollView scroll=new ScrollView(this);scroll.addView(page);scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});AiosUi.page(this,scroll);setContentView(scroll);
    }
}
