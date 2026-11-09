package os.aios.agent;

import android.app.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;

/** Native rule management as a Settings-style grouped page. The displayed order is the exact evaluation order. */
public final class NotificationManagementActivity extends Activity {
    private NotificationLedger ledger;
    private final android.database.ContentObserver changes=new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())){
        @Override public void onChange(boolean self){if(editing==-2&&!isFinishing()&&!isDestroyed())render();}
    };
    @Override protected void onStart(){super.onStart();getContentResolver().registerContentObserver(NotificationGovernanceProvider.URI,true,changes);}
    @Override protected void onStop(){getContentResolver().unregisterContentObserver(changes);super.onStop();}
    private LinearLayout body;
    private boolean trash;
    private long revision;
    private JSONArray rules;
    private String draftPackage="",draftValue="",draftInstruction="";
    private int draftField,draftAction,draftImportance=1,draftError;
    private int editing=-2;
    private EditText packageInput,valueInput,instructionInput;
    private AiosUi.Choice fieldInput,actionInput,importanceInput,errorInput;
    private int res(String key){return getResources().getIdentifier("governance_"+key,"string",getPackageName());}
    private String text(String key){return getString(res(key));}
    private android.content.SharedPreferences prefs(){return getSharedPreferences("notification_preferences",0);}
    @Override public void onCreate(Bundle state){super.onCreate(state);ledger=new NotificationLedger(this);
        if(state!=null){trash=state.getBoolean("trash");draftPackage=state.getString("package","");draftValue=state.getString("value","");draftInstruction=state.getString("instruction","");draftField=state.getInt("field");draftAction=state.getInt("action");editing=state.getInt("editing",-2);draftImportance=state.getInt("importance",1);draftError=state.getInt("error");}
        render();if(editing!=-2)edit(editing,true);
    }
    @Override protected void onSaveInstanceState(Bundle state){captureDraft();state.putBoolean("trash",trash);state.putString("package",draftPackage);state.putString("value",draftValue);state.putString("instruction",draftInstruction);state.putInt("field",draftField);state.putInt("action",draftAction);state.putInt("editing",editing);state.putInt("importance",draftImportance);state.putInt("error",draftError);super.onSaveInstanceState(state);}
    @Override public void onDestroy(){ledger.close();super.onDestroy();}
    private void captureDraft(){if(packageInput!=null){draftPackage=String.valueOf(packageInput.getTag());draftValue=valueInput.getText().toString();draftInstruction=instructionInput.getText().toString();draftField=fieldInput.getSelectedItemPosition();draftAction=actionInput.getSelectedItemPosition();draftImportance=importanceInput.getSelectedItemPosition();draftError=errorInput.getSelectedItemPosition();}}

    // ---- Page --------------------------------------------------------------------------------
    private void render(){
        setTitle(res("title"));
        LinearLayout page=AiosUi.column(this);
        page.addView(AiosUi.appBar(this,text("title"),this::finish));
        page.addView(AiosUi.chipStrip(this,AiosUi.chip(this,text("rules"),!trash,()->{if(trash){trash=false;render();}}),AiosUi.chip(this,text("trash"),trash,()->{if(!trash){trash=true;render();}})));
        AiosUi.gap(page,8);
        body=AiosUi.column(this);page.addView(body);
        AiosUi.gap(page,trash?24:96);
        ScrollView scroll=new ScrollView(this);scroll.addView(page);scroll.setClipToPadding(false);
        FrameLayout root=new FrameLayout(this);root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(!trash){
            LinearLayout fab=AiosUi.fab(this,"add",text("add"),()->edit(-1,false));
            FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM|android.view.Gravity.END);fp.setMargins(0,0,AiosUi.dp(this,16),AiosUi.dp(this,24));root.addView(fab,fp);
        }
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        AiosUi.page(this,root);setContentView(root);
        try{if(trash){showTrash();return;}
            JSONObject snapshot=ledger.rules();revision=snapshot.getLong("revision");rules=snapshot.getJSONArray("items");
            LinearLayout settings=AiosUi.group(this);body.addView(settings);
            Switch filter=AiosUi.m3switch(this,prefs().getBoolean("model_filter",true),value->prefs().edit().putBoolean("model_filter",value).apply());
            LinearLayout filterRow=AiosUi.settingRow(this,"filter",text("model_filter"),null,filter,true,false,null);AiosUi.clickable(filterRow,filter::toggle);settings.addView(filterRow);
            Switch smart=AiosUi.m3switch(this,prefs().getBoolean("smart_importance",false),value->prefs().edit().putBoolean("smart_importance",value).apply());
            LinearLayout smartRow=AiosUi.settingRow(this,"tune",text("smart_importance"),null,smart,false,false,null);AiosUi.clickable(smartRow,smart::toggle);settings.addView(smartRow);
            java.text.NumberFormat numbers=java.text.NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0));
            settings.addView(AiosUi.settingRow(this,"tasks",text("summary_title"),getString(res("summary_value"),numbers.format(prefs().getInt("summary_threshold",20))),null,false,true,this::summarySettings));
            TextView filterHelp=AiosUi.secondary(this,text("model_filter_help"));filterHelp.setPadding(AiosUi.dp(this,32),AiosUi.dp(this,8),AiosUi.dp(this,32),AiosUi.dp(this,8));body.addView(filterHelp);
            body.addView(AiosUi.label(this,text("rules")));
            TextView help=AiosUi.secondary(this,text("order_help"));help.setPadding(AiosUi.dp(this,32),0,AiosUi.dp(this,32),AiosUi.dp(this,12));body.addView(help);
            if(rules.length()==0){body.addView(AiosUi.empty(this,"notification",text("no_rules"),null));return;}
            LinearLayout list=AiosUi.group(this);body.addView(list);
            for(int i=0;i<rules.length();i++){
                final int index=i;JSONObject rule=rules.getJSONObject(i);
                String name=rule.optString("name",text("rule")),action=rule.getString("action");
                Switch enabled=AiosUi.m3switch(this,rule.optBoolean("enabled",true),value->{try{rule.put("enabled",value);save();}catch(Exception e){failure();}});
                LinearLayout row=AiosUi.settingRow(this,actionIcon(action),getString(res("rule_position"),i+1,name),conditionLabel(rule.optJSONObject("when"))+" · "+text(action),enabled,i==0,i+1==rules.length(),()->edit(index,false));
                if(!rule.optBoolean("enabled",true))for(TextView t:texts(row))t.setTextColor(AiosTheme.muted(this));
                list.addView(row);
            }
        }catch(Exception e){failure();}
    }
    private static String actionIcon(String action){switch(action){case "allow":return "check";case "model":return "model";default:return "filter";}}
    private java.util.List<TextView> texts(View v){java.util.ArrayList<TextView> out=new java.util.ArrayList<>();if(v instanceof TextView)out.add((TextView)v);else if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)out.addAll(texts(g.getChildAt(i)));}return out;}
    private String conditionLabel(JSONObject condition)throws JSONException{
        if(condition==null||condition.length()==0)return text("otherwise");
        if(condition.has("all")||condition.has("any")){boolean all=condition.has("all");JSONArray children=condition.getJSONArray(all?"all":"any");if(children.length()==0)return text("otherwise");java.util.ArrayList<String> labels=new java.util.ArrayList<>();for(int i=0;i<children.length();i++)labels.add(conditionLabel(children.getJSONObject(i)));return android.text.TextUtils.join(text(all?"and":"or"),labels);}
        if(condition.has("not"))return getString(res("not"),conditionLabel(condition.getJSONObject("not")));
        return text("field_"+condition.getString("field"))+" "+text(condition.getString("op"))+" “"+condition.getString("value")+"”";
    }
    private void move(int index,int delta){try{Object other=rules.get(index+delta);rules.put(index+delta,rules.get(index));rules.put(index,other);save();}catch(Exception e){failure();}}
    private void save(){try{ledger.saveRules(rules,revision);render();}catch(Exception e){failure();render();}}

    // ---- Rule editor -------------------------------------------------------------------------
    private AiosUi.Choice choice(LinearLayout target,String label,String[] values,int selected){
        target.addView(AiosUi.fieldLabel(this,text(label)));String[] names=new String[values.length];for(int i=0;i<values.length;i++)names[i]=text(values[i]);
        AiosUi.Choice view=new AiosUi.Choice(this,names,Math.max(0,Math.min(selected,values.length-1)),null);target.addView(view.view);return view;
    }
    private void edit(int index,boolean restored){try{
        JSONObject original=index>=0?rules.getJSONObject(index):new JSONObject();
        if(!restored){draftPackage="";draftValue="";draftInstruction=original.optString("instruction");draftField=0;draftImportance=original.optInt("importance",1);draftError=original.optString("on_error","allow").equals("filter")?1:0;draftAction=Math.max(0,java.util.Arrays.asList("allow","filter","model").indexOf(original.optString("action","filter")));}
        editing=index;
        LinearLayout form=AiosUi.column(this);
        // Existing complex predicates are retained; editing their action never broadens them.
        boolean preserve=index>=0;
        if(preserve){form.addView(AiosUi.fieldLabel(this,text("condition")));TextView when=AiosUi.body(this,conditionLabel(original.optJSONObject("when")));when.setPadding(AiosUi.dp(this,16),0,AiosUi.dp(this,16),AiosUi.dp(this,16));form.addView(when);}
        LinearLayout conditions=AiosUi.column(this);form.addView(conditions);
        conditions.addView(AiosUi.fieldLabel(this,text("field_package")));
        packageInput=AiosUi.field(this,text("package_hint"),appLabel(draftPackage));packageInput.setTag(draftPackage);packageInput.setFocusable(false);packageInput.setCursorVisible(false);packageInput.setOnClickListener(v->chooseApp());
        packageInput.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,new os.aios.core.AiosIcon(this,"chevron",AiosTheme.muted(this)),null);conditions.addView(packageInput);
        fieldInput=choice(conditions,"condition",new String[]{"field_text","field_title","field_channel","field_conversation"},draftField);
        valueInput=AiosUi.field(this,text("contains_hint"),draftValue);valueInput.setMaxLines(5);conditions.addView(valueInput);
        if(preserve)conditions.setVisibility(View.GONE);
        actionInput=choice(form,"action",new String[]{"allow","filter","model"},draftAction);
        instructionInput=AiosUi.area(this,text("instruction_hint"),draftInstruction,2);form.addView(instructionInput);
        importanceInput=choice(form,"importance",new String[]{"importance_low","importance_normal","importance_high"},draftImportance);
        errorInput=choice(form,"on_error",new String[]{"allow","filter"},draftError);
        TextView help=AiosUi.secondary(this,text("model_help"));help.setPadding(AiosUi.dp(this,16),0,AiosUi.dp(this,16),AiosUi.dp(this,8));form.addView(help);
        final AlertDialog[] open=new AlertDialog[1];
        if(preserve){
            // Ordering and removal act on the saved list immediately; the editor closes first so the page re-renders.
            View up=index>0?AiosUi.tonalButton(this,text("move_up"),()->{open[0].dismiss();move(index,-1);}):null;
            View down=index+1<rules.length()?AiosUi.tonalButton(this,text("move_down"),()->{open[0].dismiss();move(index,1);}):null;
            form.addView(AiosUi.actions(this,AiosUi.dangerButton(this,text("delete"),()->{open[0].dismiss();rules.remove(index);save();}),up,down));
        }
        AlertDialog dialog=AiosUi.dialog(this).setTitle(res(index<0?"add":"edit")).setView(AiosUi.dialogContent(this,form)).setPositiveButton(res("save"),null).setNegativeButton(android.R.string.cancel,null).create();open[0]=dialog;
        dialog.setOnDismissListener(d->{editing=-2;packageInput=null;});dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            captureDraft();JSONObject rule=new JSONObject(original.toString());
            if(index<0){JSONArray all=new JSONArray();if(!draftPackage.trim().isEmpty())all.put(new JSONObject().put("field","package").put("op","equals").put("value",draftPackage.trim()));
                if(!draftValue.isEmpty())all.put(new JSONObject().put("field",new String[]{"text","title","channel","conversation"}[draftField]).put("op","contains").put("value",draftValue));
                rule.put("id",java.util.UUID.randomUUID().toString()).put("when",new JSONObject().put("all",all));}
            rule.put("action",new String[]{"allow","filter","model"}[draftAction]).put("instruction",draftInstruction).put("model",original.optString("model","inherit")).put("importance",draftImportance).put("on_error",draftError==0?"allow":"filter");
            JSONArray next=new JSONArray(rules.toString());if(index<0)next.put(rule);else next.put(index,rule);
            NotificationRules.validate(next);ledger.saveRules(next,revision);dialog.dismiss();render();
        }catch(Exception e){failure();}}));dialog.show();
    }catch(Exception e){failure();}}

    // ---- Trash -------------------------------------------------------------------------------
    private void showTrash()throws Exception{
        JSONArray rows=ledger.list(true,100,0);
        TextView help=AiosUi.secondary(this,text("trash_help"));help.setPadding(AiosUi.dp(this,32),0,AiosUi.dp(this,32),AiosUi.dp(this,12));body.addView(help);
        if(rows.length()==0){body.addView(AiosUi.empty(this,"delete",text("empty_trash"),null));return;}
        LinearLayout list=AiosUi.group(this);body.addView(list);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i),notice=row.getJSONObject("notification"),result=row.getJSONObject("result");
            String title=notice.optString("title"),app=appLabel(notice.getString("package")),category=result.optString("category","uncategorized");
            String when=android.text.format.DateUtils.formatDateTime(this,row.getLong("received"),android.text.format.DateUtils.FORMAT_SHOW_DATE|android.text.format.DateUtils.FORMAT_SHOW_TIME|android.text.format.DateUtils.FORMAT_ABBREV_ALL);
            String summary=when+" · "+text("category_"+category);if(!row.optString("job_state").isEmpty())summary+=" · "+text("job_"+row.optString("job_state"));
            list.addView(AiosUi.settingRow(this,category.equals("suspected_fraud")?"warning":"notification",title.isEmpty()?app:app+" · "+title,summary,null,i==0,i+1==rows.length(),()->trashDetail(row)));
        }
    }
    private void trashDetail(JSONObject row){try{
        JSONObject notice=row.getJSONObject("notification"),result=row.getJSONObject("result");String identity=row.getString("identity");String job=row.optString("job_state");
        LinearLayout form=AiosUi.column(this);
        TextView message=AiosUi.selectable(this,notice.optString("text"),16,AiosTheme.text(this));message.setPadding(AiosUi.dp(this,16),0,AiosUi.dp(this,16),AiosUi.dp(this,8));form.addView(message);
        String when=android.text.format.DateUtils.formatDateTime(this,row.getLong("received"),android.text.format.DateUtils.FORMAT_SHOW_DATE|android.text.format.DateUtils.FORMAT_SHOW_TIME);
        TextView meta=AiosUi.secondary(this,appLabel(notice.getString("package"))+" · "+when+"\n"+text("category_"+result.optString("category","uncategorized"))+(result.optString("reason").isEmpty()?"":" · "+result.optString("reason"))+(job.isEmpty()?"":"\n"+text("job_"+job)));
        meta.setPadding(AiosUi.dp(this,16),0,AiosUi.dp(this,16),AiosUi.dp(this,16));form.addView(meta);
        AlertDialog dialog=AiosUi.dialog(this).setTitle(os.aios.core.DisplayText.clean(notice.optString("title").isEmpty()?appLabel(notice.getString("package")):notice.optString("title"))).setView(AiosUi.dialogContent(this,form)).setNegativeButton(android.R.string.cancel,null).create();
        LinearLayout actions=AiosUi.group(this);java.util.ArrayList<View> items=new java.util.ArrayList<>();
        items.add(AiosUi.settingRow(this,"refresh",text("restore"),null,null,true,true,()->{dialog.dismiss();operation("restore",identity);}));
        if(!job.isEmpty())items.add(AiosUi.settingRow(this,"history",text("undo"),null,null,true,true,()->{dialog.dismiss();operation("undo",identity);}));
        if(job.equals("failed"))items.add(AiosUi.settingRow(this,"bolt",text("retry"),null,null,true,true,()->{dialog.dismiss();operation("block",identity);}));
        items.add(AiosUi.settingRow(this,"delete",text("delete"),null,null,true,true,()->{dialog.dismiss();ledger.clear(identity);NotificationGovernanceProvider.changed(this);render();}));
        for(int i=0;i<items.size();i++){View v=items.get(i);v.setBackground(AiosUi.ripple(this,AiosUi.groupShape(this,AiosTheme.surfaceHigh(this),i==0,i+1==items.size())));((LinearLayout.LayoutParams)v.getLayoutParams()).bottomMargin=i+1==items.size()?0:AiosUi.dp(this,2);actions.addView(v);}
        ((LinearLayout.LayoutParams)actions.getLayoutParams()).setMargins(0,0,0,AiosUi.dp(this,8));form.addView(actions);
        dialog.show();
    }catch(Exception e){failure();}}
    private void operation(String method,String identity){
        Bundle args=new Bundle();args.putString("identity",identity);
        Bundle result=getContentResolver().call(NotificationGovernanceProvider.URI,method,null,args);
        if(result!=null&&result.getString("error_code","").equals("notification_expired"))Toast.makeText(this,res("restore_expired"),Toast.LENGTH_LONG).show();else if(result==null||result.containsKey("error"))failure();else render();
    }

    // ---- Pickers -----------------------------------------------------------------------------
    private String appLabel(String pkg){if(pkg.isEmpty())return text("all_apps");try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
    private void chooseApp(){
        java.util.List<android.content.pm.ApplicationInfo> apps=getPackageManager().getInstalledApplications(0);
        java.text.Collator collator=java.text.Collator.getInstance(getResources().getConfiguration().getLocales().get(0));
        apps.sort((left,right)->collator.compare(getPackageManager().getApplicationLabel(left).toString(),getPackageManager().getApplicationLabel(right).toString()));
        java.util.ArrayList<String> packages=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();packages.add("");labels.add(text("all_apps"));
        for(android.content.pm.ApplicationInfo app:apps){packages.add(app.packageName);labels.add(getPackageManager().getApplicationLabel(app).toString());}
        AiosUi.dialog(this).setTitle(res("package_hint")).setSingleChoiceItems(labels.toArray(new String[0]),packages.indexOf(String.valueOf(packageInput.getTag())),(d,index)->{draftPackage=packages.get(index);packageInput.setTag(draftPackage);packageInput.setText(labels.get(index));d.dismiss();}).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void summarySettings(){
        int[] thresholds={20,30,50,100};String[] choices=new String[thresholds.length];java.text.NumberFormat numbers=java.text.NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0));for(int i=0;i<thresholds.length;i++)choices[i]=numbers.format(thresholds[i]);
        int current=java.util.Arrays.binarySearch(thresholds,prefs().getInt("summary_threshold",20));
        AiosUi.dialog(this).setTitle(res("summary_settings")).setSingleChoiceItems(choices,current<0?0:current,(d,index)->{
            prefs().edit().putInt("summary_threshold",thresholds[index]).apply();d.dismiss();render();
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void failure(){Toast.makeText(this,res("failed"),Toast.LENGTH_LONG).show();}
}
