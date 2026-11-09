package os.aios.agent;
import android.content.Context;import android.view.*;import android.widget.LinearLayout;
import com.android.internal.view.FloatingActionMode;
import com.android.internal.widget.floatingtoolbar.FloatingToolbar;
/** Overlay windows have no DecorView. Host the platform selection toolbar explicitly. */
final class RomSelectionController implements SelectionController {
 FloatingActionMode selectionMode;private ViewTreeObserver.OnPreDrawListener selectionPosition;
 final AssistantSelectionPanel owner;
 RomSelectionController(AssistantSelectionPanel owner){this.owner=owner;}
 public boolean selecting(){return selectionMode!=null;}
 public void finishSelection(){if(selectionMode!=null)selectionMode.finish();}
 @Override public ActionMode start(View child,ActionMode.Callback callback){
  finishSelection();
  ActionMode.Callback2 wrapped=new ActionMode.Callback2(){
   public boolean onCreateActionMode(ActionMode mode,Menu menu){return callback.onCreateActionMode(mode,menu);}
   public boolean onPrepareActionMode(ActionMode mode,Menu menu){return callback.onPrepareActionMode(mode,menu);}
   public boolean onActionItemClicked(ActionMode mode,MenuItem item){return callback.onActionItemClicked(mode,item);}
   public void onGetContentRect(ActionMode mode,View view,android.graphics.Rect rect){if(callback instanceof ActionMode.Callback2)((ActionMode.Callback2)callback).onGetContentRect(mode,view,rect);else super.onGetContentRect(mode,view,rect);}
   public void onDestroyActionMode(ActionMode mode){
    callback.onDestroyActionMode(mode);
    if(selectionPosition!=null&&owner.getViewTreeObserver().isAlive())owner.getViewTreeObserver().removeOnPreDrawListener(selectionPosition);
    selectionPosition=null;selectionMode=null;if(owner.selectionFinished!=null)owner.post(owner.selectionFinished);
   }
  };
  FloatingActionMode mode=new FloatingActionMode(owner.getContext(),wrapped,child,new FloatingToolbar(owner));
  if(!wrapped.onCreateActionMode(mode,mode.getMenu())){mode.finish();return null;}
  selectionMode=mode;mode.invalidate();
  selectionPosition=()->{if(selectionMode==mode)mode.updateViewLocationInWindow();return true;};
  owner.getViewTreeObserver().addOnPreDrawListener(selectionPosition);return mode;
 }
 @Override public void windowFocusChanged(boolean focused){if(selectionMode!=null)selectionMode.onWindowFocusChanged(focused);}
}
