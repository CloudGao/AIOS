package os.aios.codex;

import org.json.JSONObject;

/** Optional extension contract; native Harness operation does not depend on an extension. */
interface RuntimeExtension {
    JSONObject call(String action,JSONObject parameters)throws Exception;
    interface Mcp extends RuntimeExtension {
        void reconcile(JSONObject packages)throws Exception;
    }
}
