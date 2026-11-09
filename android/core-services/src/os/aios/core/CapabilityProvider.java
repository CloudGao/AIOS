package os.aios.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;

/** Narrow endpoint in the existing Core process. Does not grant Runtime access to Core business writes. */
public final class CapabilityProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String domain, String action, Bundle extras) {
        Bundle result = new Bundle();
        result.putInt("protocol_version", 1);
        try {
            if (!"capabilities".equals(domain) || extras == null || extras.getInt("protocol_version") != 1)
                throw new IllegalArgumentException("capability_protocol_mismatch");
            String arguments = extras.getString("arguments", "{}");
            if (arguments.length() > 65536) throw new IllegalArgumentException("capability_report_too_large");
            result.putString("result", CoreCapabilities.call(getContext(), action, new JSONObject(arguments)).toString());
        } catch (Exception error) { result.putString("error", error.getClass().getSimpleName() + ":" + error.getMessage()); }
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
