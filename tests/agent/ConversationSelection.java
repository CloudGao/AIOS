package os.aios.agent;
import android.app.*;import android.os.*;
/** Acceptance-only restoration of the original selected conversation; never sends a turn. */
public final class ConversationSelection extends Instrumentation{
 private String thread;
 public void onCreate(Bundle p){super.onCreate(p);thread=p.getString("thread");start();}
 public void onStart(){Bundle result=new Bundle();try{AssistantConversation.selectThread(getTargetContext(),thread);result.putString("result","PASS selected existing conversation without sending");finish(Activity.RESULT_OK,result);}catch(Exception e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
