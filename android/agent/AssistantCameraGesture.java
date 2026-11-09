package os.aios.agent;

/** Capture intent survives camera startup, but cancelled and superseded gestures cannot shoot. */
final class AssistantCameraGesture {
    private static String token="";private static boolean released,cancelled;
    static synchronized String begin(boolean shoot){token=java.util.UUID.randomUUID().toString();released=shoot;cancelled=false;return token;}
    static synchronized boolean valid(String id){return token.equals(id)&&!cancelled;}
    static synchronized boolean ready(String id){return valid(id)&&released;}
    static synchronized void release(String id,boolean cancel){if(token.equals(id)){released=true;cancelled=cancel;}}
}
