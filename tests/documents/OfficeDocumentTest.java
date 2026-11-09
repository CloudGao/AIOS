package os.aios.core.documents;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import os.aios.runtime.DocumentContract;

public final class OfficeDocumentTest {
    static int passed;
    interface Check {void run()throws Exception;}
    static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);passed++;}
    static void rejects(Check c)throws Exception{try{c.run();}catch(Exception expected){passed++;return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception{
        byte[] docx=OfficeDocument.create("docx",new JSONObject().put("paragraphs",new JSONArray().put("Hello 世界").put("keep me")));
        OfficeDocument doc=new OfficeDocument(docx,"docx");eq(doc.units(new JSONObject()).get(0).getString("text"),"Hello 世界");
        byte[] rel=doc.entries.get("_rels/.rels");doc.edit(new JSONObject().put("action","word.replace_text").put("paragraph",1).put("old","世界").put("replacement","العالم"));
        OfficeDocument changed=new OfficeDocument(doc.bytes(),"docx");eq(changed.units(new JSONObject()).get(0).getString("text"),"Hello العالم");eq(changed.units(new JSONObject()).get(1).getString("text"),"keep me");eq(Arrays.equals(rel,changed.entries.get("_rels/.rels")),true);
        String xml=new String(doc.entries.get("word/document.xml"),StandardCharsets.UTF_8);xml=xml.replace("Hello العالم","Hel</w:t></w:r><w:r><w:t>lo 世界");doc.entries.put("word/document.xml",xml.getBytes(StandardCharsets.UTF_8));
        doc.edit(new JSONObject().put("action","word.replace_text").put("paragraph",1).put("old","llo 世").put("replacement","X"));eq(doc.units(new JSONObject()).get(0).getString("text"),"HeX界");
        rejects(()->doc.edit(new JSONObject().put("action","word.replace_text").put("paragraph",1).put("old","absent").put("replacement","bad")));
        doc.entries.put("_xmlsignatures/sig1.xml",new byte[]{1});rejects(()->doc.edit(new JSONObject().put("action","word.replace_text")));
        byte[] xlsx=OfficeDocument.create("xlsx",new JSONObject().put("sheet","预算").put("cells",new JSONArray().put(new JSONObject().put("cell","A1").put("value","Hello"))));
        OfficeDocument book=new OfficeDocument(xlsx,"xlsx");eq(book.sheets().containsKey("预算"),true);eq(book.units(new JSONObject()).get(0).getString("text"),"Hello");
        book.edit(new JSONObject().put("action","sheet.set_cells").put("sheet","预算").put("cells",new JSONArray().put(new JSONObject().put("cell","B3").put("type","formula").put("value","SUM(A1:A2)")).put(new JSONObject().put("cell","A2").put("type","number").put("value","2.5"))));
        OfficeDocument roundtrip=new OfficeDocument(book.bytes(),"xlsx");List<JSONObject> cells=roundtrip.units(new JSONObject().put("sheet","预算").put("range","A2:B3"));eq(cells.size(),2);eq(cells.get(0).getString("text"),"2.5");eq(cells.get(1).getString("formula"),"SUM(A1:A2)");eq(cells.get(1).getBoolean("formula_recalculated"),false);
        rejects(()->OfficeDocument.cell("XFE1"));rejects(()->OfficeDocument.cell("A0"));rejects(()->OfficeDocument.range("B3:A1"));
        rejects(()->OfficeDocument.parse("<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><x>&x;</x>".getBytes(StandardCharsets.UTF_8)));
        rejects(()->OfficeDocument.parse("<!DOCTYPE x><x/>".getBytes(StandardCharsets.UTF_16)));
        rejects(()->new OfficeDocument(new byte[]{0,1,2},"docx"));
        rejects(()->DocumentContract.validate("save",new JSONObject().put("target","/tmp/a").put("pretend_safe",true)));
        eq(DocumentContract.canonical(new JSONObject().put("b",1).put("a",2)),DocumentContract.canonical(new JSONObject().put("a",2).put("b",1)));
        eq(DocumentContract.format("report.DOCX"),"docx");rejects(()->DocumentContract.format("report.exe"));
        System.out.println("PASS "+passed+" document host assertions");
    }
}
