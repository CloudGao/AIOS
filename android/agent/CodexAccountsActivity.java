package os.aios.agent;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosTheme;
import os.aios.core.AiosUi;
import java.util.concurrent.*;

/** Native account dialog; all credentials go only through the signed runtime Binder. */
public final class CodexAccountsActivity extends Activity {
    static final String[] IDS={"chatgpt","deepseek","glm","qwen_coding","qwen","kimi","claude","custom"};
    String[] NAMES;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    CodexAccountClient client; LinearLayout body; TextView status,code; EditText key,model,url; TextView primary,web,cancel;
    int dp(int n){return AiosUi.dp(this,n);}
    String selected="",codeValue="",verificationUrl="";JSONObject snapshot=new JSONObject();boolean busy,foreground,webLoginView; int inFlight, generation; String lastError="";
    final Runnable poll=()->refresh(false);
    public void onCreate(Bundle b){super.onCreate(b);NAMES=new String[]{getString(R.string.account_chatgpt),getString(R.string.account_deepseek),getString(R.string.account_glm),getString(R.string.account_qwen_coding),getString(R.string.account_qwen),getString(R.string.account_kimi),getString(R.string.account_claude),getString(R.string.account_custom)};setTitle(getString(R.string.account_login));client=new CodexAccountClient(this);
        getWindow().setLayout(-1,-2);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Object retained=getLastNonConfigurationInstance();
        if(retained instanceof Draft){Draft d=(Draft)retained;snapshot=d.snapshot;showDetail(d.provider,d.webLogin);if(key!=null){key.setText(d.key);model.setText(d.model);url.setText(d.url);}}
        else choose();refresh(false);
    }
    LinearLayout layout(){LinearLayout root=AiosUi.column(this);root.setPadding(dp(24),dp(16),dp(24),dp(20));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);return root;}
    TextView text(String value,int size){TextView t=AiosUi.text(this,value,size,size>=20?AiosTheme.text(this):AiosTheme.muted(this));if(size>=20)t.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));t.setPadding(0,dp(4),0,dp(8));body.addView(t);return t;}
    /** Full-width tonal button; the primary action of a page uses filled(). */
    TextView button(String value,Runnable action){TextView b=AiosUi.tonalButton(this,value,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);body.addView(b,lp);return b;}
    TextView filled(String value,Runnable action){TextView b=AiosUi.filledButton(this,value,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);body.addView(b,lp);return b;}
    TextView link(String value,Runnable action){TextView b=AiosUi.textButton(this,value,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.topMargin=dp(4);body.addView(b,lp);return b;}
    EditText field(String label,String value,boolean secret){body.addView(AiosUi.fieldLabel(this,label));EditText e=AiosUi.field(this,label,value);e.setSingleLine(true);os.aios.core.DisplayText.editable(e);e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));if(!secret)os.aios.core.DisplayText.editable(e);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);body.addView(e);return e;}
    void choose(){generation++;primary=null;web=null;cancel=null;code=null;key=null;model=null;url=null;selected="";lastError="";getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);body=layout();text(getString(R.string.account_title),23);status=text(getString(R.string.account_loading),14);
        String active=snapshot.optString("provider");
        for(int i=0;i<IDS.length;i++){final int n=i;boolean current=IDS[i].equals(active)||(IDS[i].equals("chatgpt")&&active.equals("openai"));LinearLayout row=AiosUi.settingRow(this,current?"check":null,NAMES[i],current?getString(R.string.account_active):null,null,i==0,i==IDS.length-1,()->detail(IDS[n]));body.addView(row);}
        body.addView(AiosUi.actions(this,AiosUi.textButton(this,getString(R.string.account_close),this::finish)));paint(snapshot);
    }
    void detail(String id){showDetail(id,false);}
    void showDetail(String id,boolean login){webLoginView=login;generation++;primary=null;web=null;cancel=null;code=null;key=null;model=null;url=null;selected=id;lastError="";body=layout();codeValue="";verificationUrl="";
        String name=id.equals("openai")?getString(R.string.gpt_api_title):id;for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))name=NAMES[i];
        LinearLayout head=AiosUi.row(this);head.addView(AiosUi.iconButton(this,"back",getString(R.string.account_back),()->{if(id.equals("openai")||login)detail("chatgpt");else choose();}));TextView heading=AiosUi.heading(this,name);heading.setPadding(dp(4),0,0,0);head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);hl.setMarginStart(-dp(12));body.addView(head,hl);
        status=text("",14);
        if(id.equals("chatgpt")&&!login){
            text(getString(R.string.gpt_auth_hint),14);
            button(getString(R.string.gpt_web_title),()->showDetail("chatgpt",true));
            button(getString(R.string.gpt_api_title),()->detail("openai"));
            JSONObject profiles=snapshot.optJSONObject("profiles"),legacy=profiles==null?null:profiles.optJSONObject("custom");
            if(legacy!=null&&legacy.optBoolean("configured")&&legacy.optString("baseUrl").replaceAll("/+$", "").equals("https://api.openai.com/v1"))
                button(getString(R.string.gpt_legacy),()->detail("custom"));
            paint(snapshot);return;
        }
        if(id.equals("chatgpt")){
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            text(getString(R.string.account_web_hint),14);
            code=AiosUi.text(this,getString(R.string.account_not_started),28,AiosTheme.accent(this));code.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));code.setLetterSpacing(.08f);code.setGravity(Gravity.CENTER);code.setPadding(dp(16),dp(20),dp(16),dp(20));code.setBackground(AiosUi.ripple(this,AiosUi.shape(this,AiosTheme.surfaceHigh(this),16)));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2);cl.topMargin=dp(8);cl.bottomMargin=dp(8);body.addView(code,cl);
            AiosUi.clickable(code,()->{if(!codeValue.isEmpty()){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(getString(R.string.account_clipboard),codeValue));Toast.makeText(this,os.aios.core.DisplayText.clean(getString(R.string.account_copied)),Toast.LENGTH_SHORT).show();}});code.setContentDescription(os.aios.core.DisplayText.clean(getString(R.string.account_copy_code)));
            primary=filled(getString(R.string.account_get_code),()->perform("accounts/login",new JSONObject()));
            web=button(getString(R.string.account_open_web),()->{if(!verificationUrl.isEmpty())try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(verificationUrl)));}catch(Exception e){status.setText(getString(R.string.account_no_browser));}});web.setEnabled(false);
            cancel=button(getString(R.string.account_cancel_auth),()->perform("accounts/cancel",new JSONObject()));
            link(getString(R.string.account_logout),()->remove("chatgpt"));
            paint(snapshot);
        }else {
            // Password masking keeps this form usable on the AIOS remote display.
            JSONObject cfg=snapshot.optJSONObject("profiles");cfg=cfg==null?new JSONObject():cfg.optJSONObject(id);if(cfg==null)cfg=new JSONObject();
            text(getString(hint(id)),14);
            String endpoint=endpoint(id);
            url=field(getString(R.string.account_endpoint),cfg.optString("baseUrl",endpoint),false);url.setEnabled(id.equals("qwen")||id.equals("kimi")||id.equals("custom"));
            model=field(getString(R.string.account_model),cfg.optString("model",defaultModel(id)),false);
            if(id.equals("openai")){
                button(getString(R.string.gpt_choose_model),()->new AlertDialog.Builder(this).setTitle(getString(R.string.gpt_choose_model)).setItems(new String[]{"gpt-6-luna","gpt-6.1-terra","gpt-6.1-sol","gpt-6-astra"},(d,which)->model.setText(new String[]{"gpt-6-luna","gpt-6.1-terra","gpt-6.1-sol","gpt-6-astra"}[which])).show());
                text(getString(R.string.gpt_effort_hint),14);
            }
            key=field(cfg.optBoolean("configured")?getString(R.string.account_saved_key):getString(R.string.account_api_key),"",true);
            primary=filled(getString(R.string.account_save),()->{try{JSONObject p=new JSONObject().put("provider",id).put("baseUrl",url.getText().toString()).put("model",model.getText().toString()).put("apiKey",key.getText().toString());key.setText("");perform("accounts/save",p);}catch(Exception e){status.setText(getString(R.string.account_check));}});
            LinearLayout links=AiosUi.row(this);links.addView(AiosUi.textButton(this,getString(R.string.account_docs),()->{String link=documentation(id);try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(Exception e){status.setText(link);}}));
            links.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));links.addView(AiosUi.textButton(this,getString(R.string.account_remove_key),()->remove(id)));LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(-1,-2);ll.topMargin=dp(8);body.addView(links,ll);paint(snapshot);
        }
    }
    String providerName(String id){if(id.equals("openai"))return getString(R.string.gpt_api_title);for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return NAMES[i];return id;}
    static int hint(String id){switch(id){
        case "openai":return R.string.gpt_api_hint;
        case "qwen":return R.string.account_qwen_hint;
        case "qwen_coding":return R.string.account_qwen_coding_hint;
        case "glm":return R.string.account_glm_hint;
        case "kimi":return R.string.account_kimi_hint;
        case "claude":return R.string.account_claude_hint;
        default:return R.string.account_key_hint;
    }}
    static String endpoint(String id){switch(id){
        case "openai":return "https://api.openai.com/v1";
        case "deepseek":return "https://api.deepseek.com";
        case "glm":return "https://open.bigmodel.cn/api/v1";
        case "qwen_coding":return "https://coding.dashscope.aliyuncs.com/v1";
        case "qwen":return "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";
        case "kimi":return "https://api.kimi.com/coding/v1";
        case "claude":return "https://api.anthropic.com/v1";
        default:return "";
    }}
    static String defaultModel(String id){switch(id){
        case "openai":return "gpt-6-luna";
        case "deepseek":return "deepseek-v4-pro";
        case "glm":return "glm-5.3";
        case "qwen_coding":return "qwen3.7-plus";
        case "qwen":return "auto";
        case "kimi":return "k3.1-pro";
        case "claude":return "claude-sonnet-4-6";
        default:return "";
    }}
    static String documentation(String id){switch(id){
        case "openai":return "https://developers.openai.com/api/docs/quickstart";
        case "deepseek":return "https://api-docs.deepseek.com/quick_start/agent_integrations/codex/";
        case "glm":return "https://docs.bigmodel.cn/cn/coding-plan/tool/codex";
        case "qwen":case "qwen_coding":return "https://help.aliyun.com/zh/model-studio/codex";
        case "kimi":return "https://www.kimi.com/code/docs/en/third-party-tools/codex.html";
        case "claude":return "https://platform.claude.com/docs/en/api/messages/create";
        default:return "https://learn.chatgpt.com/docs/enterprise/gateway-compatibility";
    }}
    void remove(String id){new AlertDialog.Builder(this).setMessage(os.aios.core.DisplayText.clean(id.equals("chatgpt")?getString(R.string.account_confirm_logout):getString(R.string.account_confirm_remove))).setNegativeButton(os.aios.core.DisplayText.clean(getString(R.string.account_keep)),null).setPositiveButton(os.aios.core.DisplayText.clean(getString(R.string.account_remove)),(d,w)->{try{perform("accounts/remove",new JSONObject().put("provider",id));}catch(Exception ignored){}}).show();}
    void paint(JSONObject s){if(status==null)return;if(!lastError.isEmpty()){status.setText(lastError);return;}String active=s.optString("provider"),state=s.optString("state");
        if(selected.isEmpty()){status.setText(active.isEmpty()?getString(R.string.account_choose):state.equals("authenticated")?getString(R.string.account_chatgpt_saved):state.equals("configured")?getString(R.string.account_current_prefix)+providerName(active)+getString(R.string.account_configured_suffix):getString(R.string.account_incomplete));return;}
        if(selected.equals("chatgpt")&&code==null){status.setText(getString(R.string.account_current_prefix)+providerName(active));return;}
        if(selected.equals("chatgpt")&&active.equals("chatgpt")){
            boolean waiting=state.equals("waiting"),done=state.equals("authenticated");codeValue=waiting?s.optString("userCode"):"";verificationUrl=waiting?s.optString("verificationUrl"):"";

            code.setText(done?getString(R.string.account_saved):waiting?codeValue:state.equals("expired")?getString(R.string.account_expired):getString(R.string.account_signed_out));
            status.setText(done?getString(R.string.account_saved_hint):waiting?getString(R.string.account_waiting)+verificationUrl:state.equals("failed")?s.optString("error",getString(R.string.account_auth_failed)):getString(R.string.account_start_hint));
            web.setEnabled(waiting);primary.setEnabled(!busy&&!waiting&&!done);cancel.setEnabled(waiting&&!busy);
        }else if(!selected.equals("chatgpt")){status.setText(selected.equals(active)?getString(R.string.account_enabled_hint):getString(R.string.account_switch_hint));}
    }
    void perform(String op,JSONObject params){if(busy){status.setText(getString(R.string.account_busy));return;}final int requestGeneration=++generation;lastError="";inFlight++;busy=true;status.setText(op.equals("accounts/login")?getString(R.string.account_getting_code):getString(R.string.account_working));if(primary!=null)primary.setEnabled(false);
        io.execute(()->{try{JSONObject r=client.call(new JSONObject().put("op",op).put("params",params));main.post(()->{inFlight--;busy=inFlight>0;snapshot=r;if(!isDestroyed()&&requestGeneration==generation){if(primary!=null)primary.setEnabled(true);paint(r);schedule();}});}catch(Exception e){main.post(()->{inFlight--;busy=inFlight>0;if(!isDestroyed()&&requestGeneration==generation){if(primary!=null)primary.setEnabled(true);if(op.equals("accounts/login")&&selected.equals("chatgpt")&&code!=null)code.setText(getString(R.string.account_code_unavailable));lastError=operationName(op)+getString(R.string.account_failed_suffix)+(e.getMessage()==null?getString(R.string.account_timeout):e.getMessage());status.setText(lastError);schedule();}});}});
    }
    String operationName(String op){return op.equals("accounts/remove")?getString(R.string.account_remove_operation):op.equals("accounts/cancel")?getString(R.string.account_cancel_operation):op.equals("accounts/login")?getString(R.string.account_code_operation):getString(R.string.account_save_operation);}
    void refresh(boolean ignored){if(!client.isRuntimeInstalled()){status.setText(getString(R.string.account_runtime_missing));return;}if(busy||isDestroyed()){schedule();return;}final int requestGeneration=generation;inFlight++;busy=true;io.execute(()->{try{JSONObject r=client.call(new JSONObject().put("op","accounts/status"));main.post(()->{inFlight--;busy=inFlight>0;snapshot=r;if(!isDestroyed()&&requestGeneration==generation)paint(r);schedule();});}catch(Exception e){main.post(()->{inFlight--;busy=inFlight>0;if(!isDestroyed()&&requestGeneration==generation)status.setText(getString(R.string.account_read_failed)+(e.getMessage()==null?getString(R.string.account_timeout):e.getMessage()));schedule();});}});}
    void schedule(){main.removeCallbacks(poll);if(foreground&&!isDestroyed()&&!isFinishing())main.postDelayed(poll,2500);}
    protected void onResume(){super.onResume();foreground=true;paint(snapshot);schedule();}
    protected void onPause(){foreground=false;main.removeCallbacks(poll);super.onPause();}
    static final class Draft {String provider,key,model,url;boolean webLogin;JSONObject snapshot;}
    @Override public Object onRetainNonConfigurationInstance(){if(selected.isEmpty())return null;Draft d=new Draft();d.provider=selected;d.webLogin=webLoginView;d.snapshot=snapshot;d.key=key==null?"":key.getText().toString();d.model=model==null?"":model.getText().toString();d.url=url==null?"":url.getText().toString();return d;}
    protected void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdownNow();client.close();super.onDestroy();}
}
