package os.aios.apk;
/** Private Binder protocol; APK and shell payload ship atomically in the same signed package. */
public final class ShellProtocol {
    private ShellProtocol() {}
    public static final String DESCRIPTOR="os.aios.app.shell.v1";
    public static final String AUTHORITY="os.aios.app.activation";
    public static final int CALL=android.os.IBinder.FIRST_CALL_TRANSACTION;
    public static final int FRAME=CALL+1;
    public static final int EVENT=CALL+2;
    public static final int LAUNCH=CALL+3;
    public static final int FILE=CALL+4;
    public static final int INSTALL=CALL+5;
    public static final int MAX_JSON=131072;
}
