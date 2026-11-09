package os.aios.core.workspace;

/** Runs only against the explicitly named acceptance database. */
public class CoreDreamAcceptance extends android.app.Instrumentation {
    @Override public void onCreate(android.os.Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        android.os.Bundle result = new android.os.Bundle();
        try {
            int count = DreamDeviceCases.run(getTargetContext());
            result.putString("stream", "PASS Dream Android SQLite: " + count + " assertions\n");
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("stream", android.util.Log.getStackTraceString(failure));
            finish(1, result);
        }
    }
}
