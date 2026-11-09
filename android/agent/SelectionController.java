package os.aios.agent;
import android.view.ActionMode;
import android.view.View;

/** Host menu lifecycle attached to the shared overlay view. */
interface SelectionController {
 boolean selecting();
 void finishSelection();
 ActionMode start(View child,ActionMode.Callback callback);
 void windowFocusChanged(boolean focused);
}
