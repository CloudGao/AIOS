package os.aios.codex;

import android.app.*;
import android.os.*;
import android.content.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

/** Production entry opens the phone workspace; diagnostic chat exists only in debug builds. */
public final class RuntimeActivity extends Activity {
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    TextView status,output;EditText input;String thread="",threadRuntime="",threadProvider="";long cursor;
    final Runnable poll=new Runnable(){public void run(){RuntimeService service=RuntimeService.instance;if(service!=null)try{
        JSONObject s=service.status();status.setText((s.getBoolean("running")?"手机端运行中":"尚未启动")+" · AIOS "+s.getString("version")+"\n"+(s.getString("mode").equals("fixture")?"测试模式（模拟模型响应）":"真实模型模式")+" · UID "+s.getInt("androidUid")+(s.optString("error").isEmpty()?"":"\n"+s.getString("error")));
        JSONObject events=service.command(new JSONObject().put("op","events").put("after",cursor));cursor=events.getLong("cursor");
        JSONArray items=events.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject e=items.getJSONObject(i);JSONObject p=e.optJSONObject("params");
            if(e.optString("method").equals("item/agentMessage/delta")&&p!=null)output.append(p.optString("delta"));
            if(e.optString("method").equals("account/login/completed")&&p!=null)output.setText(p.optBoolean("success")?"ChatGPT 登录成功":"登录未完成："+p.optString("error"));
            if(e.optString("method").equals("runtime/aiosToolExecuted")&&p!=null)output.append("\n[手机工具："+p.optString("tool")+" · "+p.optString("state")+"]\n");
            if(e.optString("method").equals("turn/completed")&&p!=null){JSONObject turn=p.optJSONObject("turn");if(turn!=null&&!turn.optString("status").equals("completed"))output.append("\n本轮："+turn.optString("status")+"\n"+turn.optString("error"));}
        }
    }catch(Exception ignored){}handler.postDelayed(this,700);}};
    public void onCreate(Bundle b){super.onCreate(b);
        if(!BuildFlags.DEBUG){
            try{startActivity(new Intent().setComponent(new ComponentName("os.aios.agent","os.aios.agent.MainActivity")));}
            catch(ActivityNotFoundException e){Toast.makeText(this,DisplayText.clean("AIOS 应用不可用，请检查系统安装"),Toast.LENGTH_LONG).show();}
            finish();return;
        }
        startForegroundService(new Intent(this,RuntimeService.class));
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);int pad=(int)(20*getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);box.setBackgroundColor(0xfff8f9f4);
        TextView title=DisplayText.bind(new TextView(this));title.setText("AIOS 运行时");title.setTextSize(25);box.addView(title);
        status=DisplayText.bind(new TextView(this));status.setTextSize(15);box.addView(status);
        Button start=DisplayText.bind(new Button(this));start.setText("启动手机运行时");start.setOnClickListener(v->run(()->{RuntimeService.instance.start("live");return "已启动";}));box.addView(start);
        Button login=DisplayText.bind(new Button(this));login.setText("登录 / 模型");login.setOnClickListener(v->{try{startActivity(new Intent().setComponent(new ComponentName("os.aios.agent","os.aios.agent.CodexAccountsActivity")));}catch(Exception e){output.setText("请更新 AIOS App 后，从 AIOS 的登录 / 模型入口继续");}});box.addView(login);
        input=DisplayText.editable(new EditText(this));input.setHint("输入内容，验证手机端对话");box.addView(input);
        Button send=DisplayText.bind(new Button(this));send.setText("发送");send.setOnClickListener(v->{String text=input.getText().toString().trim();if(text.isEmpty())return;run(()->{
            RuntimeService service=RuntimeService.instance;service.start("live");
            String runtimeId=service.status().getString("runtimeId");String provider=service.status().getString("provider");if(!provider.equals(threadProvider)){thread="";threadProvider=provider;}
            if(thread.isEmpty())thread=service.command(new JSONObject().put("op","thread/start")).getJSONObject("thread").getString("id");
            else if(!runtimeId.equals(threadRuntime))service.command(new JSONObject().put("op","rpc").put("method","thread/resume").put("params",new JSONObject().put("threadId",thread)));
            threadRuntime=runtimeId;
            handler.post(()->output.setText(""));service.command(new JSONObject().put("op","rpc").put("method","turn/start").put("params",new JSONObject().put("threadId",thread).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",text)))));return "";
        });});box.addView(send);
        output=DisplayText.bind(new TextView(this));output.setTextSize(15);output.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(output);box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(box);
        if(b!=null){thread=b.getString("thread","");threadProvider=b.getString("threadProvider","");threadRuntime=b.getString("threadRuntime","");cursor=b.getLong("cursor");output.setText(b.getString("output",""));}
    }
    interface Work{String run()throws Exception;}
    void run(Work work){io.execute(()->{String result;try{result=work.run();}catch(Exception e){result=RuntimeService.safeError(e);}String text=result;if(!text.isEmpty())handler.post(()->output.setText(text));});}
    protected void onResume(){super.onResume();if(BuildFlags.DEBUG)handler.post(poll);}
    protected void onPause(){handler.removeCallbacks(poll);super.onPause();}
    protected void onSaveInstanceState(Bundle b){if(!BuildFlags.DEBUG){super.onSaveInstanceState(b);return;}b.putString("thread",thread);b.putString("threadProvider",threadProvider);b.putString("threadRuntime",threadRuntime);b.putLong("cursor",cursor);b.putString("output",output.getText().toString());super.onSaveInstanceState(b);}
    protected void onDestroy(){handler.removeCallbacks(poll);io.shutdownNow();super.onDestroy();}
}
