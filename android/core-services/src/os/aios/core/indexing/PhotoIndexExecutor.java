package os.aios.core.indexing;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import org.json.*;
import os.aios.runtime.DeviceSearchClient;
import os.aios.core.embedding.*;

/** Native bounded handler. Does not enqueue, retry, sleep or arrange its next invocation. */
public final class PhotoIndexExecutor {
    public interface Gate{void check()throws Exception;}
    private final Context context;private final DeviceSearchClient device;
    public PhotoIndexExecutor(Context c){context=c;device=new DeviceSearchClient(c);}
    public JSONObject execute(String node,Gate gate)throws Exception{
        gate.check();try(PhotoIndexStore store=new PhotoIndexStore(context)){
            if(node.equals("discover_delta")){
                JSONObject discovery=store.discovery();if(discovery.optInt("complete")==1){JSONArray removed=store.invisible(128);if(removed.length()>0){for(int i=0;i<removed.length();i++){gate.check();JSONObject row=removed.getJSONObject(i);JSONArray generations=store.generations();if(generations.length()==0){String oldSpace=row.optString("space_id");if(oldSpace.matches("[a-f0-9]{64}"))try(VectorIndex index=new VectorIndex(context,oldSpace)){index.remove(row.getLong("id"));}}for(int g=0;g<generations.length();g++){JSONObject generation=generations.getJSONObject(g);try(VectorIndex index=new VectorIndex(context,generation.getString("space_id"),generation.getLong("id"))){index.remove(row.getLong("id"));}}store.forgetInvisible(row.getLong("id"));}return store.progress();}}JSONObject page=device.call("query",new JSONObject().put("source","photos").put("limit",32).put("after_id",discovery.getLong("after_id")));
                if(!page.optString("state").equals("available"))throw new SecurityException("media_permission_required");store.discover(page.getJSONArray("items"),page.optBoolean("has_more"),page.optLong("after_id"));return store.progress();
            }
            if(node.equals("report_progress")){JSONObject active=new ModelRegistry(context).manifest();String target=active.getString("space_id");store.prepare(active);try(VectorIndex index=new VectorIndex(context,target,store.generationFor(target))){store.publishGeneration(target,index.size());}return store.progress();}
            if(!node.equals("process_photo_batch"))throw new IllegalArgumentException("Unknown photo operator");
            ModelRegistry models=new ModelRegistry(context);boolean faceReady=models.validated("face");if(faceReady)store.retryUnavailableFaces();models.pinCurrent();store.prepare(models.manifest());String space=models.manifest().getString("space_id");JSONArray pending=store.pending(space,32);long deadline=SystemClock.elapsedRealtime()+120000;int processed=0;
            try(VectorIndex vectors=new VectorIndex(context,space,store.generationFor(space));EmbeddingClient inference=new EmbeddingClient(context,true)){
                for(int i=0;i<pending.length()&&SystemClock.elapsedRealtime()<deadline;i++){
                    gate.check();JSONObject row=pending.getJSONObject(i),source=new JSONObject(row.getString("source_json"));long id=row.getLong("id");String version=row.getString("source_version");
                    JSONObject request=new JSONObject().put("source","photos").put("id",source.getString("id")).put("source_version",version);
                    JSONObject current=device.call("get",request);JSONArray found=current.optJSONArray("items");if(found==null||found.length()!=1||!found.getJSONObject(0).optString("source_version").equals(version)||!found.getJSONObject(0).optString("database_version").equals(source.optString("database_version"))){store.invalidate(id);vectors.remove(id);continue;}
                    Gate currentSource=()->{gate.check();JSONObject fresh=device.call("get",request);JSONArray visible=fresh.optJSONArray("items");if(visible==null||visible.length()!=1||!visible.getJSONObject(0).optString("source_version").equals(version)||!visible.getJSONObject(0).optString("database_version").equals(source.optString("database_version")))throw new java.util.concurrent.CancellationException("source_changed");};
                    if(row.optString("exif_state").equals("pending")){
                        try{JSONObject exif;try(ParcelFileDescriptor fd=device.open(new JSONObject(request.toString()).put("original",true))){exif=PhotoExif.extract(fd,true);}catch(Exception original){try(ParcelFileDescriptor fd=device.open(request)){exif=PhotoExif.extract(fd,false);}}
                            currentSource.check();store.publish(id,version,"exif",exif.getString("state"),exif);
                        }catch(java.util.concurrent.CancellationException e){throw e;}catch(SecurityException e){throw e;}catch(Exception e){store.publish(id,version,"exif","failed",new JSONObject().put("error",e.getClass().getSimpleName()));}
                    }
                    try(ParcelFileDescriptor fd=device.open(request)){
                        Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(()->new android.content.res.AssetFileDescriptor(ParcelFileDescriptor.dup(fd.getFileDescriptor()),0,android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH)),(decoder,info,src)->{decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());if(max>2048)decoder.setTargetSampleSize(Math.max(1,max/2048));});
                        try{
                            if(row.optString("embedding_state").equals("pending")){
                                try{Bundle output=inference.run("image",imageInput(bitmap),null,false,space);float[] vector=output.getFloatArray("output_0");currentSource.check();vectors.put(id,vector);store.publish(id,version,"embedding","ready",null);}catch(java.util.concurrent.CancellationException e){throw e;}catch(Exception e){if(!models.validated("image"))throw e;store.publish(id,version,"embedding","failed",new JSONObject().put("error",failure(e)));}
                            }
                            if(row.optString("face_state").equals("pending")&&!faceReady){currentSource.check();store.publish(id,version,"face","model_unavailable",null);}
                            if(row.optString("face_state").equals("pending")&&faceReady){
                                try{Bundle output=inference.run("face",faceInput(bitmap),null,false,space);float[] a=output.getFloatArray("output_0"),b=output.getFloatArray("output_1");JSONArray boxes=FaceDecoder.decode(a.length>b.length?a:b,a.length>b.length?b:a,bitmap.getWidth(),bitmap.getHeight());currentSource.check();store.publish(id,version,"face","ready",new JSONObject().put("boxes",boxes).put("detected_count",boxes.length()).put("width",bitmap.getWidth()).put("height",bitmap.getHeight()).put("coordinates","orientation_corrected_decoded_pixels").put("threshold",0.6).put("model_space",space));}catch(java.util.concurrent.CancellationException e){throw e;}catch(Exception e){if(!models.validated("face"))throw e;store.publish(id,version,"face","failed",new JSONObject().put("error",e.getClass().getSimpleName()));}
                            }
                        }finally{bitmap.recycle();}
                    }catch(java.util.concurrent.CancellationException e){throw e;}catch(SecurityException e){throw e;}catch(Exception e){if(!models.validated("image")||(faceReady&&!models.validated("face")))throw e;JSONObject latest=store.photo(id);if(latest!=null&&latest.optString("embedding_state").equals("pending"))store.publish(id,version,"embedding","failed",new JSONObject().put("error",failure(e)));if(latest!=null&&latest.optString("face_state").equals("pending"))store.publish(id,version,"face","failed",new JSONObject().put("error",e.getClass().getSimpleName()));}
                    processed++;
                }
            }return store.progress().put("batch_processed",processed);
        }
    }
    private static String failure(Throwable error){while(error.getCause()!=null)error=error.getCause();String reason=error.getClass().getSimpleName()+": "+error.getMessage();return reason.substring(0,Math.min(300,reason.length()));}
    public static float[] imageInput(Bitmap bitmap){return ClipImageInput.encode(bitmap);}
    public static float[] faceInput(Bitmap original){Bitmap letterbox=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(letterbox);canvas.drawColor(Color.BLACK);float scale=Math.min(192f/original.getWidth(),192f/original.getHeight()),w=original.getWidth()*scale,h=original.getHeight()*scale;canvas.drawBitmap(original,null,new RectF((192-w)/2,(192-h)/2,(192+w)/2,(192+h)/2),new Paint(Paint.FILTER_BITMAP_FLAG));int[] pixels=new int[192*192];letterbox.getPixels(pixels,0,192,0,0,192,192);letterbox.recycle();float[] data=new float[pixels.length*3];for(int i=0;i<pixels.length;i++){data[i*3]=((pixels[i]>>16)&255)/127.5f-1;data[i*3+1]=((pixels[i]>>8)&255)/127.5f-1;data[i*3+2]=(pixels[i]&255)/127.5f-1;}return data;}
}
