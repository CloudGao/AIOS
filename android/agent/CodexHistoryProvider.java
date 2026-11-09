package os.aios.agent;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.Bundle;import org.json.*;
/** Signature-protected, cursor-only access to older native Codex history pages. */
public final class CodexHistoryProvider extends ContentProvider{
 private CodexAccountClient client;private NativeThreadPager pager;
 public boolean onCreate(){client=new CodexAccountClient(getContext());pager=new NativeThreadPager((m,p)->client.call(new JSONObject().put("op","rpc").put("method",m).put("params",p)));return true;}
 @Override public Bundle call(String method,String arg,Bundle extras){Bundle out=new Bundle();try{
  String thread=extras.getString("thread_id","");if(thread.isEmpty())throw new IllegalArgumentException("thread_id required");
  if(!client.call(new JSONObject().put("op","status")).optBoolean("running"))client.call(new JSONObject().put("op","start").put("mode","live"));
  JSONObject page;
  if(method.equals("latest")){page=pager.recent(thread,NativeThreadPager.TURN_PAGE);page.put("next_cursor",page.getJSONObject("history_page").opt("next_cursor"));}
  else if(method.equals("older_turns"))page=pager.olderTurns(thread,extras.getString("cursor",null));
  else if(method.equals("older_items"))page=pager.olderItems(thread,extras.getString("turn_id",""),extras.getString("cursor",null));
  else throw new IllegalArgumentException("Unknown history operation");
  JSONArray turns=page.optJSONArray("turns");if(turns!=null)for(int i=0;i<turns.length();i++){JSONObject turn=turns.getJSONObject(i);if(turn.optBoolean("items_has_older"))turn.put("leading_user_item",pager.leadingUser(thread,turn.getString("id")));}
  // Raw history can exceed Binder's transaction limit. The FD retains the unlinked file until read.
  java.io.File file=java.io.File.createTempFile("history-",".json",getContext().getCacheDir());
  try{try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)){stream.write(page.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   out.putParcelable("result_fd",android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY));
  }finally{file.delete();}
 }catch(Exception e){out.putString("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));}return out;}

 public String getType(Uri u){return "application/json";}public Cursor query(Uri u,String[]p,String s,String[]a,String o){throw new UnsupportedOperationException();}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
}
