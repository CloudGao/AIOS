package os.aios.speech;
import android.content.Context;
import android.media.*;
/** Same-app recognition uses the app microphone permission and public SDK only. */
final class SpeechCapture {
 static AudioRecord create(Context context,AudioFormat format,int rate){return new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(format).setBufferSizeInBytes(Math.max(4096,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)*2)).build();}
}
