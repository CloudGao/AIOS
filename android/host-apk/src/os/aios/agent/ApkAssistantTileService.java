package os.aios.agent;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;

/** Opens the existing assistant after the user's explicit tile click. */
public final class ApkAssistantTileService extends TileService {
    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::openAssistant);
        else openAssistant();
    }

    private void openAssistant() {
        Intent intent = new Intent(this, ApkAssistantEntryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}
