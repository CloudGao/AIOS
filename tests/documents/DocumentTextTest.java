package os.aios.core.documents;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class DocumentTextTest {
    static int count;
    static void check(boolean b){if(!b)throw new AssertionError("text assertion "+count);count++;}
    public static void main(String[] args)throws Exception{
        for(String source:List.of("a,b\r\nc,d\r\n","\"a,b\",\"x\"\"y\"\n","one,\"line1\nline2\"\n","a,","\"\"","")){
            List<List<String>> table=DocumentEngine.table(source,',');String normalized=DocumentEngine.encodeTable(table,',',source.contains("\r\n")?"\r\n":"\n",source.endsWith("\n"));check(table.equals(DocumentEngine.table(normalized,',')));
        }
        check(DocumentEngine.table("one,\"line1\nline2\"",',').get(0).get(1).equals("line1\nline2"));
        check(DocumentEngine.table("\"a\"\"b\"",',').get(0).get(0).equals("a\"b"));
        for(String bad:List.of("\"unclosed","a\"b,c","\"closed\"bad")){try{DocumentEngine.table(bad,',');throw new AssertionError("invalid CSV accepted");}catch(IllegalArgumentException expected){count++;}}
        check(DocumentEngine.decode("\ufeff中文\r\n".getBytes(StandardCharsets.UTF_8)).startsWith("\ufeff"));
        try{DocumentEngine.decode(new byte[]{(byte)0xff,(byte)0xfe});throw new AssertionError("encoding accepted");}catch(java.nio.charset.CharacterCodingException expected){count++;}
        check(DocumentEngine.splitSurrogate("a😀b",2));check(!DocumentEngine.splitSurrogate("a😀b",1));check(DocumentEngine.findIgnoreCase("ABC中文","bc",0)==1);
        System.out.println("PASS "+count+" text/CSV assertions");
    }
}
