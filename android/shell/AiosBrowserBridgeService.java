package com.android.shell;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** AIOS-only, bounded browser automation bridge in Chrome's accepted shell UID. */
public final class AiosBrowserBridgeService extends Service {
    public static final String DESCRIPTOR="os.aios.browserbridge.IBrowserBridge";
    public static final int TRANSACTION_RUN=IBinder.FIRST_CALL_TRANSACTION;
    private static final int MAX_REQUEST=64*1024;
    private static final int MAX_RESPONSE=1024*1024;
    private static final String DEBUG_BINARY_SHA256="67fcac36c4e9a1930d5808a5349f39174b7ccd718f6b56b615b3df3974532a1e";
    private static final Set<String> COMMANDS=Set.of(
        "status","snapshot","click","fill","type","press","hover","select","check","uncheck",
        "scroll","get","wait","tab","open","back","forward","reload","close");

    private final Binder binder=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
            if(code!=TRANSACTION_RUN)return super.onTransact(code,data,reply,flags);
            try{
                data.enforceInterface(DESCRIPTOR);enforceAgentCaller();
                String request=data.readString();
                if(request==null||request.length()>MAX_REQUEST)throw new IllegalArgumentException("Invalid request size");
                reply.writeNoException();reply.writeString(run(new JSONObject(request)).toString());
            }catch(Exception e){
                reply.writeNoException();
                try{reply.writeString(new JSONObject().put("success",false).put("error",safe(e)).toString());}
                catch(Exception ignored){reply.writeString("{\"success\":false,\"error\":\"bridge failure\"}");}
            }
            return true;
        }
    };
    @Override public IBinder onBind(Intent intent){return binder;}
    private void enforceAgentCaller()throws Exception{
        int uid=Binder.getCallingUid();if(uid==Process.myUid())return;
        String[] packages=getPackageManager().getPackagesForUid(uid);
        if(packages!=null)for(String name:packages)if("os.aios.agent".equals(name))return;
        throw new SecurityException("Caller is not AIOS Control Agent");
    }
    private JSONObject run(JSONObject request)throws Exception{
        String session=request.optString("session");
        if(!session.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid session");
        String command=request.optString("command");if(!COMMANDS.contains(command))throw new IllegalArgumentException("Unsupported browser command");
        JSONArray input=request.optJSONArray("args");if(input==null||input.length()>32)throw new IllegalArgumentException("Invalid arguments");
        List<String> argv=new ArrayList<>();
        argv.add(executable());argv.add("--session");argv.add(session);
        argv.add("--cdp");argv.add("abstract:chrome_devtools_remote");argv.add("--json");argv.add(command);
        for(int i=0;i<input.length();i++){String arg=input.getString(i);if(arg.indexOf('\u0000')>=0||arg.length()>16384)throw new IllegalArgumentException("Invalid argument");argv.add(arg);}
        ProcessBuilder builder=new ProcessBuilder(argv);builder.redirectErrorStream(true);
        builder.environment().put("AGENT_BROWSER_SOCKET_DIR",getDir("aios-agent-browser",MODE_PRIVATE).getAbsolutePath());
        builder.environment().put("AGENT_BROWSER_IDLE_TIMEOUT_MS","300000");builder.environment().put("AGENT_BROWSER_DISABLE_STREAM","1");
        builder.environment().put("AGENT_BROWSER_DAEMON_ABSTRACT","1");
        java.lang.Process child=builder.start();ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream in=child.getInputStream()){byte[] buffer=new byte[8192];int read;while((read=in.read(buffer))!=-1){if(output.size()+read>MAX_RESPONSE){child.destroyForcibly();throw new IllegalStateException("Browser response exceeds 1 MiB");}output.write(buffer,0,read);}}
        if(!child.waitFor(35,TimeUnit.SECONDS)){child.destroyForcibly();throw new IllegalStateException("Browser command timed out");}
        String text=output.toString(StandardCharsets.UTF_8).trim();JSONObject result;
        try{result=text.isEmpty()?new JSONObject():new JSONObject(text.substring(text.lastIndexOf('\n')+1));}
        catch(Exception e){throw new IllegalStateException("Invalid browser response: "+clip(text));}
        if(child.exitValue()!=0&&!result.has("error"))result.put("success",false).put("error",clip(text));return result;
    }
    private static String clip(String value){return value==null?"":value.substring(0,Math.min(500,value.length()));}
    private static String safe(Exception e){String value=e.getMessage();return value==null?e.getClass().getSimpleName():clip(value);}
    private static String executable()throws Exception{
        File system=new File("/system_ext/bin/agent-browser");if(system.canExecute())return system.getAbsolutePath();
        File debug=new File("/data/local/tmp/agent-browser");
        if(!android.os.Build.IS_DEBUGGABLE||!debug.canExecute()||!DEBUG_BINARY_SHA256.equals(sha256(debug)))throw new IllegalStateException("Verified agent-browser binary unavailable");
        return debug.getAbsolutePath();
    }
    private static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int read;while((read=in.read(buffer))!=-1)digest.update(buffer,0,read);}
        StringBuilder out=new StringBuilder();for(byte value:digest.digest())out.append(String.format("%02x",value));return out.toString();
    }
}
