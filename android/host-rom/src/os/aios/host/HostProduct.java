package os.aios.host;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only product selection; runtime availability is owned by Core, not this class. */
public final class HostProduct {
    private static final JSONObject PRODUCT;
    static {
        try { PRODUCT = new JSONObject(ProductManifest.JSON); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private HostProduct() {}
    public static int screenSlots() { return PRODUCT.optJSONObject("limits").optInt("virtual_sessions"); }
    public static int reservedScreenSlots() { return PRODUCT.optJSONObject("limits").optInt("scheduler_reserved"); }
    public static int headlessSlots() { return PRODUCT.optJSONObject("limits").optInt("headless_sessions"); }
    public static boolean includes(String id) {
        JSONArray modules = PRODUCT.optJSONArray("modules");
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.optJSONObject(i);
            if (module.optString("id").equals(id)) return module.optBoolean("packaged");
        }
        return false;
    }
    public static JSONArray providerModules(String provider) throws Exception {
        JSONArray result = new JSONArray(), modules = PRODUCT.getJSONArray("modules");
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.getJSONObject(i);
            if (module.getBoolean("packaged") && module.getString("provider").equals(provider))
                result.put(new JSONObject(module.toString()));
        }
        return result;
    }
    public static String moduleFor(String capability) {
        String owner = ""; int specificity = -1;
        JSONArray modules = PRODUCT.optJSONArray("modules");
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.optJSONObject(i);
            JSONArray selectors = module.optJSONArray("capabilities");
            for (int j = 0; j < selectors.length(); j++) {
                String selector = selectors.optString(j);
                boolean matches = selector.endsWith(".*") ? capability.startsWith(selector.substring(0, selector.length() - 1)) : capability.equals(selector);
                if (matches && selector.length() > specificity) { owner = module.optString("id"); specificity = selector.length(); }
            }
        }
        return owner;
    }
}
