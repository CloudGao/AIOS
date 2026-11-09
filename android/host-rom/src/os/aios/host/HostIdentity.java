package os.aios.host;

/** Public contract for deployment-specific model evidence; installed by trusted process wiring. */
public final class HostIdentity {
    public interface Properties {
        String systemIncremental();
        String vendorFingerprint();
    }
    private static Properties properties;
    private HostIdentity(){}
    public static synchronized void install(Properties value){
        if(value==null)throw new IllegalArgumentException("host_identity_required");
        if(properties!=null&&properties!=value)throw new IllegalStateException("host_identity_already_bound");
        properties=value;
    }
    private static synchronized Properties get(){
        if(properties==null)throw new IllegalStateException("host_identity_not_initialized");
        return properties;
    }
    public static String systemIncremental(){return get().systemIncremental();}
    public static String vendorFingerprint(){return get().vendorFingerprint();}
}
