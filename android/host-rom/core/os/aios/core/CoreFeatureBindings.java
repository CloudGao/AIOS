package os.aios.core;

import android.content.Context;
import os.aios.host.HostProduct;

/** Explicit build-selected factories. Staging omits references to excluded feature implementations. */
final class CoreFeatureBindings {
    private static boolean registered;
    static synchronized void register(Context context) {
        if (registered) return;
        os.aios.core.automation.AutomationFeatures.register();
        if (HostProduct.includes("documents")) CoreFeatures.registerDocuments(os.aios.core.documents.DocumentCoordinator::get);
        if (HostProduct.includes("local-inference")) CoreFeatures.registerInference(os.aios.core.inference.LocalTextInference::get);
        if (HostProduct.includes("photo-index")) {
            os.aios.core.embedding.BundledModels.initialize(context);
            CoreFeatures.registerPhotoSearch(new os.aios.core.search.IndexedPhotoSearch());
        }
        registered = true;
    }
    static org.json.JSONObject photoModelStatus(Context context) throws Exception {
        return HostProduct.includes("photo-index") ? new os.aios.core.embedding.ModelRegistry(context).status()
            : new org.json.JSONObject().put("state","not_packaged").put("reason_code","photo_index_not_packaged");
    }
}
