package os.aios.apk.shell;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.hardware.input.InputManagerGlobal;
import android.media.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;


/** Launched ONLY by adb shell from the installed, signed APK. Not an Android Service. */
public final class ShellMain {
    private static final class Wire { static final String DESCRIPTOR="os.aios.app.shell.v1",AUTHORITY="os.aios.app.activation"; static final int CALL=IBinder.FIRST_CALL_TRANSACTION,FRAME=CALL+1,EVENT=CALL+2,LAUNCH=CALL+3,FILE=CALL+4,INSTALL=CALL+5,MAX_JSON=131072; }
    private static final String PACKAGE="os.aios.app";
    private final Context context;
    private final int owner;
    private final ShellPackages packages;
    private final String epoch=UUID.randomUUID().toString();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Screen> screens=new LinkedHashMap<>();
    private final Set<String> released=new HashSet<>();
    private volatile IBinder events;
    private PhysicalTouchMonitor physicalTouch;
    private final java.util.concurrent.atomic.AtomicLong physicalDowns=new java.util.concurrent.atomic.AtomicLong();
    private boolean monitorsInput(){return monitor!=null||(physicalTouch!=null&&physicalTouch.alive());}
    private InputMonitor monitor;
    private InputEventReceiver receiver;
    private ShellMain(Context c)throws Exception{context=c;owner=c.getPackageManager().getPackageUid(PACKAGE,0);packages=new ShellPackages(c,android.os.UserHandle.getUserId(owner));}
    public static void main(String[] args)throws Exception{
        if(android.os.Process.myUid()!=2000)throw new SecurityException("adb_shell_required");
        Looper.prepareMainLooper();
        initializeWindowManager();
        ActivityThread thread=ActivityThread.systemMain();
        Context context=((Context)thread.getSystemContext()).createPackageContext("com.android.shell",0);
        ShellMain host=new ShellMain(context);host.start();Looper.loop();
    }
    /** app_process has no bindApplication shared-memory mapping. Android 17 may
     * fetch animation scale from that mapping while first connecting to WMS.
     * WMS itself has already been acquired before the optional scale lookup fails.
     * Recover only that precise failure and read the same value over Binder.
     * Older releases keep their normal framework initialization unchanged. */
    private static void initializeWindowManager() throws RemoteException {
        try { WindowManagerGlobal.getWindowManagerService(); }
        catch (IllegalStateException missing) {
            if (!"ApplicationSharedMemory not initialized".equals(missing.getMessage())) throw missing;
            IWindowManager window = WindowManagerGlobal.getWindowManagerService();
            if (window == null) throw missing;
            android.animation.ValueAnimator.setDurationScale(window.getCurrentAnimatorScale());
        }
    }
    private void start()throws Exception{
        try{
            monitor=InputManagerGlobal.getInstance().monitorGestureInput("AIOS user takeover",0);
            HandlerThread monitorThread=new HandlerThread("aios-physical-input");monitorThread.start();
            receiver=new InputEventReceiver(monitor.getInputChannel(),monitorThread.getLooper()){
                @Override public void onInputEvent(InputEvent event){
                    try{if(event instanceof MotionEvent){MotionEvent motion=(MotionEvent)event;
                        if(motion.getDeviceId()>=0&&motion.getActionMasked()==MotionEvent.ACTION_DOWN)takeover();}}
                    finally{finishInputEvent(event,false);}
                }
            };
        }catch(Exception unsupported){if(monitor!=null)monitor.dispose();monitor=null;}
        if(monitor==null)physicalTouch=PhysicalTouchMonitor.start(
            context.getPackageManager().getApplicationInfo(PACKAGE,0).nativeLibraryDir,
            this::takeover,()->{System.err.println("AIOS_TOUCH_MONITOR_LOST");System.exit(2);});
        Bundle registration=new Bundle();registration.putBinder("service",service);
        registration.putString("instance",epoch);registration.putBoolean("input_monitor",monitorsInput());
        IBinder providerToken=new Binder();
        android.app.IActivityManager manager=ActivityManager.getService();
        android.app.ContentProviderHolder holder=manager.getContentProviderExternal(Wire.AUTHORITY,0,providerToken,"aios-activation");
        if(holder==null||holder.provider==null)throw new IllegalStateException("activation_provider_unavailable");
        Bundle response;
        try {
            android.content.AttributionSource source=new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
            response=holder.provider.call(source,Wire.AUTHORITY,"register",null,registration);
        } finally {manager.removeContentProviderExternalAsUser(Wire.AUTHORITY,providerToken,0);}
        if(response==null||response.getInt("uid",-1)!=owner)throw new SecurityException("activation_owner_mismatch");
        events=response.getBinder("events");if(events==null)throw new IllegalStateException("activation_failed");
        events.linkToDeath(()->main.post(this::shutdown),0);
        System.out.println("AIOS_ADB_READY "+epoch);
    }
    private void takeover(){
        physicalDowns.incrementAndGet();
        if(events==null)return;Parcel p=Parcel.obtain();
        try{p.writeInterfaceToken(Wire.DESCRIPTOR);p.writeString(epoch);p.writeInt(0);events.transact(Wire.EVENT,p,null,IBinder.FLAG_ONEWAY);}
        catch(RemoteException dead){shutdown();}finally{p.recycle();}
    }
    private void shutdown(){System.err.println("AIOS_APP_BINDER_LOST");if(physicalTouch!=null)physicalTouch.stop();for(Screen screen:screens.values())screen.close();screens.clear();if(receiver!=null)receiver.dispose();if(monitor!=null)monitor.dispose();System.exit(0);}
    private final Binder service=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(Binder.getCallingUid()!=owner)throw new SecurityException("app_uid_required");
            data.enforceInterface(Wire.DESCRIPTOR);
            try{
                if(code==Wire.FILE){
                    if(!epoch.equals(data.readString()))throw new IllegalStateException("stale_shell_instance");
                    String path=data.readString();boolean create=data.readBoolean();
                    try(ParcelFileDescriptor fd=ShellFiles.open(path,create)){reply.writeNoException();reply.writeTypedObject(fd,0);}return true;
                }
                if(code==Wire.INSTALL){
                    if(!epoch.equals(data.readString()))throw new IllegalStateException("stale_shell_instance");
                    String json=data.readString();if(json==null||json.length()>Wire.MAX_JSON)throw new IllegalArgumentException("request_too_large");
                    ParcelFileDescriptor[] files=data.createTypedArray(ParcelFileDescriptor.CREATOR);boolean handedOff=false;
                    long identity=Binder.clearCallingIdentity();
                    try{JSONObject result=packages.submit(new JSONObject(json),files);handedOff=true;reply.writeNoException();reply.writeString(result.toString());return true;}
                    finally{Binder.restoreCallingIdentity(identity);if(!handedOff&&files!=null)for(ParcelFileDescriptor fd:files)if(fd!=null)fd.close();}
                }
                if(code==Wire.CALL){
                    String op=data.readString(),json=data.readString();
                    if(json==null||json.length()>Wire.MAX_JSON)throw new IllegalArgumentException("request_too_large");
                    JSONObject args=new JSONObject(json);JSONObject result;
                    if(op.equals("install_status")){
                        if(!epoch.equals(args.getString("instance")))throw new IllegalStateException("stale_shell_instance");
                        result=packages.status(args.getString("id"));
                    }else if(op.equals("file_operation")){
                        if(!epoch.equals(args.getString("instance")))throw new IllegalStateException("stale_shell_instance");
                        long identity=Binder.clearCallingIdentity();try{result=ShellFiles.execute(args.getString("operation"),args.getJSONObject("arguments"));}finally{Binder.restoreCallingIdentity(identity);}
                    }else if(op.equals("system_operation")){
                        if(!epoch.equals(args.getString("instance")))throw new IllegalStateException("stale_shell_instance");
                        result=onMain(()->ShellSystem.execute(context,args.getString("operation"),args.getJSONObject("arguments")));
                    }else result=op.equals("input_status")?new JSONObject().put("input_monitor",monitorsInput()).put("physical_downs",physicalDowns.get()):onMain(()->dispatch(op,args));
                    String encoded=result.toString();if(encoded.length()>Wire.MAX_JSON)throw new IllegalStateException("response_too_large");
                    reply.writeNoException();reply.writeString(encoded);return true;
                }
                if(code==Wire.LAUNCH){
                    Intent intent=data.readTypedObject(Intent.CREATOR);int display=data.readInt();
                    onMain(()->{launchIntent(intent,display);return true;});reply.writeNoException();return true;
                }
                if(code==Wire.FRAME){
                    String instance=data.readString(),id=data.readString();
                    Bitmap bitmap=onMain(()->screen(instance,id).capture());
                    reply.writeNoException();reply.writeTypedObject(bitmap,0);if(bitmap!=null)bitmap.recycle();return true;
                }
                return false;
            }catch(Exception failure){
                if(failure instanceof SecurityException||failure instanceof IllegalArgumentException)reply.writeException(failure);
                else if(failure instanceof android.system.ErrnoException errno&&errno.errno==android.system.OsConstants.EEXIST)reply.writeException(new IllegalArgumentException("destination_exists"));
                else if(failure instanceof android.system.ErrnoException errno&&(errno.errno==android.system.OsConstants.EACCES||errno.errno==android.system.OsConstants.EPERM))reply.writeException(new SecurityException("shell_file_access_denied"));
                else reply.writeException(new IllegalStateException(failure.getMessage(),failure));return true;
            }
        }
    };
    private <T>T onMain(Callable<T> action)throws Exception{
        FutureTask<T> task=new FutureTask<>(action);main.post(task);
        try{return task.get(12,TimeUnit.SECONDS);}catch(TimeoutException timeout){task.cancel(false);throw new IllegalStateException("outcome_unknown_timeout",timeout);}
    }
    private JSONObject dispatch(String op,JSONObject p)throws Exception{
        switch(op){
            case "install_status":if(!epoch.equals(p.getString("instance")))throw new IllegalStateException("stale_shell_instance");return packages.status(p.getString("id"));
            case "status":return new JSONObject().put("instance",epoch).put("input_monitor",monitorsInput()).put("input_monitor_backend",monitor!=null?"framework":physicalTouch!=null&&physicalTouch.alive()?"evdev":"unavailable").put("physical_downs",physicalDowns.get()).put("screens",screens.size()).put("max_sessions",4);
            case "create":{
                String id=p.getString("request");if(!id.matches("[A-Za-z0-9_-]{1,128}")&&!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("invalid_request_id");
                if(screens.containsKey(id)||released.contains(id))throw new IllegalArgumentException("request_id_reused");
                if(screens.size()>=4||released.size()>4096)throw new IllegalStateException("screen_capacity_reached");
                unlocked();Screen screen;try{screen=new Screen(id);}catch(RuntimeException error){released.add(id);throw error;}screens.put(id,screen);return screen.info();
            }
            case "release_state":{
                if(!epoch.equals(p.getString("instance")))return new JSONObject().put("state","UNKNOWN");
                String id=p.getString("request");return new JSONObject().put("state",screens.containsKey(id)?"PRESENT":released.contains(id)?"RELEASED":"UNKNOWN");
            }
            case "close":{Screen s=screen(p);s.close();screens.remove(s.id);released.add(s.id);return new JSONObject().put("released",true);}
            case "launch":{
                Screen s=screen(p);unlocked();String pkg=p.getString("package");
                Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null)throw new IllegalArgumentException("application_not_launchable");
                startOnDisplay(intent,s.display());return new JSONObject().put("submitted",true);
            }
            case "key":{Screen s=screen(p);unlocked();int key=p.getInt("key");if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ENTER)throw new IllegalArgumentException("key_not_allowed");
                long t=SystemClock.uptimeMillis();for(int a:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}){KeyEvent k=new KeyEvent(t,t,a,key,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD,0,0,InputDevice.SOURCE_KEYBOARD);k.setDisplayId(s.display());inject(k);}return new JSONObject().put("submitted",true);}
            case "gesture":{Screen s=screen(p);unlocked();gesture(s,p);return new JSONObject().put("submitted",true);}
            case "tasks":{
                JSONArray rows=new JSONArray();for(ActivityManager.RunningTaskInfo t:ActivityTaskManager.getService().getTasks(100,false,false,Display.INVALID_DISPLAY)){
                    if(t.userId!=android.os.UserHandle.getUserId(owner))continue;
                    rows.put(new JSONObject().put("id",t.taskId).put("display",t.displayId).put("base",t.baseActivity==null?"":t.baseActivity.getPackageName()).put("top",t.topActivity==null?"":t.topActivity.getPackageName()).put("class",t.topActivity==null?"":t.topActivity.getClassName()).put("visible",t.isVisible));
                }return new JSONObject().put("tasks",rows);
            }
            case "move":{unlocked();int display=p.getInt("display");knownDisplay(display);ActivityTaskManager.getService().moveRootTaskToDisplay(p.getInt("task"),display);return new JSONObject().put("submitted",true);}
            case "intent":{
                unlocked();int display=p.getInt("display");knownDisplay(display);
                Intent i=Intent.parseUri(p.getString("intent"),Intent.URI_INTENT_SCHEME);
                if(i.getSelector()!=null||i.getClipData()!=null||(i.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION))!=0)throw new SecurityException("privileged_uri_grant_refused");
                startOnDisplay(i,display);return new JSONObject().put("submitted",true);
            }
            case "force_stop":{String pkg=p.getString("package");if(!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||pkg.equals(PACKAGE)||pkg.equals("android")||pkg.equals("com.android.systemui"))throw new IllegalArgumentException("protected_package");context.getSystemService(ActivityManager.class).forceStopPackage(pkg);return new JSONObject().put("submitted",true);}
            case "diagnostic":{
                String name=p.getString("name");if(!Set.of("meminfo","cpuinfo","diskstats","thermalservice","battery","activity","media_session","usb","window","wifi","power").contains(name))throw new IllegalArgumentException("unknown_diagnostic");
                java.lang.Process process=new ProcessBuilder("/system/bin/dumpsys","-t","3",name).redirectErrorStream(true).start();
                byte[] bytes=process.getInputStream().readNBytes(48000);process.destroy();return new JSONObject().put("state","succeeded").put("data",new JSONObject().put("text",new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).put("truncated",bytes.length==48000));
            }
            default:throw new IllegalArgumentException("unsupported_shell_operation");
        }
    }
    private void launchIntent(Intent intent,int display)throws RemoteException{
        unlocked();knownDisplay(display);if(intent==null||intent.getSelector()!=null||intent.getClipData()!=null||(intent.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION))!=0)throw new SecurityException("privileged_uri_grant_refused");
        startOnDisplay(intent,display);
    }
    private void startOnDisplay(Intent source,int display)throws RemoteException{
        Intent intent=new Intent(source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Context created from ActivityThread.systemMain inherits the system op package.
        // Attribute shell launches explicitly to the real shell UID/package instead.
        int result=ActivityTaskManager.getService().startActivityAsUser(null,"com.android.shell",null,
            intent,intent.resolveTypeIfNeeded(context.getContentResolver()),null,null,0,
            Intent.FLAG_ACTIVITY_NEW_TASK,null,ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle(),
            android.os.UserHandle.getUserId(owner));
        if(result<0)throw new IllegalStateException("activity_launch_failed_"+result);
    }
    private void knownDisplay(int id){if(id==0)return;for(Screen s:screens.values())if(s.display()==id)return;throw new SecurityException("unowned_display");}
    private Screen screen(JSONObject p)throws Exception{return screen(p.getString("instance"),p.getString("request"));}
    private Screen screen(String instance,String id){if(!epoch.equals(instance))throw new IllegalStateException("stale_shell_instance");Screen s=screens.get(id);if(s==null)throw new IllegalStateException("screen_unavailable");return s;}
    private void unlocked(){if(context.getSystemService(KeyguardManager.class).isKeyguardLocked())throw new SecurityException("device_locked");}
    private void inject(InputEvent event){if(!InputManagerGlobal.getInstance().injectInputEvent(event,2))throw new IllegalStateException("input_not_accepted");}
    private void gesture(Screen s,JSONObject p)throws Exception{
        JSONArray ids=p.getJSONArray("ids"),counts=p.getJSONArray("counts"),xs=p.getJSONArray("xs"),ys=p.getJSONArray("ys");
        int n=ids.length(),duration=p.getInt("duration"),hold=p.getInt("hold");
        if(n<1||n>5||counts.length()!=n||duration<1||duration>5000||hold<0||hold>3000)throw new IllegalArgumentException("invalid_gesture");
        int total=0;int[] offsets=new int[n];Set<Integer> unique=new HashSet<>();
        for(int i=0;i<n;i++){int count=counts.getInt(i);if(count<1||count>256||ids.getInt(i)<0||ids.getInt(i)>31||!unique.add(ids.getInt(i)))throw new IllegalArgumentException("invalid_pointer");offsets[i]=total;total+=count;}
        if(xs.length()!=total||ys.length()!=total)throw new IllegalArgumentException("invalid_coordinates");
        for(int i=0;i<total;i++)if(xs.getInt(i)<0||ys.getInt(i)<0||xs.getInt(i)>=s.width||ys.getInt(i)>=s.height)throw new IllegalArgumentException("coordinate_out_of_bounds");
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[n];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[n];
        for(int i=0;i<n;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=ids.getInt(i);props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].pressure=1;coords[i].size=1;coords[i].x=xs.getInt(offsets[i]);coords[i].y=ys.getInt(offsets[i]);}
        long down=SystemClock.uptimeMillis();int active=0;
        try{
            for(int i=0;i<n;i++){active=i+1;motion(s,down,i==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_POINTER_DOWN|(i<<8),active,props,coords);}
            if(hold>0)SystemClock.sleep(hold);
            int steps=Math.max(1,duration/16);
            for(int step=1;step<=steps;step++){for(int i=0;i<n;i++){float position=(counts.getInt(i)-1)*step/(float)steps;int a=(int)position,b=Math.min(a+1,counts.getInt(i)-1);float fraction=position-a;int offset=offsets[i];coords[i].x=xs.getInt(offset+a)*(1-fraction)+xs.getInt(offset+b)*fraction;coords[i].y=ys.getInt(offset+a)*(1-fraction)+ys.getInt(offset+b)*fraction;}
                motion(s,down,MotionEvent.ACTION_MOVE,n,props,coords);SystemClock.sleep(Math.max(1,duration/steps));}
            for(int i=n-1;i>=0;i--){motion(s,down,i==0?MotionEvent.ACTION_UP:MotionEvent.ACTION_POINTER_UP|(i<<8),i+1,props,coords);active=i;}
        }finally{if(active>0)try{motion(s,down,MotionEvent.ACTION_CANCEL,active,props,coords);}catch(Exception ignored){}}
    }
    private void motion(Screen s,long down,int action,int count,MotionEvent.PointerProperties[] props,MotionEvent.PointerCoords[] coords){
        if(action!=MotionEvent.ACTION_CANCEL)unlocked();
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,-1,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{e.setDisplayId(s.display());inject(e);}finally{e.recycle();}
    }
    private final class Screen {
        final String id;final int width=1080,height=1920;final ImageReader reader;final VirtualDisplay virtual;Bitmap latest;
        Screen(String id){this.id=id;reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
            try{virtual=context.getSystemService(DisplayManager.class).createVirtualDisplay("AIOS "+id,width,height,320,reader.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY|DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH|DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED|DisplayManager.VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL);
                if(virtual==null)throw new IllegalStateException("virtual_display_unavailable");
                reader.setOnImageAvailableListener(ignored->updateFrame(),main);}
            catch(RuntimeException e){reader.close();throw e;}
        }
        int display(){return virtual.getDisplay().getDisplayId();}
        JSONObject info()throws Exception{return new JSONObject().put("instance",epoch).put("request",id).put("display",display()).put("width",width).put("height",height);}
        private void updateFrame(){try(Image image=reader.acquireLatestImage()){
            if(image==null)return;Image.Plane plane=image.getPlanes()[0];
            Bitmap padded=Bitmap.createBitmap(plane.getRowStride()/plane.getPixelStride(),height,Bitmap.Config.ARGB_8888);
            Bitmap next;
            try{padded.copyPixelsFromBuffer(plane.getBuffer());next=Bitmap.createBitmap(padded,0,0,width,height);if(next!=padded)padded.recycle();}
            catch(RuntimeException e){padded.recycle();throw e;}
            if(latest!=null)latest.recycle();latest=next;
        }}
        Bitmap capture(){
            unlocked();updateFrame();
            if(latest==null)throw new IllegalStateException("screen_frame_not_ready");
            // Static surfaces do not produce a new Image for every observation.
            // Keep the last rendered frame; callers own only their returned copy.
            return latest.copy(Bitmap.Config.ARGB_8888,false);
        }
        void close(){reader.setOnImageAvailableListener(null,null);virtual.release();reader.close();if(latest!=null){latest.recycle();latest=null;}}

    }
}
