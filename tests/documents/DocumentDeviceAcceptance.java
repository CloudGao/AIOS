package os.aios.agent;

import android.app.Instrumentation;
import android.content.Context;
import android.os.*;
import org.json.*;
import os.aios.runtime.*;
import java.io.*;
import java.util.*;

/** Real Agent UID -> Core -> isolated worker -> storage adapter integration; no existing files edited. */
public final class DocumentDeviceAcceptance extends Instrumentation {
    private CoreRuntimeClient core;private int checks;private String directory,owner="document-acceptance-"+UUID.randomUUID();private JSONArray failures=new JSONArray();private Set<String> openHandles=new HashSet<>();
    public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private JSONObject call(String action,JSONObject p)throws Exception{return core.call("documents",action,p.put("_caller_thread",owner));}
    private JSONObject await(String action,JSONObject p)throws Exception{
        JSONObject reply=call(action,p);long until=SystemClock.elapsedRealtime()+90000;while(reply.optString("state").equals("running")||reply.optString("state").equals("queued")||reply.optString("state").equals("committing")){Thread.sleep(150);reply=call("status",new JSONObject().put("job_id",reply.getString("job_id")));if(SystemClock.elapsedRealtime()>until)throw new AssertionError("job timeout: "+action);}
        if(reply.has("result"))reply=reply.getJSONObject("result");check(reply.optString("state").equals("succeeded"),action+": "+reply);JSONObject data=reply.getJSONObject("data");if(data.has("document_id"))openHandles.add(data.getString("document_id"));return data;
    }
    private JSONObject create(String format,String text)throws Exception{return await("create",new JSONObject().put("format",format).put("text",text));}
    private JSONObject save(JSONObject artifact,String name)throws Exception{return await("save",new JSONObject().put("document_id",artifact.getString("document_id")).put("source_version",artifact.getString("source_version")).put("target",directory+"/"+name).put("request_id",owner+":"+name));}
    private JSONObject read(JSONObject h)throws Exception{return await("read",new JSONObject().put("document_id",h.getString("document_id")));}
    private void close(JSONObject h)throws Exception{call("close",new JSONObject().put("document_id",h.getString("document_id")));}
    interface Test {void run()throws Exception;}
    private void test(String name,Test test){try{test.run();}catch(Throwable e){try{failures.put(new JSONObject().put("name",name).put("error",e.toString()));}catch(Exception ignored){}}finally{for(String id:openHandles)try{call("close",new JSONObject().put("document_id",id));}catch(Exception ignored){}openHandles.clear();}}
    public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();core=new CoreRuntimeClient(context);File folder=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),owner);check(folder.mkdir(),"create isolated fixture directory");directory=folder.getPath();
        test("capabilities",()->check(call("capabilities",new JSONObject()).getJSONObject("data").getJSONArray("formats").length()==8,"format count"));
        test("text edit/save/conflict",()->{
            JSONObject h=create("txt","Hello 世界\r\nsecond line\r\n");check(read(h).getJSONArray("items").getJSONObject(0).getString("text").equals("Hello 世界\r\n"),"CRLF retained");
            JSONObject changed=await("edit",new JSONObject().put("document_id",h.getString("document_id")).put("source_version",h.getString("source_version")).put("action","text.replace_range").put("start",6).put("end",8).put("expected","世界").put("text","AIOS"));
            JSONObject saved=save(changed,"note.txt");check(saved.getBoolean("readback_verified"),"save readback");check(saved.optString("event_state").equals("published"),"save event published");
            JSONObject reopened=await("open",new JSONObject().put("source",saved.getString("source")));check(read(reopened).getJSONArray("items").getJSONObject(0).getString("text").equals("Hello AIOS\r\n"),"output contents");
            JSONObject found=await("find",new JSONObject().put("document_id",reopened.getString("document_id")).put("query","AIOS"));check(found.getJSONArray("items").length()==1,"literal match");
            JSONObject receipt=call("status",new JSONObject().put("request_id",owner+":note.txt"));check(receipt.getString("state").equals("succeeded"),"durable save receipt");
            JSONObject denied=core.call("documents","read",new JSONObject().put("document_id",h.getString("document_id")).put("_caller_thread",owner+"-other"));String deniedJob=denied.getString("job_id");for(int n=0;n<100&&Set.of("running","queued").contains(denied.optString("state"));n++){Thread.sleep(100);denied=core.call("documents","status",new JSONObject().put("job_id",deniedJob).put("_caller_thread",owner+"-other"));}check(denied.optString("state").equals("failed"),"cross-owner handle denied");
            close(h);close(changed);close(reopened);
        });
        test("docx",()->{JSONObject h=create("docx","合同 世界");JSONObject edit=await("edit",new JSONObject().put("document_id",h.getString("document_id")).put("source_version",h.getString("source_version")).put("action","word.replace_text").put("paragraph",1).put("old","世界").put("replacement","AIOS"));check(read(edit).getJSONArray("items").getJSONObject(0).getString("text").equals("合同 AIOS"),"word paragraph edit");save(edit,"contract.docx");close(h);close(edit);});
        test("xlsx",()->{JSONObject h=await("create",new JSONObject().put("format","xlsx").put("sheet","预算").put("cells",new JSONArray().put(new JSONObject().put("cell","A1").put("value","成本"))));JSONObject edit=await("edit",new JSONObject().put("document_id",h.getString("document_id")).put("source_version",h.getString("source_version")).put("action","sheet.set_cells").put("sheet","预算").put("cells",new JSONArray().put(new JSONObject().put("cell","B2").put("type","number").put("value","12.5"))));check(read(edit).getJSONArray("items").length()==2,"spreadsheet cells");save(edit,"budget.xlsx");close(h);close(edit);});
        test("csv",()->{JSONObject h=create("csv","name,description\r\nitem,\"line1\nline2\"\r\n");check(read(h).getJSONArray("items").getJSONObject(3).getString("text").equals("line1\nline2"),"quoted multiline CSV");JSONObject e=await("edit",new JSONObject().put("document_id",h.getString("document_id")).put("source_version",h.getString("source_version")).put("action","table.set_cell").put("row",2).put("column",1).put("value","a,b"));save(e,"data.csv");check(read(e).getJSONArray("items").getJSONObject(2).getString("text").equals("a,b"),"CSV escaped edit");close(h);close(e);});
        test("pdf",()->{File source=new File(directory,"source.pdf");android.graphics.pdf.PdfDocument pdf=new android.graphics.pdf.PdfDocument();try{android.graphics.pdf.PdfDocument.Page page=pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(300,400,1).create());android.graphics.Paint paint=new android.graphics.Paint();paint.setTextSize(18);page.getCanvas().drawText("AIOS PDF verification",20,60,paint);pdf.finishPage(page);try(OutputStream out=new FileOutputStream(source)){pdf.writeTo(out);}}finally{pdf.close();}
            JSONObject h=await("open",new JSONObject().put("source",source.getPath()));JSONObject found=await("find",new JSONObject().put("document_id",h.getString("document_id")).put("query","verification"));check(found.getJSONArray("items").length()==1,"PDF text extraction");JSONObject image=await("render",new JSONObject().put("document_id",h.getString("document_id")).put("page",1));save(image,"page.png");JSONObject rotated=await("edit",new JSONObject().put("document_id",h.getString("document_id")).put("source_version",h.getString("source_version")).put("action","pdf.rotate_page").put("page",1).put("degrees",90));save(rotated,"rotated.pdf");close(h);close(image);close(rotated);
        });
        test("search metadata",()->{Thread.sleep(1200);JSONObject p=new JSONObject().put("version",2).put("sources",new JSONArray().put("files")).put("filters",new JSONObject().put("file",new JSONObject().put("directory","Download/"+owner+"/"))).put("_caller_thread",owner);JSONObject data=core.call("workspace","recall.query",p).getJSONObject("data");check(data.getJSONArray("items").length()>=4,"files indexed");});
        test("search content",()->{JSONObject p=new JSONObject().put("version",2).put("mode","content").put("sources",new JSONArray().put("files")).put("query","AIOS").put("filters",new JSONObject().put("file",new JSONObject().put("directory","Download/"+owner+"/"))).put("_caller_thread",owner);int hits=0,pages=0;for(;;){JSONObject d=core.call("workspace","recall.query",p).getJSONObject("data");hits+=d.getJSONArray("items").length();if(!d.getBoolean("has_more"))break;check(++pages<10,"bounded continuation");p=new JSONObject().put("version",2).put("cursor",d.getString("next_cursor")).put("_caller_thread",owner);}check(hits>=2,"name-independent text recall");});
        result.putString("result",new JSONObject().put("checks",checks).put("failures",failures).put("fixture_directory",directory).toString());finish(failures.length()==0?-1:0,result);
    }catch(Throwable e){result.putString("error",e.toString());finish(0,result);}}
}
