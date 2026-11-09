package os.aios.core.documents;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.json.*;
import os.aios.runtime.DocumentContract;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.pdmodel.interactive.form.*;

/** Runs only in an Android isolated UID. All input/output authority is an inherited FD. */
public final class DocumentEngine {
    public static JSONObject execute(Context c,String op,JSONObject p,ParcelFileDescriptor input,ParcelFileDescriptor output)throws Exception{
        String format=p.getString("format");DocumentContract.require(DocumentContract.FORMATS.contains(format),"format_unsupported");
        if(op.equals("render")){
            DocumentContract.require(format.equals("pdf")&&input!=null&&output!=null,"operation_unsupported");
            try(PdfRenderer renderer=new PdfRenderer(ParcelFileDescriptor.dup(input.getFileDescriptor()))){int page=DocumentContract.bounded(p,"page",1,1,renderer.getPageCount());try(PdfRenderer.Page sheet=renderer.openPage(page-1)){
                int edge=DocumentContract.bounded(p,"max_edge",1200,100,2048);float scale=edge/(float)Math.max(sheet.getWidth(),sheet.getHeight());Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(sheet.getWidth()*scale)),Math.max(1,Math.round(sheet.getHeight()*scale)),Bitmap.Config.ARGB_8888);try{bitmap.eraseColor(Color.WHITE);sheet.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(output.getFileDescriptor()))){DocumentContract.require(bitmap.compress(Bitmap.CompressFormat.PNG,100,out),"render_failed");}return new JSONObject().put("page",page).put("width",bitmap.getWidth()).put("height",bitmap.getHeight()).put("mime_type","image/png");}finally{bitmap.recycle();}
            }}
        }
        byte[] bytes=input==null?new byte[0]:read(input);JSONObject verification=new JSONObject();
        if(op.equals("create")){
            DocumentContract.require(!format.equals("pdf")&&!format.equals("pptx"),"operation_unsupported");
            bytes=Set.of("docx","xlsx").contains(format)?OfficeDocument.create(format,p):p.optString("text","").getBytes(StandardCharsets.UTF_8);
            units(c,bytes,format,p);write(output,bytes);return new JSONObject().put("verified",true).put("bytes",bytes.length);
        }
        if(op.equals("edit")){
            if(Set.of("docx","xlsx","pptx").contains(format)){OfficeDocument doc=new OfficeDocument(bytes,format);verification=doc.edit(p);List<JSONObject> expectedUnits=doc.units(p);bytes=doc.bytes();DocumentContract.require(new OfficeDocument(bytes,format).units(p).toString().equals(expectedUnits.toString()),"verification_failed");}
            else if(format.equals("pdf")){PDFBoxResourceLoader.init(c);try(PDDocument doc=PDDocument.load(bytes)){
                DocumentContract.require(!doc.isEncrypted()&&doc.getSignatureDictionaries().isEmpty(),"signed_or_encrypted_document");String action=p.getString("action");
                if(action.equals("pdf.rotate_page")){int page=DocumentContract.bounded(p,"page",1,1,doc.getNumberOfPages()),degrees=p.getInt("degrees");DocumentContract.require(degrees==0||degrees==90||degrees==180||degrees==270,"invalid_rotation");doc.getPage(page-1).setRotation(degrees);}
                else if(action.equals("pdf.fill_fields")){PDAcroForm form=doc.getDocumentCatalog().getAcroForm();DocumentContract.require(form!=null&&!form.hasXFA(),"form_unsupported");JSONObject values=p.getJSONObject("fields");DocumentContract.require(values.length()<=50,"edit_budget_exceeded");Iterator<String> keys=values.keys();while(keys.hasNext()){String key=keys.next();PDField field=form.getField(key);DocumentContract.require(field!=null&&!field.isReadOnly()&&(field instanceof PDTextField||field instanceof PDCheckBox),"field_unsupported");field.setValue(values.getString(key));}}
                else throw new IllegalArgumentException("operation_unsupported");
                ByteArrayOutputStream out=new ByteArrayOutputStream();doc.save(out);bytes=out.toByteArray();
                try(PDDocument check=PDDocument.load(bytes)){DocumentContract.require(check.getNumberOfPages()==doc.getNumberOfPages(),"verification_failed");if(action.equals("pdf.rotate_page"))DocumentContract.require(check.getPage(p.optInt("page",1)-1).getRotation()==p.getInt("degrees"),"verification_failed");else{Iterator<String> keys=p.getJSONObject("fields").keys();while(keys.hasNext()){String key=keys.next();DocumentContract.require(check.getDocumentCatalog().getAcroForm().getField(key).getValueAsString().equals(p.getJSONObject("fields").getString(key)),"verification_failed");}}}
                verification.put("verified",true).put("visual_fidelity_verified",false);
            }}
            else{String value=decode(bytes),action=p.getString("action");boolean bom=value.startsWith("\ufeff");String body=bom?value.substring(1):value;
                if(action.equals("text.replace_range")&&Set.of("txt","md").contains(format)){int start=p.getInt("start"),end=p.getInt("end");DocumentContract.require(start>=0&&end>=start&&end<=body.length()&&!splitSurrogate(body,start)&&!splitSurrogate(body,end),"invalid_range");DocumentContract.require(body.substring(start,end).equals(p.getString("expected")),"source_changed");value=(bom?"\ufeff":"")+body.substring(0,start)+p.getString("text")+body.substring(end);}
                else if(action.equals("table.set_cell")&&Set.of("csv","tsv").contains(format)){char delimiter=format.equals("csv")?',':'\t';List<List<String>> table=table(body,delimiter);int row=p.getInt("row")-1,column=p.getInt("column")-1;DocumentContract.require(row>=0&&row<table.size()&&column>=0&&column<table.get(row).size(),"invalid_range");table.get(row).set(column,p.getString("value"));value=(bom?"\ufeff":"")+encodeTable(table,delimiter,body.contains("\r\n")?"\r\n":"\n",body.endsWith("\n"));verification.put("serialization","csv_quotes_normalized");}
                else throw new IllegalArgumentException("operation_unsupported");bytes=value.getBytes(StandardCharsets.UTF_8);DocumentContract.require(decode(bytes).equals(value),"verification_failed");verification.put("verified",true);
            }
            write(output,bytes);return verification.put("bytes",bytes.length);
        }
        List<JSONObject> units=units(c,bytes,format,p);
        if(op.equals("inspect")){JSONObject info=new JSONObject().put("format",format).put("units",units.size()).put("bytes",bytes.length).put("locator_base",1).put("character_offsets","utf16_zero_based");if(format.equals("xlsx"))info.put("sheets",new JSONArray(new OfficeDocument(bytes,format).sheets().keySet()));
            if(format.equals("pdf")){PDFBoxResourceLoader.init(c);try(PDDocument doc=PDDocument.load(bytes)){info.put("pages",doc.getNumberOfPages()).put("signed",!doc.getSignatureDictionaries().isEmpty());JSONArray fields=new JSONArray();PDAcroForm form=doc.getDocumentCatalog().getAcroForm();if(form!=null){for(PDField field:form.getFieldTree()){if(fields.length()>=100)break;fields.put(new JSONObject().put("name",field.getFullyQualifiedName()).put("type",field.getFieldType()).put("read_only",field.isReadOnly()).put("value",field.getValueAsString()));}}info.put("fields",fields);}}
            return info;}
        DocumentContract.require(op.equals("read")||op.equals("find"),"operation_unsupported");
        int index=DocumentContract.bounded(p,"index",0,0,units.size()),offset=DocumentContract.bounded(p,"offset",0,0,4000000),budget=DocumentContract.bounded(p,"max_chars",DocumentContract.MAX_TEXT,256,DocumentContract.MAX_TEXT),limit=DocumentContract.bounded(p,"limit",50,1,100);
        JSONArray result=new JSONArray();int used=0,visited=0;String query=p.optString("query","");if(op.equals("find"))DocumentContract.require(!query.isEmpty()&&query.length()<=1000,"invalid_query");
        while(index<units.size()&&result.length()<limit&&used<budget){JSONObject unit=units.get(index);String value=unit.getString("text");DocumentContract.require(offset<=value.length(),"invalid_range");
            if(op.equals("read")){int take=Math.min(value.length()-offset,Math.max(0,budget-used-300));if(take==0&&!value.isEmpty()){DocumentContract.require(result.length()>0,"result_budget_too_small");break;}if(splitSurrogate(value,offset+take))take--;if(take==0&&!value.isEmpty())break;JSONObject item=new JSONObject(unit.toString()).put("text",value.substring(offset,offset+take)).put("char_start",offset).put("char_end",offset+take);result.put(item);used+=take+300;offset+=take;if(offset==value.length()){index++;offset=0;}else break;}
            else{int match=p.optBoolean("case_sensitive",true)?value.indexOf(query,offset):findIgnoreCase(value,query,offset);if(match<0){index++;offset=0;visited++;continue;}int start=Math.max(0,match-100),end=Math.min(value.length(),match+query.length()+100);JSONObject hit=new JSONObject().put("locator",unit.getJSONObject("locator")).put("char_start",match).put("char_end",match+query.length()).put("text",value.substring(start,end));if(used+hit.toString().length()>budget){DocumentContract.require(result.length()>0,"result_budget_too_small");break;}result.put(hit);used+=hit.toString().length();offset=match+query.length();}
        }
        return new JSONObject().put("items",result).put("unit_count",units.size()).put("has_more",index<units.size()).put("next",index<units.size()?new JSONObject().put("index",index).put("offset",offset):JSONObject.NULL).put("text_state",format.equals("pdf")&&units.stream().allMatch(u->u.optString("text").isBlank())?"needs_ocr":"available").put("matching",op.equals("find")?"literal_within_unit":"not_applicable");
    }
    static int findIgnoreCase(String value,String q,int start){for(int i=start;i<=value.length()-q.length();i++)if(value.regionMatches(true,i,q,0,q.length()))return i;return -1;}
    static boolean splitSurrogate(String s,int p){return p>0&&p<s.length()&&Character.isHighSurrogate(s.charAt(p-1))&&Character.isLowSurrogate(s.charAt(p));}
    static List<JSONObject> units(Context c,byte[] bytes,String format,JSONObject p)throws Exception{
        if(Set.of("docx","xlsx","pptx").contains(format))return new OfficeDocument(bytes,format).units(p);
        List<JSONObject> out=new ArrayList<>();
        if(format.equals("pdf")){PDFBoxResourceLoader.init(c);try(PDDocument doc=PDDocument.load(bytes)){DocumentContract.require(!doc.isEncrypted(),"password_required");DocumentContract.require(doc.getNumberOfPages()<=2000,"page_limit");int from=DocumentContract.bounded(p,"from_page",1,1,Math.max(1,doc.getNumberOfPages())),to=DocumentContract.bounded(p,"to_page",doc.getNumberOfPages(),from,doc.getNumberOfPages()),chars=0;PDFTextStripper stripper=new PDFTextStripper();for(int i=from;i<=to;i++){stripper.setStartPage(i);stripper.setEndPage(i);String text=stripper.getText(doc);chars+=text.length();DocumentContract.require(chars<=4000000,"document_complexity_limit");out.add(OfficeDocument.unit(text,new JSONObject().put("page",i)));}}return out;}
        String text=decode(bytes);if(text.startsWith("\ufeff"))text=text.substring(1);
        if(format.equals("csv")||format.equals("tsv")){List<List<String>> rows=table(text,format.equals("csv")?',':'\t');for(int i=0;i<rows.size();i++)for(int j=0;j<rows.get(i).size();j++)out.add(OfficeDocument.unit(rows.get(i).get(j),new JSONObject().put("row",i+1).put("column",j+1)));}
        else{String[] lines=text.split("(?<=\n)",-1);int start=0;for(int i=0;i<lines.length;i++){out.add(OfficeDocument.unit(lines[i],new JSONObject().put("line",i+1).put("start",start)));start+=lines[i].length();}}
        DocumentContract.require(out.size()<=100000,"document_complexity_limit");return out;
    }
    static String decode(byte[] bytes)throws Exception{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();DocumentContract.require(text.indexOf(0)<0&&text.length()<=4000000,"encoding_or_size_unsupported");return text;}
    static byte[] read(ParcelFileDescriptor fd)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){DocumentContract.require(out.size()+n<=DocumentContract.MAX_BYTES,"source_too_large");out.write(b,0,n);}return out.toByteArray();}}
    static void write(ParcelFileDescriptor fd,byte[] bytes)throws Exception{DocumentContract.require(fd!=null&&bytes.length<=DocumentContract.MAX_BYTES,"output_size_limit");try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()))){out.write(bytes);out.flush();}}
    static List<List<String>> table(String text,char delimiter){List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quoted=false,closed=false;for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(quoted){if(ch=='"'){if(i+1<text.length()&&text.charAt(i+1)=='"'){value.append('"');i++;}else{quoted=false;closed=true;}}else value.append(ch);}else if(ch=='"'){DocumentContract.require(value.length()==0&&!closed,"invalid_csv");quoted=true;}else if(ch==delimiter){row.add(value.toString());value.setLength(0);closed=false;}else if(ch=='\n'||ch=='\r'){if(ch=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(value.toString());rows.add(row);row=new ArrayList<>();value.setLength(0);closed=false;}else{DocumentContract.require(!closed,"invalid_csv");value.append(ch);}DocumentContract.require(rows.size()<=100000&&row.size()<=16384,"table_size_limit");}DocumentContract.require(!quoted,"invalid_csv");if(value.length()>0||!row.isEmpty()||closed||rows.isEmpty()){row.add(value.toString());rows.add(row);}return rows;}
    static String encodeTable(List<List<String>> rows,char delimiter,String newline,boolean trailing){StringBuilder out=new StringBuilder();for(int i=0;i<rows.size();i++){if(i>0)out.append(newline);List<String> row=rows.get(i);for(int j=0;j<row.size();j++){if(j>0)out.append(delimiter);String v=row.get(j);if(v.indexOf(delimiter)>=0||v.contains("\"")||v.contains("\n")||v.contains("\r"))out.append('"').append(v.replace("\"","\"\"")).append('"');else out.append(v);}}if(trailing)out.append(newline);return out.toString();}
}
