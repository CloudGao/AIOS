package android.database.sqlite;
import org.json.*;import java.io.*;import android.content.ContentValues;
public class SQLiteDatabase implements AutoCloseable{
 public static final int CONFLICT_REPLACE=5;
 final Process process;final BufferedWriter input;final BufferedReader output;
 public SQLiteDatabase(String script)throws Exception{process=new ProcessBuilder("python3",script).start();input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));output=new BufferedReader(new InputStreamReader(process.getInputStream()));}
 synchronized JSONObject run(String sql,Object[] args){try{JSONObject q=new JSONObject().put("sql",sql).put("args",new JSONArray(args==null?new Object[0]:args));input.write(q.toString());input.newLine();input.flush();String line=output.readLine();if(line==null)throw new IllegalStateException("SQLite bridge exited");JSONObject r=new JSONObject(line);if(r.has("error"))throw new IllegalArgumentException(r.getString("error"));return r;}catch(IOException|JSONException e){throw new IllegalStateException(e);}}
 public void execSQL(String s){run(s,null);}public void execSQL(String s,Object[] a){run(s,a);}
 public JSONArray rows(String sql,String[] a){return run(sql,a).optJSONArray("rows");}
 public long insertOrThrow(String table,String ignored,ContentValues v){String columns=String.join(",",v.keySet()),qs=String.join(",",java.util.Collections.nCopies(v.size(),"?"));run("INSERT INTO "+table+"("+columns+") VALUES("+qs+")",v.values().toArray());return 1;}
 public long insertWithOnConflict(String table,String ignored,ContentValues v,int conflict){String columns=String.join(",",v.keySet()),qs=String.join(",",java.util.Collections.nCopies(v.size(),"?"));run((conflict==CONFLICT_REPLACE?"INSERT OR REPLACE":"INSERT")+" INTO "+table+"("+columns+") VALUES("+qs+")",v.values().toArray());return 1;}
 public int update(String table,ContentValues v,String where,String[] args){java.util.ArrayList<Object> values=new java.util.ArrayList<>(v.values());java.util.Collections.addAll(values,args);String sets=String.join(",",v.keySet().stream().map(k->k+"=?").toList());return run("UPDATE "+table+" SET "+sets+" WHERE "+where,values.toArray()).optInt("changes");}
 public void close(){process.destroy();}
}
