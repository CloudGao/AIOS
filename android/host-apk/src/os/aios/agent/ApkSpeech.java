package os.aios.agent;

import android.content.ComponentName;
import android.content.Context;

/** The bundled recognizer is the APK default, independent of OEM services and downloads. */
final class ApkSpeech {
    static ComponentName local(Context c) { return new ComponentName(c.getPackageName(), "os.aios.speech.SenseVoiceService"); }
    static ComponentName select(Context c) { return local(c); }
    static ComponentName fallback(Context c, ComponentName failed, int error) { return null; }
    static boolean supports(java.util.Locale locale) {return java.util.Set.of("zh","cmn","yue","en","ja","ko").contains(locale.getLanguage());}
}
