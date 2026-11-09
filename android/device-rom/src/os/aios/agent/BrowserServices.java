package os.aios.agent;

import android.content.*;
import android.os.*;
import android.app.KeyguardManager;
import org.json.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** ROM backend client for the shell-UID browser bridge. */
final class BrowserServices implements AutoCloseable {
    private static final String DESCRIPTOR="os.aios.browserbridge.IBrowserBridge";
    private static final int TRANSACTION_RUN=IBinder.FIRST_CALL_TRANSACTION;
    private static final ComponentName COMPONENT=new ComponentName("com.android.shell","com.android.shell.AiosBrowserBridgeService");
    private final Context context;
    private final Map<String,Long> observedInputGeneration=new HashMap<>();
    BrowserServices(Context context){this.context=context;}

    JSONObject execute(JSONObject p)throws Exception{
        String action=p.getString("action");JSONObject args=p.optJSONObject("arguments");if(args==null)args=new JSONObject();
        String session=sessionName(UiSessionManager.caller.get());
        if(context.getSystemService(KeyguardManager.class).isKeyguardLocked())return new JSONObject().put("state","permission_required").put("reason","手机已锁定");
        boolean referenceAction=Set.of("click","fill","hover","select","check","uncheck").contains(action);
        if(referenceAction&&!Objects.equals(observedInputGeneration.get(session),ForegroundUiService.physicalInputGeneration))
            return new JSONObject().put("state","observation_required").put("session_id",session).put("reason","页面尚未观察，或观察后检测到用户操作");
        Command command=translate(action,args);
        JSONObject request=new JSONObject().put("session",session).put("command",command.name).put("args",new JSONArray(command.args));
        JSONObject raw=call(request);
        if(raw.optBoolean("success",false)){if(action.equals("observe"))observedInputGeneration.put(session,ForegroundUiService.physicalInputGeneration);return new JSONObject().put("state","succeeded").put("session_id",session).put("data",raw.opt("data"));}
        return new JSONObject().put("state","failed").put("session_id",session).put("reason",raw.optString("error","Browser command failed"));
    }
    private Command translate(String action,JSONObject p)throws Exception{
        switch(action){
            case "status":return cmd("status");
            case "observe":{List<String>a=list("-i");if(p.optBoolean("compact",true))a.add("-c");return cmd("snapshot",a);}
            case "open":return cmd("open",required(p,"url"));
            case "back":case "forward":case "reload":return cmd(action);
            case "click":case "hover":case "check":case "uncheck":return cmd(action,required(p,"ref"));
            case "fill":return cmd("fill",required(p,"ref"),required(p,"text"));
            case "type":return cmd("type",required(p,"text"));
            case "press":return cmd("press",required(p,"key"));
            case "select":return cmd("select",required(p,"ref"),required(p,"value"));
            case "scroll":{List<String>a=list(p.optString("direction","down"));if(p.has("pixels"))a.add(Integer.toString(p.getInt("pixels")));return cmd("scroll",a);}
            case "wait":{if(p.has("text"))return cmd("wait","--text",p.getString("text"));if(p.has("url"))return cmd("wait","--url",p.getString("url"));return cmd("wait",Integer.toString(p.optInt("ms",1000)));}
            case "tabs":return cmd("tab");
            case "tab_new":return p.has("url")?cmd("tab","new",p.getString("url")):cmd("tab","new");
            case "tab_select":return cmd("tab",Integer.toString(p.getInt("index")));
            case "tab_close":return p.has("index")?cmd("tab","close",Integer.toString(p.getInt("index"))):cmd("tab","close");
            case "release":return cmd("close");
            default:throw new IllegalArgumentException("Unsupported browser action");
        }
    }
    private static String required(JSONObject p,String key)throws Exception{String value=p.getString(key);if(value.isEmpty())throw new IllegalArgumentException(key+" is required");return value;}
    private static List<String> list(String...v){return new ArrayList<>(Arrays.asList(v));}
    private static Command cmd(String name,String...args){return new Command(name,list(args));}
    private static Command cmd(String name,List<String>args){return new Command(name,args);}
    private static final class Command{final String name;final List<String>args;Command(String n,List<String>a){name=n;args=a;}}
    private JSONObject call(JSONObject request)throws Exception{
        CountDownLatch connected=new CountDownLatch(1);IBinder[] remote=new IBinder[1];
        ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){remote[0]=b;connected.countDown();}public void onServiceDisconnected(ComponentName n){remote[0]=null;}};
        if(!context.bindService(new Intent().setComponent(COMPONENT),connection,Context.BIND_AUTO_CREATE))throw new IllegalStateException("Browser bridge unavailable");
        try{
            if(!connected.await(5,TimeUnit.SECONDS)||remote[0]==null)throw new IllegalStateException("Browser bridge connection timed out");
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{data.writeInterfaceToken(DESCRIPTOR);data.writeString(request.toString());remote[0].transact(TRANSACTION_RUN,data,reply,0);reply.readException();return new JSONObject(reply.readString());}
            finally{data.recycle();reply.recycle();}
        }finally{context.unbindService(connection);}
    }
    public void close(){}
    private static String sessionName(String thread){String source=thread==null?"interactive":thread;return "aios-"+Integer.toUnsignedString(source.hashCode(),36);}
}
