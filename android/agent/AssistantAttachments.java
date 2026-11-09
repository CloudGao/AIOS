package os.aios.agent;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.*;
import org.json.JSONObject;
import java.io.*;
import java.util.UUID;

/** User-selected copies only. No directory scans or uploads occur before Send. */
final class AssistantAttachments {
    static final long MAX_BYTES=32L*1024*1024;
    static Uri create(Context c,boolean photo,String mime) {
        ContentValues v=new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME,"assistant-"+UUID.randomUUID()+(photo?".jpg":".bin"));
        v.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,photo?"Pictures/AIOS":"Download/AIOS");
        v.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri uri=c.getContentResolver().insert(photo?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
        if(uri==null)throw new IllegalStateException(c.getString(R.string.assistant_could_not_create_attachment_63419));return uri;
    }
    static JSONObject copy(Context c,Uri source) throws Exception {
        if(!"content".equals(source.getScheme()))throw new IllegalArgumentException(c.getString(R.string.assistant_choose_an_attachment_from_files_or_photos_cb342));
        String name=c.getString(R.string.assistant_attachment_99f6f),mime=c.getContentResolver().getType(source);
        if(mime==null)mime="application/octet-stream";
        try(Cursor cursor=c.getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
        }
        Uri target=create(c,false,mime);
        try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=c.getContentResolver().openOutputStream(target)){
            if(in==null||out==null)throw new IOException(c.getString(R.string.assistant_could_not_read_attachment_429db));
            byte[] buffer=new byte[32768];long total=0;int n;
            while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException(c.getString(R.string.assistant_attachment_must_be_no_larger_than_32_mb_7bd29));out.write(buffer,0,n);}
            if(total==0)throw new IOException(c.getString(R.string.assistant_attachment_is_empty_e6d50));
        }catch(Exception e){delete(c,target);throw e;}
        try{return describe(c,target,name);}catch(Exception e){delete(c,target);throw e;}
    }
    static JSONObject describe(Context c,Uri uri,String displayName) throws Exception {
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{MediaStore.MediaColumns.DATA,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){
            if(cursor==null||!cursor.moveToFirst())throw new IOException(c.getString(R.string.assistant_attachment_unavailable_0cb4f));
            long size=cursor.getLong(1);
            // Pending MediaStore rows can lag behind the closed output stream.
            try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(uri,"r")){if(fd!=null&&fd.getStatSize()>=0)size=fd.getStatSize();}
            if(size<=0||size>MAX_BYTES)throw new IOException(c.getString(R.string.assistant_attachment_is_empty_or_larger_than_32_mb_fd205));
            return new JSONObject().put("uri",uri.toString()).put("path",cursor.getString(0)).put("name",displayName==null?cursor.getString(2):displayName).put("mime",c.getContentResolver().getType(uri)).put("bytes",size);
        }
    }
    static void publish(Context c,Uri uri){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);if(c.getContentResolver().update(uri,values,null,null)!=1)throw new IllegalStateException(c.getString(R.string.assistant_could_not_save_attachment_efe23));}
    static JSONObject publish(Context c,JSONObject attachment) throws Exception {
        Uri uri=Uri.parse(attachment.getString("uri"));publish(c,uri);
        // Publishing renames MediaStore's .pending file; expose only its final path.
        return describe(c,uri,attachment.getString("name"));
    }
    static void delete(Context c,Uri uri){if(uri!=null)try{c.getContentResolver().delete(uri,null,null);}catch(Exception e){android.util.Log.w("AIOSAttachments","Unable to remove draft",e);}}
    static Bitmap thumbnail(Context c,Uri uri) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),(decoder,info,source)->{
            int width=info.getSize().getWidth(),height=info.getSize().getHeight();
            float scale=Math.min(1f,1024f/Math.max(width,height));
            decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
    }
    static JSONObject imageInput(Context c,JSONObject attachment) throws Exception {
        Bitmap bitmap=thumbnail(c,Uri.parse(attachment.getString("uri")));
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,80,bytes);
            if(bytes.size()>300000){bytes.reset();bitmap.compress(Bitmap.CompressFormat.JPEG,45,bytes);}
            if(bytes.size()>300000)throw new IOException(c.getString(R.string.assistant_image_is_too_large_crop_it_before_sending_16add));
            return new JSONObject().put("type","image").put("url","data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));
        }finally{bitmap.recycle();}
    }
}
