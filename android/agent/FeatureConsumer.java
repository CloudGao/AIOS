package os.aios.agent;

/** Optional consumer lifetime, separate from business execution and capability state. */
interface FeatureConsumer extends AutoCloseable {
    void start();
    @Override void close();
}
