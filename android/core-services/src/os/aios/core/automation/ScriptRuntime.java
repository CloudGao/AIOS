package os.aios.core.automation;

import android.content.Context;
import android.os.SystemClock;
import androidx.javascriptengine.*;
import java.util.concurrent.*;
import org.json.*;

/** Fresh isolate for every invocation. The host polls an async RPC queue, requiring no
 * WebView DOM, Node, console side channel, or optional message-port extension. */
public final class ScriptRuntime {
    public interface Host {Object invoke(String op,JSONObject args)throws Exception;void check()throws Exception;}
    private static final Semaphore SLOT=new Semaphore(1);
    private static final int MAX_RESULT=65536;
    private final Context context;
    ScriptRuntime(Context c){context=c.getApplicationContext();}
    static boolean supported(){return JavaScriptSandbox.isSupported();}
    public JSONObject execute(String source,JSONObject metadata,int timeout,Host host,boolean validate)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(!SLOT.tryAcquire(100,TimeUnit.MILLISECONDS)){host.check();if(SystemClock.elapsedRealtime()>=deadline)throw new TimeoutException("Script runtime busy");}
        JavaScriptSandbox sandbox=null;JavaScriptIsolate isolate=null;
        com.google.common.util.concurrent.ListenableFuture<JavaScriptSandbox> connecting=null;
        try{
            host.check();
            if(!supported())throw new UnsupportedOperationException("JavaScriptSandbox unavailable: check installed WebView");
            connecting=JavaScriptSandbox.createConnectedInstanceAsync(context);
            sandbox=waitFor(connecting,deadline,host);
            if(!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_TERMINATION)||!sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE))throw new UnsupportedOperationException("WebView lacks isolate termination or heap limits");
            IsolateStartupParameters options=new IsolateStartupParameters();options.setMaxHeapSizeBytes(16L*1024*1024);
            isolate=sandbox.createIsolate(options);
            if(validate){
                waitFor(isolate.evaluateJavaScriptAsync("new Function('ctx',"+JSONObject.quote("'use strict';\n"+source+"\n;if(typeof main !== 'function')throw new Error('Define main(ctx)'); return main(ctx);")+"); 'compiled'"),deadline,host);
                return new JSONObject().put("syntax_valid",true).put("executed",false);
            }
            String bootstrap="'use strict'; var __queue=[],__pending={},__seq=0,__done=false,__value=null,__error=null;"
                +"function __rpc(op,args){return new Promise((resolve,reject)=>{if(__queue.length>=64){reject(new Error('RPC queue limit'));return;}const id=++__seq;__pending[id]={resolve,reject};__queue.push({id,op,args});});}"
                +"function __reply(id,ok,value){const p=__pending[id];if(!p)return;delete __pending[id];ok?p.resolve(value):p.reject(new Error(String(value)));}"
                +"const ctx=Object.freeze(Object.assign("+metadata.toString()+",{call:(name,args={})=>__rpc('call',{name,args}),log:(value)=>__rpc('log',{value:String(value)}),state:Object.freeze({get:()=>__rpc('state.get',{}),set:(value)=>__rpc('state.set',{value})})}));"
                +"Promise.resolve().then(()=>{const run=new Function('ctx',"+JSONObject.quote("'use strict';\n"+source+"\n;if(typeof main !== 'function')throw new Error('Define main(ctx)'); return main(ctx);")+");return run(ctx);}).then(value=>{__value=JSON.stringify(value===undefined?null:value);__done=true;},error=>{__error=String(error.stack||error);__done=true;});'started';";
            waitFor(isolate.evaluateJavaScriptAsync(bootstrap),deadline,host);
            int requests=0;
            while(true){
                host.check();
                String raw=waitFor(isolate.evaluateJavaScriptAsync("JSON.stringify({done:__done,value:__value,error:__error,calls:__queue.splice(0,64)})"),deadline,host);
                if(raw.length()>MAX_RESULT)throw new IllegalArgumentException("Script output exceeds 64 KiB");
                JSONObject batch=new JSONObject(raw);JSONArray calls=batch.getJSONArray("calls");
                for(int i=0;i<calls.length();i++){
                    if(++requests>64)throw new IllegalArgumentException("Script exceeded 64 host calls");
                    host.check();JSONObject call=calls.getJSONObject(i);Object value;boolean ok=true;
                    try{value=host.invoke(call.getString("op"),call.getJSONObject("args"));}catch(Exception e){ok=false;value=e.toString();}
                    String wrapped=new JSONArray().put(value==null?JSONObject.NULL:value).toString();
                    String encoded=wrapped.substring(1,wrapped.length()-1);
                    if(encoded.length()>MAX_RESULT){ok=false;encoded=JSONObject.quote("Host response exceeds 64 KiB");}
                    waitFor(isolate.evaluateJavaScriptAsync("__reply("+call.getLong("id")+","+ok+","+encoded+");'ok'"),deadline,host);
                }
                if(batch.getBoolean("done")){
                    if(!batch.isNull("error"))throw new IllegalArgumentException(batch.getString("error"));
                    return new JSONObject().put("result",new JSONTokener(batch.getString("value")).nextValue());
                }
                Thread.sleep(25);
            }
        }finally{
            try{if(isolate!=null)isolate.close();}
            finally{try{
                if(sandbox!=null)sandbox.close();
                else if(connecting!=null){final var pending=connecting;pending.addListener(()->{try{pending.get().close();}catch(Exception ignored){}},Runnable::run);}
            }finally{SLOT.release();}}
        }
    }
    private static <T>T waitFor(Future<T> f,long deadline,Host host)throws Exception{
        while(true){host.check();long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)throw new TimeoutException("Script execution timed out");
            try{return f.get(Math.min(remaining,100),TimeUnit.MILLISECONDS);}catch(TimeoutException e){if(SystemClock.elapsedRealtime()>=deadline)throw e;}
        }
    }
}
