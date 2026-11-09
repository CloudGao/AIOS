package os.aios.codex;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Foreground owner of the native child. Signature Binder is the production transport. */
public final class RuntimeService extends Service {
    static volatile RuntimeService instance;
    final ExecutorService requests=Executors.newCachedThreadPool();
    final ArrayDeque<JSONObject> history=new ArrayDeque<>();
    long historyChars;
    volatile String activeThread="",activeTurn="";
    final java.util.concurrent.ConcurrentHashMap<String,String> activeTurns=new java.util.concurrent.ConcurrentHashMap<>();
    final ArrayDeque<Integer> historySizes=new ArrayDeque<>();
    volatile long cursor;
    final String eventEpoch=java.util.UUID.randomUUID().toString();
    volatile CodexProcess child;
    RuntimeExtension.Mcp phoneMcp;
    RuntimeExtension cliPackages;
    AndroidNetwork network;
    AutoCloseable fixture;
    String fixtureUrl;
    volatile String mode="live",error="";
    JSONArray startupDiagnostics=new JSONArray();
    LocalServerSocket debugSocket;
    ProviderStore accounts;
    volatile JSONObject login=new JSONObject();
    final Object accountLock=new Object();
    final Messenger binder=new Messenger(new Handler(Looper.getMainLooper()){
        public void handleMessage(Message message){
            Messenger reply=message.replyTo;Bundle data=message.getData();ParcelFileDescriptor requestFd=data.getParcelable("json_fd",ParcelFileDescriptor.class);String inline=data.getString("json",requestFd==null?"{}":null);int id=message.arg1;int uid=message.sendingUid;
            boolean allowed=false;try{allowed=uid==os.aios.host.HostBindings.agentUid(RuntimeService.this)||uid==android.os.Process.myUid();}catch(Exception ignored){}final boolean trusted=allowed;
            requests.execute(()->{String result;try{String payload=inline==null?readRequest(requestFd):inline;result=trusted?answer(payload,true):"{\"ok\":false,\"error\":\"Caller not allowed\"}";}catch(Exception e){result="{\"ok\":false,\"error\":\"Request transfer failed; read state before retrying writes\"}";try{if(requestFd!=null)requestFd.close();}catch(IOException ignored){}}if(reply!=null)sendResult(reply,id,result);});
        }
    });
    private static String readRequest(ParcelFileDescriptor descriptor)throws Exception{
        if(descriptor==null)throw new IOException("Missing runtime request payload");
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            byte[] bytes=input.readNBytes(16*1024*1024+1);if(bytes.length>16*1024*1024)throw new IOException("Runtime request too large");return new String(bytes,StandardCharsets.UTF_8);
        }
    }
    private void sendResult(Messenger reply,int id,String result){
        ParcelFileDescriptor descriptor=null;File temporary=null;
        try{
            Bundle bundle=new Bundle();
            if(result.length()<=48_000)bundle.putString("json",result);
            else{
                byte[] bytes=result.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>64*1024*1024)throw new IOException("Runtime reply exceeds history transfer limit");
                temporary=File.createTempFile("binder-reply-",".json",getCacheDir());
                try(FileOutputStream output=new FileOutputStream(temporary)){output.write(bytes);}
                descriptor=ParcelFileDescriptor.open(temporary,ParcelFileDescriptor.MODE_READ_ONLY);
                if(!temporary.delete())throw new IOException("Unable to unlink runtime reply");
                temporary=null;bundle.putParcelable("json_fd",descriptor);
            }
            Message out=Message.obtain(null,1);out.arg1=id;out.setData(bundle);reply.send(out);
        }catch(Exception failure){
            try{Message out=Message.obtain(null,1);out.arg1=id;Bundle b=new Bundle();b.putString("json","{\"ok\":false,\"error\":\"Result transfer failed; read state before retrying writes\"}");out.setData(b);reply.send(out);}catch(RemoteException ignored){}
        }finally{
            if(descriptor!=null)try{descriptor.close();}catch(IOException ignored){}
            if(temporary!=null)temporary.delete();
        }
    }
    public IBinder onBind(Intent intent){return binder.getBinder();}
    private os.aios.runtime.AvailabilityReporter availability;
    public void onCreate(){super.onCreate();accounts=new ProviderStore(this);instance=this;availability=RuntimeCapabilities.start(this);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("runtime","AIOS 运行时",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,RuntimeActivity.class),PendingIntent.FLAG_IMMUTABLE);
        startForeground(162,new Notification.Builder(this,"runtime").setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(DisplayText.clean("AIOS 在手机上运行")).setContentText(DisplayText.clean("本地会话与工具运行时")).setContentIntent(open).build());
        requests.execute(()->{try{start("live");}catch(Exception e){error=safeError(e);}});
        if(BuildFlags.DEBUG)requests.execute(this::debugListen);
    }
    public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    synchronized void start(String requestedMode)throws Exception {
        if(!Set.of("live","fixture").contains(requestedMode))throw new IllegalArgumentException("Unknown runtime mode");
        if(requestedMode.equals("fixture")&&!BuildFlags.DEBUG)throw new SecurityException("Fixture is absent from production builds");
        if(child!=null&&child.process.isAlive()&&mode.equals(requestedMode)){
            if(!requestedMode.equals("live")||child.networkProxy.equals(AndroidNetwork.proxyUrl(this)))return;
            if(login.optString("state").equals("waiting")||!child.closeForNetworkChangeIfIdle())
                throw new IllegalStateException("网络代理已变化；请等待当前任务或登录结束后再试。现有任务不会被中断或重发。");
        }
        stopChild();mode=requestedMode;error="";
        if(network==null)network=new AndroidNetwork(this);
        if(mode.equals("fixture")&&fixture==null){
            Class<?> type=Class.forName("os.aios.codex.ResponsesFixture");
            fixture=(AutoCloseable)type.getDeclaredConstructor().newInstance();fixtureUrl=(String)type.getDeclaredMethod("url").invoke(fixture);
        }
        String provider=accounts.active();
        File home=new File(getFilesDir(),mode.equals("fixture")?"fixture-codex":provider.equals("chatgpt")?"codex":"provider-"+provider);
        try {
            child=new CodexProcess(this,home,this::event,mode.equals("fixture")?fixtureUrl:null,network.caFile);
            phoneMcp=RuntimeFeatureBindings.mcp(child);
            if(cliPackages==null)try{cliPackages=RuntimeFeatureBindings.cli(this);RuntimeCapabilities.recovered("cli");}catch(Exception optional){RuntimeCapabilities.failed("cli",optional);}
            String inventory=getSharedPreferences("app_capability_packages",0).getString("packages",null);
            if(inventory!=null&&phoneMcp!=null)try{phoneMcp.reconcile(new JSONObject(inventory));}
            catch(Exception optional){android.util.Log.w("AIOSCapabilities","MCP inventory reconciliation unavailable; native Harness remains running",optional);}
        } catch(Exception e) { error=safeError(e);if(e instanceof CodexProcess.StartFailure)startupDiagnostics=((CodexProcess.StartFailure)e).diagnostics;throw e; }
        event(new JSONObject().put("method","runtime/ready").put("params",status()));
    }
    synchronized void stopChild(){CodexProcess old=child;child=null;login=new JSONObject();if(old!=null)old.close();}
    void event(JSONObject json){
        JSONObject eventParams=json.optJSONObject("params");String eventMethod=json.optString("method");
        if(eventParams!=null&&eventMethod.equals("turn/started")){JSONObject turn=eventParams.optJSONObject("turn");activeThread=eventParams.optString("threadId");activeTurn=turn==null?eventParams.optString("turnId"):turn.optString("id");activeTurns.put(activeThread,activeTurn);}
        JSONObject debugParams=json.optJSONObject("params"),debugItem=debugParams==null?null:debugParams.optJSONObject("item");
        String debugMethod=json.optString("method");
        android.util.Log.i("AIOSCodexEvent",debugMethod+(debugItem==null?"":" item="+debugItem.optString("type")+" status="+debugItem.optString("status"))+((debugMethod.equals("warning")||debugMethod.equals("thread/settings/updated"))?" "+json.toString():""));
        if("account/login/completed".equals(json.optString("method"))){JSONObject p=json.optJSONObject("params"),current=login;if(p!=null&&p.optString("loginId").equals(current.optString("loginId")))try{login=new JSONObject().put("state",p.optBoolean("success")?"authenticated":"failed").put("error",p.optBoolean("success")?"":"授权未完成或验证码已过期，请重试");}catch(Exception ignored){}}
        synchronized(history){try{json.put("runtimeCursor",++cursor);}catch(JSONException ignored){}
        int size=json.toString().length();
        while(!history.isEmpty()&&(history.size()>=2000||historyChars+size>2*1024*1024)){history.removeFirst();historyChars-=historySizes.removeFirst();}
        if(size<=2*1024*1024){history.addLast(json);historySizes.addLast(size);historyChars+=size;}
        history.notifyAll();}
        if(eventParams!=null&&eventMethod.equals("turn/completed")){JSONObject turn=eventParams.optJSONObject("turn");String id=turn==null?eventParams.optString("turnId"):turn.optString("id");activeTurns.remove(eventParams.optString("threadId"),id);if(id.equals(activeTurn)){activeThread="";activeTurn="";}}
    }
    JSONObject status()throws JSONException {
        CodexProcess p=child;return new JSONObject().put("running",p!=null&&p.process.isAlive()).put("mode",mode)
            .put("version","0.162.1").put("provider",accounts.active()).put("androidUid",android.os.Process.myUid()).put("pid",p==null?0:p.pid()).put("runtimeId",p==null?JSONObject.NULL:p.runtimeId)
            .put("active_turn_count",activeTurns.size()).put("active_turns",new JSONObject(activeTurns)).put("toolCalls",p==null?0:p.toolCalls).put("cursor",cursor).put("error",error)
            .put("initialization",p==null?JSONObject.NULL:p.initialization)
            .put("debug",BuildFlags.DEBUG).put("workspace",p==null?JSONObject.NULL:p.workspace.getAbsolutePath());
    }
    JSONObject command(JSONObject q)throws Exception {
        String op=q.getString("op");JSONObject params=q.optJSONObject("params");if(params==null)params=new JSONObject();
        if(op.equals("start")){start(q.optString("mode","live"));return status();}
        if(op.equals("stop")){stopChild();return status();}
        if(op.equals("status"))return status();
        if(op.equals("events")){JSONArray values=new JSONArray();long after=q.optLong("after",0);synchronized(history){
            boolean reset=after>cursor;if(reset)after=0;
            if(after==cursor&&q.optInt("wait_ms",0)>0)history.wait(Math.min(1000,q.optInt("wait_ms")));
            long next=after;for(JSONObject v:history)if(v.optLong("runtimeCursor")>after){values.put(v);next=v.optLong("runtimeCursor");if(values.length()>=256)break;}
            boolean gap=after<cursor&&(history.isEmpty()||after<history.getFirst().optLong("runtimeCursor")-1);
            return new JSONObject().put("items",values).put("cursor",next).put("epoch",eventEpoch).put("reset",reset||gap);
        }}
        if(op.equals("diagnostics")){if(!BuildFlags.DEBUG||!mode.equals("fixture"))throw new SecurityException("Diagnostics only available for fixture runs");return new JSONObject().put("stderr",child==null?startupDiagnostics:child.diagnostics());}
        if(op.equals("fixture/metrics")){if(!BuildFlags.DEBUG||!mode.equals("fixture")||fixture==null)throw new SecurityException("Fixture metrics unavailable");return (JSONObject)fixture.getClass().getDeclaredMethod("metrics").invoke(fixture);}
        if(op.equals("inference/generate")){
            if(params.optBoolean("stateless")){
                JSONObject cfg;String secret;
                synchronized(accountLock){
                    String provider=params.getString("provider");
                    if(!accounts.active().equals(provider))throw new IllegalStateException("inference_provider_not_active");
                    if(provider.equals("chatgpt"))throw new IllegalStateException("stateless_api_provider_required");
                    cfg=accounts.config(provider);secret=accounts.secret(provider);
                }
                return StatelessTextInference.generate(this,cfg,secret,params);
            }
            synchronized(accountLock){
                if(!accounts.active().equals(params.getString("provider")))throw new IllegalStateException("inference_provider_not_active");
                start("live");return SystemTextInference.generate(child,params);
            }
        }
        CodexProcess p=child;if(p==null||!p.process.isAlive())throw new IllegalStateException("Runtime is stopped");
        if(op.equals("intervention/forget"))return p.forgetInterventions(q.getJSONObject("params").getString("thread_id"));
        if(op.equals("intervention/resolve"))return p.resolveIntervention(params);
        if(op.equals("execution/stop")){
            String threadId=params.getString("threadId"),turnId=params.optString("turnId");
            boolean turnStopped=turnId.isEmpty();String turnError="";
            if(!turnId.isEmpty())try{
                p.call("turn/interrupt",new JSONObject().put("threadId",threadId).put("turnId",turnId),15);
                JSONObject page=p.call("thread/turns/list",new JSONObject().put("threadId",threadId).put("limit",12).put("sortDirection","desc").put("itemsView","summary"),15);
                JSONArray turns=page.optJSONArray("data");boolean found=false;
                if(turns!=null)for(int i=0;i<turns.length();i++){JSONObject candidate=turns.getJSONObject(i);if(turnId.equals(candidate.optString("id"))){found=true;turnStopped=!"inProgress".equals(candidate.optString("status"));break;}}
                if(!found)turnError="Expected turn was not returned while confirming stop";else if(!turnStopped)turnError="Expected turn is still in progress";
            }catch(Exception e){turnError=safeError(e);}
            boolean goalPaused=!params.optBoolean("pauseGoal",false);String goalError="";
            if(params.optBoolean("pauseGoal",false))try{p.pauseGoal(threadId);goalPaused=true;}catch(Exception e){goalError=safeError(e);}
            return new JSONObject().put("turn_id",turnId).put("turn_stopped",turnStopped).put("turn_error",turnError)
                .put("goal_pause_requested",params.optBoolean("pauseGoal",false)).put("goal_paused",goalPaused).put("goal_error",goalError);
        }
        if(op.equals("capabilities/reconcile")){JSONObject packages=params.getJSONObject("packages");getSharedPreferences("app_capability_packages",0).edit().putString("packages",packages.toString()).commit();if(phoneMcp!=null)phoneMcp.reconcile(packages);if(cliPackages!=null)cliPackages.call("reconcile",params);return new JSONObject().put("state","succeeded");}
        if(op.equals("cli/action")){if(cliPackages==null)return RuntimeCapabilities.unavailable("cli");return cliPackages.call(q.getString("action"),params);}
        if(op.equals("mcp/action")){if(phoneMcp==null)return RuntimeCapabilities.unavailable("mcp");return phoneMcp.call(q.getString("action"),params);}
        if(op.equals("thread/start"))return p.startThread();
        if(op.equals("schedule/thread/start"))return p.startThread(true);
        if(op.equals("dream/thread/start")){
            String selected=params.getString("model");
            if(!accounts.active().equals("chatgpt"))throw new IllegalStateException("当前提供商未提供可验证的回顾模型目录");
            JSONObject result=p.call("thread/start",DreamPolicy.params(p,selected,availableModels()).put("historyMode","paginated"),45);
            String id=result.getJSONObject("thread").getString("id");
            if(!getSharedPreferences("dream_threads",0).edit().putString(id,selected).commit())throw new IOException("回顾会话关联保存失败");
            return result;
        }
        if(op.equals("minutes/thread/start")){
            JSONObject restricted=MinutesPolicy.params(p).put("dynamicTools",new JSONArray())
                .put("environments",new JSONArray()).put("selectedCapabilityRoots",new JSONArray());
            JSONObject result=p.call("thread/start",restricted,45);
            String id=result.getJSONObject("thread").getString("id");
            if(!getSharedPreferences("minutes_threads",0).edit().putBoolean(id,true).commit())
                throw new IOException("纪要会话关联保存失败");
            return result;
        }

        if(op.equals("tools/test")){
            if(!BuildFlags.DEBUG||!mode.equals("fixture"))throw new SecurityException("Tool diagnostics require fixture mode");
            return p.aios.call(params);
        }
        if(op.equals("rpc")){
            String method=q.getString("method");
            if(!Set.of("account/read","account/login/start","account/login/cancel","account/logout","account/rateLimits/read",
                "model/list","thread/read","thread/list","thread/turns/list","thread/items/list","thread/resume","turn/start","turn/steer","turn/interrupt","thread/compact/start","thread/goal/get","thread/goal/set","thread/goal/clear").contains(method))
                throw new SecurityException("RPC method not exposed by this Android host");
            if(method.startsWith("thread/goal/")&&(getSharedPreferences("minutes_threads",0).contains(params.optString("threadId"))||getSharedPreferences("dream_threads",0).contains(params.optString("threadId"))))throw new SecurityException("Isolated summary threads cannot manage goals");
            // API keys are intentionally not accepted by the diagnostic socket; account UI comes later.
            if(method.equals("account/login/start")&&!"chatgptDeviceCode".equals(params.optString("type")))throw new SecurityException("Only device login is exposed");
            if(method.equals("thread/resume")){
                JSONObject filtered=new JSONObject().put("threadId",params.getString("threadId")).put("excludeTurns",true);
                if(getSharedPreferences("dream_threads",0).contains(params.getString("threadId")))
                    filtered=DreamPolicy.params(p,getSharedPreferences("dream_threads",0).getString(params.getString("threadId"),""),availableModels()).put("threadId",params.getString("threadId"));
                else if(getSharedPreferences("minutes_threads",0).getBoolean(params.getString("threadId"),false))
                    filtered=MinutesPolicy.params(p).put("threadId",params.getString("threadId"));
                else filtered.put("baseInstructions",p.phoneInstructions()).put("cwd",p.workspace.getAbsolutePath()).put("approvalPolicy","never").put("sandbox","danger-full-access").put("config",p.threadConfig());
                params=filtered.put("excludeTurns",true);
            }
            if(method.equals("turn/steer")){
                params=new JSONObject().put("threadId",params.getString("threadId")).put("expectedTurnId",params.getString("expectedTurnId")).put("input",params.getJSONArray("input")).put("clientUserMessageId",params.optString("clientUserMessageId")).put("additionalContext",new JSONObject().put("aios-system-language",new JSONObject().put("kind","application").put("value",p.languageInstructions())));
            }
            if(method.equals("turn/start")){
                JSONObject filtered=new JSONObject().put("threadId",params.getString("threadId")).put("input",params.getJSONArray("input"));
                if(!getSharedPreferences("minutes_threads",0).contains(params.getString("threadId"))&&!getSharedPreferences("dream_threads",0).contains(params.getString("threadId")))
                    filtered.put("additionalContext",new JSONObject().put("aios-system-language",new JSONObject().put("kind","application").put("value",p.languageInstructions())));
                if(filtered.has("additionalContext")&&params.has("applicationContext")){
                    String application=params.getString("applicationContext");
                    if(application.length()>64000)throw new IllegalArgumentException("Application context too large");
                    filtered.getJSONObject("additionalContext").put("aios-execution",new JSONObject().put("kind","application").put("value",application));
                }
                if(!filtered.has("additionalContext"))filtered.put("additionalContext",new JSONObject().put("aios-presentation",new JSONObject().put("kind","application").put("value",PresentationPolicy.INSTRUCTIONS)));

                if(getSharedPreferences("minutes_threads",0).getBoolean(params.getString("threadId"),false))
                    filtered.put("environments",new JSONArray());
                params=filtered;
                String dreamModel=getSharedPreferences("dream_threads",0).getString(params.getString("threadId"),"");
                if(!dreamModel.isEmpty()){
                    params.put("model",dreamModel).put("effort","low").put("environments",new JSONArray());
                }else if(mode.equals("live")){
                    String selected=accounts.model();
                    if(selected.isEmpty()&&accounts.active().equals("chatgpt")){JSONArray models=availableModels();for(int i=0;i<models.length();i++){JSONObject m=models.getJSONObject(i);if(m.optBoolean("isDefault")){selected=m.getString("model");break;}}if(selected.isEmpty())throw new IllegalStateException("默认模型不可用，请在配置中选择模型");}
                    if(!selected.isEmpty())params.put("model",selected);
                    {JSONObject info=reasoningInfo();String effort=info.optString("effort");if(effort.isEmpty())effort=info.optString("defaultEffort");if(!effort.isEmpty())params.put("effort",effort);}
                }
            }
            if((method.equals("turn/start")||method.equals("turn/steer"))
                    &&!getSharedPreferences("minutes_threads",0).contains(params.optString("threadId"))
                    &&!getSharedPreferences("dream_threads",0).contains(params.optString("threadId"))) {
                os.aios.runtime.CoreRuntimeClient core=new os.aios.runtime.CoreRuntimeClient(this);
                params.getJSONObject("additionalContext").put("aios-memory",TurnMemoryContext.load(
                    (action,args)->core.call("workspace",action,args),params.getString("threadId"),params.getJSONArray("input"),message->android.util.Log.i("AIOSMemory",message)));
            }
            return p.call(method,params,45);
        }
        throw new IllegalArgumentException("Unknown operation");
    }
    JSONObject accountCommand(JSONObject q)throws Exception {synchronized(accountLock){
        String op=q.getString("op");JSONObject p=q.optJSONObject("params");if(p==null)p=new JSONObject();
        if(op.equals("accounts/reasoning")){start("live");return reasoningInfo();}
        if(op.equals("accounts/reasoning/save")){
            start("live");JSONObject info=reasoningInfo();if(!info.getString("provider").equals(p.getString("provider"))||!info.getString("model").equals(p.getString("model")))throw new IllegalStateException("模型已改变，请重新设置");
            String effort=p.getString("effort");boolean valid=effort.isEmpty();JSONArray options=info.getJSONArray("options");for(int i=0;i<options.length();i++)if(effort.equals(options.getJSONObject(i).optString("reasoningEffort")))valid=true;
            if(!valid)throw new IllegalArgumentException("当前模型不支持此推理强度");accounts.effort(effort);return accountStatus();
        }
        if(op.equals("accounts/models")){start("live");return new JSONObject().put("provider",accounts.active()).put("model",accounts.model()).put("manual",!accounts.active().equals("chatgpt")).put("items",accounts.active().equals("chatgpt")?availableModels():new JSONArray());}
        if(op.equals("accounts/model")){
            start("live");if(!accounts.active().equals(p.getString("provider")))throw new IllegalStateException("提供商已改变，请重新选择");String model=p.getString("model").trim();
            if(accounts.active().equals("chatgpt")){
                boolean valid=model.isEmpty();JSONArray list=availableModels();for(int i=0;i<list.length();i++)if(model.equals(list.getJSONObject(i).optString("model")))valid=true;
                if(!valid)throw new IllegalArgumentException("模型不在当前可用列表中");
                if(!accounts.prefs.edit().putString("chatgpt_model",model).commit())throw new IllegalStateException("模型保存失败");
            }else{JSONObject cfg=accounts.publicConfig(accounts.active());cfg.put("provider",accounts.active()).put("model",model);accounts.save(cfg);stopChild();start("live");}
            return accountStatus();
        }
        if(op.equals("accounts/save")){
            accounts.save(p);
            try{if(accounts.active().equals(p.getString("provider")))stopChild();switchProvider(p.getString("provider"));}
            catch(Exception failed){throw new IllegalStateException(getString(R.string.provider_saved_start_failed),failed);}
            return accountStatus();
        }
        if(op.equals("accounts/select")){switchProvider(p.getString("provider"));return accountStatus();}
        if(op.equals("accounts/remove")){
            String id=p.getString("provider");accounts.check(id);
            if(id.equals("chatgpt")){switchProvider(id);child.call("account/logout",new JSONObject(),30);login=new JSONObject();
                if(child.call("account/read",new JSONObject().put("refreshToken",false),15).optJSONObject("account")!=null)throw new IllegalStateException("退出后账号记录仍存在，请重试；未删除会话数据");}
            else {if(accounts.active().equals(id))switchProvider("chatgpt");accounts.remove(id);}
            return accountStatus();
        }
        if(op.equals("accounts/login")){
            switchProvider("chatgpt");JSONObject state=accountStatus();if(state.optString("state").equals("authenticated")||state.optString("state").equals("waiting"))return state;
            if(!child.networkProxy.equals(AndroidNetwork.proxyUrl(this))){stopChild();start("live");}
            JSONObject r=child.call("account/login/start",new JSONObject().put("type","chatgptDeviceCode"),45);
            if(r.optString("userCode").isEmpty()||!"https://auth.openai.com/codex/device".equals(r.optString("verificationUrl")))throw new IllegalStateException("没有收到有效设备码，请重试");
            login=new JSONObject(r.toString()).put("state","waiting").put("expiresAt",System.currentTimeMillis()+15*60*1000);return accountStatus();
        }
        if(op.equals("accounts/cancel")){String id=login.optString("loginId");if(!id.isEmpty()&&child!=null)child.call("account/login/cancel",new JSONObject().put("loginId",id),15);login=new JSONObject();return accountStatus();}
        if(op.equals("accounts/status")){start("live");return accountStatus();}
        throw new IllegalArgumentException("未知账号操作");
    }}
    void switchProvider(String id)throws Exception{
        accounts.check(id);if(!id.equals("chatgpt")&&!accounts.publicConfig(id).optBoolean("configured"))throw new IllegalArgumentException("请先填写此提供商的 Key 和模型");
        String old=accounts.active();if(!old.equals(id)){stopChild();accounts.select(id);try{start("live");}catch(Exception e){accounts.select(old);try{start("live");}catch(Exception ignored){}throw e;}}else start("live");
    }
    JSONObject accountStatus()throws Exception{
        JSONObject result=new JSONObject().put("provider",accounts.active()).put("model",accounts.model()).put("effort",accounts.effort()).put("state","configured");JSONObject configs=new JSONObject();
        for(String id:ProviderStore.IDS)if(!id.equals("chatgpt"))configs.put(id,accounts.publicConfig(id));result.put("profiles",configs);
        if(accounts.active().equals("chatgpt")){
            JSONObject a=child.call("account/read",new JSONObject().put("refreshToken",false),15).optJSONObject("account");
            if(a!=null&&"chatgpt".equals(a.optString("type"))){result.put("state","authenticated").put("email",a.optString("email")).put("plan",a.optString("planType"));login=new JSONObject();}
            else {JSONObject l=login;if(l.optString("state").equals("waiting")&&l.optLong("expiresAt")<System.currentTimeMillis())l=new JSONObject().put("state","expired");result.put("state",l.optString("state","idle"));for(String k:List.of("userCode","verificationUrl","expiresAt","error"))if(l.has(k))result.put(k,l.get(k));}
        }
        return result;
    }
    JSONObject reasoningInfo()throws Exception{
        JSONArray options=new JSONArray();String defaultEffort="";String model=accounts.model();
        if(accounts.active().equals("chatgpt")){JSONArray models=availableModels();for(int i=0;i<models.length();i++){JSONObject m=models.getJSONObject(i);if(model.equals(m.optString("model"))||(model.isEmpty()&&m.optBoolean("isDefault"))){options=m.optJSONArray("supportedReasoningEfforts");if(options==null)options=new JSONArray();defaultEffort=m.optString("defaultReasoningEffort");break;}}}
        if(!accounts.active().equals("chatgpt")){defaultEffort=accounts.defaultEffort(model);JSONArray levels=accounts.reasoningLevels(model);for(int i=0;i<levels.length();i++)options.put(new JSONObject().put("reasoningEffort",levels.getJSONObject(i).getString("effort")));}
        String effort=accounts.effort();boolean valid=effort.isEmpty();for(int i=0;i<options.length();i++)if(effort.equals(options.getJSONObject(i).optString("reasoningEffort")))valid=true;if(!valid)effort="";
        return new JSONObject().put("provider",accounts.active()).put("model",model).put("effort",effort).put("defaultEffort",defaultEffort).put("options",options);
    }
    JSONArray availableModels()throws Exception{
        JSONArray items=new JSONArray();String cursor=null;Set<String> seen=new HashSet<>();
        do{JSONObject p=new JSONObject().put("limit",100);if(cursor!=null)p.put("cursor",cursor);JSONObject page=child.call("model/list",p,30);JSONArray data=page.getJSONArray("data");for(int i=0;i<data.length();i++)items.put(data.getJSONObject(i));cursor=page.isNull("nextCursor")?null:page.optString("nextCursor",null);if(cursor!=null&&!seen.add(cursor))throw new IllegalStateException("模型列表分页异常");}while(cursor!=null&&!cursor.isEmpty());return items;
    }
    String answer(String payload){return answer(payload,false);}
    String answer(String payload,boolean trusted){try{JSONObject q=new JSONObject(payload);boolean account=q.optString("op").startsWith("accounts/");if(account&&!trusted)throw new SecurityException("Account settings require AIOS signed Binder");return new JSONObject().put("ok",true).put("result",account?accountCommand(q):command(q)).toString();}
        catch(Exception e){return "{\"ok\":false,\"error\":"+JSONObject.quote(safeError(e))+"}";}}
    static String safeError(Throwable e){String text=e.getMessage();return text==null?e.getClass().getSimpleName():text.substring(0,Math.min(text.length(),1000));}
    void debugListen(){try(LocalServerSocket server=new LocalServerSocket("aios_codex_runtime_debug")){
        debugSocket=server;while(!requests.isShutdown()){LocalSocket socket=server.accept();int uid=socket.getPeerCredentials().getUid();
            if(uid!=2000&&uid!=android.os.Process.myUid()){socket.close();continue;}
            requests.execute(()->{try(LocalSocket s=socket){s.setSoTimeout(60000);InputStream input=s.getInputStream();ByteArrayOutputStream b=new ByteArrayOutputStream();int ch;
                while((ch=input.read())!=-1&&ch!=10){if(b.size()>1_000_000)throw new IOException("Too large");b.write(ch);}
                String response=answer(b.toString(StandardCharsets.UTF_8));s.getOutputStream().write((response+"\n").getBytes(StandardCharsets.UTF_8));
            }catch(IOException ignored){}});
        }
    }catch(IOException ignored){}}
    public void onDestroy(){if(availability!=null)availability.close();instance=null;stopChild();if(network!=null)network.close();try{if(fixture!=null)fixture.close();}catch(Exception ignored){}
        try{if(debugSocket!=null)debugSocket.close();}catch(IOException ignored){}requests.shutdownNow();super.onDestroy();}
}
