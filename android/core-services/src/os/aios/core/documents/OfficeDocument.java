package os.aios.core.documents;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.json.*;
import os.aios.runtime.DocumentContract;

/** Bounded OOXML package edits. Unchanged ZIP entries retain their exact uncompressed bytes. */
final class OfficeDocument {
    static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main",S="http://schemas.openxmlformats.org/spreadsheetml/2006/main",R="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    final LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();
    final String format;
    OfficeDocument(byte[] bytes,String format)throws Exception{
        this.format=format;int total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=zip.getNextEntry())!=null){
            String name=e.getName();DocumentContract.require(!name.startsWith("/")&&!name.contains("..")&&!entries.containsKey(name)&&entries.size()<2048,"invalid_package");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=zip.read(b))!=-1){total+=n;DocumentContract.require(total<=64*1024*1024&&out.size()+n<=16*1024*1024,"expanded_size_limit");out.write(b,0,n);}entries.put(name,out.toByteArray());
        }}
        DocumentContract.require(entries.containsKey("[Content_Types].xml"),"invalid_package");
        xml("[Content_Types].xml");
    }
    Document xml(String name)throws Exception{byte[] data=entries.get(name);DocumentContract.require(data!=null,"invalid_package");return parse(data);}
    static Document parse(byte[] data)throws Exception{
        String guard=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(data)).toString();DocumentContract.require(guard.indexOf(0)<0&&!guard.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")&&!guard.toUpperCase(Locale.ROOT).contains("<!ENTITY"),"external_entity_denied");
        java.util.regex.Matcher encoding=java.util.regex.Pattern.compile("encoding\\s*=\\s*['\"]([^'\"]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(guard.substring(0,Math.min(200,guard.length())));if(encoding.find())DocumentContract.require(encoding.group(1).equalsIgnoreCase("utf-8"),"xml_encoding_unsupported");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("external_entity_denied");});
        Document d=b.parse(new ByteArrayInputStream(data));checkTree(d,0,new int[]{0});return d;
    }
    static void checkTree(Node n,int depth,int[] count){DocumentContract.require(depth<100&&++count[0]<200000,"xml_complexity_limit");for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())checkTree(c,depth+1,count);}
    static List<Element> all(Node n,String namespace,String local){NodeList list=((n instanceof Document)?((Document)n).getElementsByTagNameNS(namespace,local):((Element)n).getElementsByTagNameNS(namespace,local));List<Element> out=new ArrayList<>();for(int i=0;i<list.getLength();i++)out.add((Element)list.item(i));return out;}
    static String text(Node n,String ns,String tag){StringBuilder s=new StringBuilder();for(Element e:all(n,ns,tag))s.append(e.getTextContent());return s.toString();}
    static JSONObject unit(String text,JSONObject locator)throws Exception{return new JSONObject().put("text",text).put("locator",locator);}
    List<JSONObject> units(JSONObject p)throws Exception{
        List<JSONObject> out=new ArrayList<>();
        if(format.equals("docx")){int i=0;for(Element e:all(xml("word/document.xml"),W,"p"))out.add(unit(wordText(e),new JSONObject().put("paragraph",++i)));}
        else if(format.equals("pptx")){
            Document presentation=xml("ppt/presentation.xml");Map<String,String> rels=relationships("ppt/_rels/presentation.xml.rels","ppt/");int slide=0;
            for(Element id:all(presentation,"*","sldId")){slide++;String path=rels.get(id.getAttributeNS(R,"id"));Document d=xml(path);int shape=0;for(Element e:all(d,"http://schemas.openxmlformats.org/presentationml/2006/main","sp"))out.add(unit(text(e,"http://schemas.openxmlformats.org/drawingml/2006/main","t"),new JSONObject().put("slide",slide).put("shape",++shape)));}
        }else{
            List<String> shared=new ArrayList<>();if(entries.containsKey("xl/sharedStrings.xml"))for(Element e:all(xml("xl/sharedStrings.xml"),S,"si"))shared.add(text(e,S,"t"));
            Map<String,String> sheets=sheets();String selected=p.optString("sheet","");if(!selected.isEmpty())DocumentContract.require(sheets.containsKey(selected),"sheet_missing");
            int[] range=range(p.optString("range","A1:XFD1048576"));
            for(Map.Entry<String,String> sheet:sheets.entrySet())if(selected.isEmpty()||selected.equals(sheet.getKey()))for(Element cell:all(xml(sheet.getValue()),S,"c")){
                String address=cell.getAttribute("r");int[] point=cell(address);if(point[0]<range[0]||point[0]>range[2]||point[1]<range[1]||point[1]>range[3])continue;
                String type=cell.getAttribute("t"),value=text(cell,S,"v"),formula=text(cell,S,"f");
                if(type.equals("s")){int index=Integer.parseInt(value);DocumentContract.require(index>=0&&index<shared.size(),"invalid_shared_string");value=shared.get(index);}else if(type.equals("inlineStr"))value=text(cell,S,"t");
                JSONObject item=unit(value,new JSONObject().put("sheet",sheet.getKey()).put("cell",address)).put("cell_type",type.isEmpty()?"number":type);
                if(!formula.isEmpty())item.put("formula",formula).put("cached_value",value).put("formula_recalculated",false);out.add(item);
            }
        }
        int chars=0;for(JSONObject u:out){chars+=u.getString("text").length();DocumentContract.require(chars<=4000000&&out.size()<=100000,"document_complexity_limit");}return out;
    }
    static String wordText(Element e){StringBuilder b=new StringBuilder();wordWalk(e,b);return b.toString();}
    static void wordWalk(Node n,StringBuilder b){if(n instanceof Element&&W.equals(n.getNamespaceURI())){if("t".equals(n.getLocalName())){b.append(n.getTextContent());return;}if("tab".equals(n.getLocalName()))b.append('\t');if("br".equals(n.getLocalName())||"cr".equals(n.getLocalName()))b.append('\n');}for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())wordWalk(c,b);}
    Map<String,String> relationships(String path,String base)throws Exception{Map<String,String> out=new LinkedHashMap<>();for(Element e:all(xml(path),"*","Relationship")){if(e.getAttribute("TargetMode").equals("External"))continue;String target=e.getAttribute("Target");DocumentContract.require(!target.contains("..")&&!target.contains(":"),"unsupported_relationship");out.put(e.getAttribute("Id"),target.startsWith("/")?target.substring(1):base+target);}return out;}
    Map<String,String> sheets()throws Exception{Map<String,String> rels=relationships("xl/_rels/workbook.xml.rels","xl/"),out=new LinkedHashMap<>();for(Element e:all(xml("xl/workbook.xml"),S,"sheet")){String path=rels.get(e.getAttributeNS(R,"id"));DocumentContract.require(path!=null,"invalid_relationship");out.put(e.getAttribute("name"),path);}return out;}
    void editable()throws Exception{
        for(String k:entries.keySet())DocumentContract.require(!k.startsWith("_xmlsignatures/")&&!k.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"),"signed_or_macro_document");
        if(format.equals("docx")){Document d=xml("word/document.xml");for(String tag:List.of("ins","del","fldChar","fldSimple","sdt","altChunk"))DocumentContract.require(all(d,W,tag).isEmpty(),"complex_edit_unsupported");}
    }
    JSONObject edit(JSONObject p)throws Exception{
        editable();String action=p.getString("action");
        if(format.equals("docx")&&action.equals("word.replace_text")){
            Document d=xml("word/document.xml");List<Element> paras=all(d,W,"p");int index=p.getInt("paragraph")-1;DocumentContract.require(index>=0&&index<paras.size(),"invalid_range");Element para=paras.get(index);
            DocumentContract.require(all(para,W,"tab").isEmpty()&&all(para,W,"br").isEmpty(),"complex_edit_unsupported");
            String before=text(para,W,"t"),old=p.getString("old"),replacement=p.getString("replacement");DocumentContract.require(!old.isEmpty(),"empty_match");int start=before.indexOf(old);DocumentContract.require(start>=0&&before.indexOf(old,start+1)<0,"ambiguous_or_missing_match");
            int end=start+old.length(),offset=0;boolean inserted=false;for(Element t:all(para,W,"t")){String value=t.getTextContent();int stop=offset+value.length();if(stop>start&&offset<end){String v=value.substring(0,Math.max(0,start-offset));if(!inserted){v+=replacement;inserted=true;}v+=value.substring(Math.min(value.length(),Math.max(0,end-offset)));t.setTextContent(v);t.setAttributeNS("http://www.w3.org/XML/1998/namespace","xml:space","preserve");}offset=stop;}
            DocumentContract.require(text(para,W,"t").equals(before.substring(0,start)+replacement+before.substring(end)),"verification_failed");putXml("word/document.xml",d);
            return new JSONObject().put("verified",true).put("formatting","inserted_text_uses_first_matched_run");
        }
        if(format.equals("xlsx")&&action.equals("sheet.set_cells")){
            String path=sheets().get(p.getString("sheet"));DocumentContract.require(path!=null,"sheet_missing");Document d=xml(path);List<Element> roots=all(d,S,"sheetData");DocumentContract.require(roots.size()==1,"invalid_package");Element data=roots.get(0);JSONArray changes=p.getJSONArray("cells");DocumentContract.require(changes.length()>0&&changes.length()<=100,"edit_budget_exceeded");
            for(int i=0;i<changes.length();i++){
                JSONObject change=changes.getJSONObject(i);Iterator<String> changeKeys=change.keys();while(changeKeys.hasNext())DocumentContract.require(Set.of("cell","type","value").contains(changeKeys.next()),"unsupported_cell_field");String address=change.getString("cell");int[] point=cell(address);Element row=null,target=null;
                for(Element e:all(data,S,"row"))if(e.getAttribute("r").equals(Integer.toString(point[1])))row=e;
                if(row==null){row=d.createElementNS(S,"row");row.setAttribute("r",Integer.toString(point[1]));Node next=null;for(Element e:all(data,S,"row"))if(Integer.parseInt(e.getAttribute("r"))>point[1]){next=e;break;}data.insertBefore(row,next);}
                for(Element e:all(row,S,"c"))if(e.getAttribute("r").equals(address))target=e;
                if(target==null){target=d.createElementNS(S,"c");target.setAttribute("r",address);Node next=null;for(Element e:all(row,S,"c"))if(cell(e.getAttribute("r"))[0]>point[0]){next=e;break;}row.insertBefore(target,next);}
                for(Element f:all(target,S,"f"))DocumentContract.require(!f.hasAttribute("t"),"shared_or_array_formula_unsupported");
                for(String tag:List.of("f","v","is"))for(Element e:all(target,S,tag))e.getParentNode().removeChild(e);
                String type=change.optString("type","string");target.removeAttribute("t");
                if(type.equals("formula")){String formula=change.getString("value");DocumentContract.require(!formula.contains("[")&&!formula.contains("]")&&!formula.startsWith("="),"external_or_invalid_formula");Element f=d.createElementNS(S,"f");f.setTextContent(formula);target.appendChild(f);}
                else if(type.equals("number")||type.equals("boolean")){String value=change.getString("value");if(type.equals("number"))DocumentContract.require(Double.isFinite(Double.parseDouble(value)),"invalid_number");else{DocumentContract.require(value.equals("0")||value.equals("1"),"invalid_boolean");target.setAttribute("t","b");}Element v=d.createElementNS(S,"v");v.setTextContent(value);target.appendChild(v);}
                else{DocumentContract.require(type.equals("string"),"type_unsupported");target.setAttribute("t","inlineStr");Element is=d.createElementNS(S,"is"),t=d.createElementNS(S,"t");t.setAttributeNS("http://www.w3.org/XML/1998/namespace","xml:space","preserve");t.setTextContent(change.getString("value"));is.appendChild(t);target.appendChild(is);}
            }
            // Dimensions are hints; removing them avoids stale bounds after adding cells.
            for(Element e:all(d,S,"dimension"))e.getParentNode().removeChild(e);putXml(path,d);
            Document relationships=xml("xl/_rels/workbook.xml.rels");for(Element relation:all(relationships,"*","Relationship"))if(relation.getAttribute("Type").endsWith("/calcChain")){String target=relation.getAttribute("Target");if(!target.contains(".."))entries.remove(target.startsWith("/")?target.substring(1):"xl/"+target);relation.getParentNode().removeChild(relation);}putXml("xl/_rels/workbook.xml.rels",relationships);
            Document types=xml("[Content_Types].xml");for(Element type:all(types,"*","Override"))if(type.getAttribute("ContentType").endsWith("spreadsheetml.calcChain+xml"))type.getParentNode().removeChild(type);putXml("[Content_Types].xml",types);
            Document workbook=xml("xl/workbook.xml");List<Element> calculations=all(workbook,S,"calcPr");Element calc=calculations.isEmpty()?workbook.createElementNS(S,"calcPr"):calculations.get(0);calc.setAttribute("fullCalcOnLoad","1");calc.setAttribute("forceFullCalc","1");if(calculations.isEmpty())workbook.getDocumentElement().appendChild(calc);putXml("xl/workbook.xml",workbook);
            return new JSONObject().put("verified",true).put("formula_recalculated",false).put("dependent_cached_values","may_be_stale");
        }
        throw new IllegalArgumentException("operation_unsupported");
    }
    void putXml(String name,Document d)throws Exception{TransformerFactory f=TransformerFactory.newInstance();Transformer t=f.newTransformer();t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");ByteArrayOutputStream out=new ByteArrayOutputStream();t.transform(new DOMSource(d),new StreamResult(out));entries.put(name,out.toByteArray());}
    byte[] bytes()throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();DocumentContract.require(out.size()<=DocumentContract.MAX_BYTES,"output_size_limit");}}return out.toByteArray();}
    static int[] cell(String address){DocumentContract.require(address.matches("[A-Z]{1,3}[1-9][0-9]{0,6}"),"invalid_cell");int i=0,column=0;while(i<address.length()&&Character.isLetter(address.charAt(i)))column=column*26+address.charAt(i++)-'A'+1;int row=Integer.parseInt(address.substring(i));DocumentContract.require(column<=16384&&row<=1048576,"invalid_cell");return new int[]{column,row};}
    static int[] range(String value){String[] parts=value.split(":",-1);DocumentContract.require(parts.length==1||parts.length==2,"invalid_range");int[] a=cell(parts[0]),b=cell(parts[parts.length-1]);DocumentContract.require(a[0]<=b[0]&&a[1]<=b[1],"invalid_range");return new int[]{a[0],a[1],b[0],b[1]};}
    static byte[] create(String format,JSONObject p)throws Exception{
        String root="<?xml version=\"1.0\" encoding=\"UTF-8\"?>";Map<String,String> entries=new LinkedHashMap<>();
        String part=format.equals("docx")?"word/document.xml":"xl/workbook.xml",type=format.equals("docx")?"wordprocessingml.document":"spreadsheetml.sheet";
        entries.put("_rels/.rels",root+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+R+"/officeDocument\" Target=\""+part+"\"/></Relationships>");
        entries.put("[Content_Types].xml",root+"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/"+part+"\" ContentType=\"application/vnd.openxmlformats-officedocument."+type+".main+xml\"/>"+(format.equals("xlsx")?"<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>":"")+"</Types>");
        if(format.equals("docx")){StringBuilder body=new StringBuilder();JSONArray paragraphs=p.optJSONArray("paragraphs");if(paragraphs==null)paragraphs=new JSONArray().put(p.optString("text",""));for(int i=0;i<paragraphs.length();i++)body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(escape(paragraphs.getString(i))).append("</w:t></w:r></w:p>");entries.put(part,root+"<w:document xmlns:w=\""+W+"\"><w:body>"+body+"</w:body></w:document>");}
        else{String name=p.optString("sheet","Sheet1");DocumentContract.require(!name.isEmpty()&&name.length()<=31&&!name.matches(".*[\\[\\]:*?/\\\\].*"),"invalid_sheet_name");entries.put(part,root+"<workbook xmlns=\""+S+"\" xmlns:r=\""+R+"\"><sheets><sheet name=\""+escape(name)+"\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");entries.put("xl/_rels/workbook.xml.rels",root+"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+R+"/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");entries.put("xl/worksheets/sheet1.xml",root+"<worksheet xmlns=\""+S+"\"><sheetData/></worksheet>");}
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,String> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        OfficeDocument doc=new OfficeDocument(out.toByteArray(),format);if(format.equals("xlsx")&&p.has("cells"))doc.edit(new JSONObject(p.toString()).put("sheet",p.optString("sheet","Sheet1")).put("action","sheet.set_cells"));return doc.bytes();
    }
    static String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
