package os.aios.core;
import os.aios.runtime.R;
import android.content.Context;import android.view.View;import android.widget.*;import java.util.*;
public final class ExecutionDetails {
 private static final Map<String,boolean[]> states=new LinkedHashMap<>();
 public static LinearLayout create(Context c,LinearLayout parent,String key,boolean active){return create(c,parent,key,active,active);}
 public static LinearLayout create(Context c,LinearLayout parent,String key,boolean running,boolean pending){return create(c,parent,key,running,pending,null);}
 public static LinearLayout create(Context c,LinearLayout parent,String key,boolean running,boolean pending,Runnable onExpand){
  boolean active=running||pending;
  boolean[] state=states.get(key);if(state==null){state=new boolean[]{active,active};states.put(key,state);if(states.size()>256)states.remove(states.keySet().iterator().next());}
  if(state[1]&&!active)state[0]=false;if(!state[1]&&active)state[0]=true;state[1]=active;
  final boolean[] saved=state;
  LinearLayout group=AiosUi.column(c),body=AiosUi.column(c);TextView toggle=AiosUi.textButton(c,c.getString(R.string.execution_details),null);
  toggle.setContentDescription(c.getString(R.string.execution_details));
  body.setVisibility(state[0]?View.VISIBLE:View.GONE);
  AiosUi.clickable(toggle,()->{saved[0]=!saved[0];body.setVisibility(saved[0]?View.VISIBLE:View.GONE);toggle.setText(c.getString(saved[0]?R.string.execution_details_hide:R.string.execution_details));if(saved[0]&&onExpand!=null)onExpand.run();});
  toggle.setText(c.getString(state[0]?R.string.execution_details_hide:R.string.execution_details));
  group.addView(toggle);group.addView(body);parent.addView(group);if(saved[0]&&onExpand!=null)body.post(onExpand);return body;
 }
}
