package os.aios.core;
import android.view.*;import android.widget.*;
/** Keep selected transcript views alive while the native copy toolbar owns them. */
public final class TextSelection {
 private TextSelection(){}
 public static boolean active(View v){
  if(v instanceof TextView && !(v instanceof EditText) && ((TextView)v).hasSelection() && v.hasFocus())return true;
  if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)if(active(group.getChildAt(i)))return true;}
  return false;
 }
}
