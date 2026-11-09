package os.aios.agent;
import android.content.*;import android.content.pm.*;
public final class SpeechSelectionTest{
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static void rejected(Context c){boolean failed=false;try{SpeechModelSelection.selected(c,new ComponentName("host","Default"));}catch(IllegalStateException expected){failed=true;}check(failed);}
 public static void main(String[] args){Context c=new Context();ComponentName host=new ComponentName("host","Default");check(SpeechModelSelection.selected(c,host).equals(host));
 SpeechModelSelection.save(c,SpeechModelSelection.SYSTEM);check(SpeechModelSelection.selected(c,host)==null);
 ComponentName local=new ComponentName("os.aios.app","os.aios.speech.SenseVoiceService");ServiceInfo internal=new ServiceInfo();internal.exported=false;c.manager.services.put(local.flattenToString(),internal);SpeechModelSelection.save(c,local.flattenToString());check(SpeechModelSelection.usesBuiltin(c,host));
 ComponentName external=new ComponentName("provider","Recognizer");ServiceInfo service=new ServiceInfo();c.manager.services.put(external.flattenToString(),service);SpeechModelSelection.save(c,external.flattenToString());check(SpeechModelSelection.selected(c,host).equals(external));check(!SpeechModelSelection.usesBuiltin(c,host));
 service.exported=false;rejected(c);service.exported=true;service.enabled=false;rejected(c);service.enabled=true;service.applicationInfo.enabled=false;rejected(c);service.applicationInfo.enabled=true;service.permission="unrelated.permission";rejected(c);service.permission="android.permission.BIND_SPEECH_RECOGNITION_SERVICE";c.manager.services.remove(external.flattenToString());rejected(c);
 SpeechModelSelection.save(c,"malformed");rejected(c);SpeechModelSelection.save(c,SpeechModelSelection.DEFAULT);check(SpeechModelSelection.selected(c,host).equals(host));
 check(SpeechModelSelection.current(new Context()).equals(SpeechModelSelection.DEFAULT));System.out.println("SpeechModelSelection: "+checks+" behavior assertions passed");}
}
