package os.aios.agent;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** One inventory for system capabilities, with explicit local/cloud bindings, as a Settings-style grouped page. */
public final class ModelManagementActivity extends Activity {
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle saved){super.onCreate(saved);}
    @Override public void onResume(){super.onResume();render();}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private int resource(String name){return getResources().getIdentifier(name,"string",getPackageName());}
    private String label(String capability){int id=resource("models_cap_"+capability.replace('.','_'));return id==0?capability:getString(id);}
    private String modelLabel(String id){return id.equals("inherit")?getString(resource("models_inherit")):id.equals("builtin")?getString(resource("models_specialized")):id.equals(SharedModelCatalog.LOCAL)?"LFM2.5-350M Q4_K_M":id.startsWith("cloud:")?id.substring(6):id.equals("none")?getString(resource("models_no_fallback")):id;}
    private void render(){
        setTitle(resource("models_title"));
        LinearLayout page=AiosUi.column(this);
        page.addView(AiosUi.appBar(this,getString(resource("models_title")),this::finish));
        LinearLayout status=AiosUi.card(this);page.addView(status);
        status.addView(AiosUi.secondary(this,getString(resource("models_description"))));AiosUi.gap(status,10);
        LinearLayout state=AiosUi.row(this);ProgressBar spinner=AiosUi.progress(this);LinearLayout.LayoutParams sp=AiosUi.lp(AiosUi.dp(this,18),AiosUi.dp(this,18));sp.rightMargin=AiosUi.dp(this,12);state.addView(spinner,sp);
        TextView statusText=AiosUi.body(this,getString(resource("models_loading")));state.addView(statusText,AiosUi.weight());status.addView(state);
        worker.execute(()->{String text;boolean ready=false;try{
            JSONObject info=new os.aios.runtime.CoreRuntimeClient(this).call("inference","status",new JSONObject());
            int id=resource("models_state_"+info.optString("state"));String name=id==0?getString(resource("models_state_unverified")):getString(id);ready=info.optString("state").equals("available");
            text=getString(resource("models_local_status"),name,android.text.format.Formatter.formatShortFileSize(this,info.optLong("bytes")));
            JSONObject photo=info.optJSONObject("photo_models");if(photo!=null&&SharedModelCatalog.ready(this,"photo-index")){text+="\n"+getString(resource(photo.optString("state").equals("model_disabled")?"model_choice_disabled":photo.optString("state").equals("ready")?"models_photo_ready":"models_photo_unverified"));if(!photo.optBoolean("face_verified"))text+="\n"+getString(resource("optional_model_face_unavailable"));}
        }catch(Exception e){text=getString(resource("models_read_failed"));}String result=text;boolean ok=ready;runOnUiThread(()->{if(isDestroyed())return;statusText.setText(result);
            state.removeView(spinner);View dot=AiosUi.dot(this,ok?AiosTheme.accent(this):AiosTheme.warn(this));LinearLayout.LayoutParams dp=AiosUi.lp(AiosUi.dp(this,8),AiosUi.dp(this,8));dp.rightMargin=AiosUi.dp(this,16);state.addView(dot,0,dp);});});
        try{
            JSONArray capabilities=SharedModelCatalog.capabilities(this);
            LinearLayout list=AiosUi.group(this);
            java.util.ArrayList<Object[]> specs=new java.util.ArrayList<>();
            for(int i=0;i<capabilities.length();i++){
                JSONObject row=capabilities.getJSONObject(i);String id=row.getString("id");JSONObject binding=row.getJSONObject("binding");boolean selectable=row.optBoolean("selectable");
                String summary=row.optString("state").equals("not_integrated")?getString(resource("models_not_integrated")):modelLabel(binding.getString("model"));
                if(id.equals("photo.image_embedding")||id.equals("photo.text_embedding"))summary="OpenCLIP ViT-B/16 DataComp · LiteRT GPU";
                if(id.equals("photo.face_detection"))summary="BlazeFace · LiteRT GPU";
                specs.add(new Object[]{id.startsWith("photo.")?"image":id.startsWith("notification.")?"notification":"model",label(id),summary,selectable?(Runnable)()->choose(id,false):null});
                if(selectable)specs.add(new Object[]{"refresh",getString(resource("models_fallback")),modelLabel(binding.optString("fallback","none")),(Runnable)()->choose(id,true)});
            }
            for(int i=0;i<specs.size();i++){Object[] s=specs.get(i);LinearLayout row=AiosUi.settingRow(this,(String)s[0],(CharSequence)s[1],(CharSequence)s[2],null,i==0,i+1==specs.size(),(Runnable)s[3]);
                if(s[3]==null)for(int k=0;k<row.getChildCount();k++)if(row.getChildAt(k) instanceof LinearLayout){LinearLayout texts=(LinearLayout)row.getChildAt(k);for(int t=0;t<texts.getChildCount();t++)((TextView)texts.getChildAt(t)).setTextColor(AiosTheme.muted(this));}
                list.addView(row);}
            page.addView(list);
        }catch(Exception e){error();}
        LinearLayout optional=AiosUi.group(this);
        optional.addView(AiosUi.settingRow(this,"mic",getString(resource("model_choice_speech")),SpeechModelSelection.label(this),null,true,false,()->startActivity(new Intent(this,SpeechModelSelectionActivity.class))));
        if(os.aios.host.HostProduct.includes("photo-index"))optional.addView(AiosUi.settingRow(this,"image",getString(resource("model_choice_photo")),getString(resource("model_choice_mobileclip")),null,false,false,()->openModel(os.aios.host.HostBindings.CORE_PACKAGE,"os.aios.core.embedding.PhotoModelSelectionActivity")));
        if(os.aios.host.HostProduct.includes("local-inference"))optional.addView(AiosUi.settingRow(this,"model",getString(resource("optional_model_lfm")),getString(resource("optional_model_manage")),null,true,false,()->openModel(os.aios.host.HostBindings.CORE_PACKAGE,"os.aios.core.inference.LocalTextModelActivity")));
        if(os.aios.host.HostProduct.includes("photo-index"))optional.addView(AiosUi.settingRow(this,"image",getString(resource("optional_model_face")),getString(resource("optional_model_manage")),null,false,false,()->openModel(os.aios.host.HostBindings.CORE_PACKAGE,"os.aios.core.embedding.FaceModelActivity")));
        if(os.aios.host.HostProduct.includes("minutes"))optional.addView(AiosUi.settingRow(this,"model",getString(resource("optional_model_speaker")),getString(resource("optional_model_manage")),null,false,true,()->openModel("os.aios.speech","os.aios.speech.SpeakerModelActivity")));
        if(optional.getChildCount()>0)page.addView(optional);
        ScrollView scroll=new ScrollView(this);scroll.addView(page);scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        AiosUi.page(this,scroll);setContentView(scroll);
    }
    private void choose(String capability,boolean fallback){
        java.util.ArrayList<String> ids=new java.util.ArrayList<>(),names=new java.util.ArrayList<>();
        if(fallback){if(!capability.equals("text.default")){ids.add("inherit");names.add(getString(resource("models_inherit")));}ids.add("none");names.add(getString(resource("models_no_fallback")));}
        else if(!capability.equals("text.default")){ids.add("inherit");names.add(getString(resource("models_inherit")));}
        worker.execute(()->{
            if(SharedModelCatalog.ready(this,"local-inference")){ids.add(SharedModelCatalog.LOCAL);names.add(modelLabel(SharedModelCatalog.LOCAL));}
            try(CodexAccountClient client=new CodexAccountClient(this)){
                JSONObject catalog=client.call(new JSONObject().put("op","accounts/models"));String provider=catalog.optString("provider");JSONArray items=catalog.optJSONArray("items");
                if(!provider.isEmpty()&&items!=null)for(int i=0;i<items.length();i++){
                    JSONObject item=items.getJSONObject(i);String model=item.optString("model");if(model.isEmpty())continue;
                    ids.add("cloud:"+provider+":"+model);names.add(getString(resource("models_cloud_item"),provider,item.optString("displayName",model)));
                }
                if(!provider.isEmpty()&&!catalog.optString("model").isEmpty()){
                    String id="cloud:"+provider+":"+catalog.getString("model");if(!ids.contains(id)){ids.add(id);names.add(getString(resource("models_cloud_item"),provider,catalog.getString("model")));}
                }
            }catch(Exception ignored){/* Keep only choices independently confirmed available. */}
            int current;try{JSONObject value=SharedModelCatalog.binding(this,capability);current=ids.indexOf(value.optString(fallback?"fallback":"model",fallback?"none":"inherit"));}catch(Exception e){current=-1;}
            int checked=current;
            runOnUiThread(()->{if(isDestroyed())return;
                AiosUi.dialog(this).setTitle(fallback?getString(resource("models_fallback"))+" · "+label(capability):label(capability)).setSingleChoiceItems(names.toArray(new String[0]),checked,(d,index)->{d.dismiss();
                    Runnable save=()->{try{JSONObject value=SharedModelCatalog.binding(this,capability);value.put(fallback?"fallback":"model",ids.get(index));SharedModelCatalog.save(this,capability,value);render();}catch(Exception e){error();}};
                    if(ids.get(index).startsWith("cloud:"))AiosUi.dialog(this).setMessage(resource("models_cloud_disclosure")).setPositiveButton(resource("models_save"),(dialog,which)->save.run()).setNegativeButton(android.R.string.cancel,null).show();else save.run();
                }).setNegativeButton(android.R.string.cancel,null).show();
            });
        });
    }
    private void openModel(String owner,String activity){try{startActivity(new Intent().setClassName(owner,activity));}catch(Exception e){error();}}
    private void error(){Toast.makeText(this,resource("models_save_failed"),Toast.LENGTH_LONG).show();}
}
