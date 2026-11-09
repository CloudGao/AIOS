package os.aios.runtime.models;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.InputStream;
import os.aios.core.AiosUi;

/** Runs in the model owner's UID. Download/import is always an explicit user action. */
public abstract class OptionalModelActivity extends Activity {
    protected abstract String modelId();
    protected void installed()throws Exception{}
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean cancelled;private boolean busy;private TextView status;private LinearLayout actions;
    private int resource(String name){return getResources().getIdentifier("optional_model_"+name,"string",getPackageName());}
    private String text(String name){return getString(resource(name));}
    @Override public void onCreate(Bundle state){super.onCreate(state);render();}
    private void render(){
        OptionalModelSpec spec=OptionalModelSpec.get(modelId());setTitle(resource(spec.id));
        LinearLayout page=AiosUi.column(this);page.addView(AiosUi.appBar(this,text(spec.id),this::finish));
        page.addView(AiosUi.body(this,text("description")));
        page.addView(AiosUi.body(this,text(spec.licensePending?"license_pending":"license_terms")));
        page.addView(AiosUi.secondary(this,android.text.format.Formatter.formatFileSize(this,spec.bytes)+"\nSHA-256: "+spec.sha256));
        status=AiosUi.body(this,text("checking"));page.addView(status);actions=AiosUi.column(this);page.addView(actions);
        button("source",()->open(spec.source));button("license",()->open(spec.license));
        if(spec.url.isEmpty())page.addView(AiosUi.body(this,text("import_only")));
        else button("download",()->new AlertDialog.Builder(this).setTitle(resource("download")).setMessage(resource(spec.licensePending?"confirm_pending":"confirm"))
            .setPositiveButton(resource("continue"),(d,w)->run(()->OptionalModelStore.download(this,spec.id,()->cancelled))).setNegativeButton(android.R.string.cancel,null).show());
        button("import",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),41));
        ScrollView scroll=new ScrollView(this);scroll.addView(page);scroll.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets b=i.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});AiosUi.page(this,scroll);setContentView(scroll);
        worker.execute(()->{boolean ready=OptionalModelStore.verified(this,spec.id);runOnUiThread(()->{if(!isDestroyed()&&!busy)status.setText(resource(ready?"ready":"missing"));});});
    }
    private void button(String label,Runnable click){actions.addView(AiosUi.textButton(this,text(label),click));}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){status.setText(resource("no_browser"));}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();run(()->{try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new java.io.IOException("model_input_unavailable");OptionalModelStore.importFile(this,modelId(),input,()->cancelled);}});}}
    private interface Work{void execute()throws Exception;}
    private void run(Work work){if(busy)return;busy=true;status.setText(resource("working"));for(int i=0;i<actions.getChildCount();i++)actions.getChildAt(i).setEnabled(false);
        worker.execute(()->{boolean success=false;try{work.execute();installed();success=true;}catch(Exception e){android.util.Log.w("AIOSOptionalModel","Model operation failed",e);}final boolean ok=success;runOnUiThread(()->{if(isDestroyed())return;busy=false;status.setText(resource(ok?"ready":"failed"));for(int i=0;i<actions.getChildCount();i++)actions.getChildAt(i).setEnabled(true);});});
    }
    @Override public void onDestroy(){cancelled=true;worker.shutdownNow();super.onDestroy();}
}
