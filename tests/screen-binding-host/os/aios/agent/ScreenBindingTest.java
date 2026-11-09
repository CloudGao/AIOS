package os.aios.agent;
import android.content.*;import org.json.*;import java.util.*;
public class ScreenBindingTest {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 interface Action {void run()throws Exception;}static void rejects(Action a)throws Exception {boolean rejected=false;try{a.run();}catch(IllegalStateException e){rejected=true;}check(rejected);}
 public static void main(String[] args)throws Exception {
 Context c=new Context();JSONObject original=AssistantConversation.fresh(c);String a=original.getString("conversation_id");
 c.getSharedPreferences("assistant_codex_threads",0).edit().putString(a,"thread-a").commit();
 JSONObject request=AssistantConversation.enqueue(c,"原任务上下文");
 AssistantConversation.attachUiSession(c,"screen-1","thread-a");AssistantConversation.attachUiSession(c,"screen-2","thread-a");
 check(AssistantConversation.screens(c,a).length()==2);
 check(AssistantConversation.bindUiSession(c,"screen-1").getString("request_id").equals(request.getString("request_id")));
 check(AssistantConversation.state(c).getJSONArray("history").getJSONObject(0).getString("text").equals("原任务上下文"));
 rejects(()->AssistantConversation.fresh(c));rejects(()->AssistantConversation.selectThread(c,"thread-a"));
 AssistantConversation.unbindUiSession(c,"screen-1");check(AssistantConversation.state(c).getString("status").equals("queued"));
 AssistantConversation.cancel(c);JSONObject second=AssistantConversation.fresh(c);String b=second.getString("conversation_id");
 c.getSharedPreferences("assistant_codex_threads",0).edit().putString(b,"thread-b").commit();
 AssistantConversation.attachUiSession(c,"screen-b","thread-b");
 rejects(()->AssistantConversation.attachUiSession(c,"screen-1","thread-b"));check(AssistantConversation.uiOwner(c,"screen-1").getString("thread_id").equals("thread-a"));
 AssistantConversation.bindUiSession(c,"screen-2");check(AssistantConversation.state(c).getString("conversation_id").equals(a));check(AssistantConversation.state(c).getJSONArray("history").length()==1);
 AssistantConversation.releaseUiSession(c,"screen-2");check(AssistantConversation.activeUiSession(c).isEmpty());check(AssistantConversation.screens(c,a).length()==1);check(AssistantConversation.uiOwner(c,"screen-2").length()==0);
 rejects(()->AssistantConversation.bindUiSession(c,"screen-2"));
 AssistantConversation.attachUiSession(c,"screen-new","thread-a");check(AssistantConversation.bindUiSession(c,"screen-new").getString("conversation_id").equals(a));
 check(AssistantConversation.screens(c,b).length()==1);
 rejects(()->AssistantConversation.attachUiSession(c,"unknown","missing-thread"));
 c.getSharedPreferences("ui_session_legacy",0).edit().putString("assistant_state",original.toString()).commit();rejects(()->AssistantConversation.bindUiSession(c,"legacy"));
 AssistantConversation.reconcileUiSessions(c,Set.of("screen-new","screen-b"));check(AssistantConversation.screens(c,a).length()==1);check(AssistantConversation.activeUiSession(c).isEmpty());
 AssistantConversation.bindUiSession(c,"screen-new");AssistantConversation.unbindUiSession(c,"screen-1");check(AssistantConversation.activeUiSession(c).equals("screen-new"));
 AssistantConversation.releaseUiSession(c,"screen-new");check(AssistantConversation.state(c).getJSONArray("history").length()==1);check(AssistantConversation.screens(c,a).length()==0);
 System.out.println("Screen binding: "+checks+" assertions passed");
 }
}