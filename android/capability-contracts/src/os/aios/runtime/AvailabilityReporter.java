package os.aios.runtime;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;

/** Process health reporting, not a task scheduler. No writes to user data or device probes with side effects. */
public final class AvailabilityReporter implements AutoCloseable {
    public interface Probe { JSONArray read() throws Exception; }
    private final CapabilityClient client;
    private final String provider, instance = UUID.randomUUID().toString();
    private final Probe probe;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private boolean started, closed;
    public AvailabilityReporter(CapabilityClient client, String provider, Probe probe) {
        this.client = client; this.provider = provider; this.probe = probe;
    }
    public synchronized void start() {
        if (started || closed) return;
        started = true;
        worker.scheduleWithFixedDelay(this::publish, 0, 30, TimeUnit.SECONDS);
    }
    public synchronized void refresh() { if (!closed) worker.execute(this::publish); }
    public synchronized void publishNow() throws Exception { if (!closed) client.report(provider, instance, probe.read()); }
    private void publish() {
        try { publishNow(); }
        catch (Exception error) { android.util.Log.w("AIOSCapabilities", "Availability report deferred; Core will expire stale readiness", error); }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        worker.execute(() -> { try { client.report(provider, instance, new JSONArray()); } catch (Exception ignored) { /* TTL is the crash fallback. */ } });
        worker.shutdown();
    }
}
