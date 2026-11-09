package os.aios.core;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;

/** UTF-8 text IO available on every supported Android runtime. */
public final class CompatFiles {
 private CompatFiles() {}
 public static String readString(Path path)throws IOException{return readString(path,StandardCharsets.UTF_8);}
 public static String readString(Path path,Charset charset)throws IOException{
  return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
 }
 public static Path writeString(Path path,CharSequence text,OpenOption... options)throws IOException{return writeString(path,text,StandardCharsets.UTF_8,options);}
 public static Path writeString(Path path,CharSequence text,Charset charset,OpenOption... options)throws IOException{
  ByteBuffer bytes=charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
  byte[] data=new byte[bytes.remaining()];bytes.get(data);return Files.write(path,data,options);
 }
}
