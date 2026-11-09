package com.android.launcher3.aios;

import android.content.*;
import android.database.ContentObserver;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import com.android.launcher3.*;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.*;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.views.ActivityContext;
import org.json.*;
import java.util.*;
import java.util.stream.Stream;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

/** Launcher projection only. MiniApp's registry remains the single lifecycle owner. */
public final class MiniApps {
 public static final String PACKAGE="os.aios.miniapp";
 public static final Uri DIRECTORY=Uri.parse("content://os.aios.miniapp.launcher");
 private static volatile List<Entry> entries=List.of();
 private static String fingerprint="";
 public static final class Entry {
  public final String id,name,generation,state;public final int version;public final long desktopRequest;
  Entry(JSONObject j)throws Exception{id=j.getString("id");name=DisplayText.clean(j.getString("name"));generation=j.getString("generation");state=j.getString("state");version=j.getInt("version");desktopRequest=j.optLong("desktop_request",0);}
  String key(){return id+"/"+generation;}
 }
 public static List<Entry> read(Context c)throws Exception {
  Bundle b=c.getContentResolver().call(DIRECTORY,"list",null,null);if(b==null||!b.containsKey("json"))throw new IllegalStateException("Missing mini app directory");
  JSONArray a=new JSONArray(b.getString("json"));ArrayList<Entry> result=new ArrayList<>();for(int i=0;i<a.length();i++)result.add(new Entry(a.getJSONObject(i)));return result;
 }
 public static void attach(AllAppsStore store,ActivityContext owner) {
  Context c=owner.asContext();
  boolean[] closed={false}, registered={false};
  Handler handler=new Handler(Looper.getMainLooper());
  Runnable refresh=()->MODEL_EXECUTOR.execute(()->{
   try {
    if(c.getPackageManager().resolveContentProvider(DIRECTORY.getAuthority(),0)==null){
     // Missing host is not an uninstall of every mini app: hide the projection, retain saved layout.
     MAIN_EXECUTOR.execute(()->{if(!closed[0]){entries=List.of();store.refreshMiniApps();}});
     return;
    }
    List<Entry> next=read(c);
    MAIN_EXECUTOR.execute(()->{
     if(closed[0])return;
     String nextFingerprint=next.stream().map(e->e.key()+":"+e.name+":"+e.version+":"+e.state)
       .sorted().collect(java.util.stream.Collectors.joining(";"));
     boolean changed=!fingerprint.equals(nextFingerprint);
     fingerprint=nextFingerprint;entries=List.copyOf(next);store.refreshMiniApps();
     if(c instanceof Launcher)requestDesktop((Launcher)c,next);
     if(changed)LauncherAppState.getInstance(c).getModel().enqueueModelUpdateTask((controller,model,apps)->{
      Map<String,Entry> current=new HashMap<>();for(Entry e:next)current.put(e.key(),e);
      controller.deleteAndBindComponentsRemoved(
       i->isMini(i)&&!current.containsKey(key(i.getIntent())),"Mini app uninstalled or reinstalled");
      List<ItemInfo> updated=model.updateAndCollectWorkspaceItemInfos(android.os.Process.myUserHandle(),item->{
       if(!isMini(item))return false;
       Entry entry=current.get(key(item.getIntent()));if(entry==null)return false;
       decorate(c,item,entry);item.intent=intent(entry,!"installed".equals(entry.state));
       if(item.id!=ItemInfo.NO_ID)controller.getModelWriter().updateItemInDatabase(item);
       return true;
      },null);
      controller.bindUpdatedWorkspaceItems(updated);
     });
    });
   } catch(Exception e) {android.util.Log.w("MiniApps","Directory refresh deferred",e);}
  });
  ContentObserver observer=new ContentObserver(handler) {
   public void onChange(boolean self){handler.removeCallbacks(refresh);handler.postDelayed(refresh,120);}
  };
  Runnable connect=()->{
   if(closed[0])return;
   if(!registered[0])try {
    c.getContentResolver().registerContentObserver(DIRECTORY,true,observer);registered[0]=true;
   } catch(Exception e) {android.util.Log.w("MiniApps","Directory observer unavailable",e);}
   // A missing/locked/updating host must never prevent the normal Launcher from starting.
   // Retry on resume as well as registry notifications, including the first post-unlock resume.
   refresh.run();
  };
  android.app.Application.ActivityLifecycleCallbacks lifecycle=new android.app.Application.ActivityLifecycleCallbacks(){
   public void onActivityResumed(android.app.Activity a){if(a==c)connect.run();}
   public void onActivityCreated(android.app.Activity a,Bundle b){}
   public void onActivityStarted(android.app.Activity a){}
   public void onActivityPaused(android.app.Activity a){}
   public void onActivityStopped(android.app.Activity a){}
   public void onActivitySaveInstanceState(android.app.Activity a,Bundle b){}
   public void onActivityDestroyed(android.app.Activity a){}
  };
  android.app.Application app=c instanceof android.app.Activity?((android.app.Activity)c).getApplication():null;
  if(app!=null)app.registerActivityLifecycleCallbacks(lifecycle);
  owner.closeOnDestroy(()->{
   closed[0]=true;handler.removeCallbacks(refresh);
   if(registered[0])c.getContentResolver().unregisterContentObserver(observer);
   if(app!=null)app.unregisterActivityLifecycleCallbacks(lifecycle);
  });
  connect.run();
 }
 // Shared registry requests are consumed only by the ROM native launcher.
 private static final Set<String> adding=new HashSet<>();
 static void requestDesktop(Launcher launcher,List<Entry> apps){
  if(launcher.isDestroyed())return;
  if(launcher.isWorkspaceLoading()){launcher.getWorkspace().postDelayed(()->requestDesktop(launcher,entries),500);return;}
  android.content.SharedPreferences prefs=launcher.getSharedPreferences("mini_desktop_requests",0);
  for(Entry entry:apps){
   String key=entry.key();long request=entry.desktopRequest;
   if(!"installed".equals(entry.state)||request<=0||prefs.getLong(key,0)>=request||adding.contains(key))continue;
   if(launcher.getWorkspace().mapOverItems((info,view)->isMini(info)&&key.equals(key(info.getIntent())))!=null){prefs.edit().putLong(key,request).apply();continue;}
   adding.add(key);
   com.android.launcher3.accessibility.LauncherAccessibilityDelegate delegate=(com.android.launcher3.accessibility.LauncherAccessibilityDelegate)launcher.getAccessibilityDelegate();
   boolean accepted=delegate.addToWorkspace(new MiniInfo(launcher,entry),false,success->{
    adding.remove(key);if(success)prefs.edit().putLong(key,request).apply();
   });
   if(!accepted)adding.remove(key);
  }
 }
 public static AppInfo[] merge(Context c,AppInfo[] nativeApps){
  ArrayList<AppInfo> result=new ArrayList<>();for(AppInfo a:nativeApps)if(!isMini(a)&&!(PACKAGE.equals(a.componentName.getPackageName())&&android.os.Process.myUserHandle().equals(a.user)&&a.componentName.getClassName().endsWith(".LibraryActivity")))result.add(a);
  for(Entry e:entries)result.add(new MiniInfo(c,e));result.sort(AppInfo.COMPONENT_KEY_COMPARATOR);return result.toArray(new AppInfo[0]);
 }
 static Intent intent(Entry e,boolean manage){return new Intent(Intent.ACTION_VIEW).setClassName(PACKAGE,PACKAGE+(manage?".ManageActivity":".HostActivity")).setData(Uri.parse("aios-miniapp://app/"+e.key())).putExtra("app_id",e.id).putExtra("generation",e.generation).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|(manage?0:Intent.FLAG_ACTIVITY_NEW_DOCUMENT));}
 public static boolean isIntent(Intent i){return i!=null&&i.getComponent()!=null&&PACKAGE.equals(i.getComponent().getPackageName())&&i.getData()!=null&&"aios-miniapp".equals(i.getData().getScheme())&&"app".equals(i.getData().getHost())&&i.getData().getPathSegments().size()==2;}
 public static boolean isLibrary(ItemInfo i){return i!=null&&android.os.Process.myUserHandle().equals(i.user)&&i.getTargetComponent()!=null&&PACKAGE.equals(i.getTargetComponent().getPackageName())&&i.getTargetComponent().getClassName().endsWith(".LibraryActivity");}
 public static boolean isMini(ItemInfo i){return i!=null&&android.os.Process.myUserHandle().equals(i.user)&&isIntent(i.getIntent());}
 static String key(Intent i){return String.join("/",i.getData().getPathSegments());}
 public static Entry find(Context c,Intent i)throws Exception{for(Entry e:read(c))if(e.key().equals(key(i)))return e;return null;}
 public static void decorate(Context c,ItemInfoWithIcon item,Entry e){item.title=e.name;item.contentDescription=e.name+", "+c.getString(R.string.aios_mini_app);item.bitmap=BitmapInfo.fromBitmap(icon(c,e));item.runtimeStatusFlags|=ItemInfoWithIcon.FLAG_SYSTEM_YES;}
 public static Bitmap icon(Context c,Entry e){
  Bitmap bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint p=new Paint(3);
  int[] colors={0xff456d88,0xff5c6e49,0xff8b586b,0xff756195,0xff367c79,0xff986743};p.setColor(colors[Math.floorMod(e.id.hashCode(),colors.length)]);canvas.drawRoundRect(12,8,184,180,42,42,p);
  p.setColor(Color.WHITE);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(78);p.setTextAlign(Paint.Align.CENTER);String initial=e.name.substring(0,e.name.offsetByCodePoints(0,1));canvas.drawText(initial,98,119,p);
  boolean dark=(c.getResources().getConfiguration().uiMode&48)==32;p.setColor(dark?0xff26352f:0xfff7faf7);canvas.drawRoundRect(0,132,60,192,18,18,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(dark?0xffb7cebf:0xff456754);canvas.drawRoundRect(2,134,58,190,16,16,p);p.setStyle(Paint.Style.FILL);for(int x:new int[]{20,40})for(int y:new int[]{152,172})canvas.drawCircle(x,y,4,p);return bitmap;
 }
 public static final class MiniInfo extends AppInfo {
  final Entry entry;
  MiniInfo(Context c,Entry e){entry=e;user=android.os.Process.myUserHandle();componentName=new ComponentName(PACKAGE,PACKAGE+".entry."+e.id+"."+e.generation);container=LauncherSettings.Favorites.CONTAINER_ALL_APPS;intent=intent(e,!"installed".equals(e.state));decorate(c,this,e);sectionName=e.name.substring(0,e.name.offsetByCodePoints(0,1)).toUpperCase(Locale.getDefault());}
  @Override public WorkspaceItemInfo makeWorkspaceItem(Context c){WorkspaceItemInfo item=new WorkspaceItemInfo(this);decorate(c,item,entry);return item;}
 }
 public static Stream<SystemShortcut.Factory> shortcuts(ItemInfo item){
  return item.isInAllApps()?Stream.of(INFO,UNINSTALL,SystemShortcut.ADD_TO_HOME_SCREEN):Stream.of(INFO,UNINSTALL,SystemShortcut.REMOVE);
 }
 private static final SystemShortcut.Factory<ActivityContext> INFO=(c,i,v)->new ManageShortcut(c,i,v,false);
 private static final SystemShortcut.Factory<ActivityContext> UNINSTALL=(c,i,v)->new ManageShortcut(c,i,v,true);
 private static final class ManageShortcut extends SystemShortcut<ActivityContext>{
  final boolean uninstall;
  ManageShortcut(ActivityContext c,ItemInfo i,View v,boolean u){super(u?R.drawable.ic_uninstall_no_shadow:R.drawable.info_24px,u?R.string.uninstall_drop_target_label:R.string.app_info_drop_target_label,c,i,v,false);uninstall=u;}
  public void onClick(View v){AbstractFloatingView.closeAllOpenViews(mTarget);Intent open=new Intent(mItemInfo.getIntent()).setClassName(PACKAGE,PACKAGE+".ManageActivity").putExtra("uninstall",uninstall).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);try{v.getContext().startActivity(open);}catch(Exception e){android.widget.Toast.makeText(v.getContext(),DisplayText.clean(R.string.activity_not_available),0).show();}}
 }
}
