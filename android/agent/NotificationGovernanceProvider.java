package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;

/** Signature protected presentation state. No operation publishes automation events. */
public final class NotificationGovernanceProvider extends ContentProvider {
    static final Uri URI=Uri.parse("content://os.aios.agent.notifications");
    @Override public boolean onCreate(){return true;}
    static void changed(Context context){context.getContentResolver().notifyChange(URI,null);}
    @Override public Bundle call(String method,String arg,Bundle extras){
        getContext().enforceCallingOrSelfPermission("os.aios.permission.CONTROL_SESSION","notification governance");
        try(NotificationLedger store=new NotificationLedger(getContext())){
            JSONObject result;
            switch(method){
                case "snapshot":result=store.presentation();break;
                case "pin":store.pin(require(extras,"package"),extras.getBoolean("pinned"));changed(getContext());result=new JSONObject();break;
                case "clear":store.display(require(extras,"identity"),"clear");changed(getContext());NotificationPolicyWorker.enqueue(getContext(),"summary",store.notice(require(extras,"identity")).getString("package"));result=new JSONObject();break;
                case "restore":requireActive(require(extras,"identity"));store.display(require(extras,"identity"),"restore");changed(getContext());result=new JSONObject();break;
                case "block":{
                    String identity=require(extras,"identity");store.block(identity);changed(getContext());
                    NotificationPolicyWorker.enqueue(getContext(),"block",identity);result=new JSONObject().put("state","queued");break;
                }
                case "undo":store.undo(require(extras,"identity"));if(!isActive(require(extras,"identity")))store.display(require(extras,"identity"),"archived");changed(getContext());result=new JSONObject();break;
                case "block_app":{
                    String pkg=require(extras,"package");JSONObject rules=store.rules();JSONArray next=rules.getJSONArray("items");
                    JSONArray out=new JSONArray().put(new JSONObject().put("id","app:"+pkg).put("action","filter").put("when",new JSONObject().put("field","package").put("op","equals").put("value",pkg)));
                    for(int i=0;i<next.length();i++)if(!next.getJSONObject(i).optString("id").equals("app:"+pkg))out.put(next.get(i));
                    store.saveRules(out,rules.getLong("revision"));store.blockApp(pkg);changed(getContext());result=new JSONObject();break;
                }
                default:throw new IllegalArgumentException("unsupported_notification_operation");
            }
            if(java.util.Set.of("restore","undo","block").contains(method))NotificationPolicyWorker.enqueue(getContext(),"summary",store.notice(require(extras,"identity")).getString("package"));
            Bundle response=new Bundle();response.putString("json",result.toString());return response;
        }catch(Exception error){Bundle response=new Bundle();response.putString("error",error.getClass().getSimpleName());if("notification_expired".equals(error.getMessage()))response.putString("error_code","notification_expired");return response;}
    }
    private static void requireActive(String identity){
        if(!isActive(identity))throw new IllegalStateException("notification_expired");
    }
    private static boolean isActive(String identity){
        try{android.service.notification.StatusBarNotification[] active=NotificationSources.active();if(active!=null)for(android.service.notification.StatusBarNotification notification:active)if(identity.equals(notification.getNotification().extras.getString("android.aios.identity","")))return true;}catch(Exception ignored){}
        return false;
    }
    private static String require(Bundle extras,String key){String value=extras==null?null:extras.getString(key);if(value==null||value.isEmpty()||value.length()>1024)throw new IllegalArgumentException("invalid_argument");return value;}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return "application/json";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
