package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;

/** Versioned shared inference router, restricted to platform-signed AIOS clients. */
public final class SharedInferenceProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        getContext().enforceCallingOrSelfPermission("os.aios.permission.CONTROL_SESSION","shared inference");
        Bundle out=new Bundle();try{
            JSONObject request=new JSONObject(extras==null?"{}":extras.getString("json","{}"));JSONObject result;
            switch(method){
                case "capabilities":result=new JSONObject().put("items",SharedModelCatalog.capabilities(getContext()));break;
                case "generate":{
                    String capability=request.getString("capability");if(!SharedModelCatalog.TEXT.contains(capability))throw new IllegalArgumentException("capability_not_supported");
                    result=SharedTextInference.generate(getContext(),capability,"inherit",request.getString("instruction"),request.getString("input"),request.optBoolean("json"),request.optInt("max_tokens",128),request.optJSONObject("schema"));break;
                }
                default:throw new IllegalArgumentException("operation_not_supported");
            }
            out.putString("json",result.put("version",1).toString());
        }catch(Exception e){out.putString("error",e.getClass().getSimpleName());}return out;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
