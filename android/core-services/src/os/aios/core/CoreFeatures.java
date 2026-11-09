package os.aios.core;

import android.content.Context;
import org.json.JSONObject;

/** Optional feature contracts. Absence is explicit; no substitute business implementation. */
public final class CoreFeatures {
    private CoreFeatures() {}
    public interface Documents {
        JSONObject call(String action, JSONObject parameters, String owner) throws Exception;
        JSONObject readSource(String source, String name, JSONObject parameters, String owner) throws Exception;
        JSONObject findSource(String source, String name, JSONObject parameters, String owner) throws Exception;
        void revoke();
    }
    public interface Inference {
        JSONObject status() throws Exception;
        JSONObject generate(JSONObject parameters) throws Exception;
    }
    public interface Factory<T> { T create(Context context); }
    private static Factory<Documents> documentFactory;
    private static Factory<Inference> inferenceFactory;
    private static Documents documents;
    private static Inference inference;
    private static os.aios.core.search.PhotoSearchFeature photoSearch;
    static synchronized void registerPhotoSearch(os.aios.core.search.PhotoSearchFeature feature) {
        if (photoSearch != null) throw new IllegalStateException("duplicate_photo_search_provider");
        photoSearch = feature;
    }
    public static synchronized os.aios.core.search.PhotoSearchFeature photoSearch() {
        if (photoSearch == null) throw new UnsupportedOperationException("photo_index_not_available");
        return photoSearch;
    }
    public static synchronized void enrichPhoto(Context context, JSONObject response) throws Exception {
        if (photoSearch != null) photoSearch.enrich(context, response);
    }
    static synchronized void registerDocuments(Factory<Documents> factory) {
        if (documentFactory != null) throw new IllegalStateException("duplicate_documents_provider");
        documentFactory = factory;
    }
    static synchronized void registerInference(Factory<Inference> factory) {
        if (inferenceFactory != null) throw new IllegalStateException("duplicate_inference_provider");
        inferenceFactory = factory;
    }
    public static synchronized Documents documents(Context context) throws Exception {
        if (documentFactory == null) throw new UnsupportedOperationException("documents_not_available");
        CoreCapabilities.require(context, "documents");
        if (documents == null) documents = documentFactory.create(context);
        return documents;
    }
    static synchronized Inference inference(Context context) {
        if (inferenceFactory == null) throw new UnsupportedOperationException("local_inference_not_available");
        if (inference == null) inference = inferenceFactory.create(context);
        return inference;
    }
    static synchronized void revoke() { if (documents != null) documents.revoke(); }
}
