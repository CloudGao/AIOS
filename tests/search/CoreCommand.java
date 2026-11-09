package os.aios.search.acceptance;
import android.app.Instrumentation;import android.os.Bundle;import android.net.Uri;import java.nio.charset.StandardCharsets;
/** Signature-authorized acceptance caller, running in its own process. */
public final class CoreCommand extends Instrumentation {
 private Bundle options;public void onCreate(Bundle b){super.onCreate(b);options=b;start();}
 public void onStart(){Bundle result=new Bundle();try{Bundle args=new Bundle();args.putInt("protocol_version",1);args.putString("arguments",new String(android.util.Base64.decode(options.getString("parameters"),android.util.Base64.DEFAULT),StandardCharsets.UTF_8));Bundle reply=getContext().getContentResolver().call(Uri.parse("content://os.aios.core.runtime"),options.getString("domain"),options.getString("action"),args);if(reply.containsKey("error"))throw new IllegalStateException(reply.getString("error"));result.putString("result",reply.getString("result"));finish(-1,result);}catch(Exception e){result.putString("error",e.toString());finish(1,result);}}
}
