package os.aios.runtime;

import java.util.*;
import org.json.*;

/** Documents v1: bounded operations, source versions and explicit save-as semantics. */
public final class DocumentContract {
    public static final int VERSION=1, MAX_BYTES=32*1024*1024, MAX_TEXT=16000;
    public static final Set<String> ACTIONS=Set.of("capabilities","open","inspect","read","find","render","create","edit","save","status","cancel","close");
    public static final Set<String> FORMATS=Set.of("txt","md","csv","tsv","docx","xlsx","pptx","pdf");
    private DocumentContract(){}
    public static JSONObject capabilities() throws JSONException {
        JSONArray formats=new JSONArray();
        for(String f:List.of("txt","md","csv","tsv","docx","xlsx","pptx","pdf")) {
            List<String> edits=switch(f){case "txt","md"->List.of("text.replace_range");case "csv","tsv"->List.of("table.set_cell");case "docx"->List.of("word.replace_text");case "xlsx"->List.of("sheet.set_cells");case "pdf"->List.of("pdf.fill_fields","pdf.rotate_page");default->List.of();};
            formats.put(new JSONObject().put("format",f).put("read",true).put("find",true).put("create",!f.equals("pptx")&&!f.equals("pdf")).put("edit_actions",new JSONArray(edits)).put("render",f.equals("pdf")));
        }
        return new JSONObject().put("version",VERSION).put("formats",formats).put("max_open_snapshots",4).put("max_source_bytes",MAX_BYTES).put("max_output_chars",MAX_TEXT).put("save_modes",new JSONArray(List.of("create_new"))).put("overwrite",false).put("formula_recalculation",false).put("ocr",false).put("fulltext_index",false).put("engine","aios-bounded-ooxml-1/pdfbox-android-2.0.27.0");
    }
    public static void require(boolean condition,String code){if(!condition)throw new IllegalArgumentException(code);}
    public static String format(String name){int at=name.lastIndexOf('.');String f=(at<0?name:name.substring(at+1)).toLowerCase(Locale.ROOT);require(FORMATS.contains(f),"format_unsupported");return f;}
    public static int bounded(JSONObject p,String key,int fallback,int min,int max)throws JSONException{int n=p.optInt(key,fallback);require(n>=min&&n<=max,"invalid_range");return n;}
    public static JSONObject success(JSONObject data)throws JSONException{return new JSONObject().put("state","succeeded").put("data",data);}
    public static void validate(String action,JSONObject p)throws JSONException{
        Set<String> allowed=new HashSet<>(List.of("_caller_thread"));
        switch(action){
            case "capabilities":break;
            case "open":allowed.addAll(List.of("source","name","format"));break;
            case "status":allowed.addAll(List.of("job_id","request_id"));break;
            case "cancel":allowed.add("job_id");break;
            case "close":allowed.add("document_id");break;
            case "save":allowed.addAll(List.of("document_id","source_version","target","overwrite","request_id"));break;
            case "create":allowed.addAll(List.of("format","text","paragraphs","sheet","cells"));break;
            case "edit":allowed.addAll(List.of("document_id","source_version","action","start","end","expected","text","row","column","value","paragraph","old","replacement","sheet","cells","fields","page","degrees"));break;
            case "render":allowed.addAll(List.of("document_id","page","max_edge"));break;
            case "inspect":case "read":case "find":allowed.addAll(List.of("document_id","index","offset","max_chars","limit","query","case_sensitive","sheet","range","from_page","to_page"));break;
            default:throw new IllegalArgumentException("operation_unsupported");
        }
        Iterator<String> keys=p.keys();while(keys.hasNext())require(allowed.contains(keys.next()),"unsupported_field");
        require(p.toString().length()<=60000,"request_budget_exceeded");
    }
    public static String canonical(Object value)throws JSONException{if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();o.keys().forEachRemaining(keys::add);Collections.sort(keys);StringJoiner out=new StringJoiner(",","{","}");for(String k:keys)out.add(JSONObject.quote(k)+":"+canonical(o.get(k)));return out.toString();}if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringJoiner out=new StringJoiner(",","[","]");for(int i=0;i<a.length();i++)out.add(canonical(a.get(i)));return out.toString();}return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);}
}
