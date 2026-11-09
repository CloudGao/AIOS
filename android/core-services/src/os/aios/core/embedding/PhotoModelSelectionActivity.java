package os.aios.core.embedding;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import java.io.File;
import os.aios.core.AiosUi;

/** Current paired profile or disabled; never relabel an untested alternative as compatible. */
public final class PhotoModelSelectionActivity extends Activity {
    private int id(String name){return getResources().getIdentifier("model_choice_"+name,"string",getPackageName());}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);setTitle(id("photo"));
        LinearLayout page=AiosUi.column(this);page.addView(AiosUi.appBar(this,getString(id("photo")),this::finish));page.addView(AiosUi.body(this,getString(id("photo_description"))));
        File disabled=new File(getFilesDir(),"photo-model.disabled");
        RadioGroup choices=new RadioGroup(this);RadioButton current=new RadioButton(this),off=new RadioButton(this);current.setId(1);off.setId(2);current.setText(id("mobileclip"));off.setText(id("disabled"));choices.addView(current);choices.addView(off);choices.check(disabled.exists()?2:1);page.addView(choices);
        page.addView(AiosUi.textButton(this,getString(id("save")),()->{try{
            if(choices.getCheckedRadioButtonId()==2){try(java.io.FileOutputStream out=new java.io.FileOutputStream(disabled)){out.getFD().sync();}}
            else{java.nio.file.Files.deleteIfExists(disabled.toPath());BundledModels.initialize(this);}
            finish();
        }catch(Exception error){AiosUi.dialog(this).setMessage(id("failed")).setPositiveButton(android.R.string.ok,null).show();}}));
        ScrollView scroll=new ScrollView(this);scroll.addView(page);scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});AiosUi.page(this,scroll);setContentView(scroll);
    }
}
