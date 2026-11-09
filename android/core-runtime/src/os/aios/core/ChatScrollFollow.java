package os.aios.core;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
/** A user gesture owns the viewport; layout changes cannot silently re-enable follow. */
public final class ChatScrollFollow {
 private boolean touching,following=true,userScroll;private float downY;private ScrollView attached;
 public void reset(){touching=false;following=true;userScroll=false;}
 public boolean following(){return following&&!touching;}
 public boolean touching(){return touching;}
 /** Observe before child dispatch: selectable message text may consume DOWN/MOVE. */
 public static ScrollView create(android.content.Context context){return new TrackingScrollView(context);}
 private static final class TrackingScrollView extends ScrollView {
  private ChatScrollFollow observer;
  TrackingScrollView(android.content.Context context){super(context);}
  @Override public boolean dispatchTouchEvent(MotionEvent event){if(observer!=null)observer.observe(this,event);return super.dispatchTouchEvent(event);}
 }
 private void observe(ScrollView view,MotionEvent e){
  switch(e.getActionMasked()){
   case MotionEvent.ACTION_DOWN:touching=true;userScroll=true;downY=e.getY();break;
   case MotionEvent.ACTION_MOVE:if(Math.abs(e.getY()-downY)>android.view.ViewConfiguration.get(view.getContext()).getScaledTouchSlop())following=false;break;
   case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:touching=false;view.postDelayed(()->{if(attached==view&&!touching&&atBottom(view))following=true;},180);break;
  }
 }
 public void attach(ScrollView view){attached=view;userScroll=false;if(view instanceof TrackingScrollView)((TrackingScrollView)view).observer=this;else view.setOnTouchListener((v,e)->{observe(view,e);return false;});
 view.setOnScrollChangeListener((v,x,y,ox,oy)->{if(userScroll&&!touching&&y>oy&&atBottom(view))following=true;});
 view.getViewTreeObserver().addOnGlobalLayoutListener(()->{if(attached==view&&following())bottom(view);});}
 private boolean atBottom(ScrollView view){return view.getChildCount()==0||view.getChildAt(0).getHeight()-view.getScrollY()-view.getHeight()<=AiosUi.dp(view.getContext(),48);}
 private void bottom(ScrollView view){if(view.getChildCount()>0)view.scrollTo(0,Math.max(0,view.getChildAt(0).getHeight()-view.getHeight()+view.getPaddingBottom()));}
 public void changed(ScrollView view){if(following())view.post(()->{if(attached==view&&following()&&view.isAttachedToWindow())bottom(view);});}
}
