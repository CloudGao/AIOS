package os.aios.agent;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import os.aios.core.AiosUi;
import os.aios.core.AiosTheme;

/** Foreground camera preview occupying only the free region above the assistant overlay. */
public final class AssistantCameraActivity extends Activity {
    static final String CLOSE="os.aios.agent.CLOSE_COMPOSER_CAMERA",CAPTURE="os.aios.agent.CAPTURE_COMPOSER_CAMERA",CLOSED="os.aios.agent.COMPOSER_CAMERA_CLOSED";
    private TextureView texture;private TextView message;private CameraDevice camera;private CameraCaptureSession session;private ImageReader reader;private Surface previewSurface;
    private HandlerThread worker;private Handler background;private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean resumed,capturing;private boolean registered,opening;private int generation,sensorOrientation;private Size previewSize;
    private String conversation,gesture;private boolean showPreview,previewReady;private Uri output;
    private final BroadcastReceiver commands=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!gesture.equals(i.getStringExtra("gesture")))return;if(CLOSE.equals(i.getAction()))finish();else capture();}};
    @Override public void onCreate(Bundle state){super.onCreate(state);if(getIntent().getBooleanExtra("permission_only",false)){requestPermissions(new String[]{Manifest.permission.CAMERA},82);return;}conversation=getIntent().getStringExtra("conversation_id");gesture=getIntent().getStringExtra("gesture");showPreview=getIntent().getBooleanExtra("preview",false);
        if(conversation==null||gesture==null||!AssistantCameraGesture.valid(gesture)){finish();return;}
        FrameLayout content=new FrameLayout(this);content.setBackground(AiosUi.shape(this,AiosTheme.surfaceHigh(this),28));content.setClipToOutline(true);if(!showPreview)content.setAlpha(0);
        texture=new TextureView(this);content.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        message=AiosUi.body(this,AssistantCameraActivity.this.getString(R.string.assistant_opening_camera_879c1));message.setGravity(Gravity.CENTER);content.addView(message,new FrameLayout.LayoutParams(-1,-1));setContentView(content);
        Window window=getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        android.graphics.Rect bounds=getWindowManager().getCurrentWindowMetrics().getBounds();android.graphics.Insets insets=getWindowManager().getCurrentWindowMetrics().getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int margin=AiosUi.dp(this,12),top=insets.top+margin,bottom=getIntent().getIntExtra("available_bottom",bounds.height()/2)-margin;
        if(!showPreview)bottom=top+AiosUi.dp(this,120);if(bottom-top<AiosUi.dp(this,120)){Toast.makeText(this,os.aios.core.DisplayText.clean(AssistantCameraActivity.this.getString(R.string.assistant_not_enough_preview_space_hide_the_keyboard_first_caeab)),Toast.LENGTH_LONG).show();finish();return;}
        window.setDecorFitsSystemWindows(false);WindowManager.LayoutParams params=window.getAttributes();params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;params.y=top;params.width=bounds.width()-insets.left-insets.right-2*margin;params.height=bottom-top;window.setAttributes(params);
        IntentFilter filter=new IntentFilter(CLOSE);filter.addAction(CAPTURE);registerReceiver(commands,filter,Context.RECEIVER_NOT_EXPORTED);registered=true;
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){open();}public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform();}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){closeCamera();return true;}public void onSurfaceTextureUpdated(SurfaceTexture s){}});
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},81);
    }
    @Override protected void onResume(){super.onResume();resumed=true;worker=new HandlerThread("assistant-camera");worker.start();background=new Handler(worker.getLooper());if(texture!=null&&texture.isAvailable())open();}
    private void open(){if(!resumed||!AssistantCameraGesture.valid(gesture)||opening||camera!=null||background==null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;opening=true;final int epoch=++generation;main.postDelayed(()->{if(resumed&&epoch==generation&&!previewReady)fail(AssistantCameraActivity.this.getString(R.string.assistant_camera_startup_timed_out_please_try_again_f1ed1));},8000);
        try{CameraManager manager=getSystemService(CameraManager.class);String id=null;
            for(String candidate:manager.getCameraIdList()){Integer facing=manager.getCameraCharacteristics(candidate).get(CameraCharacteristics.LENS_FACING);if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_BACK){id=candidate;break;}}
            if(id==null)throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_no_rear_camera_available_6b77d));CameraCharacteristics characteristics=manager.getCameraCharacteristics(id);Integer orientation=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);sensorOrientation=orientation==null?0:orientation;
            StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map==null)throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_camera_preview_is_unsupported_072f6));
            Size[] jpeg=map.getOutputSizes(ImageFormat.JPEG);Size[] previews=map.getOutputSizes(SurfaceTexture.class);if(jpeg==null||previews==null)throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_photo_capture_is_unsupported_9095d));
            Size photo=choose(jpeg,1920*1440);previewSize=choose(previews,1920*1080);reader=ImageReader.newInstance(photo.getWidth(),photo.getHeight(),ImageFormat.JPEG,2);reader.setOnImageAvailableListener(this::saveImage,background);
            manager.openCamera(id,new CameraDevice.StateCallback(){public void onOpened(CameraDevice device){opening=false;if(!resumed||epoch!=generation){device.close();return;}camera=device;configure(epoch);}public void onDisconnected(CameraDevice device){device.close();fail(AssistantCameraActivity.this.getString(R.string.assistant_camera_disconnected_7dc5e));}public void onError(CameraDevice device,int code){device.close();fail(AssistantCameraActivity.this.getString(R.string.assistant_camera_unavailable_please_try_later_b7a7e));}},main);
        }catch(Exception e){opening=false;fail(e.getMessage());}
    }
    private static Size choose(Size[] sizes,long budget){Size selected=null;for(Size size:sizes){long area=(long)size.getWidth()*size.getHeight();if(area<=budget&&(selected==null||area>(long)selected.getWidth()*selected.getHeight()))selected=size;}if(selected!=null)return selected;return Arrays.stream(sizes).min(Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight())).orElseThrow();}
    private void configure(int epoch){try{SurfaceTexture surface=texture.getSurfaceTexture();if(surface==null)return;surface.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());previewSurface=new Surface(surface);transform();
        camera.createCaptureSession(Arrays.asList(previewSurface,reader.getSurface()),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession configured){if(!resumed||camera==null||epoch!=generation){configured.close();return;}session=configured;try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(previewSurface);request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);session.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){if(!previewReady){previewReady=true;capture();}}},main);message.setVisibility(View.GONE);}catch(Exception e){fail(AssistantCameraActivity.this.getString(R.string.assistant_could_not_start_preview_c4432));}}public void onConfigureFailed(CameraCaptureSession failed){fail(AssistantCameraActivity.this.getString(R.string.assistant_could_not_configure_camera_preview_b7bf8));}},main);
    }catch(Exception e){fail(AssistantCameraActivity.this.getString(R.string.assistant_could_not_open_preview_03de2));}}
    private int rotationDegrees(){return getDisplay().getRotation()*90;}
    @Override public void onAttachedToWindow(){
        super.onAttachedToWindow();
        getSystemService(android.hardware.display.DisplayManager.class).registerDisplayListener(displayListener,main);
    }
    @Override public void onDetachedFromWindow(){
        getSystemService(android.hardware.display.DisplayManager.class).unregisterDisplayListener(displayListener);
        super.onDetachedFromWindow();
    }
    private final android.hardware.display.DisplayManager.DisplayListener displayListener=
            new android.hardware.display.DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}
        public void onDisplayRemoved(int id){}
        public void onDisplayChanged(int id){
            Display display=getDisplay();
            if(display!=null&&display.getDisplayId()==id)transform();
        }
    };
    private void transform(){
        if(texture==null||previewSize==null||texture.getWidth()==0||texture.getHeight()==0)return;
        texture.setTransform(previewTransform(texture.getWidth(),texture.getHeight(),previewSize,sensorOrientation,rotationDegrees()));
    }
    static Matrix previewTransform(float w,float h,Size previewSize,int sensorOrientation,int displayRotation){
        // Camera2's SurfaceTexture already applies SENSOR_ORIENTATION. Its upright
        // buffer is stretched into the TextureView; undo that stretch before
        // compensating display rotation, without applying sensor rotation twice.
        boolean sensorSwapped=sensorOrientation%180!=0;
        float bw=sensorSwapped?previewSize.getHeight():previewSize.getWidth();
        float bh=sensorSwapped?previewSize.getWidth():previewSize.getHeight();
        boolean displaySwapped=displayRotation%180!=0;
        float scale=Math.max(w/(displaySwapped?bh:bw),h/(displaySwapped?bw:bh));
        Matrix matrix=new Matrix();
        matrix.setScale(bw/w,bh/h,w/2,h/2);
        matrix.postRotate(-displayRotation,w/2,h/2);
        // Center crop with one uniform scale: circles stay round in any window.
        matrix.postScale(scale,scale,w/2,h/2);
        return matrix;
    }
    private void capture(){if(!AssistantCameraGesture.valid(gesture)){finish();return;}if(!AssistantCameraGesture.ready(gesture)||!previewReady||capturing||session==null||camera==null)return;try{if(!conversation.equals(AssistantConversation.state(this).optString("conversation_id")))throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_conversation_changed_please_take_another_photo_1d284));String state=AssistantConversation.state(this).optString("status");if(state.equals("queued")||state.equals("running"))throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_assistant_is_busy_wait_before_taking_a_photo_a1b1a));capturing=true;main.postDelayed(()->{if(capturing&&!isFinishing())fail(AssistantCameraActivity.this.getString(R.string.assistant_photo_capture_timed_out_please_try_again_1c37e));},10000);message.setText(AssistantCameraActivity.this.getString(R.string.assistant_taking_photo_3890b));message.setVisibility(View.VISIBLE);CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);request.set(CaptureRequest.JPEG_ORIENTATION,(sensorOrientation-rotationDegrees()+360)%360);session.capture(request.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){fail(AssistantCameraActivity.this.getString(R.string.assistant_photo_capture_failed_please_try_again_1d232));}},main);}catch(Exception e){capturing=false;fail(e.getMessage());}}
    private void saveImage(ImageReader source){Uri created=null;try(Image image=source.acquireNextImage()){if(image==null)return;if(!resumed)return;ByteBuffer buffer=image.getPlanes()[0].getBuffer();if(buffer.remaining()>AssistantAttachments.MAX_BYTES)throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_photo_is_too_large_cf8a6));created=AssistantAttachments.create(this,true,"image/jpeg");try(OutputStream stream=getContentResolver().openOutputStream(created)){if(stream==null)throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_could_not_save_photo_39d69));byte[] block=new byte[32768];while(buffer.hasRemaining()){int n=Math.min(buffer.remaining(),block.length);buffer.get(block,0,n);stream.write(block,0,n);}}Uri ready=created;main.post(()->{if(!resumed||isFinishing()||!AssistantCameraGesture.valid(gesture)){AssistantAttachments.delete(this,ready);return;}output=ready;try{org.json.JSONObject attachment=AssistantAttachments.describe(this,ready,AssistantCameraActivity.this.getString(R.string.assistant_captured_photo_jpg_bb8a4));String draft=getIntent().getStringExtra("draft");if(draft==null||draft.isBlank())draft=AssistantCameraActivity.this.getString(R.string.assistant_please_review_this_photo_20768);synchronized(AssistantConversation.class){if(!conversation.equals(AssistantConversation.state(this).optString("conversation_id")))throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_conversation_changed_photo_was_not_sent_e7d2c));String state=AssistantConversation.state(this).optString("status");if(state.equals("queued")||state.equals("running"))throw new IllegalStateException(AssistantCameraActivity.this.getString(R.string.assistant_assistant_is_busy_please_wait_before_taking_a_photo_c50a9));attachment=AssistantAttachments.publish(this,attachment);AssistantConversation.enqueueAttachment(this,conversation,draft+AssistantCameraActivity.this.getString(R.string.assistant_attachment_captured_photo_jpg_28419),attachment);}output=null;getIntent().putExtra("draft","");getSharedPreferences("assistant_composer",0).edit().putString("draft","").apply();sendBroadcast(new Intent("os.aios.agent.CAMERA_PHOTO_SENT").setPackage(getPackageName()));message.setVisibility(View.GONE);capturing=false;Toast.makeText(this,os.aios.core.DisplayText.clean(AssistantCameraActivity.this.getString(R.string.assistant_photo_sent_df851)),Toast.LENGTH_SHORT).show();finish();}catch(Exception e){AssistantAttachments.delete(this,ready);output=null;capturing=false;fail(e.getMessage());}});}catch(Exception e){AssistantAttachments.delete(this,created);main.post(()->fail(AssistantCameraActivity.this.getString(R.string.assistant_could_not_save_photo_please_try_again_f8a88)));}}
    private void fail(String text){if(isFinishing())return;Toast.makeText(this,os.aios.core.DisplayText.clean(text==null?AssistantCameraActivity.this.getString(R.string.assistant_camera_unavailable_83e94):text),Toast.LENGTH_LONG).show();finish();}
    private void closeCamera(){generation++;opening=false;previewReady=false;if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(reader!=null){reader.close();reader=null;}if(previewSurface!=null){previewSurface.release();previewSurface=null;}}
    @Override protected void onPause(){resumed=false;closeCamera();if(worker!=null){worker.quitSafely();worker=null;background=null;}super.onPause();}
    @Override protected void onStop(){super.onStop();if(!isChangingConfigurations())finish();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==82){Toast.makeText(this,os.aios.core.DisplayText.clean(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED?AssistantCameraActivity.this.getString(R.string.assistant_camera_access_allowed_tap_or_hold_again_to_take_a_photo_15ba4):AssistantCameraActivity.this.getString(R.string.assistant_camera_access_not_granted_eac44)),Toast.LENGTH_LONG).show();startForegroundService(new Intent(this,AssistantOrbService.class).putExtra("ui_session_id",getIntent().getStringExtra("ui_session_id")));finish();return;}if(request==81&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)open();else fail(AssistantCameraActivity.this.getString(R.string.assistant_camera_access_not_granted_eac44));}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);closeCamera();if(registered)unregisterReceiver(commands);AssistantAttachments.delete(this,output);sendBroadcast(new Intent(CLOSED).setPackage(getPackageName()).putExtra("gesture",gesture));super.onDestroy();}
}
