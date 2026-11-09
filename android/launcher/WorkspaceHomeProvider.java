package os.aios.agent;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import os.aios.workspace.WorkspaceStore;

/** Read-only bounded home summary, accessible only by the preinstalled system launcher. */
public final class WorkspaceHomeProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private void enforceLauncher(){
        try{
            ApplicationInfo app=getContext().getPackageManager().getApplicationInfo("com.android.launcher3",0);
            if(app.uid==Binder.getCallingUid()&&(app.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return;
        }catch(Exception ignored){}
        throw new SecurityException("Only the system launcher can read the home summary");
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        enforceLauncher();
        if("preview".equals(method)){
            try{return UiScreenPreview.read(AgentService.instance,arg);}catch(Exception e){Bundle unavailable=new Bundle();unavailable.putString("frame_state","unavailable");return unavailable;}
        }
        if(!"snapshot".equals(method))throw new IllegalArgumentException("Unsupported method");
        try(WorkspaceStore store=new WorkspaceStore(getContext())){
            SQLiteDatabase db=store.getReadableDatabase();
            JSONObject result=new JSONObject();
            db.beginTransactionNonExclusive();
            try{
                try(Cursor counts=db.rawQuery("SELECT status,count(*) FROM tasks GROUP BY status",null)){
                    while(counts.moveToNext())result.put(counts.getString(0),counts.getInt(1));
                }
                result.put("tasks",rows(db,"SELECT id,title,status FROM tasks WHERE status IN ('doing','todo') ORDER BY CASE status WHEN 'doing' THEN 0 ELSE 1 END,updated_at DESC,id LIMIT 3"));
                result.put("sessions",rows(db,"SELECT id,title,source FROM sessions ORDER BY updated_at DESC,id LIMIT 3"));
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            Bundle out=new Bundle();
            AgentService agent=AgentService.instance;
            JSONArray listed=agent==null?new JSONArray():agent.ui.call(new JSONObject().put("op","list")).optJSONArray("sessions");
            JSONArray screens=new JSONArray();
            if(listed!=null)for(int i=0;i<listed.length();i++)if("background".equals(listed.getJSONObject(i).optString("mode")))screens.put(listed.getJSONObject(i));
            result.put("ui_sessions",screens);
            for(int i=0;i<screens.length();i++){
                String id=screens.getJSONObject(i).getString("session_id");
                android.content.Intent view=new android.content.Intent(getContext(),UiViewerActivity.class)
                    .setData(Uri.parse("aios://screen/"+id)).putExtra("session_id",id).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                out.putParcelable("screen:"+id,android.app.PendingIntent.getActivity(getContext(),0,view,android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT));
                android.content.Intent takeover=new android.content.Intent(view).setData(Uri.parse("aios://screen/"+id+"/takeover")).putExtra("screen_action","takeover");
                out.putParcelable("takeover:"+id,android.app.PendingIntent.getActivity(getContext(),id.hashCode(),takeover,android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT));
                android.content.Intent close=new android.content.Intent(getContext(),AgentService.class).setData(Uri.parse("aios://screen/"+id+"/close")).putExtra("ui_card_action","close").putExtra("session_id",id);
                out.putParcelable("close:"+id,android.app.PendingIntent.getForegroundService(getContext(),~id.hashCode(),close,android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT));
            }
            out.putParcelable("screens",android.app.PendingIntent.getActivity(getContext(),0,new android.content.Intent(getContext(),UiSessionsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),android.app.PendingIntent.FLAG_IMMUTABLE));
            out.putString("json",result.toString());return out;
        }catch(Exception e){throw new IllegalStateException("Workspace summary unavailable",e);}
    }
    private JSONArray rows(SQLiteDatabase db,String sql)throws Exception{
        JSONArray result=new JSONArray();
        try(Cursor c=db.rawQuery(sql,null)){
            while(c.moveToNext()){
                JSONObject row=new JSONObject();
                for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.getString(i));
                result.put(row);
            }
        }
        return result;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
