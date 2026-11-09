package os.aios.agent;
import android.content.Context;import android.view.*;import android.widget.*;
/** Public-SDK selection menu for an overlay without an Activity DecorView. */
final class ApkSelectionController implements SelectionController {
 private SelectionMode selectionMode;
 final AssistantSelectionPanel owner;
 ApkSelectionController(AssistantSelectionPanel owner){this.owner=owner;}
 public boolean selecting(){return selectionMode!=null;}
 public void finishSelection(){if(selectionMode!=null)selectionMode.finish();}
 @Override public ActionMode start(View child,ActionMode.Callback callback){
  finishSelection();SelectionMode mode=new SelectionMode(child,callback);selectionMode=mode;
  if(!callback.onCreateActionMode(mode,mode.getMenu())){mode.finish();return null;}
  callback.onPrepareActionMode(mode,mode.getMenu());owner.post(()->{if(selectionMode==mode&&owner.isAttachedToWindow())mode.show();});return mode;
 }
 private final class SelectionMode extends ActionMode {
  final PopupMenu popup;final Callback callback;boolean finished;CharSequence title="",subtitle="";View custom;
  SelectionMode(View anchor,Callback callback){this.callback=callback;popup=new PopupMenu(owner.getContext(),anchor);
   popup.setOnMenuItemClickListener(item->callback.onActionItemClicked(this,item));popup.setOnDismissListener(menu->finish());}
  void show(){if(!finished)try{popup.show();}catch(android.view.WindowManager.BadTokenException lost){finish();}}
  public void setTitle(CharSequence value){title=value;}public void setTitle(int id){title=owner.getContext().getText(id);}
  public void setSubtitle(CharSequence value){subtitle=value;}public void setSubtitle(int id){subtitle=owner.getContext().getText(id);}
  public void setCustomView(View view){custom=view;}public View getCustomView(){return custom;}
  public CharSequence getTitle(){return title;}public CharSequence getSubtitle(){return subtitle;}
  public Menu getMenu(){return popup.getMenu();}public MenuInflater getMenuInflater(){return popup.getMenuInflater();}
  public int getType(){return TYPE_FLOATING;}
  public void invalidate(){if(!finished)callback.onPrepareActionMode(this,getMenu());}
  public void finish(){if(finished)return;finished=true;popup.dismiss();callback.onDestroyActionMode(this);if(selectionMode==this)selectionMode=null;if(owner.selectionFinished!=null)owner.post(owner.selectionFinished);}
 }
 public void windowFocusChanged(boolean focused){}
}
