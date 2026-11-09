package os.aios.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Public ASSIST entry. External callers cannot forward modes, gestures or session IDs. */
public final class ApkAssistantEntryActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this,AssistantActivity.class));
        finish();
    }
}
