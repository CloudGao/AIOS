package os.aios.core.workspace;
import org.json.*;import android.database.sqlite.SQLiteDatabase;
/** Explicit boundary adapter; real memory persistence is covered by Android compilation/tests. */
class WorkspaceMemory{static int writes;WorkspaceMemory(WorkspaceStore s,SQLiteDatabase d){}JSONObject call(String op,JSONObject p)throws Exception{writes++;if(!op.equals("memory.create")&&!op.equals("memory.update"))throw new AssertionError("Unexpected automatic memory write");return new JSONObject().put("id","memory-test").put("state","active");}}
