package os.aios.agent;
import android.app.Instrumentation;import android.os.Bundle;
public class DreamAcceptance extends Instrumentation{
 public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 public void onStart(){Bundle result=new Bundle();try{int n=os.aios.workspace.DreamDeviceCases.run(getTargetContext());result.putString("stream","PASS: "+n+" actual Android SQLite/Workspace Dream assertions in isolated test DB; user DB untouched\n");finish(-1,result);}catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
}
