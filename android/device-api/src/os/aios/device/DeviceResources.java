package os.aios.device;

/** Host-owned foreground service lifetime, not a second task scheduler. */
public interface DeviceResources {
    Lease acquireForeground(int serviceType);
    interface Lease extends AutoCloseable { @Override void close(); }
}
