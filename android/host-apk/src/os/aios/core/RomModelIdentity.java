package os.aios.core;
/** APK model evidence is bound to the installed Android build, never invented ROM properties. */
final class RomModelIdentity {
    private static final os.aios.host.HostIdentity.Properties VALUE=new os.aios.host.HostIdentity.Properties(){
        public String systemIncremental(){return android.os.Build.VERSION.INCREMENTAL;}
        public String vendorFingerprint(){return android.os.Build.FINGERPRINT;}
    };
    static void install(){os.aios.host.HostIdentity.install(VALUE);}
}
