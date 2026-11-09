package os.aios.miniapp;
import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import os.aios.core.AiosUi;

/** Shared app drawer, safe to embed outside the mini-app process. */
public final class MiniAppGrid extends ScrollView {
 private final LinearLayout body;private JSONArray entries=new JSONArray();private String fingerprint="";private int columns,epoch;private boolean registered;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){reload();}};
 public MiniAppGrid(Context c){super(c);setFillViewport(true);body=AiosUi.column(c);body.setPadding(AiosUi.dp(c,12),0,AiosUi.dp(c,12),AiosUi.dp(c,24));addView(body);}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();try{getContext().getContentResolver().registerContentObserver(LauncherProvider.URI,true,observer);registered=true;}catch(Exception ignored){}reload();}
 @Override protected void onDetachedFromWindow(){epoch++;if(registered){getContext().getContentResolver().unregisterContentObserver(observer);registered=false;}super.onDetachedFromWindow();}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);int n=Math.max(2,Math.min(8,w/AiosUi.dp(getContext(),96)));if(n!=columns||w!=oldw){columns=n;render();}}
 public void reload(){final int request=++epoch;AsyncTask.THREAD_POOL_EXECUTOR.execute(()->{
  JSONArray result=null;try{Bundle b=getContext().getContentResolver().call(LauncherProvider.URI,"list",null,null);result=new JSONArray(b.getString("json"));}catch(Exception ignored){}
  final JSONArray loaded=result;handler.post(()->{if(!isAttachedToWindow()||request!=epoch)return;if(loaded==null){fingerprint="";body.removeAllViews();body.addView(AiosUi.secondary(getContext(),getContext().getString(R.string.mini_grid_unavailable)));return;}String key=loaded.toString();if(!key.equals(fingerprint)){fingerprint=key;entries=loaded;render();}});
 });}
 private Intent intent(JSONObject app,Class<?> target)throws JSONException{return new Intent(getContext(),target).setAction(Intent.ACTION_VIEW).setData(new Uri.Builder().scheme("aios-miniapp").authority("app").appendPath(app.getString("id")).appendPath(app.getString("generation")).build());}
 private void open(JSONObject app,boolean manage){try{getContext().startActivity(intent(app,manage?ManageActivity.class:HostActivity.class));}catch(Exception e){Toast.makeText(getContext(),R.string.mini_grid_unavailable,Toast.LENGTH_LONG).show();}}
 private void menu(View anchor,JSONObject app){PopupMenu menu=new PopupMenu(getContext(),anchor);menu.getMenu().add(0,1,0,R.string.mini_desktop_add);menu.getMenu().add(0,2,1,R.string.mini_grid_manage);menu.setOnMenuItemClickListener(item->{if(item.getItemId()==2)open(app,true);else pin(app);return true;});menu.show();}
 private void pin(JSONObject app){AsyncTask.THREAD_POOL_EXECUTOR.execute(()->{int message=R.string.mini_desktop_failed;try{
  Bundle args=new Bundle();args.putString("json",new JSONObject().put("id",app.getString("id")).put("generation",app.getString("generation")).toString());
  Bundle b=getContext().getContentResolver().call(Uri.parse("content://os.aios.miniapp.manage"),"desktop",null,args);
  String result=new JSONObject(b.getString("json")).optString("data");message=switch(result){case "requested"->R.string.mini_desktop_requested;case "pinned"->R.string.mini_desktop_pinned;case "unsupported"->R.string.mini_desktop_unsupported;default->R.string.mini_desktop_failed;};
 }catch(Exception ignored){}final int text=message;handler.post(()->{if(isAttachedToWindow())Toast.makeText(getContext(),text,Toast.LENGTH_LONG).show();});});}
 private void render(){
  int y=getScrollY();body.removeAllViews();body.addView(AiosUi.heading(getContext(),getContext().getString(R.string.mini_grid_title)));
  TextView hint=AiosUi.meta(getContext(),getContext().getString(R.string.mini_grid_hint));hint.setPadding(0,AiosUi.dp(getContext(),8),0,AiosUi.dp(getContext(),16));body.addView(hint);
  GridLayout grid=new GridLayout(getContext());int count=Math.max(2,columns);grid.setColumnCount(count);body.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  int visible=0;for(int i=0;i<entries.length();i++){JSONObject app=entries.optJSONObject(i);if(app==null)continue;
   String name=app.optString("name");boolean installed="installed".equals(app.optString("state"));
   LinearLayout cell=AiosUi.column(getContext());cell.setGravity(Gravity.CENTER_HORIZONTAL);cell.setPadding(AiosUi.dp(getContext(),6),AiosUi.dp(getContext(),12),AiosUi.dp(getContext(),6),AiosUi.dp(getContext(),12));
   ImageView icon=new ImageView(getContext());icon.setImageIcon(MiniAppIcon.create(app.optString("id"),name));cell.addView(icon,new LinearLayout.LayoutParams(AiosUi.dp(getContext(),56),AiosUi.dp(getContext(),56)));
   TextView label=AiosUi.body(getContext(),name);label.setGravity(Gravity.CENTER);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(0,AiosUi.dp(getContext(),6),0,0);cell.addView(label,new LinearLayout.LayoutParams(-1,-2));
   GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(visible/count),GridLayout.spec(visible%count));lp.width=Math.max(0,(getWidth()-getPaddingLeft()-getPaddingRight()-body.getPaddingLeft()-body.getPaddingRight())/count);lp.height=-2;grid.addView(cell,lp);visible++;
   cell.setContentDescription(name);AiosUi.clickable(cell,()->open(app,!installed));cell.setOnLongClickListener(v->{menu(v,app);return true;});
  }
  if(visible==0)body.addView(AiosUi.empty(getContext(),"apps",getContext().getString(R.string.mini_grid_empty),getContext().getString(R.string.mini_grid_empty_hint)));
  post(()->scrollTo(0,y));
 }
}
