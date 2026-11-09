package os.aios.agent;

import android.content.*;
import android.content.pm.*;

/** User-selected recognizer for assistant voice input. Minutes retain their batch decoder. */
final class SpeechModelSelection {
    static final String DEFAULT="host_default",SYSTEM="system_default";
    static String current(Context c){return c.getSharedPreferences("speech-model-selection",0).getString("recognizer",DEFAULT);}
    static ComponentName builtin(Context c){
        ComponentName local=new ComponentName(c.getPackageName(),"os.aios.speech.SenseVoiceService");
        try{c.getPackageManager().getServiceInfo(local,0);return local;}catch(PackageManager.NameNotFoundException ignored){return new ComponentName("os.aios.speech","os.aios.speech.SenseVoiceService");}
    }
    static ComponentName selected(Context c,ComponentName hostDefault){
        String value=current(c);if(DEFAULT.equals(value))return hostDefault;if(SYSTEM.equals(value))return null;
        ComponentName component=ComponentName.unflattenFromString(value);if(component==null)throw new IllegalStateException("speech_selection_invalid");
        try{ServiceInfo info=c.getPackageManager().getServiceInfo(component,0);if(!info.enabled||!info.applicationInfo.enabled||(!component.getPackageName().equals(c.getPackageName())&&!info.exported)||!"android.permission.BIND_SPEECH_RECOGNITION_SERVICE".equals(info.permission))throw new IllegalStateException("speech_selection_unavailable");}
        catch(PackageManager.NameNotFoundException missing){throw new IllegalStateException("speech_selection_unavailable",missing);}return component;
    }
    static boolean usesBuiltin(Context c,ComponentName hostDefault){return builtin(c).equals(selected(c,hostDefault));}
    static String label(Context c){String value=current(c);String key=DEFAULT.equals(value)?"host_default":SYSTEM.equals(value)?"system_default":value.equals(builtin(c).flattenToString())?"sensevoice":null;
        if(key!=null)return c.getString(c.getResources().getIdentifier("model_choice_"+key,"string",c.getPackageName()));
        try{return c.getPackageManager().getServiceInfo(ComponentName.unflattenFromString(value),0).loadLabel(c.getPackageManager()).toString();}catch(Exception missing){return c.getString(c.getResources().getIdentifier("model_choice_missing","string",c.getPackageName()));}}
    static void save(Context c,String value){c.getSharedPreferences("speech-model-selection",0).edit().putString("recognizer",value).apply();}
}
