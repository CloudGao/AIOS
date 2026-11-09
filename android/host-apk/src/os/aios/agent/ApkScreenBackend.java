package os.aios.agent;

import android.content.*;import android.graphics.*;import android.os.*;import android.util.Size;
import android.view.*;import android.view.accessibility.*;import android.widget.ImageView;
import java.util.*;import java.util.concurrent.*;import org.json.*;import os.aios.device.ScreenBackend;

/** Mechanism adapter. Session ownership and observation tokens remain in UiSessionController. */
final class ApkScreenBackend implements ScreenBackend {
    private final Context context;
    ApkScreenBackend(Context c){context=c.getApplicationContext();}
    public String implementationId(){return "aios.direct-adb.v1";}
    private SharedPreferences receipts(){return context.getSharedPreferences("apk_screen_receipts",0);}
    public ReleaseState releaseState(String request,int display){
        String epoch=receipts().getString(request,"");if(epoch.isEmpty())return ReleaseState.UNKNOWN;
        int boot=android.provider.Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);
        int createdBoot=receipts().getInt("boot:"+request,-1);
        if(boot>=0&&createdBoot>=0&&boot!=createdBoot)return ReleaseState.RELEASED;
        int knownDisplay=receipts().getInt("display:"+request,display);
        android.hardware.display.DisplayManager manager=context.getSystemService(android.hardware.display.DisplayManager.class);
        if(knownDisplay>0&&manager!=null&&manager.getDisplay(knownDisplay)==null)return ReleaseState.RELEASED;
        if(!AdbBridge.connected())return ReleaseState.UNKNOWN;
        try{return ReleaseState.valueOf(AdbBridge.call("release_state",new JSONObject().put("instance",epoch).put("request",request)).getString("state"));}
        catch(Exception e){return ReleaseState.UNKNOWN;}
    }
    public void create(String request,String name,List<String> packages,Executor executor,Creation callback){
        if(!AdbBridge.connected())throw new IllegalStateException("adb_activation_required");
        if(ForegroundUiService.instance==null)throw new IllegalStateException("accessibility_required");
        if(receipts().contains(request))throw new IllegalArgumentException("creation_request_id_reused");
        if(!receipts().edit().putString(request,AdbBridge.instance()).putInt("boot:"+request,android.provider.Settings.Global.getInt(context.getContentResolver(),"boot_count",-1)).commit())throw new IllegalStateException("screen_receipt_write_failed");
        try{JSONObject value=AdbBridge.call("create",new JSONObject().put("request",request));
            SessionImpl session=new SessionImpl(value);if(!receipts().edit().putInt("display:"+request,session.display).commit()){session.close();throw new IllegalStateException("screen_receipt_write_failed");}executor.execute(()->callback.onSessionCreated(session));}
        catch(Exception error){throw new IllegalStateException("screen_creation_unconfirmed",error);}
    }
    public Preview preview(Context c){return new PreviewImpl(c);}
    private static final class SessionImpl implements Session {
        final String epoch,id;final int display,width,height;
        private Executor callbackExecutor;private Lifecycle callback;private boolean closed,blocked;
        private final Runnable death=this::disconnected;
        private void disconnected(){blocked=true;closed=true;AdbBridge.removeDeath(death);if(callback!=null)callbackExecutor.execute(()->callback.onClosed(BLOCK_REASON_UNKNOWN));}
        SessionImpl(JSONObject p)throws JSONException{epoch=p.getString("instance");id=p.getString("request");display=p.getInt("display");width=p.getInt("width");height=p.getInt("height");if(display<=0)throw new SecurityException("physical_display_refused");AdbBridge.onDeath(death);}
        JSONObject args(){try{return new JSONObject().put("instance",epoch).put("request",id);}catch(JSONException e){throw new IllegalStateException(e);}}
        void ready(){if(closed||!epoch.equals(AdbBridge.instance())||!AdbBridge.connected())throw new IllegalStateException("screen_unavailable");}
        void inputReady(){ready();if(blocked)throw new IllegalStateException("screen_user_control");}
        public Size getDisplaySize(){return new Size(width,height);}
        public int refreshObservationChannel(){ready();if(ForegroundUiService.instance==null)throw new IllegalStateException("accessibility_required");return display;}
        public List<AccessibilityWindowInfo> getAccessibilityWindows(){
            ready();ForegroundUiService service=ForegroundUiService.instance;if(service==null)throw new IllegalStateException("accessibility_required");
            if(service.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked())throw new SecurityException("device_locked");
            List<AccessibilityWindowInfo> windows=service.getWindowsOnAllDisplays().get(display);
            if(windows==null)return Collections.emptyList();List<AccessibilityWindowInfo> result=new ArrayList<>();
            for(AccessibilityWindowInfo window:windows){if(window.getDisplayId()!=display)throw new SecurityException("window_display_mismatch");result.add(window);}return result;
        }
        public Frame capture(){ready();Bitmap bitmap=AdbBridge.frame(epoch,id);if(bitmap==null)throw new IllegalStateException("screen_frame_not_ready");return new Frame(bitmap,SystemClock.elapsedRealtimeNanos());}
        public void setLifecycleCallback(Executor executor,Lifecycle listener){callbackExecutor=executor;callback=listener;executor.execute(()->{if(!closed&&AdbBridge.connected()&&epoch.equals(AdbBridge.instance()))listener.onActive();else listener.onClosed(BLOCK_REASON_UNKNOWN);});}
        public void launchApplication(String pkg){inputReady();try{AdbBridge.call("launch",args().put("package",pkg));}catch(JSONException e){throw new IllegalStateException(e);}}
        public void tap(int x,int y){performGesture(new int[]{0},new int[]{1},new int[]{x},new int[]{y},0,60);}
        public void doubleTap(int x,int y,int interval){tap(x,y);SystemClock.sleep(interval);tap(x,y);}
        public void longPress(int x,int y){performGesture(new int[]{0},new int[]{1},new int[]{x},new int[]{y},650,50);}
        public void performGesture(int[] ids,int[] counts,int[] xs,int[] ys,int hold,int duration){inputReady();gesture(ids,counts,xs,ys,hold,duration);}
        void gesture(int[] ids,int[] counts,int[] xs,int[] ys,int hold,int duration){ready();try{AdbBridge.call("gesture",args().put("ids",new JSONArray(ids)).put("counts",new JSONArray(counts)).put("xs",new JSONArray(xs)).put("ys",new JSONArray(ys)).put("hold",hold).put("duration",duration));}catch(JSONException e){throw new IllegalStateException(e);}}
        public void insertText(String text,boolean replace,boolean commit){
            inputReady();if(text.length()>32768)throw new IllegalArgumentException("text_too_large");
            AccessibilityNodeInfo input=null;for(AccessibilityWindowInfo window:getAccessibilityWindows()){
                AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
                AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if(focus!=null&&focus.isEditable()){input=focus;break;}
            }
            if(input==null)throw new IllegalStateException("editable_node_unavailable");
            String value=text;
            if(!replace){String previous=input.getText()==null?"":input.getText().toString();int start=input.getTextSelectionStart(),end=input.getTextSelectionEnd();
                if(start<0||end<start||end>previous.length())throw new IllegalStateException("text_selection_unavailable");value=previous.substring(0,start)+text+previous.substring(end);}
            Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
            if(!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments))throw new IllegalStateException("text_action_not_accepted");
            if(commit)key(KeyEvent.KEYCODE_ENTER);
        }
        private void key(int key){inputReady();try{AdbBridge.call("key",args().put("key",key));}catch(JSONException e){throw new IllegalStateException(e);}}
        public void back(){key(KeyEvent.KEYCODE_BACK);}
        public void notifyBlocked(){ready();blocked=true;if(callback!=null)callbackExecutor.execute(()->callback.onBlocked(BLOCK_REASON_CALLER_INITIATED,""));}
        public void requestUnblock(){ready();blocked=false;if(callback!=null)callbackExecutor.execute(()->callback.onActive());}
        public void handOverApplications(){ready();try{JSONArray tasks=AdbBridge.call("tasks",new JSONObject()).getJSONArray("tasks");for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);if(t.getInt("display")==display)AdbBridge.call("move",new JSONObject().put("task",t.getInt("id")).put("display",0));}}catch(JSONException e){throw new IllegalStateException(e);}}
        public void close(){
            if(closed)return;blocked=true;
            try{ready();AdbBridge.call("close",args());closed=true;AdbBridge.removeDeath(death);if(callback!=null)callbackExecutor.execute(()->callback.onClosed(0));}
            catch(RuntimeException unknown){closed=true;AdbBridge.removeDeath(death);android.util.Log.w("AIOSApkScreen","Close unconfirmed; durable release receipt retained",unknown);}
            // A local close never returns the scheduler lease. releaseState must prove removal.
        }
    }
    private static final class PreviewImpl implements Preview {
        private final ImageView view;private final Handler handler=new Handler(Looper.getMainLooper());
        private static final ExecutorService worker=Executors.newFixedThreadPool(2);
        private SessionImpl session;private boolean interactive;private int generation;private Bitmap bitmap;
        private float downX,downY;private long downAt;
        PreviewImpl(Context c){view=new ImageView(c);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setContentDescription(c.getString(R.string.apk_screen_preview));
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){poll();}public void onViewDetachedFromWindow(View v){handler.removeCallbacksAndMessages(null);}});
            view.setOnTouchListener((v,e)->touch(e));}
        public View view(){return view;}
        public void attach(Session value){detach();if(!(value instanceof SessionImpl))throw new IllegalArgumentException("preview_backend_mismatch");session=(SessionImpl)value;poll();}
        public void detach(){generation++;session=null;interactive=false;handler.removeCallbacksAndMessages(null);view.setImageDrawable(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
        public void setInteractive(boolean enabled){interactive=enabled;}
        private void poll(){SessionImpl current=session;int version=generation;if(current==null||!view.isAttachedToWindow())return;
            worker.execute(()->{Bitmap next=null;try{next=current.capture().bitmap;}catch(Exception ignored){}final Bitmap result=next;
                handler.post(()->{if(version!=generation||session!=current){if(result!=null)result.recycle();return;}
                    if(result!=null){Bitmap old=bitmap;bitmap=result;view.setImageBitmap(result);if(old!=null)old.recycle();}handler.postDelayed(this::poll,500);});});}
        private boolean touch(MotionEvent event){SessionImpl current=session;if(!interactive||current==null)return false;
            float scale=Math.min(view.getWidth()/(float)current.width,view.getHeight()/(float)current.height);
            if(scale<=0)return true;float x=(event.getX()-(view.getWidth()-current.width*scale)/2)/scale,y=(event.getY()-(view.getHeight()-current.height*scale)/2)/scale;
            if(x<0||y<0||x>=current.width||y>=current.height)return true;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=x;downY=y;downAt=SystemClock.uptimeMillis();}
            else if(event.getActionMasked()==MotionEvent.ACTION_UP){int x0=(int)downX,y0=(int)downY,x1=(int)x,y1=(int)y;int duration=(int)Math.max(60,Math.min(1500,SystemClock.uptimeMillis()-downAt));
                worker.execute(()->{try{current.gesture(new int[]{0},new int[]{2},new int[]{x0,x1},new int[]{y0,y1},0,duration);}catch(Exception ignored){}});}
            return true;
        }
    }
}
