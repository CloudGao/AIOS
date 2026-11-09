package os.aios.core;

import os.aios.host.HostIdentity;

/** Private ROM property reads never enter shared model validation code. */
final class RomModelIdentity {
    private static final HostIdentity.Properties PROPERTIES=new HostIdentity.Properties(){
        public String systemIncremental(){return android.os.SystemProperties.get("ro.system.build.version.incremental");}
        public String vendorFingerprint(){return android.os.SystemProperties.get("ro.vendor.build.fingerprint");}
    };
    static void install(){HostIdentity.install(PROPERTIES);}
}
