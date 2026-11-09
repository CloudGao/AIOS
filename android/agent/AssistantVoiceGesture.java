package os.aios.agent;

/** Process-local gesture gate. Cancellation takes effect before Activity/broadcast delivery. */
final class AssistantVoiceGesture {
    private static String token="",conversation="";
    private static boolean released,cancelled;
    static synchronized String begin(String destination){token=java.util.UUID.randomUUID().toString();conversation=destination;released=false;cancelled=false;return token;}
    static synchronized void release(String id,boolean cancel){if(token.equals(id)){released=true;cancelled=cancel;}}
    static synchronized boolean valid(String id){return token.equals(id)&&!cancelled;}
    static synchronized boolean released(String id){return token.equals(id)&&released;}
    static synchronized String conversation(String id){return token.equals(id)?conversation:"";}
}
