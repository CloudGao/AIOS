package os.aios.codex;
import java.io.*;
public class RuntimeProtocolReaderTest {
 static int checks;
 static void check(boolean value){if(!value)throw new AssertionError();checks++;}
 static String read(String s)throws Exception{return new RuntimeProtocolReader(new StringReader(s)).readFrame();}
 public static void main(String[] args)throws Exception{
  String request="{\"id\":1,\"method\":\"item/tool/call\",\"params\":{\"arguments\":{\"text\":\"data:image/png;base64,AAAA\"}}}";
  check(read(request).equals(request));
  String small="{\"id\":1,\"result\":{\"text\":\"hello\\nworld\",\"array\":[null,1,true,{\"url\":\"https://example.org\"}]}}";
  check(read(small).equals(small));
  check(read("{\"result\":{\"url\":\"data:image/png;base64,AAAA\"}}").contains("media retained"));
  check(read("{\"result\":{\"url\":\"data:image\\/png;base64,AAAA\"}}").contains("media retained"));
  String head="{\"id\":1,\"result\":{\"image\":\"data:image/png;base64,";
  String tail="\",\"text\":\"still readable\"}}\n";
  Reader huge=new Reader(){long position;public int read(char[] b,int off,int n){if(position>=head.length()+160L*1024*1024+tail.length())return -1;int count=0;while(count<n&&position<head.length()+160L*1024*1024+tail.length()){b[off+count++]=position<head.length()?head.charAt((int)position):position<head.length()+160L*1024*1024?'A':tail.charAt((int)(position-head.length()-160L*1024*1024));position++;}return count;}public void close(){}};
  String projected=new RuntimeProtocolReader(new BufferedReader(huge)).readFrame();
  check(projected.length()<200&&projected.contains("still readable"));
  RuntimeProtocolReader multiple=new RuntimeProtocolReader(new StringReader("{}\n{\"result\":true}\n"));
  check(multiple.readFrame().equals("{}"));check(multiple.readFrame().equals("{\"result\":true}"));check(multiple.readFrame()==null);
  try{read("{\"result\":\""+"x".repeat(RuntimeProtocolReader.MAX_CHARS)+"\"}");throw new AssertionError();}catch(IOException expected){checks++;}
  try{read("{\"result\":\"data:image/png;base64,AAA");throw new AssertionError();}catch(IOException expected){checks++;}
  System.out.println("Runtime protocol projection: "+checks+" checks passed (160 MiB media, 48 MiB heap)");
 }
}
