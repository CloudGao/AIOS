package os.aios.agent;

import os.aios.device.ScreenBackend;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Read-only, bounded thumbnails. Never reads the main display or changes observation tokens. */
final class UiScreenPreview {
    private static final LinkedHashMap<String,Bundle> cache=new LinkedHashMap<>();
    private static final class Lease {
        UiSessionController task; ScreenBackend.Session session; int epoch; boolean active;
        Lease(UiSessionController t){task=t;if(t!=null){session=t.session;epoch=t.epoch;active=t.state.equals("active")&&t.nativeActive&&t.displayId>0;}}
    }
    private static <T> T onMain(AgentService a,java.util.concurrent.Callable<T> call)throws Exception{
        FutureTask<T> job=new FutureTask<>(call);a.ui.handler.post(job);
        try{return job.get(3,TimeUnit.SECONDS);}catch(Exception e){job.cancel(false);throw e;}
    }
    static Bundle read(AgentService a,String id)throws Exception{
        Bundle empty=new Bundle();empty.putString("frame_state","unavailable");
        if(a==null||id==null||!Authorization.enabled(a)){synchronized(cache){cache.clear();}return empty;}
        Lease lease=onMain(a,()->new Lease(a.ui.find(id)));
        if(lease.task==null || onMain(a,()->lease.task.state.equals("closed"))){synchronized(cache){cache.remove(id);}return empty;}
        if(lease.active && lease.session!=null){
            byte[] bytes=null;
            try{
                synchronized(a.screenshotLock){
                    try(ScreenBackend.Frame image=lease.session.capture()){
                        if(image!=null){
                            Bitmap full=image.bitmap;
                            Bitmap small=null;
                            try{
                                int width=Math.min(360,full.getWidth());int height=Math.max(1,Math.round(full.getHeight()*(width/(float)full.getWidth())));
                                small=Bitmap.createScaledBitmap(full,width,height,true);
                                ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,75,out);
                                if(out.size()<=180000)bytes=out.toByteArray();
                            }finally{if(small!=null&&small!=full)small.recycle();}
                        }
                    }
                }
            }catch(Exception ignored){}
            boolean current=onMain(a,()->Authorization.enabled(a)&&a.ui.find(id)==lease.task&&lease.task.session==lease.session&&lease.task.epoch==lease.epoch&&lease.task.state.equals("active")&&lease.task.nativeActive);
            if(bytes!=null&&current){
                Bundle frame=new Bundle();frame.putString("frame_state","live");frame.putByteArray("jpeg",bytes);frame.putLong("captured_at",System.currentTimeMillis());
                synchronized(cache){cache.put(id,frame);while(cache.size()>32)cache.remove(cache.keySet().iterator().next());}
                return new Bundle(frame);
            }
        }
        if(!Authorization.enabled(a)){synchronized(cache){cache.clear();}return empty;}
        synchronized(cache){Bundle previous=cache.get(id);if(previous==null)return empty;Bundle last=new Bundle(previous);last.putString("frame_state","last_frame");return last;}
    }
}
