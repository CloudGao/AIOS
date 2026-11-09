import java.nio.file.*;
import java.nio.charset.*;
import java.util.Arrays;
import os.aios.core.CompatFiles;
import io.github.muntashirakon.crypto.spake2.*;

public final class ActivationCompatTest {
 static void require(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempFile("aios-utf8-", ".txt");
  try{
   String text="中文繁體🙂\nagent";CompatFiles.writeString(file,text);require(CompatFiles.readString(file).equals(text));
   CompatFiles.writeString(file,"+",StandardOpenOption.APPEND);require(CompatFiles.readString(file).equals(text+"+"));
   Files.write(file,new byte[]{(byte)0xc3,0x28});boolean rejected=false;try{CompatFiles.readString(file);}catch(CharacterCodingException expected){rejected=true;}require(rejected);
  }finally{Files.delete(file);}
  byte[] aliceName="adb pair client\0".getBytes(StandardCharsets.UTF_8),bobName="adb pair server\0".getBytes(StandardCharsets.UTF_8);
  for(boolean correct:new boolean[]{true,false}){
   Spake2Context alice=new Spake2Context(Spake2Role.Alice,aliceName,bobName),bob=new Spake2Context(Spake2Role.Bob,bobName,aliceName);
   byte[] a=alice.generateMessage("123456".getBytes(StandardCharsets.UTF_8));
   byte[] b=bob.generateMessage((correct?"123456":"654321").getBytes(StandardCharsets.UTF_8));
   require(Arrays.equals(alice.processMessage(b),bob.processMessage(a))==correct);alice.destroy();bob.destroy();
  }
  System.out.println("UTF-8 roundtrip/append/malformed input and SPAKE2 matching/wrong-code checks passed");
 }
}
