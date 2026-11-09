package os.aios.agent;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosUi;
import os.aios.core.AiosTheme;

/** Retained pages: mini apps, complete home, workspace. */
public final class ApkHomeActivity extends MainActivity {
    private boolean screens=true,resumed,loading,refreshPending;
    private int selectedPage;
    private os.aios.miniapp.MiniAppGrid miniApps;
    private LinearLayout root,cards;
    private SwipePages pages;
    private View workspace;
    private ScrollView screenPage;
    private HorizontalScrollView screenStrip;
    private String layoutKey="";
    private final java.util.Map<String,ImageView> images=new java.util.HashMap<>();
    private final java.util.Map<String,TextView> captions=new java.util.HashMap<>();
    private final java.util.List<Bitmap> bitmaps=new java.util.ArrayList<>();
    private final Handler refreshHandler=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable refresh=this::loadScreens;

    @Override public void onCreate(Bundle state){
        selectedPage=state==null?0:Math.max(-1,Math.min(1,state.getInt("apk_home_page",state.getBoolean("apk_screen_home",true)?0:1)));screens=selectedPage==0;
        super.onCreate(state);
        android.content.SharedPreferences prefs=getSharedPreferences("apk-onboarding",0);
        boolean upgraded=false;
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);upgraded=info.lastUpdateTime>info.firstInstallTime;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        if(!prefs.contains("handled")&&upgraded&&Authorization.enabled(this))prefs.edit().putBoolean("handled",true).apply();
        if(!prefs.getBoolean("handled",false)){startActivity(new Intent(this,ApkSettingsActivity.class).putExtra("onboarding",true));finish();return;}
        selectPage(selectedPage,false);
        if(getSharedPreferences("apk-permissions",0).getInt("version",0)<ApkPermissionsActivity.VERSION)startActivity(new Intent(this,ApkPermissionsActivity.class).putExtra("automatic",true));
    }
    @Override public void setContentView(View view){
        if(root==null)createShell();
        if(workspace!=null)pages.removeView(workspace);
        workspace=view;pages.addView(workspace,new FrameLayout.LayoutParams(-1,-1));
        pages.positionPages();
        super.setContentView(root);
    }
    private void createShell(){
        root=AiosUi.column(this);root.setBackgroundColor(AiosTheme.background(this));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return WindowInsets.CONSUMED;});
        pages=new SwipePages();root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        miniApps=new os.aios.miniapp.MiniAppGrid(this);miniApps.setId(7102);pages.addView(miniApps,new FrameLayout.LayoutParams(-1,-1));
        screenPage=new ScrollView(this);screenPage.setId(7101);screenPage.setFillViewport(true);
        LinearLayout body=AiosUi.column(this);body.setPadding(0,0,0,AiosUi.dp(this,24));
        cards=AiosUi.column(this);body.addView(cards);screenPage.addView(body);pages.addView(screenPage,new FrameLayout.LayoutParams(-1,-1));
    }
    private void selectPage(boolean showScreens){selectPage(showScreens?0:1,true);}
    private void selectPage(int page,boolean animate){
        selectedPage=page;screens=page==0;if(pages==null)return;
        pages.moveTo(page,animate);
        if(page==-1&&miniApps!=null)miniApps.reload();
        refreshHandler.removeCallbacks(refresh);if(resumed&&screens)refreshHandler.post(refresh);
    }
    private final android.content.BroadcastReceiver homeChanges=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent intent){
        refreshPending=true;refreshHandler.removeCallbacks(refresh);if(resumed&&screens)refreshHandler.post(refresh);
    }};
    @Override protected void onResume(){super.onResume();resumed=true;registerReceiver(homeChanges,new android.content.IntentFilter(os.aios.workspace.WorkspaceStore.CHANGED),android.content.Context.RECEIVER_NOT_EXPORTED);selectPage(selectedPage,false);}
    @Override protected void onPause(){unregisterReceiver(homeChanges);resumed=false;refreshHandler.removeCallbacks(refresh);if(pages!=null)pages.moveTo(selectedPage,false);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("apk_home_page",selectedPage);state.putBoolean("apk_screen_home",screens);super.onSaveInstanceState(state);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);selectPage(true);}
    @Override public void onBackPressed(){if(!screens){selectPage(true);return;}super.onBackPressed();}
    @Override protected void onDestroy(){refreshHandler.removeCallbacks(refresh);worker.shutdownNow();releaseFrames();super.onDestroy();}
    private void releaseFrames(){for(ImageView image:images.values())image.setImageDrawable(null);for(Bitmap bitmap:bitmaps)bitmap.recycle();bitmaps.clear();}

    private void loadScreens(){
        if(!resumed||!screens||loading)return;loading=true;refreshPending=false;
        worker.execute(()->{
            JSONObject snapshot=new JSONObject();JSONArray rows=new JSONArray();java.util.Map<String,Bitmap> frames=new java.util.HashMap<>();java.util.Map<String,String> frameStates=new java.util.HashMap<>();boolean failed=false;
            try{
                try(os.aios.workspace.WorkspaceStore store=new os.aios.workspace.WorkspaceStore(this)){snapshot=store.call("home.snapshot",new JSONObject()).getJSONObject("data");}
                AgentService agent=AgentService.instance;
                JSONArray all=agent==null?null:agent.ui.call(new JSONObject().put("op","list")).optJSONArray("sessions");
                if(all!=null)for(int i=0;i<all.length()&&rows.length()<4;i++){
                    JSONObject row=all.getJSONObject(i);if(!"background".equals(row.optString("mode")))continue;rows.put(row);
                    String id=row.getString("session_id");Bundle preview=UiScreenPreview.read(agent,id);byte[] jpeg=preview.getByteArray("jpeg");
                    frameStates.put(id,preview.getString("frame_state","unavailable"));
                    if(jpeg!=null){Bitmap bitmap=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(bitmap!=null)frames.put(id,bitmap);}
                }
            }catch(Exception e){failed=true;}
            final JSONArray result=rows;final JSONObject summary=snapshot;final boolean error=failed;
            refreshHandler.post(()->{
                loading=false;if(isDestroyed()||!resumed||!screens){for(Bitmap bitmap:frames.values())bitmap.recycle();if(!isDestroyed()&&resumed&&screens)refreshHandler.post(refresh);return;}
                showScreens(summary,result,frames,frameStates,error);refreshHandler.removeCallbacks(refresh);refreshHandler.postDelayed(refresh,refreshPending?0:2000);
            });
        });
    }
    private void openWorkspace(String tab,String key,String id){
        Intent intent=new Intent(this,ApkHomeActivity.class).putExtra("workspace_tab",tab);
        if(key!=null)intent.putExtra(key,id);
        super.onNewIntent(intent);selectPage(false);
    }
    /** Settings-style section header: accent label on the left, optional “All” text button on the right. */
    private void section(int title,Runnable all){
        LinearLayout row=AiosUi.row(this);row.setPadding(AiosUi.dp(this,20),0,AiosUi.dp(this,8),0);
        TextView label=AiosUi.label(this,getString(title));label.setPadding(0,AiosUi.dp(this,10),0,AiosUi.dp(this,8));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(all!=null)row.addView(AiosUi.textButton(this,getString(R.string.apk_home_all),all));
        cards.addView(row);
    }
    private void stat(LinearLayout parent,int count,int label,int color){
        LinearLayout cell=AiosUi.column(this);cell.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView value=AiosUi.heading(this,java.text.NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0)).format(count));value.setTextColor(color);cell.addView(value);
        TextView name=AiosUi.meta(this,getString(label));name.setGravity(Gravity.CENTER_HORIZONTAL);name.setPadding(0,AiosUi.dp(this,2),0,0);cell.addView(name);parent.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
    }
    /** One-line hint inside a group (empty states / errors), on the grouped surface. */
    private void note(LinearLayout group,CharSequence text,boolean first,boolean last){
        TextView t=AiosUi.secondary(this,text);t.setPadding(AiosUi.dp(this,16),AiosUi.dp(this,18),AiosUi.dp(this,16),AiosUi.dp(this,18));
        t.setBackground(AiosUi.groupShape(this,AiosTheme.surface(this),first,last));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=last?0:AiosUi.dp(this,2);group.addView(t,lp);
    }
    private void showScreens(JSONObject summary,JSONArray rows,java.util.Map<String,Bitmap> frames,java.util.Map<String,String> states,boolean error){
        String date=android.text.format.DateFormat.getBestDateTimePattern(getResources().getConfiguration().getLocales().get(0),"MMM d EEEE");
        String dateLabel=new java.text.SimpleDateFormat(date,getResources().getConfiguration().getLocales().get(0)).format(new java.util.Date());
        StringBuilder key=new StringBuilder(Boolean.toString(error)).append(summary.toString()).append(dateLabel);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);key.append(row.optString("session_id")).append(row.optString("app_label")).append(row.optString("user_state"));}
        releaseFrames();
        if(!layoutKey.equals(key.toString())){
            int offset=screenStrip==null?0:screenStrip.getScrollX();
            layoutKey=key.toString();cards.removeAllViews();images.clear();captions.clear();
            // Compact header: date (16sp) on the left, settings icon on the right. No app title.
            LinearLayout header=AiosUi.row(this);header.setMinimumHeight(AiosUi.dp(this,56));header.setPadding(AiosUi.dp(this,24),AiosUi.dp(this,8),AiosUi.dp(this,8),0);
            header.addView(AiosUi.body(this,dateLabel),new LinearLayout.LayoutParams(0,-2,1));
            header.addView(AiosUi.iconButton(this,"settings",getString(R.string.apk_home_configuration),()->openWorkspace("settings",null,null)));cards.addView(header);

            // Background tasks: horizontal preview strip in a single 28dp card.
            section(R.string.apk_screen_home_title,()->startActivity(new Intent(this,UiSessionsActivity.class)));
            LinearLayout hero=AiosUi.card(this);hero.setPadding(0,AiosUi.dp(this,16),0,AiosUi.dp(this,8));cards.addView(hero);
            screenStrip=new HorizontalScrollView(this);screenStrip.setHorizontalScrollBarEnabled(false);screenStrip.setClipToPadding(false);screenStrip.setPadding(AiosUi.dp(this,16),0,AiosUi.dp(this,16),0);
            LinearLayout strip=AiosUi.row(this);strip.setGravity(Gravity.TOP);screenStrip.addView(strip);hero.addView(screenStrip);
            if(rows.length()==0){TextView empty=AiosUi.secondary(this,getString(error?R.string.apk_screens_unavailable:R.string.assistant_no_background_screen_tasks_106b9));empty.setPadding(AiosUi.dp(this,20),0,AiosUi.dp(this,20),AiosUi.dp(this,4));hero.addView(empty);}
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);String id=row.optString("session_id"),label=row.optString("app_label",getString(R.string.apk_screen_page));
                LinearLayout card=AiosUi.column(this);int pad=AiosUi.dp(this,12);card.setPadding(pad,pad,pad,pad);card.setBackground(AiosUi.ripple(this,AiosUi.shape(this,AiosTheme.surfaceHigh(this),16)));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(AiosUi.dp(this,164),-2);cp.setMarginEnd(AiosUi.dp(this,8));strip.addView(card,cp);
                LinearLayout title=AiosUi.row(this);TextView name=AiosUi.oneLine(AiosUi.body(this,label));title.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                ImageView close=AiosUi.iconButton(this,"close",getString(R.string.assistant_close_this_virtual_screen_1fa50),()->startService(new Intent(this,AgentService.class).putExtra("ui_card_action","close").putExtra("session_id",id)));
                close.setLayoutParams(new LinearLayout.LayoutParams(AiosUi.dp(this,36),AiosUi.dp(this,36)));close.setPadding(AiosUi.dp(this,6),AiosUi.dp(this,6),AiosUi.dp(this,6),AiosUi.dp(this,6));title.addView(close);card.addView(title);
                LinearLayout state=AiosUi.row(this);View dot=AiosUi.dot(this,AiosUi.statusColor(this,row.optString("user_state","running")));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(AiosUi.dp(this,8),AiosUi.dp(this,8));dl.setMarginEnd(AiosUi.dp(this,8));state.addView(dot,dl);
                state.addView(AiosUi.oneLine(AiosUi.secondary(this,AssistantLocale.label(this,row.optString("user_state","running")))));card.addView(state);
                ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(AiosUi.shape(this,AiosTheme.background(this),12));image.setClipToOutline(true);image.setContentDescription(getString(R.string.apk_preview_description,label));
                LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,AiosUi.dp(this,180));ip.topMargin=AiosUi.dp(this,8);card.addView(image,ip);images.put(id,image);
                TextView caption=AiosUi.meta(this,"");caption.setPadding(0,AiosUi.dp(this,6),0,0);card.addView(caption);captions.put(id,caption);
                AiosUi.clickable(card,()->startActivity(new Intent(this,UiViewerActivity.class).putExtra("session_id",id)));card.setContentDescription(getString(R.string.apk_preview_description,label));
            }
            screenStrip.post(()->{if(screenStrip!=null)screenStrip.scrollTo(offset,0);});

            // In-progress tasks: statistics header row + status rows in one grouped list.
            section(R.string.apk_home_progress,()->openWorkspace("tasks",null,null));
            LinearLayout tasks=AiosUi.group(this);cards.addView(tasks);
            JSONArray list=summary.optJSONArray("tasks");boolean hasTasks=!error&&list!=null&&list.length()>0;
            LinearLayout stats=AiosUi.row(this);stats.setPadding(AiosUi.dp(this,16),AiosUi.dp(this,18),AiosUi.dp(this,16),AiosUi.dp(this,18));stats.setBackground(AiosUi.groupShape(this,AiosTheme.surface(this),true,false));
            stat(stats,summary.optInt("doing"),R.string.apk_home_doing,AiosTheme.accent(this));stat(stats,summary.optInt("todo"),R.string.apk_home_todo,AiosTheme.warn(this));stat(stats,summary.optInt("done"),R.string.apk_home_done,AiosTheme.muted(this));
            LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);sl.bottomMargin=AiosUi.dp(this,2);tasks.addView(stats,sl);
            if(error)note(tasks,getString(R.string.apk_home_unavailable),false,true);
            else if(!hasTasks)note(tasks,getString(R.string.apk_home_no_tasks),false,true);
            else for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);String id=item.optString("id"),status=item.optString("status","todo");
                tasks.addView(AiosUi.statusRow(this,AiosUi.statusColor(this,status),item.optString("title"),getString("doing".equals(status)?R.string.apk_home_doing:R.string.apk_home_todo),null,false,i+1==list.length(),()->openWorkspace("tasks","workspace_task",id)));}

            // Recent conversations as grouped rows.
            section(R.string.apk_home_recent,()->openWorkspace("sessions",null,null));
            LinearLayout recent=AiosUi.group(this);cards.addView(recent);
            JSONArray sessions=summary.optJSONArray("sessions");
            if(sessions==null||sessions.length()==0)note(recent,getString(R.string.apk_home_no_sessions),true,true);
            else for(int i=0;i<sessions.length();i++){
                JSONObject item=sessions.optJSONObject(i);String id=item.optString("id");
                recent.addView(AiosUi.settingRow(this,"chat",item.optString("title"),java.util.Set.of("codex","assistant_pending").contains(item.optString("source"))?sessionSubtitle(item):getString(R.string.apk_home_local_conversation),null,i==0,i+1==sessions.length(),()->openWorkspace("sessions","workspace_session",id)));
            }

            TextView swipe=AiosUi.meta(this,getString(R.string.apk_home_swipe));swipe.setGravity(Gravity.CENTER_HORIZONTAL);swipe.setPadding(AiosUi.dp(this,16),AiosUi.dp(this,4),AiosUi.dp(this,16),0);cards.addView(swipe);
        }
        for(String id:images.keySet()){Bitmap bitmap=frames.get(id);images.get(id).setImageBitmap(bitmap);captions.get(id).setText(bitmap==null?R.string.apk_preview_waiting:"live".equals(states.get(id))?R.string.apk_preview_live:R.string.apk_preview_last);}
        bitmaps.addAll(frames.values());
    }
    private final class SwipePages extends FrameLayout {
        private float downX,downY,origin,position;
        private boolean ignore,dragging;
        private VelocityTracker velocity;
        private android.animation.ValueAnimator animator;
        SwipePages(){super(ApkHomeActivity.this);position=selectedPage;setClipChildren(true);}
        private void stopAnimation(){if(animator!=null){android.animation.ValueAnimator old=animator;animator=null;old.cancel();}}
        void positionPages(){
            float width=getWidth();
            miniApps.setTranslationX((-1-position)*width);
            miniApps.setImportantForAccessibility(position==-1?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            screenPage.setVisibility(View.VISIBLE);screenPage.setTranslationX(-position*width);
            screenPage.setImportantForAccessibility(position==0?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if(workspace!=null){workspace.setVisibility(View.VISIBLE);workspace.setTranslationX((1-position)*width);
                workspace.setImportantForAccessibility(position==1?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);positionPages();}
        void moveTo(int page,boolean animate){
            stopAnimation();float target=page;
            if(!animate||getWidth()==0||Math.abs(target-position)<0.001f){position=target;positionPages();return;}
            android.animation.ValueAnimator next=android.animation.ValueAnimator.ofFloat(position,target);animator=next;
            next.setDuration(240);next.setInterpolator(new android.view.animation.DecelerateInterpolator());
            next.addUpdateListener(a->{position=(float)a.getAnimatedValue();positionPages();});
            next.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(animator==next){animator=null;position=target;positionPages();}}});
            next.start();
        }
        @Override public boolean dispatchTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();}
            if(velocity!=null)velocity.addMovement(e);
            boolean handled=super.dispatchTouchEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){if(velocity!=null){velocity.recycle();velocity=null;}}
            return handled;
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                downX=e.getX();downY=e.getY();origin=position;dragging=false;
                ignore=downX<AiosUi.dp(getContext(),24)||downX>getWidth()-AiosUi.dp(getContext(),24)||blocksSwipe(this,e.getRawX(),e.getRawY());
                if(animator!=null&&!ignore){stopAnimation();dragging=true;}
            }else if(action==MotionEvent.ACTION_POINTER_DOWN){ignore=true;}
            else if(action==MotionEvent.ACTION_MOVE&&!ignore){
                float dx=e.getX()-downX,dy=e.getY()-downY;int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if(Math.abs(dy)>slop&&Math.abs(dy)>Math.abs(dx)){ignore=true;return false;}
                if(Math.abs(dx)>slop*2&&Math.abs(dx)>Math.abs(dy)*1.8f&&((position<1&&dx<0)||(position>-1&&dx>0))){
                    stopAnimation();dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
                }
            }
            return dragging;
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_MOVE:
                    if(dragging&&!ignore&&getWidth()>0){position=Math.max(Math.max(-1,Math.round(origin)-1),Math.min(Math.min(1,Math.round(origin)+1),origin-(e.getX()-downX)/getWidth()));positionPages();}break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    ignore=true;dragging=false;selectPage(selectedPage,true);break;
                case MotionEvent.ACTION_UP:
                    if(dragging&&!ignore){
                        float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000,ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());speed=velocity.getXVelocity();}
                        float travel=e.getX()-downX;float fling=Math.max(AiosUi.dp(getContext(),400),ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity());
                        int target=Math.round(position);
                        if(Math.abs(speed)>fling&&Math.abs(travel)>ViewConfiguration.get(getContext()).getScaledTouchSlop()*2)target=speed<0?(int)Math.floor(position)+1:(int)Math.ceil(position)-1;
                        target=Math.max(Math.max(-1,Math.round(origin)-1),Math.min(Math.min(1,Math.round(origin)+1),target));
                        selectPage(target,true);
                    }dragging=false;break;
                case MotionEvent.ACTION_CANCEL:dragging=false;selectPage(selectedPage,true);break;
            }
            return true;
        }
        private boolean blocksSwipe(View view,float rawX,float rawY){android.graphics.Rect bounds=new android.graphics.Rect();if(!view.getGlobalVisibleRect(bounds)||!bounds.contains((int)rawX,(int)rawY))return false;if(view instanceof EditText||view instanceof HorizontalScrollView||(view instanceof TextView&&((TextView)view).hasSelection()))return true;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(blocksSwipe(group.getChildAt(i),rawX,rawY))return true;}return false;}
    }
}
