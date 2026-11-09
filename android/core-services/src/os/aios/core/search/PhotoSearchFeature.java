package os.aios.core.search;

import android.content.Context;
import org.json.JSONObject;

/** Optional photo enrichment, distinct from the fixed recall coordinator. */
public interface PhotoSearchFeature {
    interface Page {
        JSONObject next(int limit, String query, JSONObject filters) throws Exception;
    }
    Page semantic(Context context, String query, String visualQuery) throws Exception;
    Page derived(Context context) throws Exception;
    void enrich(Context context, JSONObject response) throws Exception;
}
