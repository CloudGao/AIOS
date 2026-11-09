package com.android.launcher3.aios;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.launcher3.Launcher;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native minus-one page. Workspace data stays in the AIOS app. */
public final class AiosOverlay implements LauncherOverlayManager,
        LauncherOverlayManager.LauncherOverlayTouchProxy, android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    // Same Material 3 roles as os.aios.core.AiosTheme so the minus-one page matches AIOS screens.
    private int INK,MUTED,BLUE,SURFACE,SURFACE_HIGH,BACKGROUND,SELECTED,TONAL,ON_TONAL,OUTLINE,WARN;
    private int paletteMode;
    private int systemColor(String role,boolean dark){int id=launcher.getResources().getIdentifier("system_"+role+(dark?"_dark":"_light"),"color","android");return launcher.getColor(id);}
    private void palette(){
        paletteMode=launcher.getResources().getConfiguration().uiMode;
        boolean dark=(paletteMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        INK=systemColor("on_surface",dark);MUTED=systemColor("on_surface_variant",dark);BLUE=systemColor("primary",dark);
        SURFACE=systemColor(dark?"surface_container_high":"surface",dark);SURFACE_HIGH=systemColor(dark?"surface_container_highest":"surface_container_high",dark);BACKGROUND=systemColor("surface_container",dark);SELECTED=systemColor("primary_container",dark);
        TONAL=systemColor("secondary_container",dark);ON_TONAL=systemColor("on_secondary_container",dark);OUTLINE=systemColor("outline",dark);WARN=dark?0xfff3c26b:0xff8a5a00;
        panel.setBackgroundColor(BACKGROUND);panel.setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private final Launcher launcher;
    private final Page panel;
    private final LinearLayout content;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LauncherOverlayCallbacks callbacks;
    private ValueAnimator animator;
    private float progress;
    private float fling;
    private boolean destroyed, loading;
    private String lastSnapshot;
    private Bundle screenLinks=new Bundle();
    private final java.util.Map<String,android.widget.ImageView> screenImages=new java.util.HashMap<>();
    private final java.util.Map<String,TextView> screenHints=new java.util.HashMap<>();
    private final java.util.Map<String,String> screenStates=new java.util.HashMap<>();
    private final java.util.Map<String,android.graphics.Bitmap> previewFrames=new java.util.HashMap<>();
    private android.widget.HorizontalScrollView screenStrip;
    private void clearPreviews(){for(android.widget.ImageView v:screenImages.values())v.setImageDrawable(null);for(android.graphics.Bitmap b:previewFrames.values())b.recycle();previewFrames.clear();}
    private final Runnable refresh=()->loadSnapshot();
    private final Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks() {
        public void onActivityResumed(Activity a){if(a==launcher&&isVisible())loadSnapshot();}
        public void onActivityPaused(Activity a){if(a==launcher)handler.removeCallbacks(refresh);}
        public void onActivityCreated(Activity a,Bundle b){}
        public void onActivityStarted(Activity a){}
        public void onActivityStopped(Activity a){}
        public void onActivitySaveInstanceState(Activity a,Bundle b){}
        public void onActivityDestroyed(Activity a){}
    };

    public AiosOverlay(Launcher launcher) {
        this.launcher=launcher;
        panel=new Page();
        panel.setVisibility(View.GONE);
        palette();
        panel.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        ScrollView scroll=new ScrollView(launcher);
        scroll.setFillViewport(true);scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        content=column();content.setPadding(dp(24),dp(18),dp(24),dp(28));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        panel.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        ((ViewGroup)launcher.getWindow().getDecorView().findViewById(android.R.id.content)).addView(panel,
                new ViewGroup.LayoutParams(-1,-1));
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->position(progress));
        render(null,null);
        launcher.getSharedPrefs().registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(launcher.getSharedPrefs(), "pref_enable_minus_one");
        launcher.getApplication().registerActivityLifecycleCallbacks(lifecycle);
    }

    @Override public void onSharedPreferenceChanged(android.content.SharedPreferences prefs,String key){
        if(!"pref_enable_minus_one".equals(key))return;
        boolean enabled=prefs.getBoolean(key,true);
        if(!enabled)hideOverlay(0);
        launcher.setLauncherOverlay(enabled?this:null);
    }
    public boolean isVisible(){return progress>0;}
    @Override public void setOverlayCallbacks(LauncherOverlayCallbacks cb){callbacks=cb;}
    @Override public void onFlingVelocity(float velocity){fling=velocity;}
    @Override public void onOverlayMotionEvent(MotionEvent event,float amount){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN: stopAnimation();fling=0;loadSnapshot();break;
            case MotionEvent.ACTION_MOVE: position(amount);break;
            case MotionEvent.ACTION_UP: settle(amount>.33f||fling>900?1:0,260);break;
            case MotionEvent.ACTION_CANCEL: settle(0,180);break;
        }
    }
    private void stopAnimation(){if(animator!=null){animator.cancel();animator=null;}}
    private void position(float value){
        progress=Math.max(0,Math.min(1,value));
        panel.setVisibility(progress==0?View.GONE:View.VISIBLE);
        panel.setTranslationX(-(1-progress)*panel.getWidth());
        launcher.getRootView().setImportantForAccessibility(progress>0
                ?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if(callbacks!=null)callbacks.onOverlayScrollChanged(progress);
        if(progress==0)handler.removeCallbacks(refresh);
    }
    private void settle(float target,int duration){
        stopAnimation();
        if(duration==0){position(target);return;}
        animator=ValueAnimator.ofFloat(progress,target);
        animator.setDuration(duration);animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a->position((float)a.getAnimatedValue()));animator.start();
    }
    @Override public void openOverlay(){if(!launcher.getSharedPrefs().getBoolean("pref_enable_minus_one",true))return;loadSnapshot();settle(1,260);}
    @Override public void hideOverlay(int duration){settle(0,duration);}
    @Override public void onActivityDestroyed(){
        launcher.getSharedPrefs().unregisterOnSharedPreferenceChangeListener(this);
        launcher.setLauncherOverlay(null);
        destroyed=true;clearPreviews();stopAnimation();handler.removeCallbacksAndMessages(null);worker.shutdownNow();
        launcher.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
        position(0);((ViewGroup)panel.getParent()).removeView(panel);
    }
    private void loadSnapshot(){
        if(destroyed||loading)return;
        loading=true;handler.removeCallbacks(refresh);
        worker.execute(()->{
            String data=null,error=null;Bundle links=new Bundle();
            java.util.Map<String,android.graphics.Bitmap> frames=new java.util.HashMap<>();
            java.util.Map<String,String> frameStates=new java.util.HashMap<>();
            try{
                Bundle result=launcher.getContentResolver().call(
                    Uri.parse("content://os.aios.agent.home"),"snapshot",null,null);
                if(result==null||result.getString("json")==null)throw new IllegalStateException();
                data=result.getString("json");links=result;
                JSONArray screens=new JSONObject(data).optJSONArray("ui_sessions");
                if(screens!=null)for(int i=0;i<Math.min(4,screens.length());i++){
                    String id=screens.getJSONObject(i).getString("session_id");
                    Bundle frame=launcher.getContentResolver().call(Uri.parse("content://os.aios.agent.home"),"preview",id,null);
                    if(frame==null)continue;
                    frameStates.put(id,frame.getString("frame_state","unavailable"));byte[] jpeg=frame.getByteArray("jpeg");
                    if(jpeg!=null){android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(bitmap!=null)frames.put(id,bitmap);}
                }
            }catch(Exception e){error="暂时无法读取工作空间，轻点打开 AIOS";}
            final String snapshot=data,problem=error;final Bundle actions=links;
            handler.post(()->{
                loading=false;if(destroyed){for(android.graphics.Bitmap b:frames.values())b.recycle();return;}screenLinks=actions;
                if(snapshot==null||!snapshot.equals(lastSnapshot)||paletteMode!=launcher.getResources().getConfiguration().uiMode){
                    lastSnapshot=snapshot;render(snapshot,problem);
                }
                for(android.widget.ImageView v:screenImages.values())v.setImageDrawable(null);
                for(android.graphics.Bitmap b:previewFrames.values())b.recycle();previewFrames.clear();previewFrames.putAll(frames);
                for(String id:screenImages.keySet()){
                    android.graphics.Bitmap frame=previewFrames.get(id);screenImages.get(id).setImageBitmap(frame);
                    String state=screenStates.get(id),kind=frameStates.get(id);TextView hint=screenHints.get(id);
                    if(frame!=null){hint.setText("live".equals(kind)?"实时画面":"paused".equals(state)?"已暂停 · 最后画面":"最后画面 · 暂未更新");hint.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hint.setBackgroundColor(0x99000000);hint.setTextColor(Color.WHITE);}
                    else{hint.setText("paused".equals(state)?"已暂停\n暂无保留画面":"waiting_for_app".equals(state)?"等待应用可用\n画面就绪后显示":"正在获取画面…");hint.setGravity(Gravity.CENTER);hint.setBackgroundColor(0x00ffffff);hint.setTextColor(MUTED);}
                }
                if(isVisible()&&launcher.hasBeenResumed())handler.postDelayed(refresh,2000);
            });
        });
    }
    private int dp(int n){return Math.round(n*launcher.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(launcher);v.setOrientation(1);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(launcher);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView v=DisplayText.bind(new TextView(launcher));v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private TextView bold(String value,int size,int color){TextView v=text(value,size,color);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    private View dot(int color){View v=new View(launcher);v.setBackground(bg(color,4));return v;}
    private void gap(LinearLayout v,int size){v.addView(new View(launcher),new LinearLayout.LayoutParams(1,dp(size)));}
    private void clickable(View v,String description,Runnable action){
        v.setContentDescription(DisplayText.clean(description));v.setFocusable(true);v.setOnClickListener(w->action.run());
        android.util.TypedValue value=new android.util.TypedValue();
        launcher.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true);
        v.setForeground(launcher.getDrawable(value.resourceId));
    }
    private LinearLayout card(){
        LinearLayout c=column();c.setPadding(dp(20),dp(18),dp(20),dp(18));
        c.setBackground(bg(SURFACE,28));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);
        content.addView(c,lp);return c;
    }
    private void heading(LinearLayout parent,String label,String action,Runnable click){
        LinearLayout r=row();r.addView(bold(label,18,INK),new LinearLayout.LayoutParams(0,-2,1));
        TextView all=text(action,13,BLUE);all.setMinHeight(dp(44));all.setGravity(Gravity.CENTER);
        all.setPadding(dp(8),0,0,0);clickable(all,label+"："+action,click);r.addView(all);parent.addView(r);
    }
    private void launch(String tab,String key,String id){
        Intent intent=new Intent().setClassName("os.aios.agent","os.aios.agent.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("workspace_tab",tab);
        if(key!=null)intent.putExtra(key,id);
        try{launcher.startActivity(intent);}catch(Exception e){Toast.makeText(launcher,DisplayText.clean("AIOS 暂时不可用"),Toast.LENGTH_SHORT).show();}
    }
    private void openScreen(String key){
        try{android.app.PendingIntent link=screenLinks.getParcelable(key,android.app.PendingIntent.class);
            if(link==null)throw new IllegalStateException();
            android.app.ActivityOptions options=android.app.ActivityOptions.makeBasic();
            options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            link.send(launcher,0,null,null,null,null,options.toBundle());
        }catch(Exception e){Toast.makeText(launcher,DisplayText.clean("虚拟屏已变化，请稍后重试"),Toast.LENGTH_SHORT).show();loadSnapshot();}
    }
    private void render(String raw,String error){
        palette();
        try{
            JSONObject data=raw==null?new JSONObject():new JSONObject(raw);
            int stripOffset=screenStrip==null?0:screenStrip.getScrollX();
            screenImages.clear();screenHints.clear();screenStates.clear();screenStrip=null;
            content.removeAllViews();
            LinearLayout dateRow=row();
            dateRow.addView(text(new SimpleDateFormat("M月d日  EEEE",Locale.SIMPLIFIED_CHINESE).format(new Date()),14,MUTED),new LinearLayout.LayoutParams(0,-2,1));
            TextView home=text("主屏  ›",14,BLUE);home.setGravity(Gravity.CENTER);home.setMinHeight(dp(44));home.setPadding(dp(14),0,0,0);
            clickable(home,"返回主屏",()->hideOverlay(true));dateRow.addView(home);content.addView(dateRow);
            gap(content,8);

            LinearLayout hero=card();
            heading(hero,"虚拟屏槽位","全部  ›",()->openScreen("screens"));
            JSONArray screens=data.optJSONArray("ui_sessions");
            int screenCount=screens==null?0:screens.length();
            String slotHint=screenCount>4
                ? "有 "+screenCount+" 个历史任务 · 此处只显示 4 个 · 到“全部”中关闭多余任务"
                : "最多 4 个 · 点击进入虚拟屏 · × 关闭并释放";
            hero.addView(text(slotHint,13,MUTED));gap(hero,12);
            if(screens==null||screens.length()==0){
                hero.addView(bold("暂无后台界面任务",21,INK));gap(hero,8);
                hero.addView(text("让助手操作应用，进展会显示在这里。",14,MUTED));
            }else{
                android.widget.HorizontalScrollView strip=new android.widget.HorizontalScrollView(launcher);
                strip.setHorizontalScrollBarEnabled(false);screenStrip=strip;LinearLayout cards=row();
                for(int i=0;i<Math.min(4,screens.length());i++){
                    JSONObject screen=screens.getJSONObject(i);String id=screen.getString("session_id");
                    LinearLayout tile=column();tile.setPadding(dp(10),dp(12),dp(10),dp(10));tile.setBackground(bg(SURFACE_HIGH,16));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(164),-2);lp.rightMargin=dp(10);cards.addView(tile,lp);
                    LinearLayout titleRow=row();TextView title=bold(screen.optString("task_name","本次会话"),16,INK);title.setMaxLines(2);title.setMinLines(2);title.setEllipsize(TextUtils.TruncateAt.END);titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                    TextView close=text("×",24,MUTED);close.setGravity(Gravity.CENTER);close.setMinWidth(dp(38));close.setMinHeight(dp(38));clickable(close,"关闭虚拟屏："+screen.optString("task_name"),()->openScreen("close:"+id));titleRow.addView(close);tile.addView(titleRow);gap(tile,5);
                    String state=screen.optString("state");boolean paused=screen.optString("user_state","running").equals("paused");String label=paused?"暂停":"进行中";
                    LinearLayout metaRow=row();metaRow.addView(dot(paused?WARN:BLUE),new LinearLayout.LayoutParams(dp(8),dp(8)));TextView metaText=text(screen.optString("app_label")+" · "+label,12,MUTED);metaText.setPadding(dp(6),0,0,0);metaRow.addView(metaText);tile.addView(metaRow);gap(tile,10);
                    FrameLayout viewport=new FrameLayout(launcher);viewport.setBackground(bg(BACKGROUND,12));viewport.setClipToOutline(true);tile.addView(viewport,new LinearLayout.LayoutParams(-1,dp(180)));
                    android.widget.ImageView preview=new android.widget.ImageView(launcher);preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);preview.setContentDescription(DisplayText.clean("屏幕预览："+screen.optString("task_name")));viewport.addView(preview,new FrameLayout.LayoutParams(-1,-1));
                    TextView hint=text("正在获取画面…",12,MUTED);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(4),dp(4),dp(4),dp(4));FrameLayout.LayoutParams hintLayout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);viewport.addView(hint,hintLayout);
                    screenImages.put(id,preview);screenHints.put(id,hint);screenStates.put(id,state);
                    clickable(tile,"进入虚拟屏："+screen.optString("app_label")+"，"+screen.optString("task_name"),()->openScreen("screen:"+id));
                }
                strip.addView(cards);hero.addView(strip);strip.post(()->strip.scrollTo(stripOffset,0));
            }
            gap(hero,14);TextView compose=bold("进入会话",14,ON_TONAL);compose.setMinHeight(dp(44));compose.setGravity(Gravity.CENTER);compose.setBackground(bg(TONAL,22));
            clickable(compose,"进入会话",()->launch("sessions",null,null));hero.addView(compose);

            LinearLayout tasks=card();heading(tasks,"正在推进","全部  ›",()->launch("tasks",null,null));
            gap(tasks,4);
            LinearLayout stats=row();
            stat(stats,data.optInt("doing"),"进行中",BLUE);stat(stats,data.optInt("todo"),"待办",INK);stat(stats,data.optInt("done"),"已完成",MUTED);
            tasks.addView(stats);gap(tasks,12);
            JSONArray list=data.optJSONArray("tasks");
            if(error!=null){TextView warning=text(error,14,MUTED);tasks.addView(warning);clickable(warning,"打开 AIOS",()->launch("tasks",null,null));}
            else if(raw==null)tasks.addView(text("正在读取工作空间…",14,MUTED));
            else if(list==null||list.length()==0)tasks.addView(text("暂无待办任务",14,MUTED));
            else for(int i=0;i<list.length();i++){
                JSONObject item=list.getJSONObject(i);String id=item.getString("id");
                LinearLayout line=row();line.setPadding(0,dp(10),0,dp(10));line.setMinimumHeight(dp(56));
                LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(8),dp(8));dl.rightMargin=dp(16);line.addView(dot("doing".equals(item.optString("status"))?BLUE:OUTLINE),dl);
                TextView title=text(item.getString("title"),15,INK);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);line.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                clickable(line,"打开任务："+item.getString("title"),()->launch("tasks","workspace_task",id));tasks.addView(line);
            }

            LinearLayout sessions=card();heading(sessions,"最近会话","全部  ›",()->launch("sessions",null,null));
            JSONArray recent=data.optJSONArray("sessions");
            if(recent==null||recent.length()==0)sessions.addView(text(raw==null?"正在读取会话…":"暂无会话",14,MUTED));
            else for(int i=0;i<recent.length();i++){
                JSONObject item=recent.getJSONObject(i);String id=item.getString("id");
                LinearLayout line=column();line.setPadding(0,dp(12),0,dp(12));
                TextView title=text(item.getString("title"),15,INK);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);line.addView(title);gap(line,5);
                line.addView(text(launcher.getString("codex".equals(item.optString("source"))?com.android.launcher3.R.string.aios_phone_conversation:com.android.launcher3.R.string.aios_local_conversation),12,MUTED));
                clickable(line,"打开会话："+item.getString("title"),()->launch("sessions","workspace_session",id));sessions.addView(line);
            }
            LinearLayout footer=row();
            TextView settings=text("工作空间配置",14,MUTED);settings.setMinHeight(dp(48));settings.setGravity(Gravity.CENTER_VERTICAL);
            clickable(settings,"工作空间配置",()->launch("settings",null,null));footer.addView(settings,new LinearLayout.LayoutParams(0,-2,1));
            footer.addView(text("向左滑回主屏  ›",12,MUTED));content.addView(footer);
        }catch(Exception e){content.removeAllViews();TextView fallback=text("打开 AIOS 工作空间  ›",18,BLUE);fallback.setMinHeight(dp(64));clickable(fallback,"打开 AIOS",()->launch("tasks",null,null));content.addView(fallback);}
    }
    private void stat(LinearLayout parent,int count,String label,int color){
        LinearLayout cell=column();cell.addView(bold(String.valueOf(count),22,color));gap(cell,4);cell.addView(text(label,12,MUTED));
        parent.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }

    /** Locks each touch sequence to one axis so vertical cards and horizontal return coexist. */
    private final class Page extends FrameLayout {
        private float startX,startY,startProgress;
        private int axis;
        private VelocityTracker velocity;
        Page(){super(launcher);setClickable(true);}
        private void track(MotionEvent event){
            if(velocity==null)return;
            MotionEvent screenEvent=MotionEvent.obtain(event);
            screenEvent.offsetLocation(event.getRawX()-event.getX(),event.getRawY()-event.getY());
            velocity.addMovement(screenEvent);screenEvent.recycle();
        }
        private boolean inScreenStrip(View v,float x,float y){
            if(v instanceof android.widget.HorizontalScrollView){android.graphics.Rect r=new android.graphics.Rect();return v.getGlobalVisibleRect(r)&&r.contains((int)x,(int)y);}
            if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)if(inScreenStrip(group.getChildAt(i),x,y))return true;}
            return false;
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                stopAnimation();startX=event.getRawX();startY=event.getRawY();startProgress=progress;axis=inScreenStrip(content,startX,startY)?2:0;
                if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();track(event);
            }else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&axis==0){
                float x=Math.abs(event.getRawX()-startX),y=Math.abs(event.getRawY()-startY);
                if(Math.max(x,y)>ViewConfiguration.get(launcher).getScaledTouchSlop())axis=x>y*1.3f?1:2;
            }else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                if(velocity!=null){velocity.recycle();velocity=null;}
            }
            return axis==1;
        }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow){
            // ScrollView may claim vertical events; axis lock still owns a horizontal return.
            if(axis==2)super.requestDisallowInterceptTouchEvent(disallow);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            track(event);
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&axis==1){
                position(startProgress+(event.getRawX()-startX)/Math.max(1,getWidth()));
            }else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getXVelocity();velocity.recycle();velocity=null;}
                if(axis==1)settle(event.getActionMasked()==MotionEvent.ACTION_CANCEL?startProgress:
                    speed < -900?0:speed>900?1:progress>.65f?1:0,240);
                performClick();
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
