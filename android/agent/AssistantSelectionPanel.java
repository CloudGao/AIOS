package os.aios.agent;
import android.content.Context;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/** Shared interaction lifecycle; selection menus are supplied by the host. */
class AssistantSelectionPanel extends LinearLayout {
    Runnable selectionFinished,interactionStarted;
    private final SelectionController selection;
    AssistantSelectionPanel(Context context){super(context);selection=DeviceHost.ui().selectionController(this);}
    boolean selecting(){return selection.selecting();}
    void finishSelection(){selection.finishSelection();}
    @Override public android.view.ActionMode startActionModeForChild(android.view.View child,android.view.ActionMode.Callback callback){return startActionModeForChild(child,callback,android.view.ActionMode.TYPE_FLOATING);}
    @Override public android.view.ActionMode startActionModeForChild(android.view.View child,android.view.ActionMode.Callback callback,int type){
        return type==android.view.ActionMode.TYPE_FLOATING?selection.start(child,callback):super.startActionModeForChild(child,callback,type);
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(selection!=null)selection.windowFocusChanged(focused);}
    @Override protected void onDetachedFromWindow(){finishSelection();super.onDetachedFromWindow();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&interactionStarted!=null)interactionStarted.run();
        boolean handled=super.dispatchTouchEvent(event);
        if((event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)
                &&!selecting()&&selectionFinished!=null)post(selectionFinished);
        return handled;
    }
}
