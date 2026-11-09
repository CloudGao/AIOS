package os.aios.agent;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.*;
import org.json.JSONObject;
import os.aios.apk.ShellProtocol;
import java.util.concurrent.CopyOnWriteArrayList;

/** No reconnect/retry of writes. The shell instance is part of every screen identity. */
public final class AdbBridge {
    private static volatile IBinder remote;
    private static volatile String instance="";
    private static volatile boolean physicalMonitor;
    private static volatile long monitorHeartbeat;
    private static final CopyOnWriteArrayList<Runnable> deaths=new CopyOnWriteArrayList<>();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private AdbBridge() {}
    public static boolean connected(){IBinder b=remote;return b!=null&&b.isBinderAlive();}
    public static boolean monitorsInput(){return connected()&&physicalMonitor&&SystemClock.elapsedRealtime()-monitorHeartbeat<2000;}
    public static String instance(){return instance;}
    public static void onDeath(Runnable listener){deaths.add(listener);}
    public static void removeDeath(Runnable listener){deaths.remove(listener);}
    public static synchronized void install(Context context, IBinder binder,String epoch,boolean monitor)throws RemoteException {
        if(binder==null||epoch==null||!epoch.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("invalid_shell_registration");
        if(remote!=null&&remote.isBinderAlive()&&remote!=binder)throw new IllegalStateException("shell_already_active");
        binder.linkToDeath(()->lost(context,binder),0);
        remote=binder;instance=epoch;physicalMonitor=monitor;monitorHeartbeat=SystemClock.elapsedRealtime();
        // Keep probing after a transient timeout. Recovery restores capability only;
        // the interrupted request stays paused and its observations stay invalid.
        Thread heartbeat=new Thread(()->{
            while(remote==binder){
                try{
                    long started=SystemClock.elapsedRealtime();
                    JSONObject status=call("input_status",new JSONObject());
                    if(remote!=binder)return;
                    boolean healthy=status.optBoolean("input_monitor")&&SystemClock.elapsedRealtime()-started<2000;
                    long received=SystemClock.elapsedRealtime();
                    MAIN.post(()->{
                        if(remote!=binder)return;
                        if(!healthy||SystemClock.elapsedRealtime()-received>=2000){monitorUnavailable(context,binder);return;}
                        monitorHeartbeat=received;
                        if(!physicalMonitor){
                            physicalMonitor=true;
                            ForegroundUiService service=ForegroundUiService.instance;if(service!=null)service.observesPhysicalInput=true;
                            AgentCapabilities.refresh(context);
                        }
                    });
                    Thread.sleep(500);
                }catch(Exception error){
                    MAIN.post(()->monitorUnavailable(context,binder));
                    try{Thread.sleep(500);}catch(InterruptedException stopped){return;}
                }
            }
        },"aios-input-heartbeat");heartbeat.setDaemon(true);heartbeat.start();
        MAIN.postDelayed(()->checkMonitor(context,binder),500);
        MAIN.post(()->{ForegroundUiService s=ForegroundUiService.instance;if(s!=null)s.observesPhysicalInput=monitorsInput();AgentCapabilities.refresh(context);});
    }
    private static void checkMonitor(Context context,IBinder expected){
        if(remote!=expected)return;
        if(physicalMonitor&&!monitorsInput())monitorUnavailable(context,expected);
        MAIN.postDelayed(()->checkMonitor(context,expected),500);
    }
    private static void monitorUnavailable(Context context,IBinder expected){
        if(remote!=expected||!physicalMonitor)return;
        physicalMonitor=false;ForegroundUiService.physicalInputGeneration++;
        ForegroundUiService s=ForegroundUiService.instance;if(s!=null)s.observesPhysicalInput=false;
        AgentService a=AgentService.instance;if(a!=null&&a.ui!=null)a.ui.foreground.userTakeover(context.getString(R.string.apk_shell_lost));
        AgentCapabilities.refresh(context);
    }
    private static synchronized void lost(Context context,IBinder expected){
        if(remote!=expected)return;remote=null;physicalMonitor=false;
        MAIN.post(()->{ForegroundUiService s=ForegroundUiService.instance;if(s!=null)s.observesPhysicalInput=false;
            AgentService a=AgentService.instance;if(a!=null&&a.ui!=null)a.ui.foreground.userTakeover(context.getString(R.string.apk_shell_lost));
            for(Runnable listener:deaths)listener.run();AgentCapabilities.refresh(context);});
    }
    public static IBinder events(Context context){return new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(Binder.getCallingUid()!=2000)throw new SecurityException("shell_uid_required");
            data.enforceInterface(ShellProtocol.DESCRIPTOR);
            if(code!=ShellProtocol.EVENT)return false;
            String epoch=data.readString();int display=data.readInt();
            if(!instance.equals(epoch))throw new SecurityException("stale_shell_instance");
            if(display==0)MAIN.post(()->{ForegroundUiService.physicalInputGeneration++;
                AgentService a=AgentService.instance;if(a!=null&&a.ui!=null)a.ui.foreground.userTakeover(context.getString(R.string.apk_user_takeover));});
            if(reply!=null)reply.writeNoException();return true;
        }
    };}
    public static JSONObject call(String operation,JSONObject arguments){
        IBinder b=remote;if(b==null||!b.isBinderAlive())throw new IllegalStateException("adb_activation_required");
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            String json=arguments.toString();if(json.length()>ShellProtocol.MAX_JSON)throw new IllegalArgumentException("request_too_large");
            in.writeInterfaceToken(ShellProtocol.DESCRIPTOR);in.writeString(operation);in.writeString(json);
            if(!b.transact(ShellProtocol.CALL,in,out,0))throw new IllegalStateException("shell_protocol_unavailable");
            out.readException();JSONObject result=new JSONObject(out.readString());
            if(result.has("error"))throw new IllegalStateException(result.getString("error"));return result;
        }catch(RemoteException e){throw new IllegalStateException("outcome_unknown_shell_disconnected",e);}
        catch(org.json.JSONException e){throw new IllegalStateException("invalid_shell_response",e);}
        finally{in.recycle();out.recycle();}
    }
    static ParcelFileDescriptor file(String epoch,String path,boolean create){
        IBinder b=remote;if(b==null)throw new IllegalStateException("adb_activation_required");
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{in.writeInterfaceToken(ShellProtocol.DESCRIPTOR);in.writeString(epoch);in.writeString(path);in.writeBoolean(create);
            if(!b.transact(ShellProtocol.FILE,in,out,0))throw new IllegalStateException("shell_update_required");out.readException();
            ParcelFileDescriptor fd=out.readTypedObject(ParcelFileDescriptor.CREATOR);if(fd==null)throw new IllegalStateException("file_descriptor_missing");return fd;
        }catch(RemoteException e){throw new IllegalStateException("outcome_unknown_shell_disconnected",e);}finally{in.recycle();out.recycle();}
    }
    static JSONObject install(String epoch,JSONObject job,ParcelFileDescriptor[] files)throws Exception{
        IBinder b=remote;if(b==null)throw new IllegalStateException("adb_activation_required");
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{in.writeInterfaceToken(ShellProtocol.DESCRIPTOR);in.writeString(epoch);in.writeString(job.toString());in.writeTypedArray(files,0);
            if(!b.transact(ShellProtocol.INSTALL,in,out,0))throw new IllegalStateException("shell_update_required");out.readException();return new JSONObject(out.readString());
        }catch(RemoteException e){throw new IllegalStateException("outcome_unknown_shell_disconnected",e);}finally{in.recycle();out.recycle();}
    }
    public static void launch(android.content.Intent intent,int display){
        IBinder b=remote;if(b==null)throw new IllegalStateException("adb_activation_required");
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{in.writeInterfaceToken(ShellProtocol.DESCRIPTOR);in.writeTypedObject(intent,0);in.writeInt(display);
            if(!b.transact(ShellProtocol.LAUNCH,in,out,0))throw new IllegalStateException("shell_protocol_unavailable");out.readException();
        }catch(RemoteException e){throw new IllegalStateException("outcome_unknown_shell_disconnected",e);}
        finally{in.recycle();out.recycle();}
    }
    public static Bitmap frame(String epoch,String request){
        IBinder b=remote;if(b==null)throw new IllegalStateException("adb_activation_required");
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{in.writeInterfaceToken(ShellProtocol.DESCRIPTOR);in.writeString(epoch);in.writeString(request);
            if(!b.transact(ShellProtocol.FRAME,in,out,0))throw new IllegalStateException("shell_protocol_unavailable");
            out.readException();return out.readTypedObject(Bitmap.CREATOR);
        }catch(RemoteException e){throw new IllegalStateException("screen_observation_unavailable",e);}
        finally{in.recycle();out.recycle();}
    }
}
