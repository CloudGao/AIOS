package os.aios.workspace;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persist document grants; copy only when requested. Never delete a referenced original. */
public final class MediaReferences {
    public static JSONObject importUri(Context context,Uri uri,int flags,boolean copy) throws Exception {
        if(!"content".equals(uri.getScheme()))throw new IllegalArgumentException("请选择系统文档或媒体资源");
        String name="媒体附件",mime=context.getContentResolver().getType(uri);
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null&&c.moveToFirst()&&!c.isNull(0))name=c.getString(0);
        }
        if(mime==null)mime="application/octet-stream";
        Uri stored=uri;
        if(copy) {
            File directory=new File(context.getFilesDir(),"workspace-media");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create media directory");
            File destination=new File(directory,UUID.randomUUID().toString());
            boolean complete=false;
            try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(destination)) {
                if(in==null)throw new java.io.IOException("资源不可读取");
                byte[] buffer=new byte[65536];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);out.getFD().sync();complete=true;
            } finally {if(!complete)destination.delete();}
            stored=Uri.fromFile(destination);
        } else {
            if((flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)throw new java.io.IOException("此来源不支持持久引用，请选择保存副本");
            context.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("资源不可读取");}
        }
        return new JSONObject().put("uri",stored.toString()).put("name",name).put("mime",mime).put("storage_mode",copy?"copy":"reference");
    }
    public static void cleanup(Context context,WorkspaceStore store) throws Exception {
        Set<String> used=new HashSet<>();
        org.json.JSONArray items=store.call("attachment.used_uris",new JSONObject()).getJSONArray("items");for(int i=0;i<items.length();i++)used.add(items.getJSONObject(i).getString("uri"));
        File directory=new File(context.getFilesDir(),"workspace-media");File[] files=directory.listFiles();
        // A grace period protects files being imported concurrently before their DB transaction.
        if(files!=null)for(File file:files)if(System.currentTimeMillis()-file.lastModified()>86400000L&&!used.contains(Uri.fromFile(file).toString()))file.delete();
        // URI grants can be shared with other modules in this app. Do not revoke them globally.
    }
}
