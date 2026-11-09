package os.aios.agent;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import os.aios.core.AiosTheme;

/** A quiet themed recording surface; waveform amplitude comes from the recognizer. */
final class AssistantVoiceFeedback extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float amplitude;
    private boolean cancelling;
    AssistantVoiceFeedback(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    void update(boolean cancel,float rms){cancelling=cancel;amplitude=Math.max(0,Math.min(1,rms/12f));setContentDescription(cancel?getContext().getString(R.string.assistant_release_to_cancel_f9d6e):getContext().getString(R.string.assistant_release_to_send_slide_up_to_cancel_3c2a2));invalidate();}
    @Override protected void onDraw(Canvas canvas){
        int color=cancelling?AiosTheme.onErrorContainer(getContext()):AiosTheme.accent(getContext());
        paint.setShader(new LinearGradient(0,0,0,getHeight(),new int[]{color&0xffffff,(color&0xffffff)|0x44000000},null,Shader.TileMode.CLAMP));
        float radius=28*getResources().getDisplayMetrics().density;canvas.drawRoundRect(0,0,getWidth(),getHeight(),radius,radius,paint);paint.setShader(null);paint.setColor(color);
        float density=getResources().getDisplayMetrics().density;
        paint.setTextSize(15*density);paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(cancelling?getContext().getString(R.string.assistant_release_to_cancel_f9d6e):getContext().getString(R.string.assistant_release_to_send_slide_up_to_cancel_3c2a2),getWidth()/2f,getHeight()*.42f,paint);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(2.5f*density);
        for(int i=0;i<37;i++){float x=getWidth()/2f+(i-18)*5*density;float h=(2+amplitude*(8+12*(float)Math.abs(Math.sin(i*1.7))))*density;canvas.drawLine(x,getHeight()*.65f-h/2,x,getHeight()*.65f+h/2,paint);}
    }
}
