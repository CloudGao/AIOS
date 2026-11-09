package os.aios.runtime;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Versioned, engine-neutral contracts shared by every AIOS execution path. */
public final class CoreContracts {
    public static final int VERSION=1;
    private CoreContracts(){}

    public enum ExecutionState {
        WAITING_DEPENDENCY,WAITING_TRIGGER,WAITING_RESOURCE,READY,SUBMITTING,RUNNING,
        WAITING_USER,PAUSED,SUCCEEDED,FAILED,CANCELLED,OUTCOME_UNKNOWN;
        public String wire(){return name().toLowerCase(Locale.ROOT);}
        public boolean terminal(){return this==SUCCEEDED||this==FAILED||this==CANCELLED;}
    }
    public enum SideEffect {NONE,READ,IDEMPOTENT_WRITE,NON_IDEMPOTENT_WRITE}
    public enum Trust {USER,SYSTEM_OBSERVATION,MODEL_INFERENCE,EXTERNAL_UNTRUSTED}
    public enum Resource {FOREGROUND_SCREEN,VIRTUAL_SCREEN,APP_CONTROL,MICROPHONE,CAMERA,HEAVY_INFERENCE,NETWORK,BUDGET}

    public static boolean canTransition(ExecutionState from,ExecutionState to){
        if(from==to)return true;
        if(from.terminal())return false;
        if(to==ExecutionState.CANCELLED)return true;
        return switch(from){
            case WAITING_DEPENDENCY -> Set.of(ExecutionState.WAITING_TRIGGER,ExecutionState.WAITING_RESOURCE,ExecutionState.READY,ExecutionState.PAUSED,ExecutionState.FAILED).contains(to);
            case WAITING_TRIGGER -> Set.of(ExecutionState.WAITING_RESOURCE,ExecutionState.READY,ExecutionState.PAUSED,ExecutionState.FAILED).contains(to);
            case WAITING_RESOURCE -> Set.of(ExecutionState.READY,ExecutionState.PAUSED,ExecutionState.FAILED).contains(to);
            case READY -> Set.of(ExecutionState.SUBMITTING,ExecutionState.PAUSED,ExecutionState.FAILED).contains(to);
            case SUBMITTING -> Set.of(ExecutionState.RUNNING,ExecutionState.WAITING_USER,ExecutionState.SUCCEEDED,ExecutionState.FAILED,ExecutionState.OUTCOME_UNKNOWN).contains(to);
            case RUNNING -> Set.of(ExecutionState.WAITING_USER,ExecutionState.WAITING_RESOURCE,ExecutionState.PAUSED,ExecutionState.SUCCEEDED,ExecutionState.FAILED,ExecutionState.OUTCOME_UNKNOWN).contains(to);
            case WAITING_USER,PAUSED -> Set.of(ExecutionState.WAITING_RESOURCE,ExecutionState.READY,ExecutionState.RUNNING,ExecutionState.FAILED,ExecutionState.OUTCOME_UNKNOWN).contains(to);
            case OUTCOME_UNKNOWN -> Set.of(ExecutionState.WAITING_USER,ExecutionState.SUCCEEDED,ExecutionState.FAILED).contains(to);
            default -> false;
        };
    }
    public static void requireTransition(String from,String to){
        ExecutionState a=parseState(from),b=parseState(to);
        if(!canTransition(a,b))throw new IllegalStateException("Illegal execution transition: "+from+" -> "+to);
    }
    public static ExecutionState parseState(String value){return ExecutionState.valueOf(value.toUpperCase(Locale.ROOT));}

    public static String stableId(String namespace,String...parts){
        try{
            MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(namespace.getBytes(StandardCharsets.UTF_8));
            for(String part:parts){d.update((byte)0);d.update((part==null?"":part).getBytes(StandardCharsets.UTF_8));}
            byte[] b=d.digest();StringBuilder out=new StringBuilder(namespace).append(':');
            for(int i=0;i<16;i++)out.append(String.format(Locale.ROOT,"%02x",b[i]));return out.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String randomId(String namespace){return namespace+":"+UUID.randomUUID();}

    public static JSONObject capability(String id,int version,SideEffect effect,String mode,boolean queryable,boolean cancellable,JSONArray resources)throws Exception{
        if(!id.matches("[a-z][a-z0-9_.-]{2,127}")||version<1)throw new IllegalArgumentException("Invalid capability identity");
        if(!Set.of("direct","handoff","ui_workflow").contains(mode))throw new IllegalArgumentException("Invalid execution mode");
        return new JSONObject().put("contract_version",VERSION).put("id",id).put("version",version).put("side_effect",effect.name().toLowerCase(Locale.ROOT)).put("execution_mode",mode).put("queryable",queryable).put("cancellable",cancellable).put("resources",resources==null?new JSONArray():resources);
    }
    public static JSONObject source(String id,String version,Trust trust,String locator,boolean available)throws Exception{
        return new JSONObject().put("source_id",id).put("source_version",version).put("trust",trust.name().toLowerCase(Locale.ROOT)).put("locator",locator).put("available",available);
    }
}
