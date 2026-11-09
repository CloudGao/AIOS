package os.aios.runtime.models;

import android.content.Context;
import java.io.*;
import java.net.*;

public final class OptionalModelStore {
    public static File file(Context context,String id){OptionalModelSpec spec=OptionalModelSpec.get(id);return new File(context.getFilesDir(),"optional-models/"+spec.id+"-"+spec.sha256+".model");}
    public static boolean verified(Context context,String id){try{OptionalModelSpec spec=OptionalModelSpec.get(id);File file=file(context,id);return file.isFile()&&file.length()==spec.bytes&&VerifiedModelFile.hash(file).equals(spec.sha256);}catch(Exception e){return false;}}
    public static void importFile(Context c,String id,InputStream input,VerifiedModelFile.Cancel cancel)throws Exception{OptionalModelSpec spec=OptionalModelSpec.get(id);VerifiedModelFile.install(input,file(c,id),spec.bytes,spec.sha256,cancel);}
    public static void download(Context c,String id,VerifiedModelFile.Cancel cancel)throws Exception{
        OptionalModelSpec spec=OptionalModelSpec.get(id);URL url=new URL(spec.url);
        for(int redirect=0;redirect<6;redirect++){
            if(cancel.cancelled())throw new InterruptedIOException("model_cancelled");
            if(!url.getProtocol().equals("https")||url.getUserInfo()!=null)throw new IOException("model_https_required");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept-Encoding","identity");
            try{
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("model_redirect_missing");url=new URL(url,next);continue;}
                if(status!=200)throw new IOException("model_http_failed");
                long length=connection.getContentLengthLong();if(length>=0&&length!=spec.bytes)throw new IOException("model_size_mismatch");
                try(InputStream input=connection.getInputStream()){importFile(c,id,input,cancel);}return;
            }finally{connection.disconnect();}
        }
        throw new IOException("model_redirect_limit");
    }
}
