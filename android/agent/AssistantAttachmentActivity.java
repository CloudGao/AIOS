package os.aios.agent;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import os.aios.core.AiosUi;
import os.aios.core.AiosTheme;

/** Private picker host: captures the destination conversation before leaving the overlay. */
public final class AssistantAttachmentActivity extends Activity {
    private static final int CAMERA=51,DOCUMENT=52;
    private String conversation,session;
    private Uri camera;
    private JSONObject attachment;
    private EditText question;
    private LinearLayout content;
    private AlertDialog dialog;
    private boolean busy,submitted,waiting,returning,themeChanged;
    private Bitmap preview;

    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        conversation=getIntent().getStringExtra("conversation_id");session=getIntent().getStringExtra("ui_session_id");
        if(conversation==null){finish();return;}
        if(saved!=null){waiting=saved.getBoolean("waiting");String uri=saved.getString("camera");if(uri!=null)camera=Uri.parse(uri);try{String json=saved.getString("attachment");if(json!=null)attachment=new JSONObject(json);}catch(Exception ignored){}}
        content=AiosUi.column(this);int pad=AiosUi.dp(this,24);content.setPadding(pad,0,pad,0);
        question=AiosUi.area(this,AssistantAttachmentActivity.this.getString(R.string.assistant_what_would_you_like_the_assistant_to_do_eecfd),saved==null?getIntent().getStringExtra("draft"):saved.getString("draft"),2);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        dialog=AiosUi.dialog(this).setTitle(os.aios.core.DisplayText.clean(AssistantAttachmentActivity.this.getString(R.string.assistant_add_attachment_dba9e))).setView(scroll).setNegativeButton(os.aios.core.DisplayText.clean(AssistantAttachmentActivity.this.getString(R.string.assistant_cancel_4d0b4)),(d,w)->finish()).setPositiveButton(os.aios.core.DisplayText.clean(AssistantAttachmentActivity.this.getString(R.string.assistant_send_1214d)),null).create();
        dialog.setOnCancelListener(d->finish());dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->send());render();dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);});dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        if(attachment!=null&&attachment.optString("mime").startsWith("image/")){
            String restoredUri=attachment.optString("uri");
            new Thread(()->{try{Bitmap image=AssistantAttachments.thumbnail(this,Uri.parse(restoredUri));runOnUiThread(()->{if(isFinishing()||isDestroyed()||attachment==null||!restoredUri.equals(attachment.optString("uri"))){image.recycle();return;}releasePreview();preview=image;render();});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())error(AssistantAttachmentActivity.this.getString(R.string.assistant_preview_unavailable_remove_and_select_again_7582c));});}},"assistant-attachment-preview").start();
        }
        if(saved!=null&&!waiting&&camera!=null&&attachment==null)onActivityResult(CAMERA,RESULT_OK,null);
        if(saved==null){String picker=getIntent().getStringExtra("picker");if("album".equals(picker)||"file".equals(picker))pick(DOCUMENT,"album".equals(picker));}
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(busy||waiting){themeChanged=true;return;}recreate();}
    private void render(){
        content.removeAllViews();
        if(attachment==null){
            content.addView(AiosUi.settingRow(this,"image",AssistantAttachmentActivity.this.getString(R.string.assistant_photos_c657c),null,null,true,false,()->pick(DOCUMENT,true)));
            content.addView(AiosUi.settingRow(this,"folder",AssistantAttachmentActivity.this.getString(R.string.assistant_files_49dea),null,null,false,true,()->pick(DOCUMENT,false)));
            content.addView(AiosUi.secondary(this,busy?AssistantAttachmentActivity.this.getString(R.string.assistant_preparing_attachment_e0139):AssistantAttachmentActivity.this.getString(R.string.assistant_one_attachment_at_a_time_up_to_32_mb_e3468)));
        }else{
            content.addView(AiosUi.body(this,attachment.optString("name")));
            content.addView(AiosUi.secondary(this,android.text.format.Formatter.formatShortFileSize(this,attachment.optLong("bytes"))));
            if(preview!=null){ImageView image=new ImageView(this);image.setImageBitmap(preview);image.setAdjustViewBounds(true);image.setMaxHeight(AiosUi.dp(this,220));image.setContentDescription(os.aios.core.DisplayText.clean(AssistantAttachmentActivity.this.getString(R.string.assistant_selected_image_preview_d13bd)));content.addView(image,new LinearLayout.LayoutParams(-1,-2));}
            content.addView(AiosUi.settingRow(this,"delete",AssistantAttachmentActivity.this.getString(R.string.assistant_remove_attachment_8f3ea),null,null,true,true,()->{if(busy)return;AssistantAttachments.delete(this,Uri.parse(attachment.optString("uri")));attachment=null;releasePreview();render();}));
        }
        if(question.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)question.getParent()).removeView(question);
        content.addView(question);dialog.getButton(-1).setEnabled(!busy&&attachment!=null);
        dialog.getButton(-2).setEnabled(!busy);dialog.setCancelable(!busy);question.setEnabled(!busy);
    }
    private void pick(int request,boolean photo){if(busy||waiting)return;
        try{Intent intent;
            if(request==CAMERA){intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);camera=AssistantAttachments.create(this,true,"image/jpeg");intent.putExtra(MediaStore.EXTRA_OUTPUT,camera);intent.setClipData(ClipData.newRawUri(AssistantAttachmentActivity.this.getString(R.string.assistant_photo_48e9e),camera));intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            else intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(photo?"image/*":"*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            waiting=true;startActivityForResult(intent,request);
        }catch(Exception e){waiting=false;AssistantAttachments.delete(this,camera);camera=null;error(AssistantAttachmentActivity.this.getString(R.string.assistant_could_not_open_picker_64fc2)+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);waiting=false;
        if(result!=RESULT_OK){if(request==CAMERA){AssistantAttachments.delete(this,camera);camera=null;}if(themeChanged)recreate();return;}
        Uri source=request==CAMERA?camera:data==null?null:data.getData();if(source==null){error(AssistantAttachmentActivity.this.getString(R.string.assistant_no_attachment_received_please_select_again_34dcf));return;}
        busy=true;render();new Thread(()->{
            JSONObject item=null;Bitmap bitmap=null;Exception failure=null;
            try{item=request==CAMERA?AssistantAttachments.describe(this,source,AssistantAttachmentActivity.this.getString(R.string.assistant_captured_photo_jpg_bb8a4)):AssistantAttachments.copy(this,source);if(item.optString("mime").startsWith("image/"))bitmap=AssistantAttachments.thumbnail(this,Uri.parse(item.getString("uri")));}
            catch(Exception e){failure=e;if(item!=null)AssistantAttachments.delete(this,Uri.parse(item.optString("uri")));else if(request==CAMERA)AssistantAttachments.delete(this,source);}
            JSONObject ready=item;Bitmap image=bitmap;Exception problem=failure;
            runOnUiThread(()->{busy=false;camera=null;if(isFinishing()||isDestroyed()){if(ready!=null)AssistantAttachments.delete(this,Uri.parse(ready.optString("uri")));if(image!=null)image.recycle();return;}if(problem==null){attachment=ready;preview=image;}else error(problem.getMessage());render();if(themeChanged)recreate();});
        },"assistant-attachment-copy").start();
    }
    private void send(){if(busy||attachment==null)return;
        try{String text=question.getText().toString().trim();if(text.isEmpty())text=AssistantAttachmentActivity.this.getString(R.string.assistant_please_review_this_attachment_95501);
            if(text.length()>10000)throw new IllegalArgumentException(AssistantAttachmentActivity.this.getString(R.string.assistant_keep_your_message_within_10_000_characters_dfb17));
            String prompt=getString(R.string.assistant_attachment_description,text,attachment.optString("name"));
            // Check before publishing; enqueue checks again while holding the conversation lock.
            if(!conversation.equals(AssistantConversation.state(this).optString("conversation_id")))throw new IllegalStateException(AssistantAttachmentActivity.this.getString(R.string.assistant_conversation_changed_return_to_the_original_conversation_to_send_12214));
            attachment=AssistantAttachments.publish(this,attachment);
            AssistantConversation.enqueueAttachment(this,conversation,prompt,attachment);submitted=true;
            getSharedPreferences("assistant_composer",0).edit().putString("draft","").apply();finish();
        }catch(Exception e){error(e.getMessage());}
    }
    private void error(String text){Toast.makeText(this,os.aios.core.DisplayText.clean(text==null?AssistantAttachmentActivity.this.getString(R.string.assistant_could_not_process_attachment_0f59b):text),Toast.LENGTH_LONG).show();}
    private void releasePreview(){if(preview!=null){preview.recycle();preview=null;}}
    @Override public void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("draft",question.getText().toString());out.putBoolean("waiting",waiting);if(camera!=null)out.putString("camera",camera.toString());if(attachment!=null)out.putString("attachment",attachment.toString());}
    @Override public void finish(){if(!returning){returning=true;if(!submitted&&question!=null)getSharedPreferences("assistant_composer",0).edit().putString("draft",question.getText().toString()).apply();try{if(conversation!=null&&conversation.equals(AssistantConversation.state(this).optString("conversation_id")))startForegroundService(new Intent(this,AssistantOrbService.class).setAction("continue").putExtra("ui_session_id",session).putExtra("reload_draft",true));}catch(Exception ignored){}}super.finish();}
    @Override public void onDestroy(){if(isFinishing()&&!submitted){if(attachment!=null)AssistantAttachments.delete(this,Uri.parse(attachment.optString("uri")));AssistantAttachments.delete(this,camera);}releasePreview();super.onDestroy();}
}
