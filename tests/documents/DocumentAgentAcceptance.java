package os.aios.agent;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** One real phone-model turn against synthetic fixtures; never changes an existing conversation. */
public final class DocumentAgentAcceptance extends Instrumentation {
    private Bundle options;
    public void onCreate(Bundle b){options=b;super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();String thread="";try{
        String directory=options.getString("directory");if(directory==null||!directory.matches("/storage/emulated/0/Download/document-acceptance-[a-f0-9-]+"))throw new IllegalArgumentException("fixture directory required");
        File target=new File(directory,"agent-note.txt");if(target.exists())throw new IllegalStateException("test output already exists; no overwrite");
        getTargetContext().startForegroundService(new Intent(getTargetContext(),AgentService.class));for(int i=0;i<100&&AgentService.instance==null;i++)Thread.sleep(100);
        try(CodexAccountClient client=new CodexAccountClient(getTargetContext())){
            client.call(new JSONObject().put("op","start").put("mode","live"));JSONObject started=client.call(new JSONObject().put("op","thread/start"));thread=started.getJSONObject("thread").getString("id");
            String prompt="这是文档接口的真机验收，只操作这个新建样本目录："+directory+"。先读取 skills/interfaces/aios-documents/SKILL.md。必须使用 phone.action 的 documents.open/read/edit/save/status 接口打开 note.txt，将首行 Hello AIOS 改成 Hello Agent，另存 agent-note.txt，再通过 documents.open/read 读回目标核验。不要用 shell 直接读写文档，不使用 UI，不覆盖已有文件，不创建持续目标。保存使用 edit 返回的新 artifact document_id 和 source_version。完成后只报告读回的首行和目标路径。";
            client.call(new JSONObject().put("op","rpc").put("method","turn/start").put("params",new JSONObject().put("threadId",thread).put("input",new JSONArray().put(new JSONObject().put("type","text").put("text",prompt)))));
            JSONObject history=null;long until=SystemClock.elapsedRealtime()+300000;boolean done=false;
            while(SystemClock.elapsedRealtime()<until){Thread.sleep(1500);try{history=client.call(new JSONObject().put("op","rpc").put("method","thread/read").put("params",new JSONObject().put("threadId",thread).put("includeTurns",true)));JSONArray turns=history.getJSONObject("thread").optJSONArray("turns");if(turns!=null&&turns.length()>0){String state=turns.getJSONObject(turns.length()-1).optString("status");if(state.equals("completed")||state.equals("failed")||state.equals("interrupted")){done=true;break;}}}catch(Exception pending){}}
            if(!done){try{client.call(new JSONObject().put("op","execution/stop").put("params",new JSONObject().put("threadId",thread)));}catch(Exception ignored){}throw new IllegalStateException("model_test_timeout");}
            JSONArray calls=new JSONArray(),messages=new JSONArray();collect(history,calls,messages);
            String text="";if(target.isFile())try(InputStream in=new FileInputStream(target)){text=new String(in.readNBytes(4096),StandardCharsets.UTF_8);}
            JSONObject evidence=new JSONObject().put("thread_id",thread).put("output",target.getPath()).put("verified",text.startsWith("Hello Agent\r\n")).put("first_line",text.split("\\R",2)[0]).put("tool_calls",calls).put("messages",messages);
            result.putString("result",evidence.toString());finish(evidence.getBoolean("verified")?-1:0,result);
        }
    }catch(Throwable e){result.putString("error",e.toString());result.putString("thread_id",thread);finish(0,result);}}
    private void collect(Object value,JSONArray calls,JSONArray messages)throws Exception{
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;String type=o.optString("type");if(type.toLowerCase(java.util.Locale.ROOT).contains("toolcall")&&o.toString().contains("documents."))calls.put(o);if(type.equals("agentMessage"))messages.put(o.optString("text"));java.util.Iterator<String> keys=o.keys();while(keys.hasNext())collect(o.get(keys.next()),calls,messages);}
        else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collect(a.get(i),calls,messages);}
    }
}
