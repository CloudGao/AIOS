import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import os.aios.runtime.models.VerifiedModelFile;

public final class VerifiedModelFileTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("aios-model-test-");File file=directory.resolve("model").toFile();
        byte[] good="verified model".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file.toPath(),good);String hash=VerifiedModelFile.hash(file);
        VerifiedModelFile.install(new ByteArrayInputStream(good),file,good.length,hash,()->false);
        check(Arrays.equals(good,Files.readAllBytes(file.toPath())));
        for(int mode=0;mode<5;mode++){
            int test=mode;
            InputStream input=test==4?new InputStream(){public int read()throws IOException{throw new IOException("broken input");}}:new ByteArrayInputStream(test==0?new byte[good.length+1]:test==1?new byte[good.length-1]:test==2?new byte[good.length]:good);
            boolean failed=false;
            try{VerifiedModelFile.install(input,file,good.length,hash,()->test==3);}catch(IOException expected){failed=true;}
            check(failed);check(Arrays.equals(good,Files.readAllBytes(file.toPath())));
            try(var files=Files.list(directory)){check(files.count()==1);}
        }
        Thread.currentThread().interrupt();
        try{VerifiedModelFile.install(new ByteArrayInputStream(good),file,good.length,hash,()->false);throw new AssertionError("interrupt ignored");}catch(InterruptedIOException expected){check(true);}finally{Thread.interrupted();}
        if(args.length==2){
            File face=new File(args[0]),speaker=new File(args[1]);
            check(face.length()==3628816L);check(VerifiedModelFile.hash(face).equals("3e82b513e65d00b56c35b572f964a594fdbd6e56717361bc0b64cbaf9e140a4d"));
            check(speaker.length()==28281138L);check(VerifiedModelFile.hash(speaker).equals("f682b514c05d947ee3fa91cd6ec6c5c7543479a128373fa29b1faedccd21fd11"));
        }
        Files.delete(file.toPath());Files.delete(directory);System.out.println("VerifiedModelFile: "+checks+" behavior assertions passed");
    }
}
